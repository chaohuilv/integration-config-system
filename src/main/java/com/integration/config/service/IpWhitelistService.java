package com.integration.config.service;

import com.integration.config.entity.config.IpWhitelistRule;
import com.integration.config.repository.config.IpWhitelistRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * IP 白名单校验服务
 * 支持单 IP、CIDR 网段、IP 段范围匹配
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IpWhitelistService {

    private final IpWhitelistRuleRepository ipWhitelistRuleRepository;

    private static final Pattern CIDR_PATTERN = Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})/(\\d{1,2})$");
    private static final Pattern IP_RANGE_PATTERN = Pattern.compile("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.)(\\d{1,3})-(\\d{1,3})$");

    /**
     * 校验客户端 IP 是否允许访问
     *
     * @param clientIp  客户端真实 IP（已处理代理链）
     * @param apiCode   接口编码（用于 API 级别规则匹配，可为 null）
     * @return true=允许访问，false=拒绝访问
     */
    public boolean isAllowed(String clientIp, String apiCode) {
        if (clientIp == null || clientIp.isEmpty()) {
            log.warn("[IpWhite] 客户端IP为空，拒绝访问");
            return false;
        }

        // 统一处理 localhost
        String ip = normalizeIp(clientIp);
        if (ip == null) {
            log.warn("[IpWhite] IP格式无效，拒绝访问: {}", clientIp);
            return false;
        }

        // 1. 先检查全局规则
        List<IpWhitelistRule> globalRules = ipWhitelistRuleRepository.findActiveGlobalRules();
        if (!globalRules.isEmpty()) {
            boolean allowed = checkRules(ip, apiCode, globalRules);
            log.debug("[IpWhite] 全局规则检查: ip={}, allowed={}", ip, allowed);
            if (!allowed) {
                return false; // 全局规则明确拒绝
            }
            // 全局白名单允许，跳过 API 规则（避免冲突）
        }

        // 2. 再检查 API 级别规则（如果指定了 apiCode）
        if (apiCode != null && !apiCode.isEmpty()) {
            List<IpWhitelistRule> apiRules = ipWhitelistRuleRepository.findActiveApiRules(apiCode);
            if (!apiRules.isEmpty()) {
                boolean allowed = checkRules(ip, apiCode, apiRules);
                log.debug("[IpWhite] API规则检查: ip={}, apiCode={}, allowed={}", ip, apiCode, allowed);
                return allowed;
            }
        }

        // 3. 无匹配规则 → 允许访问（默认开放）
        return true;
    }

    /**
     * 执行规则匹配
     * 规则按优先级从高到低遍历，第一个匹配的规则决定结果
     * <p>
     * 匹配逻辑：
     * - WHITELIST 模式：IP 命中 → 允许；IP 未命中任何规则 → 继续下一条规则；全部未命中 → 默认允许
     * - BLACKLIST 模式：IP 命中 → 拒绝；IP 未命中 → 继续下一条规则；全部未命中 → 默认允许
     */
    private boolean checkRules(String ip, String apiCode, List<IpWhitelistRule> rules) {
        boolean hasExplicitRule = false;

        for (IpWhitelistRule rule : rules) {
            if (!matchesIp(ip, rule.getIpList())) {
                continue; // 当前规则不匹配，继续
            }

            hasExplicitRule = true;
            // 命中黑名单 → 明确拒绝
            if ("BLACKLIST".equals(rule.getMatchMode())) {
                log.info("[IpWhite] IP {} 命中黑名单规则 [{}]({})，拒绝", ip, rule.getRuleName(), rule.getRuleCode());
                return false;
            }
            // 命中白名单 → 明确允许
            if ("WHITELIST".equals(rule.getMatchMode())) {
                log.info("[IpWhite] IP {} 命中白名单规则 [{}]({})，允许", ip, rule.getRuleName(), rule.getRuleCode());
                return true;
            }
        }

        // 有规则但 IP 未命中任何一条 → 默认允许
        return true;
    }

    /**
     * 判断 IP 是否在规则列表中
     */
    private boolean matchesIp(String ip, String ipList) {
        if (ipList == null || ipList.isEmpty()) {
            return false;
        }

        // 支持换行或逗号分隔
        String[] entries = ipList.split("[\n,]");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            if (matchesSingleEntry(ip, trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 匹配单个 IP 条目（支持单 IP、CIDR、IP 段、localhost）
     */
    private boolean matchesSingleEntry(String ip, String entry) {
        // localhost 特殊处理
        if ("localhost".equalsIgnoreCase(entry) || "127.0.0.1".equals(entry)) {
            return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
        }

        // 单 IP 匹配
        if (isValidIp(entry)) {
            return entry.equals(ip);
        }

        // CIDR 网段匹配
        var cidrMatch = CIDR_PATTERN.matcher(entry);
        if (cidrMatch.matches()) {
            return matchesCidr(ip, cidrMatch.group(1), Integer.parseInt(cidrMatch.group(2)));
        }

        // IP 段匹配 192.168.1.1-192.168.1.255
        var rangeMatch = IP_RANGE_PATTERN.matcher(entry);
        if (rangeMatch.matches()) {
            String prefix = rangeMatch.group(1);
            int start = Integer.parseInt(rangeMatch.group(2));
            int end = Integer.parseInt(rangeMatch.group(3));
            try {
                long ipNum = parseIpToInt(ip);
                long startNum = parseIpToInt(prefix + start);
                long endNum = parseIpToInt(prefix + end);
                return ipNum >= startNum && ipNum <= endNum;
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /**
     * CIDR 网段匹配
     * 例如：10.0.0.0/8 匹配 10.x.x.x
     */
    private boolean matchesCidr(String ip, String networkAddr, int prefixLen) {
        if (prefixLen < 0 || prefixLen > 32) return false;
        try {
            long ipNum = parseIpToInt(ip);
            long networkNum = parseIpToInt(networkAddr);
            long mask = prefixLen == 0 ? 0 : (~0L << (32 - prefixLen));
            return (ipNum & mask) == (networkNum & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将点分十进制 IP 转换为整数
     */
    private long parseIpToInt(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("Invalid IP: " + ip);
        long result = 0;
        for (String part : parts) {
            int octet = Integer.parseInt(part.trim());
            if (octet < 0 || octet > 255) throw new IllegalArgumentException("Invalid IP: " + ip);
            result = (result << 8) | octet;
        }
        return result;
    }

    /**
     * 校验是否为合法 IPv4 地址
     */
    private boolean isValidIp(String ip) {
        if (ip == null) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int n = Integer.parseInt(part.trim());
                if (n < 0 || n > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * 统一 IP 格式
     */
    private String normalizeIp(String ip) {
        if (ip == null) return null;
        String trimmed = ip.trim();
        // 处理 localhost
        if ("localhost".equalsIgnoreCase(trimmed)) {
            return "127.0.0.1";
        }
        // 处理 IPv6 环回地址
        if ("::1".equals(trimmed) || "0:0:0:0:0:0:0:1".equals(trimmed)) {
            return "127.0.0.1";
        }
        // 简单校验：必须包含数字和点
        if (!trimmed.contains(".") && !trimmed.contains(":")) {
            return null;
        }
        return trimmed;
    }

    // ==================== 管理接口 ====================

    public List<IpWhitelistRule> findAll() {
        return ipWhitelistRuleRepository.findAll();
    }

    public List<IpWhitelistRule> findAllActive() {
        return ipWhitelistRuleRepository.findAllActive();
    }

    public Optional<IpWhitelistRule> findById(Long id) {
        return ipWhitelistRuleRepository.findById(id);
    }

    public Optional<IpWhitelistRule> findByCode(String code) {
        return ipWhitelistRuleRepository.findByRuleCode(code);
    }

    @Transactional
    public IpWhitelistRule save(IpWhitelistRule rule) {
        return ipWhitelistRuleRepository.save(rule);
    }

    @Transactional
    public void deleteById(Long id) {
        ipWhitelistRuleRepository.deleteById(id);
    }

    public boolean existsByCodeAndIdNot(String code, Long id) {
        return id != null && ipWhitelistRuleRepository.existsByRuleCodeAndIdNot(code, id);
    }

    public boolean existsByCode(String code) {
        return ipWhitelistRuleRepository.existsByRuleCode(code);
    }

    /**
     * 校验 IP 列表格式是否合法
     * @param ipList IP 列表文本
     * @return 错误信息，无错误返回 null
     */
    public String validateIpList(String ipList) {
        if (ipList == null || ipList.trim().isEmpty()) {
            return "IP 列表不能为空";
        }
        String[] entries = ipList.split("[\n,]");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;
            if ("localhost".equalsIgnoreCase(trimmed) || "127.0.0.1".equals(trimmed)) continue;
            if (isValidIp(trimmed)) continue;
            if (CIDR_PATTERN.matcher(trimmed).matches()) continue;
            if (IP_RANGE_PATTERN.matcher(trimmed).matches()) continue;
            return "无效的 IP 条目: " + trimmed + "（支持格式：单个IP、CIDR如10.0.0.0/8、IP段如192.168.1.1-192.168.1.255、localhost）";
        }
        return null;
    }
}
