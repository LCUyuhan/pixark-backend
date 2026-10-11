package com.yeyuchunlan.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 图片编辑任务创建请求
 */
@Data
public class AiImageEditCreateRequest implements Serializable {

    /**
     * 要编辑的图片 id
     */
    private Long pictureId;

    /**
     * 编辑功能，取值见 AiImageEditFunctionEnum，且必须在配置的白名单内
     */
    private String function;

    /**
     * 提示词，描述编辑后的画面。接口要求不超过 800 个字符
     */
    private String prompt;

    /**
     * 向上扩展比例，仅扩图功能使用，范围 [1.0, 2.0]
     */
    private Float topScale;

    /**
     * 向下扩展比例，仅扩图功能使用，范围 [1.0, 2.0]
     */
    private Float bottomScale;

    /**
     * 向左扩展比例，仅扩图功能使用，范围 [1.0, 2.0]
     */
    private Float leftScale;

    /**
     * 向右扩展比例，仅扩图功能使用，范围 [1.0, 2.0]
     */
    private Float rightScale;

    /**
     * 图像修改幅度，仅全局风格化与指令编辑使用，范围 [0.0, 1.0]，留空按 0.5
     */
    private Float strength;

    /**
     * 超分放大倍数，仅图像超分使用，范围 1~4，留空按 1（仅高清不放大）
     */
    private Integer upscaleFactor;

    /**
     * 输入图是否为线稿，仅线稿生图使用。true 直接按线稿生成，false 先提取线稿再生成
     */
    private Boolean isSketch;

    /**
     * 涂抹区域图像（黑白蒙版，纯白为待编辑区、纯黑为保留区），仅局部重绘使用。
     * 支持裸 base64 或 data:image/png;base64, 前缀的完整 data URI，分辨率需与原图一致
     */
    private String maskImage;

    private static final long serialVersionUID = 1L;
}
