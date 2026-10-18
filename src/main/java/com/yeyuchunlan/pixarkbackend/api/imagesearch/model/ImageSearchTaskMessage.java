package com.yeyuchunlan.pixarkbackend.api.imagesearch.model;

import lombok.Data;

/**
 * 以图搜图任务消息体。只带 id，不带图片字节，也不带查询结果 —— 结果由消费者写进 Redis。
 */
@Data
public class ImageSearchTaskMessage {

    /**
     * 任务 id，同时作为幂等键：消费者重投时先查任务状态，已完成就不再打百度
     */
    private String taskId;

    /**
     * 待搜索的图片 id，消费者按它去库里取缩略图地址
     */
    private Long pictureId;

    /**
     * 服务商，入队时就定稿（留空则回落默认值）—— 结果缓存 key 也带它，避免不同服务商的结果互相污染
     */
    private String provider;

    /**
     * 提交人，用于查询接口校验归属
     */
    private Long userId;

    /**
     * 已重试次数，每次回炉由消费者加一
     */
    private Integer retryCount;
}
