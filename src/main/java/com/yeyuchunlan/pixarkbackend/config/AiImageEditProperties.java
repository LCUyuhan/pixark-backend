package com.yeyuchunlan.pixarkbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

/**
 * AI 图片编辑（阿里云百炼 万相 wanx2.1-imageedit）配置。
 * 百炼密钥由本段自己持有，写在 application-local.yml（已被 .gitignore 忽略）或从环境变量注入。
 */
@Configuration
@ConfigurationProperties(prefix = "ai-image-edit")
@Data
public class AiImageEditProperties {

    /**
     * 功能总开关，关闭时相关接口直接拒绝
     */
    private boolean enabled = true;

    /**
     * 百炼 API Key（Authorization: Bearer {apiKey}），必须是华北2（北京）地域的 Key
     */
    private String apiKey;

    /**
     * 业务空间 id，用于替换 baseUrl 中的 {workspaceId}
     */
    private String workspaceId;

    /**
     * 接入地址模板。官方已建议从 dashscope.aliyuncs.com 迁移到业务空间专属域名，旧域名仍可用
     */
    private String baseUrl = "https://{workspaceId}.cn-beijing.maas.aliyuncs.com";

    /**
     * 模型名称
     */
    private String model = "wanx2.1-imageedit";

    /**
     * 允许前端调用的功能白名单，取值见 AiImageEditFunctionEnum。
     * 枚举里声明了模型支持的全部功能，放开新功能只需在此加一项，不改代码。
     */
    private List<String> allowedFunctions = Collections.singletonList("expand");

    /**
     * 是否在生成图右下角添加「AI生成」标识。
     * 内部学习用可关闭；对外提供服务时按 AI 生成内容标识要求建议置为 true。
     */
    private boolean watermark = false;

    /**
     * 单用户每分钟最多可提交的任务数。这是**频率闸**，挡的是脚本刷额度；
     * 它管不了"同时有几个任务在跑"，那个由 maxConcurrentTasks 负责
     * 配成 0 或负数表示不限
     */
    private int submitRatePerMinute = 3;

    /**
     * 全局同时处于"处理中"的任务数上限。百炼对 wanx2.1-imageedit 官方给的是并发 2、
     * 任务下发 RPS 2，且主账号与 RAM 子账号共用 —— 这是整个 API Key 的额度，不是单用户的
     */
    private int maxConcurrentTasks = 2;

    /**
     * 一个任务最长占用并发槽位的分钟数，超过就强制回收。
     * 没有这个兜底，进程在提交后崩溃会让槽位永久泄漏，最后 AI 编辑会彻底卡死在"人数已满"
     */
    private int taskMaxRunningMinutes = 30;

    /**
     * 任务记录在 Redis 中的存活小时数。必须小于等于 24，百炼的 task_id 与结果图片 URL 均只保留 24 小时
     */
    private int taskTtlHours = 23;
}
