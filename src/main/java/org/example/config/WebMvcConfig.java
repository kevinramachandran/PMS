package org.example.config;

import org.example.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/pms-login",   // login page
                "/api/auth/login", // login API
                        "/logout",      // logout endpoint
                        "/css/**",      // static CSS
                        "/js/**",       // static JS
                        "/images/**",   // static images
                        "/favicon.ico",
                        "/actuator/**",
                        "/error"        // Spring error page
                );
    }
}
