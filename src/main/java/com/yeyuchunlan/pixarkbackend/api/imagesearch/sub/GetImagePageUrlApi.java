package com.yeyuchunlan.pixarkbackend.api.imagesearch.sub;


import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

import cn.hutool.json.JSONUtil;

/**
 * 获取以图搜图页面地址
 * （通过向百度发送 POST 请求，获取给定图片 URL 的相似图片页面地址。）
 */

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;


public class GetImagePageUrlApi {

    /**
     * 通过图片 URL 上传到百度图片搜索
     * @param imageUrl 图片的 URL 地址
     * @return 百度返回的 JSON 结果
     */
    public static String uploadImageByUrl(String imageUrl) {
        // 下载图片
        byte[] imageBytes = HttpUtil.downloadBytes(imageUrl);

        // 上传到百度
        long uptime = System.currentTimeMillis();
        String uploadUrl = "https://graph.baidu.com/upload?uptime=" + uptime;

        HttpResponse response = HttpRequest.post(uploadUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Origin", "https://graph.baidu.com")
                .header("Referer", "https://graph.baidu.com/")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Acs-Token", "")
                .form("image", imageBytes, "image.jpg")
                .form("tn", "pc")
                .form("from", "pc")
                .form("image_source", "PC_UPLOAD_URL")
                .timeout(30000)
                .execute();

        JSONObject jsonObject = JSONUtil.parseObj(response.body());
        String url =   (String) jsonObject.get("data", JSONObject.class).get("url");
        return url;
    }

    public static void main(String[] args) {
        String imageUrl = "https://www.codefather.cn/logo.png";
        String result = uploadImageByUrl(imageUrl);
        System.out.println(result);
    }
}

