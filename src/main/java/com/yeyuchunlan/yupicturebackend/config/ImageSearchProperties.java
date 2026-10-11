package com.yeyuchunlan.yupicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 以图搜图相关配置，目前只有默认服务商一项（供应商只剩百度）。
 */
@Configuration
@ConfigurationProperties(prefix = "image-search")
@Data
public class ImageSearchProperties {

    /**
     * 未显式指定 provider 时使用的默认服务商
     */
    private String defaultProvider = "baidu";
}
