# 统一接口配置系统

> 业务系统集成中心 — 通过前端配置接口参数，实现项目数据对接

## 快速开始

```bash
mvn clean package -DskipTests
java -jar target/integration-config-system-1.0.0.jar
```

| 服务 | 地址 |
|------|------|
| 管理后台 | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| H2 控制台 | http://localhost:8080/h2-console |
| 默认账号 | admin / admin123 |

## 文档目录

| 文档 | 说明 |
|------|------|
| **[docs/README.md](docs/README.md)** | 完整使用手册（**必读**） |
| `docs/rbac-design.md` | RBAC 权限系统详细设计 |
| `docs/rbac-task-summary_20260418.md` | 权限系统开发记录 |

## 核心功能

- **接口配置管理** — 可视化配置接口参数，支持占位符替换、认证、缓存、重试、限流
- **在线调试** — 无需编写代码，直接在页面测试接口调用
- **调用日志 & 审计日志** — 完整的请求/响应记录和操作审计
- **实时大盘** — 7 天趋势图、Top API 排行、CPU/内存监控
- **RBAC 权限** — 菜单/按钮级权限控制
- **API 版本控制** — 多版本共存、平滑迁移
- **Word 文档导出** — 一键导出完整接口文档
- **Java / Python / C# SDK** — 一行代码调用已配置接口

## 技术栈

Spring Boot 3.2 · MyBatis-Plus · Redis · H2/MySQL · Thymeleaf · SpringDoc OpenAPI
