package com.zt.cleanbot.controller;

import com.zt.cleanbot.model.RailcarConfig;
import com.zt.cleanbot.service.RailcarConfigService;
import com.zt.cleanbot.service.RailcarControlService;
import com.zt.cleanbot.service.VehicleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 轨道车控制接口
 */
@CrossOrigin(origins = { "*" }, maxAge = 3600L)
@Slf4j
@RestController
@RequestMapping("/api/railcar")
public class RailcarControlController {
    private static final int PARAMETER_ADMIN_ROLE_MAX = 2;

    @Autowired
    private RailcarConfigService railcarConfigService;
    @Autowired
    private RailcarControlService railcarControlService;
    @Autowired
    private VehicleService vehicleService;

    /**
     * 向轨道车发送控制命令
     */
    @PostMapping("/control")
    public ResponseEntity<Map<String, Object>> sendControlCommand(
            @RequestBody com.zt.cleanbot.dto.DeviceConfigRequest controlData,
            HttpServletRequest httpRequest) {

        try {
            log.info("收到轨道车控制请求 - 产品型号: {}, 产品编号: {}",
                    controlData.getModel(), controlData.getDeviceId());

            // 鉴权与越权校验
            Integer userId = (Integer) httpRequest.getAttribute("userId");
            Integer roleId = (Integer) httpRequest.getAttribute("roleId");
            String username = (String) httpRequest.getAttribute("username");

            if (userId == null) {
                return ResponseEntity.status(401).body(errorBody("未登录"));
            }

            if (!vehicleService.hasDeviceAccess(userId, roleId, controlData.getDeviceId())) {
                log.warn("用户 {} 越权控制设备 {}", username, controlData.getDeviceId());
                return ResponseEntity.status(403).body(errorBody("权限不足"));
            }
            if (!canSendParameters(roleId)) {
                log.warn("参数下发权限拦截：用户 {} (ID: {}, roleId: {}) 尝试下发轨道车参数 {}",
                        username, userId, roleId, controlData.getDeviceId());
                return ResponseEntity.status(403).body(errorBody("权限不足：普通用户无法下发参数"));
            }

            controlData.setUserId(userId);
            controlData.setUsername(username);

            // 发送控制命令
            boolean success = railcarControlService.sendControlCommand(controlData);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("productModel", controlData.getModel());
            response.put("productNumber", controlData.getDeviceId());

            if (success) {
                log.info("轨道车控制命令发送成功 - 产品型号: {}, 产品编号: {}",
                        controlData.getModel(), controlData.getDeviceId());
                response.put("message", "控制命令发送成功");

                // 保存配置到数据库
                try {
                    railcarConfigService.saveOrUpdateConfig(controlData);
                } catch (Exception e) {
                    log.error("保存设备配置到数据库失败", e);
                }

                return ResponseEntity.ok(response);
            } else {
                response.put("message", "控制命令发送失败");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("发送轨道车控制命令异常", e);
            return ResponseEntity.internalServerError().body(errorBody("服务器内部错误"));
        }
    }

    /**
     * 快速设置轨道车工作模式
     */
    @PostMapping("/mode")
    public ResponseEntity<Map<String, Object>> setWorkMode(
            @RequestBody Map<String, Object> modeData,
            HttpServletRequest httpRequest) {

        try {
            log.info("收到轨道车工作模式设置请求 - 数据: {}", modeData);

            com.zt.cleanbot.dto.DeviceConfigRequest controlData = new com.zt.cleanbot.dto.DeviceConfigRequest();

            if (modeData.containsKey("productModel")) {
                controlData.setModel(modeData.get("productModel").toString());
            }
            if (modeData.containsKey("productNumber")) {
                controlData.setDeviceId(modeData.get("productNumber").toString());
            }
            if (modeData.containsKey("companyCode")) {
                controlData.setCompanyCode(modeData.get("companyCode").toString());
            }

            // 鉴权
            Integer userId = (Integer) httpRequest.getAttribute("userId");
            Integer roleId = (Integer) httpRequest.getAttribute("roleId");
            String username = (String) httpRequest.getAttribute("username");
            if (userId == null)
                return ResponseEntity.status(401).body(errorBody("未登录"));

            String deviceSerial = normalizeDeviceSerial(controlData.getModel(), controlData.getDeviceId());
            if (!vehicleService.hasDeviceAccess(userId, roleId, deviceSerial)) {
                return ResponseEntity.status(403).body(errorBody("权限不足"));
            }
            if (!canSendParameters(roleId)) {
                log.warn("参数下发权限拦截：用户 {} (ID: {}, roleId: {}) 尝试设置设备 {} 的工作模式",
                        username, userId, roleId, deviceSerial);
                return ResponseEntity.status(403).body(errorBody("权限不足：普通用户无法下发参数"));
            }

            controlData.setDeviceId(deviceSerial);
            controlData.setUserId(userId);
            controlData.setUsername(username);

            if (modeData.containsKey("workMode")) {
                controlData.setWorkWay(Integer.valueOf(modeData.get("workMode").toString()));
            }
            if (modeData.containsKey("operationMode")) {
                controlData.setControlMode(Integer.valueOf(modeData.get("operationMode").toString()));
            }
            if (modeData.containsKey("operationEnable")) {
                controlData.setEnableMode(Integer.valueOf(modeData.get("operationEnable").toString()));
            }

            boolean success = railcarControlService.sendControlCommand(controlData);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            if (success) {
                response.put("message", "工作模式设置成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("message", "工作模式设置失败");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("设置轨道车工作模式异常", e);
            return ResponseEntity.internalServerError().body(errorBody("系统错误"));
        }
    }

    /**
     * 查询轨道车配置
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig(
            @RequestParam String productModel,
            @RequestParam String productNumber,
            HttpServletRequest httpRequest) {

        try {
            // 查询接口同样必须经过登录和权属校验，防止未认证读取配置
            Integer userId = (Integer) httpRequest.getAttribute("userId");
            Integer roleId = (Integer) httpRequest.getAttribute("roleId");
            if (userId == null) {
                return ResponseEntity.status(401).body(errorBody("未登录"));
            }
            String deviceSerial = normalizeDeviceSerial(productModel, productNumber);
            if (!vehicleService.hasDeviceAccess(userId, roleId, deviceSerial)) {
                return ResponseEntity.status(403).body(errorBody("无权查询该设备配置"));
            }

            RailcarConfig config = railcarConfigService.getConfig(productModel, productNumber);

            Map<String, Object> response = new HashMap<>();
            if (config != null) {
                response.put("success", true);
                response.put("config", config);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.ok(errorBody("配置不存在"));
            }

        } catch (Exception e) {
            log.error("查询配置异常", e);
            return ResponseEntity.internalServerError().body(errorBody("系统错误"));
        }
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("success", false);
        body.put("message", message);
        return body;
    }

    private String normalizeDeviceSerial(String productModel, String productNumberOrDeviceId) {
        String model = productModel == null ? "" : productModel.trim();
        String value = productNumberOrDeviceId == null ? "" : productNumberOrDeviceId.trim();
        if (value.startsWith("-")) {
            return value;
        }
        if (!model.isEmpty()) {
            return model + value;
        }
        return value;
    }

    private boolean canSendParameters(Integer roleId) {
        return roleId != null && roleId <= PARAMETER_ADMIN_ROLE_MAX;
    }
}
