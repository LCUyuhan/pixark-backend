package com.yeyuchunlan.pixarkbackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 以图搜图相关配置（供应商只剩百度）。
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
     * 首次执行之外还允许重试几次，用完进死信队列并置任务失败
     */
    private int maxRetry = 3;

    /**
     * 任务状态与结果在 Redis 里的保留时长（分钟）。百度返回的地址本身有时效，不必留太久
     */
    private long taskTtlMinutes = 60;

    /**
     * 同一张图的搜索结果缓存多久（小时）。命中就不再爬百度，是保护百度的第一道
     */
    private long resultTtlHours = 24;

    /**
     * 单用户每分钟最多提交几个新搜索任务，<=0 表示不限流。只统计未命中缓存的请求
     */
    private int ratePerMinute = 2;

    /**
     * 连续失败多少次就进冷却。按"执行失败次数"计，所以一个任务彻底失败会贡献 1 + maxRetry 次
     */
    private int failureThreshold = 3;

    /**
     * 冷却时长（秒）。冷却期内新任务直接被拒、队列里的任务也不再爬百度，防止"已经被封还猛撞"
     */
    private long cooldownSeconds = 60;
}
