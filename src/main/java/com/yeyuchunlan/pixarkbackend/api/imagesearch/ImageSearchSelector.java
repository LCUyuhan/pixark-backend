package com.yeyuchunlan.pixarkbackend.api.imagesearch;

import com.yeyuchunlan.pixarkbackend.config.ImageSearchProperties;
import com.yeyuchunlan.pixarkbackend.exception.ErrorCode;
import com.yeyuchunlan.pixarkbackend.exception.ThrowUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 以图搜图服务商选择器。
 * Spring 会把所有 {@link ImageSearchService} 实现注入进来，按 provider 建立索引。
 * 新增供应商时只需再提供一个实现 bean，本类和调用方都无需改动。
 */
@Component
public class ImageSearchSelector {

    private final Map<String, ImageSearchService> providerMap;

    private final ImageSearchProperties properties;

    public ImageSearchSelector(List<ImageSearchService> services, ImageSearchProperties properties) {
        this.providerMap = services.stream()
                .collect(Collectors.toMap(ImageSearchService::getProvider, service -> service));
        this.properties = properties;
    }

    /**
     * 选择服务商。provider 为空时回退到配置的默认服务商。
     */
    public ImageSearchService pick(String provider) {
        String key = StringUtils.hasText(provider) ? provider : properties.getDefaultProvider();
        ImageSearchService service = providerMap.get(key);
        ThrowUtils.throwIf(service == null, ErrorCode.PARAMS_ERROR, "不支持的以图搜图服务商：" + key);
        return service;
    }
}
