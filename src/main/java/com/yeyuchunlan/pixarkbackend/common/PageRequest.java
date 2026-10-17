package com.yeyuchunlan.pixarkbackend.common;

import lombok.Data;

@Data
public class PageRequest {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "descend";

    /**
     * 规范化分页参数：页码 / 页长为 0 或负数时回退默认值，
     * 防止负数穿透分页插件导致全表查询。不改变各接口原有的上限校验。
     */
    public void normalizePage() {
        if (current < 1) {
            current = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
    }
}

