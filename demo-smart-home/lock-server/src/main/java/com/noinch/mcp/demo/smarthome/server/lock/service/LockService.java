package com.noinch.mcp.demo.smarthome.server.lock.service;

import com.noinch.mcp.demo.smarthome.core.model.DeviceCommand;
import com.noinch.mcp.demo.smarthome.core.model.DeviceType;
import com.noinch.mcp.demo.smarthome.core.model.HomeDevice;
import com.noinch.mcp.demo.smarthome.core.model.HomeDeviceStatus;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Slf4j
@Service
public class LockService {

    // 模拟设备状态存储
    private final Map<String, HomeDevice> deviceStore = new ConcurrentHashMap<>();

    /**
     * 初始化一些示例设备
     */
    public LockService() {
        // 初始化智能门锁
        HomeDevice frontDoorLock = new HomeDevice();
        frontDoorLock.setId("lock-00001");
        frontDoorLock.setSpeciName("前门智能锁");
        frontDoorLock.setStatus(HomeDeviceStatus.ONLINE);
        // home
        frontDoorLock.setHomeId("home-00001");
        // device
        frontDoorLock.setDeviceType(DeviceType.LOCK);
        frontDoorLock.getDeviceProperties().put("locked", true);
        frontDoorLock.getDeviceProperties().put("battery", 85);
        deviceStore.put("lock-000001", frontDoorLock);

        // 初始化卧室门锁
        HomeDevice bedroomLock = new HomeDevice();
        bedroomLock.setId("lock-00002");
        bedroomLock.setSpeciName("卧室智能锁");
        // home
        bedroomLock.setHomeId("home-00002");
        // device
        bedroomLock.setDeviceType(DeviceType.LOCK);
        bedroomLock.setStatus(HomeDeviceStatus.ONLINE);
        bedroomLock.getDeviceProperties().put("locked", true);
        bedroomLock.getDeviceProperties().put("battery", 92);
        deviceStore.put("lock-000002", bedroomLock);
    }

    /**
     * 获取设备列表
     */
    @Tool(description = "获取所有智能锁设备列表")
    public String getLockDevices() {
        StringBuilder result = new StringBuilder("可用智能锁设备：\n");
        deviceStore.values().forEach(device -> {
            result.append("- ")
                    .append(device.getSpeciName())
                    .append(" (ID: ")
                    .append(device.getId())
                    .append(", 房间: ")
                    .append(device.getHomeId())
                    .append(", 状态: ")
                    .append(device.getStatus().getDesc())
                    .append(")\n");
        });
        return result.toString();
    }

    /**
     * 控制门锁开关
     */
    @Tool(description = "控制智能锁开关")
    public String controlLock(
            @ToolParam(description = "设备ID") String deviceId,
            @ToolParam(description = "操作：lock 上锁，unlock 解锁") String action) {

        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        if (!device.getDeviceType().equals(DeviceType.LOCK)) {
            return "不是智能锁设备: " + deviceId;
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
            if ("lock".equals(action)) {
                device.getDeviceProperties().put("locked", true);
                log.info("门锁已上锁: {}", deviceId);
            } else if ("unlock".equals(action)) {
                device.getDeviceProperties().put("locked", false);
                log.info("门锁已解锁: {}", deviceId);
            }
            return device.getSpeciName() + "已" + ("lock".equals(action) ? "上锁" : "解锁");

        } catch (Exception e) {
            log.error("门锁控制失败: " + deviceId, e);
            return device.getSpeciName() + "控制失败: " + e.getMessage();
        }
    }

    /**
     * 查询门锁状态
     */
    @Tool(description = "查询门锁当前状态")
    public String getLockStatus(
            @ToolParam(description = "设备ID") String deviceId) {

        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        boolean isLocked = (boolean) device.getDeviceProperties().getOrDefault("locked", false);
        int batteryLevel = (int) device.getDeviceProperties().getOrDefault("battery", 0);
        return String.format("%s - 状态: %s, 锁定状态: %s, 电池电量: %d%%, 最后活跃: %s",
                device.getSpeciName(),
                device.getStatus().getDesc(),
                isLocked ? "已锁定" : "已解锁",
                batteryLevel,
                device.getLastActiveTime() != null ? device.getLastActiveTime().toString() : "无");
    }

    /**
     * 查询电池电量
     */
    @Tool(description = "查询智能锁电池电量")
    public String checkBattery(
            @ToolParam(description = "设备ID") String deviceId) {

        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        int batteryLevel = (int) device.getDeviceProperties().getOrDefault("battery", 0);
        String status = batteryLevel > 20 ? "正常" : (batteryLevel > 10 ? "低电量" : "需要充电");
        return String.format("%s - 电池电量: %d%% (%s)",
                device.getSpeciName(),
                batteryLevel,
                status);
    }

    /**
     * 设置临时密码
     */
    @Tool(description = "设置临时访问密码")
    public String setTemporaryPassword(
            @ToolParam(description = "设备ID") String deviceId,
            @ToolParam(description = "临时密码") String password,
            @ToolParam(description = "有效时长（小时）") Integer hours) {

        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        if (hours == null || hours < 1 || hours > 168) {
            return "有效时长必须在1-168小时之间";
        }

        try {
            // 构造 DTO
            DeviceCommand command = new DeviceCommand();
            command.setDeviceId(deviceId);
            command.setAction("setTemporaryPassword");
            command.setParams(Map.of(
                    "deviceId", deviceId,
                    "password", password,
                    "expireHours", hours
            ));
            sendDeviceCommand(command);

            // 更新设备属性
            device.getDeviceProperties().put("temporaryPassword", password);
            device.getDeviceProperties().put("passwordExpireTime", LocalDateTime.now().plusHours(hours));
            device.setLastActiveTime(LocalDateTime.now());

            log.info("临时密码设置成功: {} - {} (有效期{}小时)", deviceId, password, hours);
            return device.getSpeciName() + "临时密码已设置: " + password + " (有效期" + hours + "小时)";

        } catch (Exception e) {
            log.error("临时密码设置失败: " + deviceId, e);
            return device.getSpeciName() + "临时密码设置失败: " + e.getMessage();
        }
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
