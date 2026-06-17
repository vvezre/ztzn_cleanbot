package com.zt.cleanbot.controller;

import com.zt.cleanbot.common.Result;
import com.zt.cleanbot.dto.DeviceInfoResponse;
import com.zt.cleanbot.dto.QRCodeScanRequest;
import com.zt.cleanbot.service.UserDeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 设备管理控制器
 */
@RestController
@RequestMapping("/device")
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
public class DeviceController {

    @Autowired
    private UserDeviceService userDeviceService;

    /**
     * 扫描二维码绑定设备
     * POST /device/scan
     *
     * 请求体示例：
     * {
     *   "companyCode": "ZTZN-PVC",
     *   "productType": "-D01",
     *   "productId": "250001"
     * }
     */
    @PostMapping("/scan")
    public Result<DeviceInfoResponse> scanQRCode(
            @RequestBody QRCodeScanRequest request,
            HttpServletRequest httpRequest) {
        try {
            // 从请求中获取当前登录用户ID
            Integer userId = (Integer) httpRequest.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "未登录");
            }

            // 调用服务层处理
            DeviceInfoResponse response = userDeviceService.scanAndBind(userId, request);

            return Result.success(response);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 获取当前用户的所有设备
     * GET /device/my-devices
     */
    @GetMapping("/my-devices")
    public Result<List<DeviceInfoResponse>> getMyDevices(HttpServletRequest httpRequest) {
        try {
            // 从请求中获取当前登录用户ID
            Integer userId = (Integer) httpRequest.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "未登录");
            }

            List<DeviceInfoResponse> devices = userDeviceService.getUserDevices(userId);

            return Result.success(devices);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 解绑设备
     * DELETE /device/unbind/{serialNumber}
     * @param serialNumber 设备序列号（productType + productId，如 -D01250001）
     */
    @DeleteMapping("/unbind/{serialNumber}")
    public Result<String> unbindDevice(
            @PathVariable String serialNumber,
            HttpServletRequest httpRequest) {
        try {
            // 从请求中获取当前登录用户ID
            Integer userId = (Integer) httpRequest.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "未登录");
            }

            Boolean success = userDeviceService.unbindDevice(userId, serialNumber);

            if (success) {
                return Result.success("设备解绑成功");
            } else {
                return Result.error(500, "设备解绑失败");
            }
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 获取设备详情
     * GET /device/info/{productId}
     */
    @GetMapping("/info/{productId}")
    public Result<DeviceInfoResponse> getDeviceInfo(
            @PathVariable String productId,
            HttpServletRequest httpRequest) {
        try {
            // 从请求中获取当前登录用户ID
            Integer userId = (Integer) httpRequest.getAttribute("userId");
            if (userId == null) {
                return Result.error(401, "未登录");
            }

            // 查询设备信息
            List<DeviceInfoResponse> devices = userDeviceService.getUserDevices(userId);
            DeviceInfoResponse device = devices.stream()
                    .filter(d -> d.getProductId().equals(productId))
                    .findFirst()
                    .orElse(null);

            if (device == null) {
                return Result.error(404, "设备不存在或未绑定");
            }

            return Result.success(device);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
