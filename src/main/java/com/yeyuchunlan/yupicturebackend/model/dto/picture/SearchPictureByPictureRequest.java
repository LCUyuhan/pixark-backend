package com.yeyuchunlan.yupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class SearchPictureByPictureRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 以图搜图服务商：baidu / aliyun，不传则用配置的默认服务商
     */
    private String provider;

    private static final long serialVersionUID = 1L;
}
