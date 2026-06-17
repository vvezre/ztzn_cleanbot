package com.zt.cleanbot.dto;

import lombok.Data;

/**
 * 二维码扫描请求DTO
 * 18字节二维码数据：公司代号(8字节) + 产品型号(4字节) + 产品编号(6字节)
 */
@Data
public class QRCodeScanRequest {
    private String companyCode;   // 公司代号，8字节，如 "ZTZN-PVC"
    private String productType;   // 产品型号，4字节，如 "-D01"
    private String productId;     // 产品编号，6字节，如 "250001"
}
