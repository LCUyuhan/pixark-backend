package com.yeyuchunlan.pixarkbackend.api.aiimageedit.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建 AI 图片编辑任务的返回结果（已中性化，不暴露百炼原始响应结构）
 */
@Data
public class AiEditCreateTaskResult implements Serializable {

    /**
     * 任务 id，百炼侧仅保留 24 小时
     */
    private String taskId;

    /**
     * 任务状态，创建成功时一般为 PENDING
     */
    private String taskStatus;

    /**
     * 调用失败的错误码，成功时为空
     */
    private String code;

    /**
     * 调用失败的错误信息，成功时为空
     */
    private String message;

    private static final long serialVersionUID = 1L;
}

