package com.yeyuchunlan.pixarkbackend.api.aiimageedit;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisOutput;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.common.Status;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.yeyuchunlan.pixarkbackend.api.aiimageedit.model.AiEditCreateTaskResult;
import com.yeyuchunlan.pixarkbackend.api.aiimageedit.model.AiEditQueryTaskResult;
import com.yeyuchunlan.pixarkbackend.api.aiimageedit.model.AliyunImageEditRequest;
import com.yeyuchunlan.pixarkbackend.config.AiImageEditProperties;
import com.yeyuchunlan.pixarkbackend.exception.ErrorCode;
import com.yeyuchunlan.pixarkbackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 阿里云百炼 万相-通用图像编辑（wanx2.1-imageedit）调用封装，使用 DashScope Java SDK。
 * 只做异步：asyncCall 下发任务，fetch 查询结果，成功时返回的图片 URL 有效期 24 小时。
 * 不使用 SDK 的 call()/wait()——那两个方法在内部阻塞轮询，会长时间占用 Tomcat 线程。
 * 这里只负责传输与响应解析，错误码到用户文案的映射、额度等业务规则由 Service 承担。
 */
@Slf4j
@Component
public class AliyunImageEditApi {

    /**
     * SDK 请求路径中的 task 段：图像编辑是 image2image（文本生成图像才是 text2image），
     * 拼出来即 /api/v1/services/aigc/image2image/image-synthesis
     */
    private static final String SDK_TASK = "image2image";

    /**
     * SDK 的 baseHttpApiUrl 必须以 /api/v1 结尾
     */
    private static final String API_VERSION_PATH = "/api/v1";

    private final AiImageEditProperties properties;

    private final ImageSynthesis imageSynthesis;

    public AliyunImageEditApi(AiImageEditProperties properties) {
        this.properties = properties;
        // 按实例传入接入地址，避免改写 SDK 的全局静态 Constants.baseHttpApiUrl（JVM 级可变状态）
        this.imageSynthesis = new ImageSynthesis(SDK_TASK, buildBaseUrl());
    }

    /**
     * 创建图像编辑任务
     *
     * @param aliyunImageEditRequest 请求参数
     * @return 创建结果，失败时 code 一定非空
     */
    public AiEditCreateTaskResult createTask(AliyunImageEditRequest aliyunImageEditRequest) {
        validateConfig();
        AiEditCreateTaskResult result = new AiEditCreateTaskResult();
        try {
            ImageSynthesisResult response = imageSynthesis.asyncCall(buildParam(aliyunImageEditRequest));
            ImageSynthesisOutput output = response.getOutput();
            if (output != null) {
                result.setTaskId(output.getTaskId());
                result.setTaskStatus(output.getTaskStatus());
                result.setCode(output.getCode());
                result.setMessage(output.getMessage());
            }
            if (StrUtil.isBlank(result.getTaskId()) && StrUtil.isBlank(result.getCode())) {
                result.setCode("EmptyTaskId");
                result.setMessage("AI 服务未返回任务 id");
            }
            return result;
        } catch (ApiException e) {
            ErrorInfo errorInfo = extractError(e);
            result.setCode(errorInfo.code);
            result.setMessage(errorInfo.message);
            return result;
        } catch (NoApiKeyException e) {
            log.error("AI 图片编辑未配置 API Key", e);
            result.setCode("InvalidApiKey");
            result.setMessage(e.getMessage());
            return result;
        }
    }

    /**
     * 查询图像编辑任务
     *
     * @param taskId 任务 id
     * @return 查询结果，失败时 code 一定非空
     */
    public AiEditQueryTaskResult queryTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR, "任务 id 不能为空");
        validateConfig();
        AiEditQueryTaskResult result = new AiEditQueryTaskResult();
        try {
            ImageSynthesisResult response = imageSynthesis.fetch(taskId, properties.getApiKey());
            ImageSynthesisOutput output = response.getOutput();
            if (output == null) {
                result.setCode("EmptyOutput");
                result.setMessage("AI 服务未返回任务信息");
                return result;
            }
            result.setTaskStatus(output.getTaskStatus());
            result.setCode(output.getCode());
            result.setMessage(output.getMessage());
            fillResults(output.getResults(), result);
            return result;
        } catch (ApiException e) {
            ErrorInfo errorInfo = extractError(e);
            result.setCode(errorInfo.code);
            result.setMessage(errorInfo.message);
            return result;
        } catch (NoApiKeyException e) {
            log.error("AI 图片编辑未配置 API Key", e);
            result.setCode("InvalidApiKey");
            result.setMessage(e.getMessage());
            return result;
        }
    }

    private ImageSynthesisParam buildParam(AliyunImageEditRequest request) {
        ImageSynthesisParam.ImageSynthesisParamBuilder<?, ?> builder = ImageSynthesisParam.builder()
                .apiKey(properties.getApiKey())
                .model(properties.getModel())
                .function(request.getFunction())
                .prompt(request.getPrompt())
                .baseImageUrl(request.getBaseImageUrl())
                // 一次任务出多张图会成倍消耗额度，当前固定 1 张
                .n(1);
        if (StrUtil.isNotBlank(request.getMaskImageUrl())) {
            builder.maskImageUrl(request.getMaskImageUrl());
        }
        if (CollUtil.isNotEmpty(request.getParameters())) {
            // SDK 没有为 strength/top_scale/upscale_factor 等提供专用方法，统一走 parameters
            builder.parameters(request.getParameters());
        }
        return builder.build();
    }

    /**
     * results 是「成功项带 url、失败项带 code/message」的混合列表：
     * 只要有一张成功，任务整体就是 SUCCEEDED，所以取第一个有 url 的项，失败原因单独留一份
     */
    private void fillResults(List<Map<String, String>> results, AiEditQueryTaskResult result) {
        if (CollUtil.isEmpty(results)) {
            return;
        }
        for (Map<String, String> item : results) {
            String url = item.get("url");
            if (StrUtil.isNotBlank(url)) {
                if (StrUtil.isBlank(result.getImageUrl())) {
                    result.setImageUrl(url);
                }
            } else if (StrUtil.isBlank(result.getFailReason())) {
                result.setFailReason(item.get("message"));
            }
        }
    }

    /**
     * SDK 把 HTTP 状态码、错误码都包进了 ApiException 的 Status，取出来交给上层做文案映射
     */
    private ErrorInfo extractError(ApiException e) {
        Status status = e.getStatus();
        if (status != null) {
            String code = StrUtil.blankToDefault(status.getCode(), "HTTP_" + status.getStatusCode());
            log.error("百炼图像编辑接口返回错误, code = {}, message = {}, requestId = {}",
                    code, status.getMessage(), status.getRequestId());
            return new ErrorInfo(code, status.getMessage());
        }
        log.error("百炼图像编辑接口调用异常", e);
        return new ErrorInfo("ApiException", e.getMessage());
    }

    private String buildBaseUrl() {
        return properties.getBaseUrl().replace("{workspaceId}", properties.getWorkspaceId()) + API_VERSION_PATH;
    }

    private void validateConfig() {
        ThrowUtils.throwIf(!StringUtils.hasText(properties.getApiKey()), ErrorCode.SYSTEM_ERROR, "AI 图片编辑未配置 api-key");
        ThrowUtils.throwIf(!StringUtils.hasText(properties.getWorkspaceId()), ErrorCode.SYSTEM_ERROR, "AI 图片编辑未配置 workspace-id");
    }

    /**
     * 从异常里提取的错误码与错误信息
     */
    private static class ErrorInfo {

        private final String code;

        private final String message;

        ErrorInfo(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }
}


