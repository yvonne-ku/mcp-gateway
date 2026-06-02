package com.noinch.mcp.demo.smarthome.custom.server.lock.service;

import com.noinch.mcp.demo.smarthome.custom.core.model.DeviceCommand;
import com.noinch.mcp.demo.smarthome.custom.core.model.DeviceType;
import com.noinch.mcp.demo.smarthome.custom.core.model.HomeDevice;
import com.noinch.mcp.demo.smarthome.custom.core.model.HomeDeviceStatus;
import com.noinch.mcp.protocol.core.mcp.annotation.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LockService {

    private final Map<String, HomeDevice> deviceStore = new ConcurrentHashMap<>();

    public LockService() {
        HomeDevice frontDoorLock = new HomeDevice();
        frontDoorLock.setId("lock-00001");
        frontDoorLock.setSpeciName("前门智能锁");
        frontDoorLock.setStatus(HomeDeviceStatus.ONLINE);
        frontDoorLock.setDeviceType(DeviceType.LOCK);
        frontDoorLock.setHomeId("home-00001");
        frontDoorLock.getDeviceProperties().put("locked", true);
        frontDoorLock.getDeviceProperties().put("battery", 85);
        deviceStore.put("lock-00001", frontDoorLock);

        HomeDevice bedroomLock = new HomeDevice();
        bedroomLock.setId("lock-00002");
        bedroomLock.setSpeciName("卧室智能锁");
        bedroomLock.setStatus(HomeDeviceStatus.ONLINE);
        bedroomLock.setDeviceType(DeviceType.LOCK);
        bedroomLock.setHomeId("home-00002");
        bedroomLock.getDeviceProperties().put("locked", true);
        bedroomLock.getDeviceProperties().put("battery", 92);
        deviceStore.put("lock-00002", bedroomLock);
    }

    @McpTool(description = "获取所有智能锁设备列表")
    public String getLockDevices() {
        StringBuilder result = new StringBuilder("可用智能锁设备：\n");
        deviceStore.values().forEach(device -> {
            result.append("- ")
                    .append(device.getSpeciName())
                    .append(" (ID: ").append(device.getId())
                    .append(", 状态: ").append(device.getStatus().getDesc())
                    .append(")\n");
        });
        return result.toString();
    }

    @McpTool(description = "控制智能锁开关")
    public String controlLock(String deviceId, String action) {
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        if (!DeviceType.LOCK.equals(device.getDeviceType())) {
            return "不是智能锁设备: " + deviceId;
        }

        try {
            DeviceCommand command = new DeviceCommand();
            command.setRequestId(UUID.randomUUID().toString());
            command.setDeviceId(deviceId);
            command.setAction(action);
            command.setParams(Map.of("deviceId", deviceId, "action", action));
            sendDeviceCommand(command);

            if ("lock".equals(action)) {
                device.getDeviceProperties().put("locked", true);
                device.setLastActiveTime(LocalDateTime.now());
                log.info("门锁已上锁: {}", deviceId);
                return device.getSpeciName() + "已上锁";
            } else if ("unlock".equals(action)) {
                device.getDeviceProperties().put("locked", false);
                device.setLastActiveTime(LocalDateTime.now());
                log.info("门锁已解锁: {}", deviceId);
                return device.getSpeciName() + "已解锁";
            }
            return "操作必须是 lock 或 unlock";
        } catch (Exception e) {
            log.error("门锁控制失败: {}", deviceId, e);
            return device.getSpeciName() + "控制失败: " + e.getMessage();
        }
    }

    @McpTool(description = "查询门锁当前状态")
    public String getLockStatus(String deviceId) {
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        boolean locked = (boolean) device.getDeviceProperties().getOrDefault("locked", false);
        int battery = (int) device.getDeviceProperties().getOrDefault("battery", 0);
        return String.format("%s - 状态: %s, 锁定: %s, 电池: %d%%, 最后活跃: %s",
                device.getSpeciName(), device.getStatus().getDesc(),
                locked ? "已锁定" : "已解锁", battery,
                device.getLastActiveTime() != null ? device.getLastActiveTime().toString() : "无");
    }

    @McpTool(description = "查询智能锁电池电量")
    public String checkBattery(String deviceId) {
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }

        int battery = (int) device.getDeviceProperties().getOrDefault("battery", 0);
        String status = battery > 20 ? "正常" : (battery > 10 ? "低电量" : "需要充电");
        return String.format("%s - 电池: %d%% (%s)", device.getSpeciName(), battery, status);
    }

    @McpTool(description = "设置临时访问密码")
    public String setTemporaryPassword(String deviceId, String password, Integer hours) {
        HomeDevice device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        if (hours == null || hours < 1 || hours > 168) {
            return "有效时长必须在 1-168 小时之间";
        }

        try {
            DeviceCommand command = new DeviceCommand();
            command.setDeviceId(deviceId);
            command.setAction("setTemporaryPassword");
            command.setParams(Map.of("deviceId", deviceId, "password", password, "expireHours", hours));
            sendDeviceCommand(command);

            device.getDeviceProperties().put("temporaryPassword", password);
            device.setLastActiveTime(LocalDateTime.now());

            log.info("临时密码设置成功: {} - {} (有效期{}小时)", deviceId, password, hours);
            return device.getSpeciName() + "临时密码已设置: " + password + " (有效期" + hours + "小时)";
        } catch (Exception e) {
            log.error("临时密码设置失败: {}", deviceId, e);
            return device.getSpeciName() + "临时密码设置失败: " + e.getMessage();
        }
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
