package com.noinch.mcp.demo.smarthome.custom.server.light.service;

import com.noinch.mcp.demo.smarthome.custom.core.model.DeviceCommand;
import com.noinch.mcp.demo.smarthome.custom.core.model.DeviceType;
import com.noinch.mcp.demo.smarthome.custom.core.model.HomeDevice;
import com.noinch.mcp.demo.smarthome.custom.core.model.HomeDeviceStatus;
import com.noinch.mcp.protocol.core.mcp.annotation.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LightService {

    private final Map<String, HomeDevice> deviceStore = new ConcurrentHashMap<>();

    public LightService() {
        HomeDevice livingRoomLight = new HomeDevice();
        livingRoomLight.setId("light-00001");
        livingRoomLight.setSpeciName("客厅主灯");
        livingRoomLight.setStatus(HomeDeviceStatus.ONLINE);
        livingRoomLight.setDeviceType(DeviceType.LIGHT);
        livingRoomLight.setHomeId("home-00001");
        livingRoomLight.getDeviceProperties().put("brightness", 80);
        livingRoomLight.getDeviceProperties().put("color", "white");
        livingRoomLight.getDeviceProperties().put("power", true);
        deviceStore.put("light-00001", livingRoomLight);

        HomeDevice bedroomLight = new HomeDevice();
        bedroomLight.setId("light-00002");
        bedroomLight.setSpeciName("卧室台灯");
        bedroomLight.setStatus(HomeDeviceStatus.ONLINE);
        bedroomLight.setDeviceType(DeviceType.LIGHT);
        bedroomLight.setHomeId("home-00001");
        bedroomLight.getDeviceProperties().put("brightness", 50);
        bedroomLight.getDeviceProperties().put("color", "warm");
        bedroomLight.getDeviceProperties().put("power", true);
        deviceStore.put("light-00002", bedroomLight);
    }

    @McpTool(description = "获取所有灯光设备列表")
    public String getLightDevices() {
        StringBuilder result = new StringBuilder("可用灯光设备：\n");
        deviceStore.values().forEach(device -> {
            result.append("- ")
                    .append(device.getSpeciName())
                    .append(" (ID: ").append(device.getId())
                    .append(", 状态: ").append(device.getStatus().getDesc())
                    .append(")\n");
        });
        return result.toString();
    }

    @McpTool(description = "控制灯光开关")
    public String controlLight(String deviceId, String action) {
        if (!action.equals("on") && !action.equals("off")) {
            return "操作必须是 on 或 off";
        }
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        if (!DeviceType.LIGHT.equals(device.getDeviceType())) {
            return "不是灯光设备: " + deviceId;
        }

        try {
            DeviceCommand command = new DeviceCommand();
            command.setRequestId(UUID.randomUUID().toString());
            command.setDeviceId(deviceId);
            command.setAction(action);
            command.setParams(Map.of("deviceId", deviceId, "action", action));
            sendDeviceCommand(command);

            if ("on".equals(action)) {
                device.getDeviceProperties().put("power", true);
                device.setStatus(HomeDeviceStatus.ONLINE);
            } else {
                device.getDeviceProperties().put("power", false);
                device.setStatus(HomeDeviceStatus.OFFLINE);
            }
            device.setLastActiveTime(LocalDateTime.now());
            log.info("灯光控制成功: {} - {}", deviceId, action);
            return device.getSpeciName() + "已" + ("on".equals(action) ? "打开" : "关闭");
        } catch (Exception e) {
            log.error("灯光控制失败: {}", deviceId, e);
            return device.getSpeciName() + "控制失败: " + e.getMessage();
        }
    }

    @McpTool(description = "调节灯光亮度")
    public String setBrightness(String deviceId, Integer brightness) {
        if (brightness < 0 || brightness > 100) {
            return "亮度值必须在0-100之间";
        }
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        try {
            DeviceCommand command = new DeviceCommand();
            command.setRequestId(UUID.randomUUID().toString());
            command.setDeviceId(deviceId);
            command.setAction("setBrightness");
            command.setParams(Map.of("deviceId", deviceId, "brightness", brightness));
            sendDeviceCommand(command);

            device.getDeviceProperties().put("brightness", brightness);
            device.setLastActiveTime(LocalDateTime.now());
            log.info("亮度调节成功: {} - {}", deviceId, brightness);
            return device.getSpeciName() + "亮度已调节至" + brightness + "%";
        } catch (Exception e) {
            log.error("亮度调节失败: {}", deviceId, e);
            return device.getSpeciName() + "亮度调节失败: " + e.getMessage();
        }
    }

    @McpTool(description = "设置灯光颜色")
    public String setLightColor(String deviceId, String color) {
        List<String> supportedColors = List.of("red", "blue", "green", "yellow", "white", "warm");
        if (!supportedColors.contains(color.toLowerCase())) {
            return "不支持的颜色，支持: " + String.join(", ", supportedColors);
        }
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        try {
            DeviceCommand command = new DeviceCommand();
            command.setRequestId(UUID.randomUUID().toString());
            command.setDeviceId(deviceId);
            command.setAction("setColor");
            command.setParams(Map.of("deviceId", deviceId, "color", color));
            sendDeviceCommand(command);

            device.getDeviceProperties().put("color", color);
            device.setLastActiveTime(LocalDateTime.now());
            log.info("颜色设置成功: {} - {}", deviceId, color);
            return device.getSpeciName() + "颜色已更改为" + color;
        } catch (Exception e) {
            log.error("颜色设置失败: {}", deviceId, e);
            return device.getSpeciName() + "颜色设置失败: " + e.getMessage();
        }
    }

    @McpTool(description = "获取设备当前状态")
    public String getDeviceStatus(String deviceId) {
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        return String.format("%s - 状态: %s, 亮度: %s%%, 颜色: %s, 最后活跃: %s",
                device.getSpeciName(), device.getStatus().getDesc(),
                device.getDeviceProperties().getOrDefault("brightness", "unknown"),
                device.getDeviceProperties().getOrDefault("color", "unknown"),
                device.getLastActiveTime() != null ? device.getLastActiveTime().toString() : "无");
    }

    private void sendDeviceCommand(DeviceCommand command) {
        log.info("发送设备命令: {}", command);
        try {
            // 模拟与控制平台交互延迟
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
