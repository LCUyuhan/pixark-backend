package com.yeyuchunlan.pixarkbackend.model.vo.picture;

import com.yeyuchunlan.pixarkbackend.api.imagesearch.model.ImageSearchResult;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 以图搜图任务视图对象，创建与轮询接口共用。
 */
@Data
public class ImageSearchTaskVO implements Serializable {

    /**
     * 任务 id
     */
    private String taskId;

    /**
     * 任务状态：PENDING（排队中）/ RUNNING（搜索中）/ SUCCEEDED / FAILED
     */
    private String taskStatus;

    /**
     * 任务状态中文文案
     */
    private String statusText;

    /**
     * 相似图片列表，任务成功时非空
     */
    private List<ImageSearchResult> results;

    /**
     * 失败原因（面向用户的文案），任务失败时非空
     */
    private String failReason;

    private static final long serialVersionUID = 1L;
}
