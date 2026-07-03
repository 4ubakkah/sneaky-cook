package com.sneakycook.recipes.api;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.springmvc.OpenApiValidationFilter;
import com.atlassian.oai.validator.springmvc.OpenApiValidationInterceptor;
import com.atlassian.oai.validator.springmvc.SpringMVCLevelResolverFactory;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Contract-first request validation [REQ-2]. Incoming HTTP requests are checked
 * against the hand-written OpenAPI spec before controller logic runs, so wire
 * types (e.g. {@code servings: 4.5} where the contract declares
 * {@code type: integer}) are rejected as contract violations — not silently
 * coerced by the JSON parser.
 */
@Configuration
class OpenApiRequestValidationConfig {

    @Bean
    Filter openApiValidationFilter() {
        return new OpenApiValidationFilter(
                true,  // request validation
                false  // response validation — error bodies are handled by ApiExceptionHandler
        );
    }

    @Bean
    WebMvcConfigurer openApiValidationInterceptor(
            @Value("classpath:openapi/recipe-api.yaml") Resource openApiSpec) throws java.io.IOException {
        OpenApiInteractionValidator validator = OpenApiInteractionValidator
                .createForSpecificationUrl(openApiSpec.getURL().toString())
                .withLevelResolver(SpringMVCLevelResolverFactory.create())
                .build();
        OpenApiValidationInterceptor interceptor = new OpenApiValidationInterceptor(validator);
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor);
            }
        };
    }
}
