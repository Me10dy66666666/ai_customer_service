package com.example.backend.interfaces.config;

import com.example.backend.interfaces.security.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;

    public WebMvcConfig(RoleInterceptor roleInterceptor) {
        this.roleInterceptor = roleInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/admin/**", "/api/analysis/**",
                        "/api/orders/**", "/api/sla-config/**",
                        "/api/agent/**", "/api/knowledge/**")
                .excludePathPatterns("/api/auth/**", "/api/health");
    }
}
