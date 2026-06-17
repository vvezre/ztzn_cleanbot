package com.zt.cleanbot.dto;

import lombok.Data;

/**
 * D12 绑定 D01 请求
 */
@Data
public class D12BindingRequest {
    /**
     * D01 完整序列号（如 -D01250001）
     */
    private String d01SerialNumber;
}
