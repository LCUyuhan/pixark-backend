package com.yeyuchunlan.yupicturebackend.model.vo.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 图片编辑的底图视图对象。
 * 用于前端本地画布（如局部重绘的涂抹蒙版）：浏览器直连 COS 地址画图会污染 canvas，
 * 导出时抛 SecurityError，因此改由后端取字节转 base64，成为同源 data URI。
 */
@Data
public class AiImageEditBaseImageVO implements Serializable {

    /**
     * 图片 id（后端 Long 已由 Jackson 序列化为字符串，前端原样回传）
     */
    private String pictureId;

    /**
     * 底图数据，格式为 data:{MIME_type};base64,{base64_data}，可直接赋给 img.src 或画布
     */
    private String dataUrl;

    /**
     * 原图宽度，画布必须按这个尺寸创建，否则蒙版与原图对不齐
     */
    private Integer picWidth;

    /**
     * 原图高度，同上
     */
    private Integer picHeight;

    /**
     * 图片格式（小写），如 webp / png / jpg
     */
    private String picFormat;

    /**
     * 解码前的图片字节数，前端可据此提示加载体积
     */
    private Long picSize;

    private static final long serialVersionUID = 1L;
}
