package com.integration.config.enums;

/**
 * 全局常量定义
 * 集中管理项目中各模块使用的常量，避免魔法值散落在各处
 */
public final class AppConstants {

    private AppConstants() {
        // 工具类禁止实例化
    }

    // ==================== Redis Key 前缀 ====================

    /** Redis 全局前缀（与 application.yml integration.cache.prefix 对应） */
    public static final String REDIS_CACHE_PREFIX = "integration:cache:";

    /** Token 存储 key 前缀 */
    public static final String REDIS_TOKEN_PREFIX = "integration:token:";

    /** 用户权限缓存 key 前缀 */
    public static final String REDIS_USER_PERMISSIONS_PREFIX = "integration:cache:user:permissions:";

    /** 用户角色缓存 key 前缀 */
    public static final String REDIS_USER_ROLES_PREFIX = "integration:cache:user:roles:";

    /** 用户可访问接口ID缓存 key 前缀 */
    public static final String REDIS_USER_APIS_PREFIX = "integration:cache:user:apis:";

    /** 用户菜单缓存 key 前缀 */
    public static final String REDIS_USER_MENUS_PREFIX = "integration:cache:user:menus:";

    /** 全局 pageMap 缓存 key */
    public static final String REDIS_GLOBAL_PAGE_MAP = "integration:cache:global:pageMap";

    // ==================== 缓存过期时间（秒） ====================

    /** 权限/角色/接口/菜单缓存过期时间：10 分钟 */
    public static final int CACHE_TTL_SECONDS = 600;

    /** RedisCacheService 默认缓存过期时间：5 分钟 */
    public static final int CACHE_DEFAULT_TTL_SECONDS = 300;

    /** 接口调用结果缓存默认过期时间：5 分钟（HttpInvokeService 中 cacheTime 为 null 时使用） */
    public static final int INVOKE_CACHE_DEFAULT_TTL = 300;

    // ==================== Token 配置 ====================

    /** Token 默认过期时间：24 小时 */
    public static final long TOKEN_DEFAULT_EXPIRE_HOURS = 24;

    /** Bearer Token 认证头前缀 */
    public static final String AUTH_BEARER_PREFIX = "Bearer ";

    /** Basic Auth 认证头前缀 */
    public static final String AUTH_BASIC_PREFIX = "Basic ";

    /** Authorization 请求头名称 */
    public static final String HEADER_AUTHORIZATION = "Authorization";

    // ==================== LoginFilter 白名单 ====================

    /** 不需要 Token 认证的路径前缀 */
    public static final String[] AUTH_EXCLUDE_PREFIXES = {
            "/login.html",
            "/api/auth/login",
            "/api/health/",
            "/api/version",
            "/h2-console",
            "/css/",
            "/js/",
            "/v3/api-docs",
            "/swagger-ui",
            "/swagger-resources"
    };

    /** 不需要 Token 认证的精确匹配路径 */
    public static final String[] AUTH_EXCLUDE_EXACTS = {
            "/",
            "/login.html",
            "/favicon.ico",
            "/v3/api-docs",
            "/swagger-ui/index.html",
            "/swagger-ui.html"
    };

    /** 静态资源后缀（自动放行） */
    public static final String[] STATIC_RESOURCE_SUFFIXES = {
            ".css", ".js", ".ico", ".png", ".jpg", ".jpeg", ".gif", ".svg",
            ".woff", ".woff2", ".ttf", ".eot", ".html"
    };

    // ==================== 系统预设 ====================

    /** 管理员角色编码 */
    public static final String ROLE_ADMIN_CODE = "ADMIN";

    /** 用户状态：启用 */
    public static final String USER_STATUS_ACTIVE = "ACTIVE";

    // ==================== 认证类型 ====================

    /** 认证类型：无 */
    public static final String AUTH_TYPE_NONE = "none";

    /** 认证类型：Bearer Token */
    public static final String AUTH_TYPE_BEARER = "bearer";

    /** 认证类型：Basic Auth */
    public static final String AUTH_TYPE_BASIC = "basic";

    /** 认证类型：API Key */
    public static final String AUTH_TYPE_API_KEY = "api_key";

    /** 认证类型：动态 Token */
    public static final String AUTH_TYPE_DYNAMIC = "dynamic";

    // ==================== Token 注入位置 ====================

    /** Token 注入位置：请求头 */
    public static final String TOKEN_POSITION_HEADER = "header";

    /** Token 注入位置：请求体 */
    public static final String TOKEN_POSITION_BODY = "body";

    /** Token 注入位置：URL 参数 */
    public static final String TOKEN_POSITION_URL = "url";

    // ==================== 请求属性名 ====================

    /** LoginFilter 存入 request 的用户ID属性名 */
    public static final String REQ_ATTR_USER_ID = "userId";

    /** LoginFilter 存入 request 的用户编码属性名 */
    public static final String REQ_ATTR_USER_CODE = "userCode";

    /** LoginFilter 存入 request 的用户名属性名 */
    public static final String REQ_ATTR_USERNAME = "username";

    /** LoginFilter 存入 request 的显示名属性名 */
    public static final String REQ_ATTR_DISPLAY_NAME = "displayName";
}