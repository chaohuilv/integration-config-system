package com.integration.config.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 全局配置
 * 访问地址：
 *   Swagger UI  : /swagger-ui.html
 *   OpenAPI JSON: /v3/api-docs
 *   OpenAPI YAML: /v3/api-docs.yaml
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("统一接口配置系统 API")
                        .description(
                                "统一接口配置管理系统后端 REST API 文档。\n\n" +
                                "**认证方式**：所有接口需要在 Header 中携带 Bearer Token：\n" +
                                "```\nAuthorization: Bearer <your_token>\n```\n\n" +
                                "**获取 Token**：通过 `/api/auth/login` 接口登录后获取。")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("integration-config-system")
                                .url("https://github.com/integration-config-system"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("登录后获取的 Access Token")));
    }
}
