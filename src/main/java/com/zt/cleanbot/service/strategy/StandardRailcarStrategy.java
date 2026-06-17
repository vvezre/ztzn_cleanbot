package com.zt.cleanbot.service.strategy;

import com.zt.cleanbot.dto.DeviceConfigRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StandardRailcarStrategy implements DeviceControlStrategy {

    private static final int FRAME_HEX_LENGTH = 140;

    @Override
    public boolean supports(String model) {
        return model != null && !model.endsWith("D12") && !model.endsWith("T12");
    }

    @Override
    public String encode(DeviceConfigRequest request) {
        try {
            StringBuilder hex = new StringBuilder(FRAME_HEX_LENGTH);

            appendIdentity(hex, request, "-D01");
            // H9-H11 follows the latest protocol table and uses packed 2-byte numeric words.
            hex.append(toWord(resolveIdentifierCode(request), 0));
            hex.append(toWord(request.getStartAddress(), 0));
            hex.append(toWord(request.getDataLength(), 35));
            hex.append(toWord(resolveBindEnabled(request), 0));

            // H13-H20: 捆绑交互接收区，非捆绑转发时补零
            hex.append(resolveInteractionPayload(request.getInteractionPayloadHex()));

            // H21-H22: 预留
            hex.append("0000");
            hex.append("0000");

            // H23-H34: 标准 D 型参数区
            hex.append(toWord(request.getControlMode(), 0));
            hex.append(toWord(request.getEnableMode(), 0));
            hex.append(toWord(request.getEdgeDelay(), 1000));
            hex.append(toWord(request.getBridgeTime(), 6000));
            hex.append(toWord(request.getErrorReturnTime(), 1000));
            hex.append(toWord(request.getWalkSpeed(), 800));
            hex.append(toWord(request.getBrushSpeed(), 1000));
            hex.append(toWord(request.getBridgeSpeed(), 800));
            hex.append(toWord(request.getHeartbeatSet(), 100));
            hex.append(toWord(request.getBatteryLowLimit(), 50));
            hex.append(toWord(request.getReserved(), 0));
            hex.append(toWord(request.getReserved2(), 0));

            return finalizeHex(hex, request);
        } catch (Exception e) {
            log.error("标准 D 型设备编码失败 - deviceId={}", request != null ? request.getDeviceId() : null, e);
            return null;
        }
    }

    private void appendIdentity(StringBuilder hex, DeviceConfigRequest request, String defaultModel) {
        String companyCode = request.getCompanyCode() != null ? request.getCompanyCode().trim() : "ZTZN-PVC";
        String model = request.getModel() != null ? request.getModel().trim() : defaultModel;
        String productNumber = extractProductNumber(request.getDeviceId());

        hex.append(stringToHex(companyCode, 8));
        hex.append(stringToHex(model, 4));
        hex.append(stringToHex(String.format("%-6s", productNumber).substring(0, 6), 6));
    }

    private String finalizeHex(StringBuilder hex, DeviceConfigRequest request) {
        String encoded = hex.toString().toUpperCase();
        if (encoded.length() != FRAME_HEX_LENGTH) {
            log.warn("标准 D 型编码长度异常 - deviceId={}, length={}, hex={}",
                    request != null ? request.getDeviceId() : null, encoded.length(), encoded);
        }
        return encoded;
    }

    private String stringToHex(String value, int length) {
        StringBuilder hex = new StringBuilder(length * 2);
        String padded = String.format("%-" + length + "s", value == null ? "" : value).substring(0, length);
        for (char c : padded.toCharArray()) {
            hex.append(String.format("%02X", (int) c));
        }
        return hex.toString();
    }

    private String toWord(Integer value, int defaultValue) {
        int finalValue = normalizeRange(value != null ? value : defaultValue, 0, 9999);
        return String.format("%04d", finalValue);
    }

    private int resolveIdentifierCode(DeviceConfigRequest request) {
        Integer identifier = request.getInfoCommandType();
        if (identifier == null) {
            return 0;
        }
        return normalizeRange(identifier, 0, 2);
    }

    private int resolveBindEnabled(DeviceConfigRequest request) {
        Integer bindStatus = request.getBindStatus();
        if (bindStatus != null) {
            return bindStatus > 0 ? 1 : 0;
        }
        if (request.getBindDeviceIds() != null && !request.getBindDeviceIds().isEmpty()) {
            return 1;
        }
        return request.getBindDeviceId() != null && !request.getBindDeviceId().trim().isEmpty() ? 1 : 0;
    }

    private String resolveInteractionPayload(String payloadHex) {
        if (payloadHex != null) {
            String normalized = payloadHex.trim().toUpperCase();
            if (normalized.length() == 32 && normalized.matches("[0-9A-F]+")) {
                return normalized;
            }
            log.warn("标准 D 型交互区无效，使用全 0 兜底 - payloadHex={}", payloadHex);
        }
        return "00000000000000000000000000000000";
    }

    private int normalizeRange(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private String extractProductNumber(String deviceId) {
        if (deviceId == null || deviceId.length() < 5) {
            return "000000";
        }
        return deviceId.length() >= 6 ? deviceId.substring(deviceId.length() - 6) : deviceId;
    }
}
