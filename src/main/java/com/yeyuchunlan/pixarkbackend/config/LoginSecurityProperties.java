package com.yeyuchunlan.pixarkbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 登录失败限流配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "login-security")
public class LoginSecurityProperties {

    /**
     * 同一账号在窗口内允许的登录失败次数，<=0 表示不限
     */
    private int accountFailLimit = 10;

    /**
     * 同一 IP 在窗口内允许的登录失败次数，<=0 表示不限
     */
    private int ipFailLimit = 30;

    /**
     * 失败计数的窗口时长（分钟）
     */
    private int failWindowMinutes = 10;
}
