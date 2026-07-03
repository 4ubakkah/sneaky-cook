package com.sneakycook.recipes.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Exposes the hand-written OpenAPI contract over HTTP so Swagger UI renders it
 * as-is [REQ-11]. The contract file is the single source of truth; springdoc
 * code scanning is disabled in application.yaml.
 */
@Configuration
public class OpenApiContractConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi/**")
                .addResourceLocations("classpath:/openapi/");
    }
}
