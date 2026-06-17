package com.zt.cleanbot.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * T型号小车命令请求 DTO
 * 用于封装前端发送的T型号小车控制命令
 */
@Data
public class TRailcarCommandRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    // ========== 基本信息 ==========
    /**
     * 公司代号（默认: ZTZN-PVC）
     */
    private String companyCode = "ZTZN-PVC";

    /**
     * 产品型号（默认: -T01）
     */
    private String productModel = "-T01";

    /**
     * 产品编号（如: 250001）
     */
    private String productId;

    // ========== 命令信息 ==========
    /**
     * 命令名称
     * 支持的命令：
     * - 基础运动: drive, back, turn_left, turn_right, stop, parking
     * - 摇杆控制: joystick_move
     * - 高级功能: auto_drive, go_on, return_to_point, enter_garage, exit_garage
     * - 参数调整: adjust_speed, adjust_brush_speed, toggle_tracking, toggle_path_planning
     * - 任务管理: create_task, select_task, save_task
     * - 参数配置: save_params, set_garage_entry
     * - 状态查询: get_status
     */
    private String command;

    /**
     * 命令参数（JSON对象）
     * 不同命令有不同的参数结构
     */
    private Map<String, Object> params;

    // ========== 用户信息（由后端自动填充）==========
    /**
     * 用户 ID（从 JWT Token 提取）
     */
    private Integer userId;

    /**
     * 用户名（从 JWT Token 提取）
     */
    private String username;

    /**
     * 命令唯一标识（由云平台生成，用于后续 ACK/结果关联）
     */
    private String commandId;

    /**
     * 链路追踪标识（用于串联小程序、云平台、设备端日志）
     */
    private String traceId;

    /**
     * 获取完整设备ID
     * @return 格式：-T01250001
     */
    public String getFullDeviceId() {
        return productModel + productId;
    }

    /**
     * 获取MQTT发布主题
     * @return 格式：RAILCAR/S/-T01250001
     */
    public String getPublishTopic() {
        return "RAILCAR/S/" + getFullDeviceId();
    }

    /**
     * 获取MQTT订阅主题（用于接收状态）
     * @return 格式：RAILCAR/R/-T01250001
     */
    public String getSubscribeTopic() {
        return "RAILCAR/R/" + getFullDeviceId();
    }
}
