package com.zt.cleanbot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.dto.DeviceConfigRequest;
import com.zt.cleanbot.dto.DeviceControlResponse;
import com.zt.cleanbot.model.TaskLog;
import com.zt.cleanbot.model.VehicleLog;
import com.zt.cleanbot.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AI 工具函数执行器
 * 负责执行 DeepSeek Function Calling 中定义的工具
 */
@Service
@Slf4j
public class AiToolExecutor {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private TaskLogService taskLogService;

    @Autowired
    private VehicleLogService vehicleLogService;

    @Autowired
    private DeviceControlService deviceControlService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 根据工具名称和参数执行对应工具
     *
     * @param toolName   工具名称
     * @param argsJson   工具参数（JSON 字符串）
     * @return 工具执行结果（字符串，返回给 AI）
     */
    public String execute(String toolName, String argsJson) {
        log.info("[AiTool] 执行工具: {}, 参数: {}", toolName, argsJson);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = objectMapper.readValue(argsJson, Map.class);
            switch (toolName) {
                case "query_device_status":
                    return queryDeviceStatus(args);
                case "list_devices":
                    return listDevices();
                case "query_task_logs":
                    return queryTaskLogs(args);
                case "query_vehicle_logs":
                    return queryVehicleLogs(args);
                case "control_device":
                    return controlDevice(args);
                default:
                    return "未知工具: " + toolName;
            }
        } catch (Exception e) {
            log.error("[AiTool] 工具执行失败: {}", toolName, e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    // ===== 工具实现 =====

    /**
     * 工具1：查询单台设备实时状态（从 Redis）
     */
    @SuppressWarnings("unchecked")
    private String queryDeviceStatus(Map<String, Object> args) {
        String deviceId = (String) args.get("device_id");
        if (deviceId == null || deviceId.isEmpty()) {
            return "参数缺失：device_id 不能为空";
        }

        Object data = redisUtil.getVehicle(deviceId);
        if (data == null) {
            return String.format("设备 %s 暂无状态数据，可能未上线或长时间未上报。", deviceId);
        }

        try {
            Map<String, Object> redisData = (Map<String, Object>) data;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("设备ID: %s\n", deviceId));

            Object battery = redisData.get("battery");
            if (battery != null) sb.append(String.format("电量: %.1f%%\n", ((Number) battery).doubleValue()));

            Object status = redisData.get("status");
            if (status != null) sb.append(String.format("状态: %s\n", status));

            Object workMode = redisData.get("workMode");
            if (workMode != null) sb.append(String.format("工作模式: %s\n", workMode));

            Object location = redisData.get("location");
            if (location instanceof Map) {
                Map<String, Object> loc = (Map<String, Object>) location;
                sb.append(String.format("GPS位置: 经度%.6f, 纬度%.6f\n",
                        ((Number) loc.get("lon")).doubleValue(),
                        ((Number) loc.get("lat")).doubleValue()));
            }

            Object speed = redisData.get("walkSpeed");
            if (speed != null) sb.append(String.format("行走速度: %.2f m/s\n", ((Number) speed).doubleValue()));

            Object runTimeTotal = redisData.get("runTimeTotal");
            if (runTimeTotal != null) {
                long totalSec = ((Number) runTimeTotal).longValue();
                sb.append(String.format("累计运行时长: %02d:%02d:%02d\n",
                        totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60));
            }

            Object mileageTotal = redisData.get("mileageTotal");
            if (mileageTotal != null) sb.append(String.format("累计里程: %.1f m\n", ((Number) mileageTotal).doubleValue()));

            Object faultCode = redisData.get("faultCode");
            if (faultCode != null && ((Number) faultCode).intValue() != 0) {
                sb.append(String.format("⚠️ 故障码: %d\n", ((Number) faultCode).intValue()));
            }

            Object lastUpdate = redisData.get("lastUpdateTime");
            if (lastUpdate != null) {
                Date d = new Date(((Number) lastUpdate).longValue());
                sb.append(String.format("最后上报: %s\n", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d)));
            }

            return sb.toString();
        } catch (Exception e) {
            return "状态数据解析失败: " + e.getMessage();
        }
    }

    /**
     * 工具2：列出所有在线设备
     */
    @SuppressWarnings("unchecked")
    private String listDevices() {
        Set<String> keys = redisUtil.getDeviceKeysOnly();
        if (keys == null || keys.isEmpty()) {
            return "当前没有设备在线数据。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("当前共有 %d 台设备有数据记录：\n", keys.size()));

        for (String deviceId : keys) {
            // 跳过 AI 会话 key
            if (deviceId.startsWith("ai:session:")) continue;

            try {
                Object data = redisUtil.getVehicle(deviceId);
                if (data instanceof Map) {
                    Map<String, Object> redisData = (Map<String, Object>) data;
                    Object battery = redisData.get("battery");
                    Object status = redisData.get("status");
                    sb.append(String.format("- %s: 电量=%s%%, 状态=%s\n",
                            deviceId,
                            battery != null ? String.format("%.0f", ((Number) battery).doubleValue()) : "未知",
                            status != null ? status : "未知"));
                } else {
                    sb.append(String.format("- %s\n", deviceId));
                }
            } catch (Exception e) {
                sb.append(String.format("- %s（数据解析异常）\n", deviceId));
            }
        }
        return sb.toString();
    }

    /**
     * 工具3：查询任务日志统计
     */
    private String queryTaskLogs(Map<String, Object> args) {
        String vehicleId = (String) args.get("vehicle_id");
        String startDateStr = (String) args.get("start_date");
        String endDateStr = (String) args.get("end_date");
        Object limitObj = args.get("limit");
        int limit = limitObj != null ? ((Number) limitObj).intValue() : 10;

        try {
            QueryWrapper<TaskLog> qw = new QueryWrapper<>();
            if (vehicleId != null && !vehicleId.isEmpty()) {
                qw.eq("vehicle_id", vehicleId);
            }
            if (startDateStr != null && !startDateStr.isEmpty()) {
                qw.ge("start_time", startDateStr + " 00:00:00");
            }
            if (endDateStr != null && !endDateStr.isEmpty()) {
                qw.le("start_time", endDateStr + " 23:59:59");
            }
            qw.orderByDesc("start_time").last("LIMIT " + Math.min(limit, 50));

            List<TaskLog> logs = taskLogService.list(qw);

            if (logs.isEmpty()) {
                return "没有找到符合条件的任务记录。";
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            float totalArea = 0;
            float totalEff = 0;
            int effCount = 0;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("共找到 %d 条任务记录：\n\n", logs.size()));

            for (TaskLog t : logs) {
                sb.append(String.format("任务ID: %s | 设备: %s | 状态: %s\n",
                        t.getId(), t.getVehicleId(), t.getStatus()));
                if (t.getStartTime() != null) {
                    sb.append(String.format("  开始: %s", sdf.format(t.getStartTime())));
                }
                if (t.getEndTime() != null) {
                    sb.append(String.format(" ~ 结束: %s", sdf.format(t.getEndTime())));
                }
                sb.append("\n");
                if (t.getCleaningArea() != null) {
                    sb.append(String.format("  清洁面积: %.1f㎡", t.getCleaningArea()));
                    totalArea += t.getCleaningArea();
                }
                if (t.getEfficiency() != null) {
                    sb.append(String.format("  效率: %.1f%%", t.getEfficiency() * 100));
                    totalEff += t.getEfficiency();
                    effCount++;
                }
                if (t.getCleaningMode() != null) {
                    sb.append(String.format("  模式: %s", t.getCleaningMode()));
                }
                sb.append("\n");
            }

            sb.append(String.format("\n统计：总清洁面积=%.1f㎡, 平均效率=%.1f%%",
                    totalArea, effCount > 0 ? (totalEff / effCount * 100) : 0));
            return sb.toString();

        } catch (Exception e) {
            log.error("[AiTool] queryTaskLogs 失败", e);
            return "查询任务日志失败: " + e.getMessage();
        }
    }

    /**
     * 工具4：查询车辆位置/电量历史
     */
    private String queryVehicleLogs(Map<String, Object> args) {
        String vehicleId = (String) args.get("vehicle_id");
        Object limitObj = args.get("limit");
        int limit = limitObj != null ? ((Number) limitObj).intValue() : 10;

        try {
            QueryWrapper<VehicleLog> qw = new QueryWrapper<>();
            if (vehicleId != null && !vehicleId.isEmpty()) {
                qw.eq("vehicle_id", vehicleId);
            }
            qw.orderByDesc("timestamp").last("LIMIT " + Math.min(limit, 50));

            List<VehicleLog> logs = vehicleLogService.list(qw);

            if (logs.isEmpty()) {
                return "没有找到车辆日志记录。";
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("共找到 %d 条车辆日志：\n", logs.size()));

            for (VehicleLog v : logs) {
                sb.append(String.format("设备: %s | 时间: %s | 电量: %.1f%% | 位置: (%.5f, %.5f) | 指令: %s\n",
                        v.getVehicleId(),
                        v.getTimestamp() != null ? sdf.format(v.getTimestamp()) : "未知",
                        v.getBattery() != null ? v.getBattery() : 0f,
                        v.getLon(), v.getLat(),
                        v.getCommandType() != null ? v.getCommandType() : "-"));
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("[AiTool] queryVehicleLogs 失败", e);
            return "查询车辆日志失败: " + e.getMessage();
        }
    }

    /**
     * 工具5：控制指定设备（发送 MQTT 控制指令）
     * control_mode: 1=AUTO启动, 2=STOP停止, 3=RESET复位, 4=CONT循环, 5=MAN手动
     * enable_mode:  0=无效, 1=左单, 2=左双, 3=右单, 4=右双
     */
    private String controlDevice(Map<String, Object> args) {
        String deviceId = (String) args.get("device_id");
        if (deviceId == null || deviceId.isEmpty()) {
            return "参数缺失：device_id 不能为空";
        }

        Object controlModeObj = args.get("control_mode");
        Object enableModeObj  = args.get("enable_mode");
        if (controlModeObj == null) {
            return "参数缺失：control_mode 不能为空（1=AUTO, 2=STOP, 3=RESET, 4=CONT, 5=MAN）";
        }

        int controlMode = ((Number) controlModeObj).intValue();
        int enableMode  = enableModeObj != null ? ((Number) enableModeObj).intValue() : 0;

        // 验证 control_mode 范围
        if (controlMode < 1 || controlMode > 5) {
            return "control_mode 超出范围，必须在 1-5 之间（1=AUTO, 2=STOP, 3=RESET, 4=CONT, 5=MAN）";
        }

        // 构建控制请求（完整参数帧：infoCommandType=0x00，由 fillMissingParamsFromDb 从数据库补全其余参数）
        // 交互帧(0x02)仅用于 D12↔D01 内部交互，对外控制统一走完整设置帧
        DeviceConfigRequest request = new DeviceConfigRequest();
        request.setDeviceId(deviceId);
        request.setCompanyCode("ZTZN-PVC");
        request.setModel(deviceId.length() >= 4 ? deviceId.substring(0, 4) : deviceId);
        // 不设置 infoCommandType（null → 后端默认 0x00 完整参数帧）
        request.setControlMode(controlMode);
        request.setEnableMode(enableMode);
        request.setFaultCode(0);
        request.setCurrentRowPosition(0);
        request.setUsername("AI_AGENT");

        try {
            DeviceControlResponse result = deviceControlService.sendControlCommand(request);
            if (Boolean.TRUE.equals(result.getSuccess())) {
                String[] modeNames = {"", "AUTO启动", "STOP停止", "RESET复位", "CONT循环", "MAN手动"};
                String modeName = controlMode <= 5 ? modeNames[controlMode] : "未知";
                return String.format("✅ 已成功向设备 %s 发送 %s 指令（MQTT主题: %s）",
                        deviceId, modeName, result.getMqttTopic());
            } else {
                return String.format("❌ 向设备 %s 发送控制指令失败：%s", deviceId, result.getMessage());
            }
        } catch (Exception e) {
            log.error("[AiTool] controlDevice 失败 - deviceId={}, controlMode={}", deviceId, controlMode, e);
            return "控制指令发送异常: " + e.getMessage();
        }
    }

    // ===== 工具定义（返回给 DeepSeek 的 tools 列表，JSON 格式） =====

    /**
     * 返回所有工具的 JSON 定义列表（OpenAI function calling 格式）
     */
    public static String getToolsDefinitionJson() {
        return "[\n" +
            "  {\n" +
            "    \"type\": \"function\",\n" +
            "    \"function\": {\n" +
            "      \"name\": \"query_device_status\",\n" +
            "      \"description\": \"查询指定设备的实时状态，包括电量、位置、速度、故障码、运行时长等。\",\n" +
            "      \"parameters\": {\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": {\n" +
            "          \"device_id\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"设备ID，例如 -D01250001 或 -D12250001\"\n" +
            "          }\n" +
            "        },\n" +
            "        \"required\": [\"device_id\"]\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  {\n" +
            "    \"type\": \"function\",\n" +
            "    \"function\": {\n" +
            "      \"name\": \"list_devices\",\n" +
            "      \"description\": \"列出所有已知设备及其简要状态（电量、在线情况）。\",\n" +
            "      \"parameters\": {\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": {}\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  {\n" +
            "    \"type\": \"function\",\n" +
            "    \"function\": {\n" +
            "      \"name\": \"query_task_logs\",\n" +
            "      \"description\": \"查询清洁任务历史记录，可按设备ID和日期范围过滤，返回任务列表及统计（总面积、平均效率）。\",\n" +
            "      \"parameters\": {\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": {\n" +
            "          \"vehicle_id\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"设备ID，不填则查所有设备\"\n" +
            "          },\n" +
            "          \"start_date\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"开始日期，格式 yyyy-MM-dd，例如 2026-03-01\"\n" +
            "          },\n" +
            "          \"end_date\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"结束日期，格式 yyyy-MM-dd，例如 2026-03-31\"\n" +
            "          },\n" +
            "          \"limit\": {\n" +
            "            \"type\": \"integer\",\n" +
            "            \"description\": \"返回条数，默认10，最大50\"\n" +
            "          }\n" +
            "        },\n" +
            "        \"required\": []\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  {\n" +
            "    \"type\": \"function\",\n" +
            "    \"function\": {\n" +
            "      \"name\": \"query_vehicle_logs\",\n" +
            "      \"description\": \"查询车辆历史位置和电量记录，可按设备ID过滤。\",\n" +
            "      \"parameters\": {\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": {\n" +
            "          \"vehicle_id\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"设备ID，不填则查所有设备\"\n" +
            "          },\n" +
            "          \"limit\": {\n" +
            "            \"type\": \"integer\",\n" +
            "            \"description\": \"返回条数，默认10，最大50\"\n" +
            "          }\n" +
            "        },\n" +
            "        \"required\": []\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  {\n" +
            "    \"type\": \"function\",\n" +
            "    \"function\": {\n" +
            "      \"name\": \"control_device\",\n" +
            "      \"description\": \"向指定设备发送控制指令，支持启动（AUTO）、停止（STOP）、复位（RESET）、循环（CONT）、手动（MAN）。仅在用户明确要求控制设备时调用。\",\n" +
            "      \"parameters\": {\n" +
            "        \"type\": \"object\",\n" +
            "        \"properties\": {\n" +
            "          \"device_id\": {\n" +
            "            \"type\": \"string\",\n" +
            "            \"description\": \"设备ID，例如 -D01250001 或 -D12250001\"\n" +
            "          },\n" +
            "          \"control_mode\": {\n" +
            "            \"type\": \"integer\",\n" +
            "            \"description\": \"控制模式：1=AUTO启动, 2=STOP停止, 3=RESET复位, 4=CONT循环, 5=MAN手动\",\n" +
            "            \"enum\": [1, 2, 3, 4, 5]\n" +
            "          },\n" +
            "          \"enable_mode\": {\n" +
            "            \"type\": \"integer\",\n" +
            "            \"description\": \"使能模式：0=无效, 1=左侧单趟, 2=左侧双趟, 3=右侧单趟, 4=右侧双趟。不指定时默认1（左单趟）\",\n" +
            "            \"enum\": [0, 1, 2, 3, 4]\n" +
            "          }\n" +
            "        },\n" +
            "        \"required\": [\"device_id\", \"control_mode\"]\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "]";
    }
}
