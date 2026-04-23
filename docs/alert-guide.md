# 告警通知系统使用指南

> 本文档详细介绍告警通知系统的设计原理、配置方法和使用流程。
> 对应章节：《README.md》第十四章 — 告警通知系统

---

## 一、概述

告警通知系统对接口调用进行实时监控，当关键指标（错误率、响应延迟、请求频率等）超过预设阈值时，自动通过钉钉、企业微信、邮件等渠道发送告警通知，帮助运维人员第一时间发现并处理问题。

### 1.1 核心特性

| 特性 | 说明 |
|------|------|
| **4 种告警类型** | 错误率、延迟、限流、连续失败 |
| **3 种通知渠道** | 钉钉、企微、邮件，并行发送 |
| **自动评估** | 后台每 60 秒自动扫描所有规则 |
| **冷却机制** | 告警后默认 5 分钟内不重复通知 |
| **状态管理** | FIRING → ACKNOWLEDGED → RESOLVED 完整生命周期 |
| **零配置** | 钉钉/企微仅需群机器人 Webhook，无需额外依赖 |

### 1.2 数据模型

```
ALERT_RULE（告警规则）
  ├── 触发条件：类型 + 阈值 + 窗口
  ├── 作用范围：GLOBAL（全局）或 API（指定接口）
  ├── 通知配置：渠道 + Webhook 地址
  └── 状态：ACTIVE / INACTIVE

ALERT_RECORD（告警记录）
  ├── 关联规则：ruleId / ruleCode / ruleName
  ├── 触发指标：alertType / actualValue / thresholdValue
  ├── 通知结果：notifyResult / notifyDetail
  └── 状态：FIRING / ACKNOWLEDGED / RESOLVED
```

---

## 二、告警类型详解

### 2.1 错误率（ERROR_RATE）

**触发逻辑**：在指定时间窗口内，失败调用数 / 总调用数 × 100%

**适用场景**：外部接口不稳定、需要关注整体可用性

**配置示例**：统计窗口 300 秒（5 分钟），阈值 10%
- 5 分钟内共调用 200 次，其中失败 20 次 → 错误率 10%，不告警
- 5 分钟内共调用 200 次，其中失败 21 次 → 错误率 10.5%，触发告警

```
窗口内必须有至少 1 次调用才会计算错误率。
零调用时不会触发（避免冷接口误报）。
```

### 2.2 延迟（LATENCY）

**触发逻辑**：在指定时间窗口内，所有成功调用的平均响应时间（毫秒）

**适用场景**：接口响应变慢、需要关注性能

**配置示例**：统计窗口 60 秒，阈值 3000ms
- 60 秒内平均响应时间 2500ms → 不告警
- 60 秒内平均响应时间 3200ms → 触发告警

```
仅统计成功调用（success = true）的响应时间。
窗口内无成功调用时不会触发。
```

### 2.3 限流（RATE_LIMIT）

**触发逻辑**：在指定时间窗口内，接口调用总次数（无论成功失败）

**适用场景**：防止接口被滥用、监控流量突增

**配置示例**：统计窗口 60 秒，阈值 1000
- 60 秒内共调用 800 次 → 不告警
- 60 秒内共调用 1200 次 → 触发告警

### 2.4 连续失败（CONSECUTIVE_FAIL）

**触发逻辑**：最近 N 次调用全部失败（无任何成功）

**适用场景**：接口完全不可用、配置错误等紧急情况

**配置示例**：阈值 5
- 最近 5 次调用：成功、成功、失败、失败、失败 → 不告警（有成功）
- 最近 5 次调用：失败、失败、失败、失败、失败 → 立即触发告警

```
连续失败不依赖时间窗口，纯粹看最近 N 条调用记录。
这是所有类型中响应最快的告警。
```

---

## 三、通知渠道配置

### 3.1 钉钉群机器人

**创建群机器人（步骤）：**

1. 在钉钉群中，点击右上角「群设置」→「智能群助手」
2. 添加机器人 → 选择「自定义」
3. 机器人名字随意填，如「接口监控告警」
4. 安全设置选择「加签」，复制签名密钥
5. 点击「完成」后复制 Webhook 地址

**配置字段：**

| 字段 | 说明 |
|------|------|
| Webhook 地址 | 必填，格式如 `https://oapi.dingtalk.com/robot/send?access_token=xxx` |
| 签名密钥 | 可选，若启用了加签则填入（SEC 开头的字符串） |

**消息格式：**

```
🔔 [告警通知] 接口错误率超标

规则名称：订单接口错误率监控
告警类型：错误率
统计范围：全局
实际值：15.3%
阈值：10%
触发时间：2026-04-22 16:30:00

详情：最近5分钟共调用200次，失败31次，错误率15.3%，超过阈值10%
```

### 3.2 企业微信群机器人

**创建群机器人（步骤）：**

1. 在企微群中，点击右上角「群管理」→「群机器人」
2. 点击「添加机器人」→ 输入名字（如「接口告警」）
3. 点击「创建」后复制 Webhook 地址

**配置字段：**

| 字段 | 说明 |
|------|------|
| Webhook 地址 | 必填，格式如 `https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx` |

企微不支持加签，仅需 Webhook 地址。

### 3.3 邮件通知

**application.yml 配置（以 QQ 邮箱为例）：**

```yaml
spring:
  mail:
    host: smtp.qq.com
    port: 465                    # QQ 邮箱使用 SSL，端口 465
    username: your-email@qq.com
    password: your-auth-code    # 非 QQ 密码，需在邮箱设置中申请「授权码」
    properties:
      mail:
        smtp:
          auth: true
          ssl:
            enable: true
        from: your-email@qq.com  # 发件人地址
```

> 不同邮箱的 SMTP 参数不同，常见如下：
>
> | 邮箱 | host | port (SSL) |
> |------|------|------------|
> | QQ 邮箱 | smtp.qq.com | 465 |
> | 163 邮箱 | smtp.163.com | 465 |
> | Gmail | smtp.gmail.com | 465 |
> | 企业邮箱 | smtp.exmail.qq.com | 465 |

告警规则中填写收件人地址，多个用英文逗号分隔，如 `ops@example.com,dev@example.com`。

---

## 四、使用流程

### 4.1 完整操作流程

```
第 1 步：配置 Webhook
         ↓
第 2 步：在系统创建告警规则
         ↓
第 3 步：点击「测试」验证通知是否正常
         ↓
第 4 步：系统自动每 60 秒评估规则
         ↓
第 5 步：收到告警 → 查看告警记录 → 确认/解决
```

### 4.2 规则状态说明

| 状态 | 说明 |
|------|------|
| 启用（ACTIVE） | 参与定时评估，超阈值触发告警 |
| 停用（INACTIVE） | 不参与评估，不触发告警 |

### 4.3 告警状态流转

```
🔥 告警中（FIRING）
    │
    ├──→ 点击「确认」→ 🟡 已确认（ACKNOWLEDGED）
    │                        │
    │                        └──→ 点击「解决」→ ✅ 已解决（RESOLVED）
    │
    └──→ 指标恢复正常 → 自动流转 → ✅ 已解决（RESOLVED）
```

- **FIRING**：指标持续超标，等待处理
- **ACKNOWLEDGED**：运维人员已知悉，等待修复
- **RESOLVED**：问题已解决或指标已恢复

---

## 五、高级配置

### 5.1 冷却机制

同一规则触发告警后，进入冷却期（默认 300 秒）。冷却期内该规则不会再次触发告警，即使指标持续超标。

**示例**：配置了冷却 300 秒（5 分钟）
- 16:00 触发错误率告警
- 16:03 指标恢复正常
- 16:05 再次超标 → 触发新告警（冷却已过）

### 5.2 指定接口告警

将「统计范围」设为「指定接口」，在「指定接口编码」中填写接口编码（与接口配置中的编码一致）。

多个接口用英文逗号分隔，如 `user-api,order-api`。

支持正则匹配（未来版本计划）。

### 5.3 告警恢复通知（未来计划）

当前版本不自动发送恢复通知。未来计划：当 FIRING → RESOLVED 时，发送一条「告警已恢复」消息。

### 5.4 告警聚合（未来计划）

当短时间内同一规则多次触发，当前版本会生成多条告警记录。未来计划：合并一定时间内的重复告警为一条。

---

## 六、REST API

### 6.1 告警规则

```bash
# 分页查询规则
GET /api/alert/rules?page=0&size=20&alertType=ERROR_RATE&status=ACTIVE

# 创建规则
POST /api/alert/rules
Content-Type: application/json

{
  "ruleName": "用户接口错误率监控",
  "ruleCode": "user-error-rate",
  "alertType": "ERROR_RATE",
  "scope": "GLOBAL",
  "threshold": 10.0,
  "windowSeconds": 300,
  "channels": ["DINGTALK", "EMAIL"],
  "dingtalkWebhook": "https://oapi.dingtalk.com/robot/send?access_token=xxx",
  "dingtalkSecret": "SEC...",
  "emailRecipients": "ops@example.com",
  "cooldownSeconds": 300,
  "status": "ACTIVE"
}

# 更新规则
PUT /api/alert/rules/{id}

# 删除规则
DELETE /api/alert/rules/{id}

# 启用/停用
POST /api/alert/rules/{id}/toggle

# 发送测试告警
POST /api/alert/rules/{id}/test

# 手动触发评估
POST /api/alert/rules/{id}/evaluate
```

### 6.2 告警记录

```bash
# 分页查询记录
GET /api/alert/records?page=0&size=20&status=FIRING&alertType=ERROR_RATE

# 确认告警
POST /api/alert/records/{id}/acknowledge

# 标记已解决
POST /api/alert/records/{id}/resolve

# 告警概览
GET /api/alert/overview
```

---

## 七、数据表

### ALERT_RULE

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| RULE_CODE | VARCHAR(50) | 规则编码，唯一 |
| RULE_NAME | VARCHAR(100) | 规则名称 |
| DESCRIPTION | VARCHAR(500) | 描述 |
| ALERT_TYPE | VARCHAR(30) | 告警类型 |
| SCOPE | VARCHAR(20) | GLOBAL 或 API |
| API_CODES | TEXT | 指定接口编码，逗号分隔 |
| THRESHOLD | DOUBLE | 阈值 |
| WINDOW_SECONDS | INT | 统计窗口（秒） |
| CHANNELS | VARCHAR(200) | 通知渠道，逗号分隔 |
| DINGTALK_WEBHOOK | TEXT | 钉钉 Webhook |
| DINGTALK_SECRET | VARCHAR(100) | 钉钉签名密钥 |
| WECOM_WEBHOOK | TEXT | 企微 Webhook |
| EMAIL_RECIPIENTS | TEXT | 邮件收件人，逗号分隔 |
| COOLDOWN_SECONDS | INT | 冷却时间（秒），默认 300 |
| STATUS | VARCHAR(20) | ACTIVE / INACTIVE |

### ALERT_RECORD

| 字段 | 类型 | 说明 |
|------|------|------|
| ID | BIGINT | 主键，雪花 ID |
| RULE_ID | BIGINT | 关联规则 ID |
| RULE_CODE | VARCHAR(50) | 关联规则编码 |
| RULE_NAME | VARCHAR(100) | 关联规则名称 |
| ALERT_TYPE | VARCHAR(30) | 告警类型 |
| ACTUAL_VALUE | VARCHAR(50) | 触发时的实际值 |
| THRESHOLD_VALUE | VARCHAR(50) | 阈值 |
| API_CODE | VARCHAR(50) | 触发接口编码 |
| DETAIL | TEXT | 告警详情 |
| CHANNELS | VARCHAR(200) | 已通知渠道 |
| NOTIFY_RESULT | VARCHAR(20) | SUCCESS / PARTIAL / FAILED |
| NOTIFY_DETAIL | TEXT | 通知结果详情 |
| STATUS | VARCHAR(20) | FIRING / ACKNOWLEDGED / RESOLVED |
| ALERT_TIME | DATETIME | 触发时间 |
| RESOLVED_TIME | DATETIME | 解决时间 |
| ACKNOWLEDGED_BY | VARCHAR(50) | 确认人 |
| ACKNOWLEDGED_TIME | DATETIME | 确认时间 |

---

## 八、常见问题

**Q：告警没有触发？**
- 检查规则状态是否为「启用」
- 检查接口是否有调用记录（零调用不会触发错误率/延迟告警）
- 检查阈值是否设置正确（如错误率填 10 表示 10%，而非 0.1）
- 查看后端日志中是否有 `[AlertEvaluationService]` 评估记录

**Q：测试告警收不到？**
- 确认 Webhook 地址是否正确（直接浏览器访问应返回 `{"errcode":0}`）
- 钉钉检查是否开启了「加签」但未填写密钥
- 邮件检查 SMTP 配置是否正确

**Q：告警重复发送？**
- 检查冷却时间是否过短（默认 300 秒）
- 冷却期内不会重复告警，指标恢复正常后重新进入检测周期

**Q：邮件发送失败？**
- 确认 `spring.mail` 配置正确
- 部分邮箱（如 QQ）需要开启「SMTP 服务」并使用「授权码」而非登录密码
- 检查邮件是否被误判为垃圾邮件
