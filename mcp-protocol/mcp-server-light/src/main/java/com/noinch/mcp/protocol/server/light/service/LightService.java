package com.noinch.mcp.protocol.server.light.service;

import com.noinch.mcp.protocol.core.mcp.annotation.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LightService {

    private final Map<String, Map<String, Object>> deviceStore = new ConcurrentHashMap<>();

    public LightService() {
        // 初始化客厅灯
        Map<String, Object> livingRoomLight = new HashMap<>();
        livingRoomLight.put("id", "light-00001");
        livingRoomLight.put("name", "客厅主灯");
        livingRoomLight.put("status", "online");
        livingRoomLight.put("brightness", 80);
        livingRoomLight.put("color", "white");
        livingRoomLight.put("power", true);
        deviceStore.put("light-00001", livingRoomLight);

        // 初始化卧室灯
        Map<String, Object> bedroomLight = new HashMap<>();
        bedroomLight.put("id", "light-00002");
        bedroomLight.put("name", "卧室台灯");
        bedroomLight.put("status", "online");
        bedroomLight.put("brightness", 50);
        bedroomLight.put("color", "warm");
        bedroomLight.put("power", true);
        deviceStore.put("light-00002", bedroomLight);
    }

    @McpTool(description = "获取所有灯光设备列表")
    public String getLightDevices() {
        StringBuilder result = new StringBuilder("可用灯光设备：\n");
        deviceStore.values().forEach(device -> {
            result.append("- ").append(device.get("name"))
                    .append(" (ID: ").append(device.get("id"))
                    .append(", 状态: ").append(device.get("status"))
                    .append(")\n");
        });
        return result.toString();
    }

    @McpTool(description = "控制灯光开关")
    public String controlLight(String deviceId, String action) {
        if (!action.equals("on") && !action.equals("off")) {
            return "操作必须是 on 或 off";
        }
        Map<String, Object> device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        device.put("power", "on".equals(action));
        device.put("status", "on".equals(action) ? "online" : "offline");
        log.info("灯光控制成功: {} - {}", deviceId, action);
        return device.get("name") + "已" + ("on".equals(action) ? "打开" : "关闭");
    }

    @McpTool(description = "调节灯光亮度")
    public String setBrightness(String deviceId, Integer brightness) {
        if (brightness < 0 || brightness > 100) {
            return "亮度值必须在0-100之间";
        }
        Map<String, Object> device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        device.put("brightness", brightness);
        log.info("亮度调节成功: {} - {}", deviceId, brightness);
        return device.get("name") + "亮度已调节至" + brightness + "%";
    }

    @McpTool(description = "设置灯光颜色")
    public String setLightColor(String deviceId, String color) {
        List<String> supportedColors = List.of("red", "blue", "green", "yellow", "white", "warm");
        if (!supportedColors.contains(color.toLowerCase())) {
            return "不支持的颜色，支持: " + String.join(", ", supportedColors);
        }
        Map<String, Object> device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        device.put("color", color);
        log.info("颜色设置成功: {} - {}", deviceId, color);
        return device.get("name") + "颜色已更改为" + color;
    }

    @McpTool(description = "获取设备当前状态")
    public String getDeviceStatus(String deviceId) {
        Map<String, Object> device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        return String.format("%s - 状态: %s, 亮度: %s%%, 颜色: %s",
                device.get("name"), device.get("status"),
                device.get("brightness"), device.get("color"));
    }
}
