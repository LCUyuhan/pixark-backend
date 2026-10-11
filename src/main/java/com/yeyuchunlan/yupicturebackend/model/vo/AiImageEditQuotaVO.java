package com.yeyuchunlan.yupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 图片编辑额度视图对象，供前端展示"今日剩余次数"
 */
@Data
public class AiImageEditQuotaVO implements Serializable {

    /**
     * 今日剩余可提交次数（已按 Redis 计数扣减，最小为 0）
     */
    private Integer remaining;

    /**
     * 单用户每日次数上限，来自配置 ai-image-edit.daily-quota-per-user
     */
    private Integer dailyLimit;

    private static final long serialVersionUID = 1L;
}
