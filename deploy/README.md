# 部署指南

本文档涵盖 Docker 单机部署、Kubernetes YAML 部署、Helm Chart 部署、优雅关闭原理、CI/CD 流水线配置。

---

## 一、环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 运行时必须 |
| Maven | 3.8+ | 编译时必须 |
| Docker | 24+ | 构建镜像 |
| Docker Compose | 2.20+ | 本地编排 |
| Kubernetes | 1.28+ | 集群部署 |
| Helm | 3.14+ | Helm Chart 部署 |
| MySQL | 8.0+ | 持久化存储（生产必选） |
| Redis | 7+ | Token 和缓存存储 |

---

## 二、Docker 单机部署

### 2.1 构建镜像

```bash
cd integration-config-system
docker build -t integration-config-system:1.0.0 \
  -f deploy/docker/Dockerfile .
```

### 2.2 Docker Compose 快速启动

```bash
# 启动（MySQL + Redis + 应用）
docker compose -f deploy/docker/docker-compose.yml up -d

# 查看日志
docker compose -f deploy/docker/docker-compose.yml logs -f app

# 关闭并清除数据
docker compose -f deploy/docker/docker-compose.yml down -v
```

访问地址：http://localhost:8080

### 2.3 自定义参数启动

```bash
docker run -d --name integration-config \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE-CONFIG_URL=jdbc:mysql://your-mysql:3306/integration_config \
  -e SPRING_DATASOURCE-LOG_URL=jdbc:mysql://your-mysql:3306/integration_log \
  -e SPRING_DATASOURCE-TOKEN_URL=jdbc:mysql://your-mysql:3306/integration_token \
  -e SPRING_DATA_REDIS_HOST=your-redis \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e SPRING_DATA_REDIS_PASSWORD=your-redis-password \
  -e SPRING_DATASOURCE-CONFIG_USERNAME=root \
  -e SPRING_DATASOURCE-CONFIG_PASSWORD=your-mysql-password \
  integration-config-system:1.0.0
```

---

## 三、Kubernetes 部署

### 3.1 文件说明

| 文件 | 顺序 | 说明 |
|------|------|------|
| `00-namespace.yaml` | 1 | 命名空间 |
| `01-configmap.yaml` | 2 | 非敏感配置 |
| `02-secrets.yaml` | 3 | 密码/密钥（需 Base64 编码） |
| `03-mysql.yaml` | 4 | MySQL StatefulSet（使用外部托管时删除） |
| `04-redis.yaml` | 5 | Redis StatefulSet（使用外部托管时删除） |
| `05-app.yaml` | 6 | 应用 Deployment + Service + HPA |
| `06-ingress.yaml` | 7 | Ingress（外部访问） |

### 3.2 快速部署

```bash
# 1. 创建命名空间
kubectl apply -f deploy/k8s/yaml/00-namespace.yaml

# 2. 生成密码 Secret（生产请使用外部托管密钥）
sed -i 's/root-secret/YOUR_REAL_PASSWORD/g' deploy/k8s/yaml/02-secrets.yaml
sed -i 's/redis-secret/YOUR_REDIS_PASSWORD/g' deploy/k8s/yaml/02-secrets.yaml

# 3. 替换镜像地址
# 编辑 deploy/k8s/yaml/05-app.yaml
# 将 image: registry.example.com/integration-config-system:1.0.0
# 替换为你的镜像地址

# 4. 按顺序部署
kubectl apply -f deploy/k8s/yaml/

# 5. 验证
kubectl get pods -n integration-system
kubectl get svc   -n integration-system

# 6. 查看应用日志
kubectl logs -n integration-system deploy/integration-config-system -f
```

### 3.3 滚动更新

```bash
# 更新镜像版本
kubectl set image deployment/integration-config-system \
  app=registry.example.com/integration-config-system:1.1.0 \
  -n integration-system

# 观察滚动更新
kubectl rollout status deployment/integration-config-system -n integration-system

# 回滚到上一版本
kubectl rollout undo deployment/integration-config-system -n integration-system

# 回滚到指定版本
kubectl rollout history deployment/integration-config-system -n integration-system
kubectl rollout undo deployment/integration-config-system \
  --to-revision=2 -n integration-system
```

### 3.4 扩缩容

```bash
# 手动扩缩
kubectl scale deployment integration-config-system \
  --replicas=5 -n integration-system

# HPA 自动扩缩（需 Metrics Server）
kubectl autoscale deployment integration-config-system \
  --min=2 --max=10 --cpu-percent=70 \
  -n integration-system
```

---

## 四、Helm Chart 部署

### 4.1 安装 / 升级

```bash
# 安装
helm install integration ./deploy/k8s/helm \
  --namespace integration-system \
  --create-namespace \
  --values ./deploy/k8s/helm/values.yaml

# 升级
helm upgrade integration ./deploy/k8s/helm \
  --namespace integration-system \
  --values ./deploy/k8s/helm/values.yaml \
  --set global.imageTag=1.1.0

# 预览渲染结果（不实际安装）
helm template integration ./deploy/k8s/helm \
  --values ./deploy/k8s/helm/values.yaml

# 原子升级（失败自动回滚）
helm upgrade --install integration ./deploy/k8s/helm \
  --namespace integration-system \
  --atomic \
  --timeout 5m
```

### 4.2 values.yaml 关键配置

```bash
# 生产部署示例
helm upgrade --install integration ./deploy/k8s/helm \
  --namespace integration-system \
  --create-namespace \
  --set global.imageRegistry=registry.example.com \
  --set global.imageTag=1.0.0 \
  --set app.replicaCount=3 \
  --set mysql.enabled=false \
  --set mysql.auth.rootPassword="YOUR_REAL_MYSQL_PASSWORD" \
  --set redis.enabled=false \
  --set redis.auth.password="YOUR_REAL_REDIS_PASSWORD" \
  --set app.ingress.host=api.your-domain.com \
  --set app.ingress.tls.enabled=true \
  --set app.ingress.tls.secretName=integration-tls \
  --atomic --timeout 10m
```

### 4.3 卸载

```bash
helm uninstall integration --namespace integration-system
kubectl delete pvc -n integration-system --selector app.kubernetes.io/name=integration-config-system
```

---

## 五、优雅关闭原理

### 5.1 问题背景

Pod 收到终止信号（`SIGTERM`）后，若立即强制杀死进程，正在处理中的 HTTP 请求会中断，调用方收到错误。

### 5.2 Spring Boot 优雅关闭

Spring Boot 2.3+ 内置优雅关闭支持：

```yaml
# application.yml（或在 Dockerfile JVM 参数中）
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 60s
```

Dockerfile 中的 JVM 参数：
```bash
java -Dserver.shutdown=graceful \
      -Dspring.lifecycle.timeout-per-shutdown-phase=30s \
      -jar app.jar
```

### 5.3 Kubernetes 中的完整优雅关闭流程

```
1. kubectl delete pod 或 滚动更新触发

2. K8s API Server 将 Pod 状态设为 Terminating

3. kube-proxy / EndpointSlice 更新
   → 新请求不再路由到该 Pod（但已有连接保持）

4. preStop Hook 执行（等待 kube-proxy 同步）
   → sleep 5  ← 配置文件：deploy/k8s/yaml/05-app.yaml

5. K8s 向容器发送 SIGTERM

6. Spring Boot 收到 SIGTERM
   → 停止接受新连接
   → 等待正在处理的请求完成（最多 30s）

7. terminationGracePeriodSeconds = 60s 后
   → 若请求仍未完成，强制发送 SIGKILL
```

### 5.4 preStop Hook 的必要性

```
不带 preStop：Pod 收到 SIGTERM → kube-proxy 还没来得及更新 EndpointSlice
           → 新请求仍可能路由到正在关闭的 Pod → 502

带 preStop (sleep 5)：
Pod 收到 SIGTERM
  → preStop 等待 5 秒（kube-proxy 有充足时间更新 EndpointSlice）
  → 新请求不再路由到该 Pod
  → 然后才发送 SIGTERM 给容器
  → Spring Boot 优雅关闭
```

### 5.5 验证优雅关闭

```bash
# 查看终止原因
kubectl describe pod <pod-name> -n integration-system

# 查看终止日志（Spring Boot 应用侧）
kubectl logs <pod-name> -n integration-system -p  # 上一个已终止容器的日志

# 在应用日志中搜索
kubectl logs <pod-name> -n integration-system | grep -i "shutting down\|graceful\|Terminating"
```

---

## 六、CI/CD 流水线

### 6.1 GitHub Actions

**配置 Secrets**（Settings → Secrets and variables → Actions）：

| Secret | 说明 |
|--------|------|
| `REGISTRY_URL` | 镜像仓库地址，如 `registry.example.com` |
| `REGISTRY_USERNAME` | 镜像仓库用户名 |
| `REGISTRY_PASSWORD` | 镜像仓库密码 |
| `KUBECONFIG_DATA` | kubeconfig Base64 编码（`base64 -w 0 ~/.kube/config`） |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码（供 Helm values 注入） |
| `REDIS_PASSWORD` | Redis 密码 |

**触发规则**：

| 事件 | 环境 | 操作 |
|------|------|------|
| Push `main` | 生产 | 自动构建 + 镜像推送 + Helm 部署 |
| Push `release/*` | 预发 | 自动构建 + 镜像推送 + Helm 部署 |
| Pull Request | 测试 | 仅运行测试，不部署 |

**多架构构建**：GitHub Actions 使用 Docker BuildX 构建 `linux/amd64,linux/arm64`，适用于 amd64 服务器和 ARM 边缘节点。

### 6.2 Jenkins

**前提条件**：
- Kubernetes 集群已配置 `kubeconfig`（凭据 ID：`kubeconfig-dev` / `kubeconfig-prod`）
- Jenkins 节点安装了 Docker-in-Docker 或有远程 Docker Host
- Config File Provider 管理了各环境的 Helm values 文件

**手动触发**：

| 参数 | 说明 |
|------|------|
| `IMAGE_TAG` | 镜像标签（留空使用 Git Commit SHA） |
| `DEPLOY_ENV` | dev / staging / prod |
| `SKIP_TESTS` | 跳过单元测试（谨慎使用） |
| `CONFIRM_ROLLOUT` | **生产环境必须勾选**，确认滚动更新 |

**流水线阶段**：
```
Prepare → Maven Build → Unit Tests → Docker Build & Push
                                          ↓
                              Security Scan（Trivy，可选）
                                          ↓
              Deploy Dev → Deploy Staging → Deploy Production
                           (自动)          (手动确认)
```

**生产部署前建议**：
1. 先在 Staging 验证通过
2. 检查滚动更新策略：`maxUnavailable: 0`（始终保持全量）
3. 勾选 `CONFIRM_ROLLOUT`

### 6.3 镜像仓库配置示例（Harbor / 私有仓库）

```bash
# Harbor 示例
docker login registry.example.com -u admin -p Harbor12345

# GitHub Actions Secret 配置
# REGISTRY_URL = registry.example.com
# REGISTRY_USERNAME = admin
# REGISTRY_PASSWORD = Harbor12345
```

---

## 七、运维清单

### 7.1 部署前检查

- [ ] MySQL / Redis 已就绪且密码已更新
- [ ] 镜像已构建并推送至仓库
- [ ] `values.yaml` 中 `mysql.auth.rootPassword` 和 `redis.auth.password` 已替换
- [ ] `imageTag` 已更新为正确版本
- [ ] Ingress TLS 证书 Secret 已创建

### 7.2 部署后验证

```bash
# Pod 状态
kubectl get pods -n integration-system

# 服务可达
curl -sf http://localhost:8080/api/health/session

# 应用健康（带 Token）
curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/health/session

# HPA 状态
kubectl get hpa -n integration-system

# Ingress 状态
kubectl get ingress -n integration-system
```

### 7.3 监控

建议配合 Prometheus + Grafana：
- 关注指标：`up{service="integration-config-system"}`（Pod 运行状态）
- JVM 堆内存使用率（`jvm_memory_used_bytes{pod=~"integration-.*"}`）
- HTTP 请求延迟（`http_server_requests_seconds_*`）
