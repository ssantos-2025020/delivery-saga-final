package com.delivery.pedidos.config;

import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfig {
    
    @Bean
    public RequestInterceptor correlationIdRequestInterceptor() {
        return requestTemplate -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null && !correlationId.isBlank()) {
                requestTemplate.header("X-Correlation-ID", correlationId);
            }
        };
    }
}
