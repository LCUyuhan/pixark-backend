package com.yeyuchunlan.pixarkbackend.api.aiimageedit.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 查询 AI 图片编辑任务的返回结果（已中性化，不暴露百炼原始响应结构）
 */
@Data
public class AiEditQueryTaskResult implements Serializable {

    /**
     * 任务状态，见 AiImageEditTaskStatusEnum
     */
    private String taskStatus;

    /**
     * 生成图片的地址，仅任务成功且有结果时非空。百炼侧有效期 24 小时
     */
    private String imageUrl;

    /**
     * 单张图片生成失败的原因。一次任务生成多张图时，任务整体仍可能是 SUCCEEDED
     */
    private String failReason;

    /**
     * 任务级错误码，成功时为空
     */
    private String code;

    /**
     * 任务级错误信息，成功时为空
     */
    private String message;

    private static final long serialVersionUID = 1L;
}
