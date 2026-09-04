package com.example.backend.interfaces.config;

import com.example.backend.interfaces.security.RoleInterceptor;
import com.example.backend.interfaces.security.PermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RoleInterceptor roleInterceptor;
    private final PermissionInterceptor permissionInterceptor;

    public WebMvcConfig(RoleInterceptor roleInterceptor, PermissionInterceptor permissionInterceptor) {
        this.roleInterceptor = roleInterceptor;
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/admin/**", "/api/analysis/**",
                        "/api/orders/**", "/api/sla-config/**",
                        "/api/agent/**", "/api/knowledge/**",
                        "/api/work-orders/**", "/api/chat/**")
                .excludePathPatterns("/api/auth/**", "/api/health");
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/public/**", "/api/health");
    }
}
