package com.noinch.mcp.demo.smarthome.server.light.service;

import com.noinch.mcp.demo.smarthome.core.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class LightService {

    // 测试用 Local Store
    private final Map<String, HomeDevice> deviceStore = new HashMap<>();

    public LightService () {
        // 初始化用户
        User user1 = new User();
        user1.setId("user-00001");
        user1.setName("张三");
        user1.setDeviceIds(List.of());

        User user2 = new User();
        user2.setId("user-00002");
        user2.setName("李四");
        user2.setDeviceIds(List.of());

        // 初始化客厅灯
        HomeDevice livingRoomLight = new HomeDevice();
        livingRoomLight.setId("light-00001");
        livingRoomLight.setSpeciName("客厅主灯");
        livingRoomLight.setStatus(HomeDeviceStatus.ONLINE);
        // Home
        livingRoomLight.setHomeId("home-00001");
        // User
        user1.getDeviceIds().add("light-00001");
        // Device
        livingRoomLight.setDeviceId("light-00001");
        livingRoomLight.setDeviceName("灯");
        livingRoomLight.setDeviceType(DeviceType.LIGHT);
        livingRoomLight.setDeviceProperties(new HashMap<>());
        livingRoomLight.getDeviceProperties().put("brightness", 80);
        livingRoomLight.getDeviceProperties().put("color", "white");
        deviceStore.put("light-00001", livingRoomLight);

        // 初始化卧室灯
        HomeDevice bedroomLight = new HomeDevice();
        bedroomLight.setId("light-00002");
        bedroomLight.setSpeciName("卧室台灯");
        bedroomLight.setStatus(HomeDeviceStatus.ONLINE);
        // User
        user1.getDeviceIds().add("light-00002");
        user2.getDeviceIds().add("light-00002");
        // Home
        bedroomLight.setHomeId("home-00001");
        // Device
        bedroomLight.setDeviceId("light-00002");
        bedroomLight.setDeviceName("灯");
        bedroomLight.setDeviceType(DeviceType.LIGHT);
        bedroomLight.setDeviceProperties(new HashMap<>());
        bedroomLight.getDeviceProperties().put("brightness", 50);
        bedroomLight.getDeviceProperties().put("color", "warm");
        deviceStore.put("light-00002", bedroomLight);
    }

    /**
     * 获取设备列表
     */
    @Tool(description = "获取所有灯光设备列表")
    public String getLightDevices() {
        StringBuilder result = new StringBuilder("可用灯光设备：\n");
        deviceStore.values().forEach(device -> {
            result.append("- ")
                    .append(device.getSpeciName())
                    .append(" (ID: ")
                    .append(device.getId())
                    .append(", 房号: ")
                    .append(device.getHomeId())
                    .append(", 状态: ")
                    .append(device.getStatus().getDesc())
                    .append(")\n");
        });
        return result.toString();
    }

    /**
     * 控制灯光开关
     */
    @Tool(description = "控制灯光开关")
    public String controlLight(
            @ToolParam(description = "设备ID") String deviceId,
            @ToolParam(description = "操作：on 开灯，off 关灯") String action) {

        if (!action.equals("on") && !action.equals("off")) {
            return "开操作必须是 on 或 off";
        }
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        if (!device.getDeviceType().equals(DeviceType.LIGHT)) {
            return "不是灯光设备: " + deviceId;
        }

        try {
            // 构造控制 DTO
            String requestId = UUID.randomUUID().toString();
            DeviceCommand command = new DeviceCommand();
            command.setRequestId(requestId);
            command.setDeviceId(deviceId);
            command.setAction(action);
            command.setParams(Map.of(
                    "deviceId", deviceId,
                    "action", action));

            // 发送控制命令
            sendDeviceCommand(command);

            // 更新设备状态
            if ("on".equals(action)) {
                device.setStatus(HomeDeviceStatus.ONLINE);
                device.getDeviceProperties().put("power", true);
            } else {
                device.setStatus(HomeDeviceStatus.OFFLINE);
                device.getDeviceProperties().put("power", false);
            }
            device.setLastActiveTime(LocalDateTime.now());

            log.info("灯光控制成功: {} - {}", deviceId, action);
            return device.getSpeciName() + "已" + ("on".equals(action) ? "打开" : "关闭");


        } catch (Exception e) {
            log.error("灯光控制失败: " + deviceId, e);
            return device.getSpeciName() + "控制失败: " + e.getMessage();
        }
    }

    /**
     * 调节灯光亮度
     */
    @Tool(description = "调节灯光亮度")
    public String setBrightness(
            @ToolParam(description = "设备ID") String deviceId,
            @ToolParam(description = "亮度值 0-100") Integer brightness) {

        if (brightness < 0 || brightness > 100) {
            return "亮度值必须在0-100之间";
        }
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        try {
            // 构造控制 DTO
            String requestId = UUID.randomUUID().toString();
            DeviceCommand command = new DeviceCommand();
            command.setRequestId(requestId);
            command.setDeviceId(deviceId);
            command.setAction("setBrightness");
            command.setParams(Map.of(
                    "deviceId", deviceId,
                    "brightness", brightness));

            // 发送控制命令
            sendDeviceCommand(command);

            // 更新设备属性
            device.getDeviceProperties().put("brightness", brightness);
            device.setLastActiveTime(LocalDateTime.now());

            log.info("亮度调节成功: {} - {}", deviceId, brightness);
            return device.getSpeciName() + "亮度已调节至" + brightness + "%";

        } catch (Exception e) {
            log.error("亮度调节失败: " + deviceId, e);
            return device.getSpeciName() + "亮度调节失败: " + e.getMessage();
        }
    }

    /**
     * 设置灯光颜色
     */
    @Tool(description = "设置灯光颜色")
    public String setLightColor(
            @ToolParam(description = "设备ID") String deviceId,
            @ToolParam(description = "颜色值，支持：red, blue, green, yellow, white, warm") String color) {

        // 支持的颜色列表
        List<String> supportedColors = List.of("red", "blue", "green", "yellow", "white", "warm");
        if (!supportedColors.contains(color.toLowerCase())) {
            return "不支持的颜色，支持的颜色: " + String.join(", ", supportedColors);
        }
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        try {
            // 构造控制 DTO
            String requestId = UUID.randomUUID().toString();
            DeviceCommand command = new DeviceCommand();
            command.setRequestId(requestId);
            command.setDeviceId(deviceId);
            command.setAction("setColor");
            command.setParams(Map.of(
                    "deviceId", deviceId,
                    "color", color
            ));

            // 发送控制命令
            sendDeviceCommand(command);

            // 更新设备属性
            device.getDeviceProperties().put("color", color);
            device.setLastActiveTime(LocalDateTime.now());

            log.info("颜色设置成功: {} - {}", deviceId, color);
            return device.getSpeciName() + "颜色已更改为" + color;

        } catch (Exception e) {
            log.error("颜色设置失败: " + deviceId, e);
            return device.getSpeciName() + "颜色设置失败: " + e.getMessage();
        }
    }

    /**
     * 获取设备状态
     */
    @Tool(description = "获取设备当前状态")
    public String getDeviceStatus(
            @ToolParam(description = "设备ID") String deviceId) {

        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        return String.format("%s - 状态: %s, 亮度: %s%%, 颜色: %s, 最后活跃: %s",
                device.getSpeciName(),
                device.getStatus().getDesc(),
                device.getDeviceProperties().getOrDefault("brightness", "unknown"),
                device.getDeviceProperties().getOrDefault("color", "unknown"),
                device.getLastActiveTime() != null ? device.getLastActiveTime().toString() : "无");
    }

    /**
     * 发送设备命令（模拟）
     */
    private void sendDeviceCommand(DeviceCommand command) {
        // 这里可以调用真实的设备控制API
        // 例如：MQTT、HTTP API、WebSocket等
        log.info("发送设备命令: {}", command);

        // 模拟延迟
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}