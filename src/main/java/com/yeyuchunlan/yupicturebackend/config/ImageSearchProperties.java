package com.yeyuchunlan.yupicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 以图搜图相关配置。默认服务商 + 阿里云百炼参数。
 * 密钥通过环境变量注入，切勿硬编码或提交到仓库。
 */
@Configuration
@ConfigurationProperties(prefix = "image-search")
@Data
public class ImageSearchProperties {

    /**
     * 未显式指定 provider 时使用的默认服务商
     */
    private String defaultProvider = "baidu";

    /**
     * 阿里云百炼配置
     */
    private Aliyun aliyun = new Aliyun();

    @Data
    public static class Aliyun {

        /**
         * DashScope API Key（Authorization: Bearer {apiKey}）
         */
        private String apiKey;

        /**
         * 业务空间 id，用于替换 endpoint 中的 {workspaceId}
         */
        private String workspaceId;

        /**
         * 模型名，需从百炼控制台确认可用模型
         */
        private String model;

        /**
         * Responses 兼容接口地址模板，{workspaceId} 会被替换
         */
        private String endpoint = "https://{workspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/responses";
    }
}
