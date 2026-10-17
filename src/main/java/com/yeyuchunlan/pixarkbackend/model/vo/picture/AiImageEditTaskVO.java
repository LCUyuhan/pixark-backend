package com.yeyuchunlan.pixarkbackend.model.vo.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 图片编辑任务视图对象，创建与查询接口共用。
 * imageUrl 为百炼临时地址，有效期 24 小时，仅供前端预览，不能直接入库。
 */
@Data
public class AiImageEditTaskVO implements Serializable {

    /**
     * 任务 id
     */
    private String taskId;

    /**
     * 任务状态：PENDING / RUNNING / SUCCEEDED / FAILED / CANCELED / UNKNOWN
     */
    private String taskStatus;

    /**
     * 任务状态中文文案
     */
    private String statusText;

    /**
     * 生成图片地址，任务成功时非空
     */
    private String imageUrl;

    /**
     * 失败原因（面向用户的文案），任务失败时非空
     */
    private String failReason;

    private static final long serialVersionUID = 1L;
}
