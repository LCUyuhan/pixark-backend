package com.yeyuchunlan.yupicturebackend.api.imagesearch.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yeyuchunlan.yupicturebackend.api.imagesearch.ImageSearchService;
import com.yeyuchunlan.yupicturebackend.api.imagesearch.model.ImageSearchResult;
import com.yeyuchunlan.yupicturebackend.config.ImageSearchProperties;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 阿里云百炼（Model Studio）以图搜图实现。
 * 走 OpenAI Responses 兼容接口，输入为公网图片 URL，结果藏在 image_search_call.output（JSON 字符串）里。
 * 使用 JDK 自带 HttpClient（Java 11+），避免第三方 HTTP 客户端在这类 chunked/长耗时响应上的兼容问题。
 */
@Slf4j
@Service
public class AliyunImageSearchService implements ImageSearchService {

    private static final String PROVIDER = "aliyun";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 大模型图搜非流式一次可达 50s+，大图更久，给足超时
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(180);

    private final ImageSearchProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public AliyunImageSearchService(ImageSearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getProvider() {
        return PROVIDER;
    }

    @Override
    public List<ImageSearchResult> search(String imageUrl) {
        ImageSearchProperties.Aliyun cfg = properties.getAliyun();
        ThrowUtils.throwIf(!StringUtils.hasText(cfg.getApiKey()), ErrorCode.SYSTEM_ERROR, "阿里云百炼未配置 api-key");
        ThrowUtils.throwIf(!StringUtils.hasText(cfg.getModel()), ErrorCode.SYSTEM_ERROR, "阿里云百炼未配置 model");
        ThrowUtils.throwIf(!StringUtils.hasText(cfg.getWorkspaceId()), ErrorCode.SYSTEM_ERROR, "阿里云百炼未配置 workspace-id");

        String url = cfg.getEndpoint().replace("{workspaceId}", cfg.getWorkspaceId());
        String body = buildRequestBody(imageUrl, cfg.getModel());

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.error("阿里云百炼以图搜图调用失败，status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "阿里云百炼以图搜图调用失败");
            }
            return parseResponse(response.body());
        } catch (IOException e) {
            log.error("阿里云百炼以图搜图网络请求异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "阿里云百炼以图搜图请求失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "阿里云百炼以图搜图请求被中断");
        }
    }

    private String buildRequestBody(String imageUrl, String model) {
        JSONObject imageItem = new JSONObject();
        imageItem.set("type", "input_image");
        imageItem.set("image_url", imageUrl);

        JSONArray content = new JSONArray();
        content.add(imageItem);

        JSONObject message = new JSONObject();
        message.set("role", "user");
        message.set("content", content);

        JSONArray input = new JSONArray();
        input.add(message);

        JSONObject tool = new JSONObject();
        tool.set("type", "image_search");
        JSONArray tools = new JSONArray();
        tools.add(tool);

        JSONObject root = new JSONObject();
        root.set("model", model);
        root.set("input", input);
        root.set("tools", tools);
        return root.toString();
    }

    private List<ImageSearchResult> parseResponse(String responseBody) {
        List<ImageSearchResult> results = new ArrayList<>();
        JSONObject root = JSONUtil.parseObj(responseBody);
        JSONArray output = root.getJSONArray("output");
        if (output == null) {
            return results;
        }
        for (Object obj : output) {
            JSONObject item = (JSONObject) obj;
            if (!"image_search_call".equals(item.getStr("type"))) {
                continue;
            }
            String inner = item.getStr("output");
            if (!StringUtils.hasText(inner)) {
                continue;
            }
            // output 是 JSON 字符串，需二次解析
            JSONArray pictures = JSONUtil.parseArray(inner);
            for (Object picObj : pictures) {
                JSONObject pic = (JSONObject) picObj;
                ImageSearchResult result = new ImageSearchResult();
                // 阿里云的 url 是图片直链，可直接展示；它未返回来源页，sourceUrl 留空
                result.setImageUrl(pic.getStr("url"));
                result.setTitle(pic.getStr("title"));
                results.add(result);
            }
        }
        return results;
    }
}
