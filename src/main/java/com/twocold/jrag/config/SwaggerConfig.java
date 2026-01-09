package com.twocold.jrag.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${app.version:v1.0.0}")
    private String appVersion;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("J-RAG Enterprise API")
                        .version(appVersion)
                        .description("专为 Java 团队打造的企业级 RAG 脚手架 API 文档.\n\n" +
                                "核心功能：\n" +
                                "*   **混合检索 (Hybrid Search)**: 向量检索 + 关键词检索 (BM25)\n" +
                                "*   **重排序 (Rerank)**: 基于语义模型的精细排序\n" +
                                "*   **权限管理 (RBAC)**: 基于用户组的多租户隔离\n" +
                                "*   **深度思考 (Deep Thinking)**: 集成 Agentic RAG 能力\n" +
                                "*   **可观测性**: 集成 LangFuse 全链路监控")
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", createAPIKeyScheme()));
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");
    }
}
