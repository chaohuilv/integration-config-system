# 统一接口配置系统

> 业务系统集成中心 - 通过前端配置接口参数，实现项目数据对接

## 🎯 项目简介

本项目旨在解决企业级项目中**多个业务系统需要对接大量外部接口**的问题。传统的做法是每个业务系统各自编写接口调用代码，导致：

- 🔴 代码重复：一个接口在多个项目中重复实现
- 🔴 配置分散：接口配置散落在各个项目中，难以统一管理
- 🔴 修改困难：接口变更需要修改多个项目的代码
- 🔴 难以监控：无法统一查看所有接口的调用情况

**统一接口配置系统**将所有接口配置集中管理，通过可视化界面配置接口参数，业务系统只需通过 SDK 调用即可。

## ✨ 核心功能

| 功能 | 说明 |
|------|------|
| 📋 **接口配置管理** | 可视化配置接口名称、编码、方法、URL、请求头、参数模板等 |
| 🧪 **在线接口调试** | 无需编写代码，直接在页面测试接口调用 |
| 📊 **调用日志记录** | 完整记录每次接口调用的请求、响应、耗时、状态 |
| 🔄 **响应缓存** | 支持配置接口缓存，减少重复调用 |
| 🔐 **认证支持** | 支持 Bearer Token、Basic Auth、API Key 等认证方式 |
| 💾 **数据持久化** | H2/MySQL 双模式，开箱即用 |
| 💻 **Java SDK** | 业务系统一行代码调用已配置的接口 |
| 🎨 **可视化界面** | 简洁美观的管理后台，无需手动操作数据库 |

## 🏗️ 技术栈

- **后端**: Spring Boot 3.2 + JPA + H2/MySQL
- **前端**: 原生 HTML/CSS/JS，无依赖
- **HTTP**: RestTemplate
- **工具**: Hutool + Jackson + Lombok
- **JDK**: 17+

## 🚀 快速开始

### 1. 编译项目

```bash
cd integration-config-system
mvn clean package -DskipTests
```

### 2. 启动服务

```bash
java -jar target/integration-config-system-1.0.0.jar
```

服务启动后访问：
- **管理后台**: http://localhost:8080
- **H2 控制台**: http://localhost:8080/h2-console

### 3. 配置接口

1. 打开 http://localhost:8080
2. 点击「新建接口」
3. 填写接口信息：
   - 接口名称：`获取用户信息`
   - 接口编码：`user-get`
   - 请求方法：`GET`
   - 目标URL：`https://jsonplaceholder.typicode.com/users/{{id}}`
4. 保存

### 4. 业务系统集成

**Maven 引入 SDK**：
```xml
<dependency>
    <groupId>com.integration</groupId>
    <artifactId>integration-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

**代码调用**：
```java
import com.integration.sdk.IntegrationClient;
import java.util.Map;

public class Demo {
    public static void main(String[] args) {
        // 初始化客户端
        IntegrationClient client = new IntegrationClient("http://localhost:8080");
        
        // 调用接口（通过编码）
        String result = client.invoke("user-get", Map.of("id", 1));
        System.out.println("结果: " + result);
    }
}
```

## 📁 项目结构

```
integration-config-system/
├── src/main/java/com/integration/config/
│   ├── IntegrationConfigApplication.java   # 启动类
│   ├── config/                              # 配置类
│   │   ├── IntegrationConfig.java          # 全局配置
│   │   ├── RestTemplateConfig.java          # HTTP 客户端配置
│   │   └── CorsConfig.java                  # 跨域配置
│   ├── controller/                          # 控制器
│   │   ├── ApiConfigController.java        # 接口配置管理
│   │   ├── InvokeController.java            # 接口调用
│   │   └── GlobalExceptionHandler.java     # 全局异常处理
│   ├── service/                            # 业务服务
│   │   ├── ApiConfigService.java           # 配置管理服务
│   │   └── HttpInvokeService.java           # HTTP 调用服务
│   ├── entity/                             # 实体类
│   │   ├── ApiConfig.java                  # 接口配置实体
│   │   └── InvokeLog.java                  # 调用日志实体
│   ├── repository/                         # 数据访问
│   ├── dto/                                # 数据传输对象
│   ├── enums/                              # 枚举类
│   └── util/                               # 工具类
├── src/main/resources/
│   ├── application.yml                     # 配置文件
│   └── static/index.html                   # 管理后台页面
├── client-sdk/                              # 客户端 SDK
│   └── integration-sdk.java
└── pom.xml
```

## 🔌 API 接口

### 配置管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/config` | POST | 创建接口配置 |
| `/api/config/{id}` | PUT | 更新接口配置 |
| `/api/config/{id}` | DELETE | 删除接口配置 |
| `/api/config/{id}` | GET | 获取接口详情 |
| `/api/config/code/{code}` | GET | 根据编码获取配置 |
| `/api/config/page` | GET | 分页查询 |
| `/api/config/active` | GET | 获取所有启用接口 |
| `/api/config/{id}/toggle` | POST | 切换状态 |

### 接口调用

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/invoke` | POST | 调用接口 |
| `/api/invoke/logs` | GET | 查询调用日志 |
| `/api/invoke/logs/{apiCode}/recent` | GET | 最近调用记录 |

### 请求示例

**调用接口**：
```bash
curl -X POST http://localhost:8080/api/invoke \
  -H "Content-Type: application/json" \
  -d '{
    "apiCode": "user-get",
    "params": {"id": 1}
  }'
```

**响应示例**：
```json
{
  "code": 200,
  "message": "调用成功",
  "data": {
    "success": true,
    "statusCode": 200,
    "data": {...},
    "costTime": 245,
    "traceId": "abc123"
  }
}
```

## 🔧 配置说明

### application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:./data/integration_config  # H2 数据库路径
    driver-class-name: org.h2.Driver
    username: sa
    password:
  
  # 切换 MySQL
  # datasource:
  #   url: jdbc:mysql://localhost:3306/integration_config
  #   driver-class-name: com.mysql.cj.jdbc.Driver
  #   username: root
  #   password: your_password

integration:
  http-connect-timeout: 10000    # 连接超时（毫秒）
  http-read-timeout: 30000         # 读取超时（毫秒）
  log-request: true               # 是否记录请求
  log-response: true              # 是否记录响应
  max-retry: 3                   # 最大重试次数
```

## 📝 接口配置说明

### 占位符替换

在 URL、请求体、请求参数中可以使用 `{{paramName}}` 占位符：

```
URL: https://api.example.com/users/{{userId}}
请求体: {"name": "{{name}}", "email": "{{email}}"}
```

调用时传入参数：
```java
client.invoke("user-create", 
    Map.of("userId", 1001, "name", "张三", "email", "zhangsan@example.com"));
```

### 认证配置

| 认证方式 | 配置格式 | 说明 |
|----------|----------|------|
| Bearer Token | `Token值` | 自动添加 `Authorization: Bearer xxx` |
| Basic Auth | `username:password` | Base64 编码后添加 |
| API Key | `HeaderName:value` 或直接是值 | 添加自定义请求头 |

## 🎨 界面预览

管理后台包含以下功能：
- **接口列表**: 查看、新建、编辑、删除、启用/禁用接口
- **接口调试**: 选择接口、填写参数、发送请求、查看响应
- **调用日志**: 查看历史调用记录、统计成功率
- **SDK文档**: 查看客户端集成方式

## 📄 License

MIT License
