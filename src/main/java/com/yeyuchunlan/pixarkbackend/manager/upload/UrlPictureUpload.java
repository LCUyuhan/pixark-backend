package com.yeyuchunlan.pixarkbackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.yeyuchunlan.pixarkbackend.exception.BusinessException;
import com.yeyuchunlan.pixarkbackend.exception.ErrorCode;
import com.yeyuchunlan.pixarkbackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    private static final long MAX_BYTES = 5 * 1024 * 1024L;
    private static final int MAX_REDIRECTS = 3;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final List<String> ALLOW_SUFFIX = Arrays.asList("jpeg", "jpg", "png", "webp");

    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");
        assertPublicHttpUrl(fileUrl);
    }

    /**
     * 只放行公网 http/https 地址：域名解析出的每个 IP 都必须是公网地址。
     * 拦的是 127.0.0.1、10.x/172.16-31.x/192.168.x、169.254.169.254（云元数据）这类内网目标。
     */
    private void assertPublicHttpUrl(String fileUrl) {
        URL url;
        try {
            url = new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        String protocol = url.getProtocol();
        ThrowUtils.throwIf(!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址");
        ThrowUtils.throwIf(StrUtil.isBlank(url.getHost()), ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(url.getHost());
        } catch (UnknownHostException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址无法解析");
        }
        for (InetAddress address : addresses) {
            ThrowUtils.throwIf(address.isAnyLocalAddress() || address.isLoopbackAddress()
                            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                            || address.isMulticastAddress() || isNonPublicReserved(address),
                    ErrorCode.PARAMS_ERROR, "不允许访问内网地址");
        }
    }

    /**
     * JDK 的 isSiteLocalAddress 不覆盖这两个网段，需要自己补：
     * 100.64.0.0/10（运营商级 NAT）与 fc00::/7（IPv6 唯一本地地址）
     */
    private boolean isNonPublicReserved(InetAddress address) {
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            return (raw[0] & 0xFF) == 100 && (raw[1] & 0xC0) == 64;
        }
        if (raw.length == 16) {
            return (raw[0] & 0xFE) == 0xFC;
        }
        return false;
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
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            // 每一跳都重新校验：否则「公网地址 302 到内网」就绕过去了
            assertPublicHttpUrl(fileUrl);
            HttpRequest request = HttpRequest.of(fileUrl)
                    .setFollowRedirects(false)
                    .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                    .setReadTimeout(READ_TIMEOUT_MS);
            try (HttpResponse response = request.execute()) {
                int status = response.getStatus();
                if (status >= 300 && status < 400) {
                    String location = response.header("Location");
                    ThrowUtils.throwIf(StrUtil.isBlank(location), ErrorCode.PARAMS_ERROR, "重定向地址缺失");
                    fileUrl = new URL(new URL(fileUrl), location).toString();
                    continue;
                }
                ThrowUtils.throwIf(status != 200, ErrorCode.PARAMS_ERROR, "文件下载失败");
                checkContentType(response, fileUrl);
                long contentLength = response.contentLength();
                ThrowUtils.throwIf(contentLength > MAX_BYTES, ErrorCode.PARAMS_ERROR, "文件大小不能超过 5M");
                copyWithLimit(response.bodyStream(), file);
                return;
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "重定向次数过多");
    }

    private void checkContentType(HttpResponse response, String fileUrl) {
        String contentType = response.header("Content-Type");
        if (StrUtil.isNotBlank(contentType)) {
            ThrowUtils.throwIf(!contentType.toLowerCase().startsWith("image/"),
                    ErrorCode.PARAMS_ERROR, "文件类型错误");
            return;
        }
        // 服务端没给 Content-Type 时，退回看 URL 后缀
        String suffix = "";
        try {
            suffix = StrUtil.emptyToDefault(FileUtil.getSuffix(new URL(fileUrl).getPath()), "").toLowerCase();
        } catch (MalformedURLException ignored) {
        }
        ThrowUtils.throwIf(!ALLOW_SUFFIX.contains(suffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

    /**
     * 流式写盘并强制字节上限：Content-Length 可能缺失或造假，不能只信响应头。
     */
    private void copyWithLimit(InputStream in, File dest) throws IOException {
        long total = 0;
        byte[] buffer = new byte[8192];
        try (OutputStream out = Files.newOutputStream(dest.toPath())) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                ThrowUtils.throwIf(total > MAX_BYTES, ErrorCode.PARAMS_ERROR, "文件大小不能超过 5M");
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * 通过 Content-Type 推断文件后缀
     *
     * @param fileUrl 文件地址
     * @return 文件后缀
     */
    private String inferSuffixFromUrl(String fileUrl) {
        try {
            assertPublicHttpUrl(fileUrl);
        } catch (RuntimeException e) {
            return "jpg";
        }
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl)
                    .setFollowRedirects(false)
                    .setConnectionTimeout(CONNECT_TIMEOUT_MS)
                    .setReadTimeout(READ_TIMEOUT_MS)
                    .execute();
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
