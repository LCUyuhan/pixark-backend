package com.yeyuchunlan.yupicturebackend.model.vo.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 图片编辑额度视图对象，供前端展示剩余次数
 */
@Data
public class AiImageEditQuotaVO implements Serializable {

    /**
     * 剩余可提交次数，取自 user.aiQuota。用完需联系管理员发放，不会按天自动重置
     */
    private Integer remaining;

    private static final long serialVersionUID = 1L;
}
