package com.yeyuchunlan.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 图片编辑结果保存请求。
 * 结果图片地址由服务端按 taskId 自己取回，不接受前端传入，避免把任意外链下载入库。
 */
@Data
public class AiImageEditSaveRequest implements Serializable {

    /**
     * 任务 id
     */
    private String taskId;

    /**
     * 保存图片的名称，为空时自动生成
     */
    private String picName;

    /**
     * true：覆盖被编辑的原图（原图文件会被删除，不可逆）；false 或为空：保存为新图片
     */
    private Boolean replace;

    private static final long serialVersionUID = 1L;
}
