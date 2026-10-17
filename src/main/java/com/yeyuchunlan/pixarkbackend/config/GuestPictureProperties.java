package com.yeyuchunlan.pixarkbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 游客（未登录）浏览公共图库的相关配置
 */
@Configuration
@ConfigurationProperties(prefix = "guest-picture")
@Data
public class GuestPictureProperties {

    /**
     * 未登录用户最多能浏览的公共图片数量
     */
    private long viewLimit = 30;
}
