package com.yeyuchunlan.pixarkbackend.config;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 允许跨域访问的来源，多个用逗号分隔；留空表示不开放跨域
     */
    @Value("${cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (StrUtil.isBlank(allowedOrigins)) {
            return;
        }
        registry.addMapping("/**")
                // 精确来源，不用通配符
                .allowedOrigins(allowedOrigins.split("\\s*,\\s*"))
                .allowCredentials(true)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*");
    }
}
