package com.integration.config.service;

import com.integration.config.entity.config.ApiConfig;
import com.integration.config.enums.Status;
import com.integration.config.repository.config.ApiConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 接口文档 Word 导出服务（字符串替换版）
 * 原理：
 * 1. 读取 .docx 模板（ZIP），直接操作原始 document.xml 字符串
 * 2. 用正则找出第一个含 {{...}} 的段落作为接口单元模板
 * 3. 对每个接口：克隆该段落单元 → 替换占位符为实际值 → 拼接
 * 4. 重打包为 .docx 输出
 *
 * 零 POI 依赖，零额外 XML API 调用，天然兼容任何 JDK 版本。
 */
@Service
public class ApiDocExportService {

    @Autowired
    private ApiConfigRepository apiConfigRepository;

    private static final String TEMPLATE_NAME = "接口文档模板.docx";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ================================================================
    // 对外 API
    // ================================================================

    public byte[] exportAll() {
        return exportGroup(null);
    }

    public byte[] exportGroup(String groupName) {
        List<ApiConfig> apis = (groupName != null && !groupName.isBlank())
                ? apiConfigRepository.findByStatusAndGroupNameOrderByCreatedAtDesc(Status.ACTIVE, groupName)
                : apiConfigRepository.findByStatusOrderByGroupNameAscCreatedAtDesc(Status.ACTIVE);

        if (apis.isEmpty()) {
            throw new RuntimeException("没有找到启用的接口");
        }

        Map<String, List<ApiConfig>> grouped = apis.stream()
                .collect(Collectors.groupingBy(
                        a -> (a.getGroupName() != null && !a.getGroupName().isBlank()) ? a.getGroupName() : "未分组",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        try {
            return generateDocx(grouped, apis.size());
        } catch (Exception e) {
            throw new RuntimeException("生成 Word 文档失败: " + e.getMessage(), e);
        }
    }

    // ================================================================
    // 核心生成逻辑（字符串拼接）
    // ================================================================

    private byte[] generateDocx(Map<String, List<ApiConfig>> grouped, int totalCount)
            throws Exception {

        byte[] templateZip = loadTemplate();
        Map<String, String> zipEntries = unzipToMap(templateZip);
        String docXml = zipEntries.get("word/document.xml");

        if (docXml == null) {
            throw new RuntimeException("模板 ZIP 中未找到 word/document.xml");
        }

        // ---------- 分析模板结构 ----------
        // 用正则找出含 {{...}} 的段落范围
        Pattern placeholderPat = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Pattern paraPat = Pattern.compile("(?<=<w:p[^>]*>)[\\s\\S]*?(?=</w:p>)");

        // 找第一个含 {{ 的 <w:p>...</w:p> 的起止索引（字符串索引）
        Matcher paraMatcher = paraPat.matcher(docXml);
        int firstPlaceholderParaStart = -1;
        int firstPlaceholderParaEnd = -1;
        int lastPlaceholderParaEnd = -1;
        int paraIndex = 0;

        // 扫描所有段落，找占位符所在段落
        List<int[]> placeholderParas = new ArrayList<>();
        while (paraMatcher.find()) {
            String paraContent = paraMatcher.group();
            Matcher pm = placeholderPat.matcher(paraContent);
            if (pm.find()) {
                if (firstPlaceholderParaStart == -1) {
                    firstPlaceholderParaStart = paraMatcher.start();
                    firstPlaceholderParaEnd = paraMatcher.end();
                }
                lastPlaceholderParaEnd = paraMatcher.end();
                placeholderParas.add(new int[]{paraMatcher.start(), paraMatcher.end()});
            }
            paraIndex++;
        }

        if (firstPlaceholderParaStart == -1) {
            throw new RuntimeException("模板 document.xml 中未找到 {{...}} 占位符");
        }

        // 接口段落模板（从第一个到最后一个含占位符的段落）
        String interfaceTemplateBlock = docXml.substring(firstPlaceholderParaStart, lastPlaceholderParaEnd);

        // 封面（第一个占位符段落之前的所有内容）
        String coverPart = docXml.substring(0, firstPlaceholderParaStart);

        // 统计部分（最后一个占位符段落之后的内容）
        String summaryPart = docXml.substring(lastPlaceholderParaEnd);

        // ---------- 构建各接口的 XML 片段 ----------
        StringBuilder allInterfaceBlocks = new StringBuilder();
        int seq = 1;

        for (Map.Entry<String, List<ApiConfig>> groupEntry : grouped.entrySet()) {
            String groupName = groupEntry.getKey();
            List<ApiConfig> groupApis = groupEntry.getValue();

            // 分组标题（Word 段落：深蓝色粗体）
            String groupPara = buildGroupPara(groupName, groupApis.size());
            allInterfaceBlocks.append(groupPara);

            // 空行
            allInterfaceBlocks.append(buildSpacingPara(400));

            // 每个接口
            for (ApiConfig api : groupApis) {
                Map<String, String> fields = buildFieldMap(api, seq, totalCount, grouped.keySet());
                String apiBlock = substituteTemplate(interfaceTemplateBlock, fields);
                allInterfaceBlocks.append(apiBlock);

                // 接口间空行
                allInterfaceBlocks.append(buildSpacingPara(200));
                seq++;
            }
        }

        // 填充统计部分占位符
        Map<String, String> summaryFields = new HashMap<>();
        summaryFields.put("totalCount", String.valueOf(totalCount));
        summaryFields.put("groupList", String.join("、", grouped.keySet()));
        summaryFields.put("exportTime", DTF.format(LocalDateTime.now()));
        String finalSummary = substituteTemplate(summaryPart, summaryFields);

        // ---------- 组装完整 document.xml ----------
        String newDocXml = coverPart + allInterfaceBlocks.toString() + finalSummary;

        // ---------- 重新打包为 docx ----------
        zipEntries.put("word/document.xml", newDocXml);
        return zipToBytes(zipEntries);
    }

    // ================================================================
    // 占位符替换
    // ================================================================

    private String substituteTemplate(String xml, Map<String, String> fields) {
        String result = xml;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", escapeXmlText(e.getValue()));
        }
        return result;
    }

    private String escapeXmlText(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ================================================================
    // 构建分组标题段落
    // ================================================================

    /**
     * 构建分组标题的 Word XML 段落。
     * 样式：深蓝色(#1F5C99)、粗体、14pt。
     */
    private String buildGroupPara(String groupName, int count) {
        String text = "━━ " + groupName + " （" + count + " 个接口）";
        // 注意：Word XML 中尖括号需要转义，但文本内容本身不需要（只在属性值里需要）
        return "<w:p>" +
                "<w:pPr>" +
                "<w:spacing w:before=\"400\" w:after=\"0\"/>" +
                "</w:pPr>" +
                "<w:r>" +
                "<w:rPr>" +
                "<w:b/>" +
                "<w:sz w:val=\"28\"/>" +
                "<w:szCs w:val=\"28\"/>" +
                "<w:color w:val=\"1F5C99\"/>" +
                "</w:rPr>" +
                "<w:t>" + escapeXmlText(text) + "</w:t>" +
                "</w:r>" +
                "</w:p>";
    }

    // ================================================================
    // 构建空行段落（用于间距）
    // ================================================================

    private String buildSpacingPara(int before) {
        return "<w:p>" +
                "<w:pPr>" +
                "<w:spacing w:before=\"" + before + "\" w:after=\"0\"/>" +
                "</w:pPr>" +
                "</w:p>";
    }

    // ================================================================
    // 字段映射
    // ================================================================

    private Map<String, String> buildFieldMap(ApiConfig api, int seq, int totalCount, Set<String> groupNames) {
        Map<String, String> fields = new HashMap<>();
        fields.put("name",        nvl(api.getName()));
        fields.put("code",        nvl(api.getCode()));
        fields.put("url",         nvl(api.getUrl()));
        fields.put("method",      api.getMethod() != null ? api.getMethod().name() : "-");
        fields.put("contentType", api.getContentType() != null ? api.getContentType().name() : "-");
        fields.put("timeout",     api.getTimeout() != null ? api.getTimeout().toString() : "30000");
        fields.put("retryCount",  api.getRetryCount() != null ? api.getRetryCount().toString() : "0");
        fields.put("authType",   nvl(api.getAuthType()));
        fields.put("groupName",   nvl(api.getGroupName()));
        fields.put("version",     nvl(api.getVersion()));
        fields.put("latestVersion", boolYesNo(api.getLatestVersion()));
        fields.put("deprecated",  boolYesNo(api.getDeprecated()));
        fields.put("description", nvl(api.getDescription()));
        fields.put("headers",     nvl(api.getHeaders()));
        fields.put("requestParams", nvl(api.getRequestParams()));
        fields.put("requestBody", nvl(api.getRequestBody()));
        fields.put("authInfo",    nvl(api.getAuthInfo()));
        fields.put("enableCache", boolYesNo(api.getEnableCache()));
        fields.put("cacheTime",   api.getCacheTime() != null ? api.getCacheTime().toString() : "-");
        fields.put("status",      api.getStatus() != null ? api.getStatus().name() : "-");
        fields.put("createdAt",   fmt(api.getCreatedAt()));
        fields.put("updatedAt",   fmt(api.getUpdatedAt()));
        return fields;
    }

    // ================================================================
    // 工具方法
    // ================================================================

    private String nvl(String s) {
        return (s != null && !s.isBlank()) ? s : "-";
    }

    private String boolYesNo(Boolean b) {
        return (b != null && b) ? "是" : "否";
    }

    private String fmt(LocalDateTime dt) {
        if (dt == null) return "-";
        return DTF.format(dt);
    }

    // ================================================================
    // 加载模板文件
    // ================================================================

    private byte[] loadTemplate() throws IOException {
        // 1. classpath: resources/static/
        InputStream is = getClass().getResourceAsStream("/static/" + TEMPLATE_NAME);
        if (is != null) {
            byte[] data = is.readAllBytes();
            is.close();
            return data;
        }

        // 2. 备选：resources 根目录（user.dir/src/main/resources/）
        String userDir = System.getProperty("user.dir", "");
        Path[] candidates = {
                Paths.get(userDir, TEMPLATE_NAME),
                Paths.get(userDir, "src", "main", "resources", TEMPLATE_NAME),
                Paths.get(userDir, "src", "main", "resources", "static", TEMPLATE_NAME),
        };
        for (Path p : candidates) {
            if (Files.exists(p)) {
                return Files.readAllBytes(p);
            }
        }

        throw new RuntimeException("未找到模板文件: " + TEMPLATE_NAME);
    }

    // ================================================================
    // ZIP 工具
    // ================================================================

    /**
     * 解压 ZIP 到 Map（文件名 → 内容字符串，UTF-8）
     */
    private Map<String, String> unzipToMap(byte[] zipData) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipData), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                }
                zis.closeEntry();
            }
        }
        return entries;
    }

    /**
     * 将 Map 重打包为 ZIP（保持原有条目顺序）
     */
    private byte[] zipToBytes(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zos.putNextEntry(entry);
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
        }
        return bos.toByteArray();
    }
}
