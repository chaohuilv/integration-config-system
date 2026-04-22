# ============================================================
# Docker 构建配置
# ============================================================
# 构建镜像：
#   docker build -t integration-config-system:1.0.0 .
#
# 运行（开发 / 单机）：
#   docker run -d \
#     --name integration-config \
#     -p 8080:8080 \
#     -e SPRING_PROFILES_ACTIVE=prod \
#     -e SPRING_DATASOURCE-CONFIG_URL=jdbc:mysql://mysql:3306/integration_config \
#     -e SPRING_DATASOURCE-LOG_URL=jdbc:mysql://mysql:3306/integration_log \
#     -e SPRING_DATASOURCE-TOKEN_URL=jdbc:mysql://mysql:3306/integration_token \
#     -e SPRING_DATA_REDIS_HOST=redis \
#     -e SPRING_DATA_REDIS_PORT=6379 \
#     --link mysql \
#     --link redis \
#     integration-config-system:1.0.0
#
# Docker Compose（生产推荐）：
#   docker compose up -d
# ============================================================

# ---- 多阶段构建 ----

# ---- 阶段一：构建 ----
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom.xml，预下载依赖（利用 Docker 缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
RUN mvn package spring-boot:package -DskipTests -B

# ---- 阶段二：运行 ----
FROM eclipse-temurin:17-jre-alpine

# Alpine 默认无 bash，使用 sh
RUN apk add --no-cache \
    curl \
    && addgroup -S appgroup \
    && adduser  -S appuser -G appgroup

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /build/target/*.jar app.jar

# 复制静态资源（接口文档模板等）
COPY --from=builder /build/src/main/resources/static/ ./static/

# 创建数据目录（H2 文件存储路径）
RUN mkdir -p /app/data && chown -R appuser:appgroup /app

# 切换非 root 用户
USER appuser

# ---- 健康检查 ----
# Spring Boot Actuator health 端点
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/api/health/session || exit 1

# ---- 优雅关闭 ----
# -Dspring.lifecycle.timeout-per-shutdown-phase=30s : 等待处理中的请求完成（默认 30 秒）
# --stop-grace-period : Docker 停止容器的最大等待时间
STOPSIGNAL SIGTERM

# JVM 优化参数
# -XX:+UseContainerSupport : 自动感知容器内存限制
# -XX:MaxRAMPercentage=75.0 : 堆内存使用 75% 的容器限制
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
            "-Dspring.lifecycle.timeout-per-shutdown-phase=30s", \
            "-Dserver.shutdown=graceful", \
            "-jar", "app.jar"]
