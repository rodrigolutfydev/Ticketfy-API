package com.lutfy.ticketfy.infra.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PaginationGuard paginationGuard;

    public WebConfig(PaginationGuard paginationGuard) {
        this.paginationGuard = paginationGuard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(paginationGuard);
    }
}
