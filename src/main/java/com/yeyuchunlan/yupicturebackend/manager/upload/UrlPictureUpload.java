package com.yeyuchunlan.yupicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URL;
@Slf4j
@Service
public class UrlPictureUpload extends PictureUploadTemplate {
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");
        // ... 跟之前的校验逻辑保持一致
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        try {
            // 通过 URL 类提取路径部分，去掉查询参数和锚点
            String path = new URL(fileUrl).getPath();
            String filename = FileUtil.getName(path);
            if (StrUtil.isNotBlank(filename) && StrUtil.isNotBlank(FileUtil.getSuffix(filename))) {
                return filename;
            }
            // URL 无后缀，通过 Content-Type 推断
            if (StrUtil.isNotBlank(filename)) {
                String suffix = inferSuffixFromUrl(fileUrl);
                return filename + "." + suffix;
            }
        } catch (Exception e) {
            log.error("从 URL 提取文件名失败, url = {}", fileUrl, e);
        }
        return FileUtil.getName(fileUrl);
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 下载文件到临时目录
        HttpUtil.downloadFile(fileUrl, file);
    }


    /**
     * 通过 Content-Type 推断文件后缀
     *
     * @param fileUrl 文件地址
     * @return 文件后缀
     */
    private String inferSuffixFromUrl(String fileUrl) {
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
                if (contentType.contains("png")) return "png";
                if (contentType.contains("webp")) return "webp";
                if (contentType.contains("gif")) return "gif";
            }
        } catch (Exception e) {
            log.error("推断文件后缀失败, url = {}", fileUrl, e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return "jpg";
    }
}

