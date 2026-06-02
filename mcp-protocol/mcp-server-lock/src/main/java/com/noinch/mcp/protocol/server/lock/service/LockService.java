package com.noinch.mcp.protocol.server.lock.service;

import com.noinch.mcp.protocol.core.mcp.annotation.McpTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LockService {

    private final Map<String, Map<String, Object>> deviceStore = new ConcurrentHashMap<>();

    public LockService() {
        Map<String, Object> frontDoorLock = new HashMap<>();
        frontDoorLock.put("id", "lock-00001");
        frontDoorLock.put("name", "前门智能锁");
        frontDoorLock.put("status", "online");
        frontDoorLock.put("locked", true);
        frontDoorLock.put("battery", 85);
        deviceStore.put("lock-00001", frontDoorLock);

        Map<String, Object> bedroomLock = new HashMap<>();
        bedroomLock.put("id", "lock-00002");
        bedroomLock.put("name", "卧室智能锁");
        bedroomLock.put("status", "online");
        bedroomLock.put("locked", true);
        bedroomLock.put("battery", 92);
        deviceStore.put("lock-00002", bedroomLock);
    }

    @McpTool(description = "获取所有智能锁设备列表")
    public String getLockDevices() {
        StringBuilder result = new StringBuilder("可用智能锁设备：\n");
        deviceStore.values().forEach(device -> {
            result.append("- ").append(device.get("name"))
                    .append(" (ID: ").append(device.get("id"))
                    .append(", 状态: ").append(device.get("status"))
                    .append(")\n");
        });
        return result.toString();
    }

    @McpTool(description = "控制智能锁开关")
    public String controlLock(String deviceId, String action) {
        Map<String, Object> device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        if ("lock".equals(action)) {
            device.put("locked", true);
            log.info("门锁已上锁: {}", deviceId);
            return device.get("name") + "已上锁";
        } else if ("unlock".equals(action)) {
            device.put("locked", false);
            log.info("门锁已解锁: {}", deviceId);
            return device.get("name") + "已解锁";
        }
        return "操作必须是 lock 或 unlock";
    }

    @McpTool(description = "查询门锁当前状态")
    public String getLockStatus(String deviceId) {
        Map<String, Object> device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        boolean locked = (boolean) device.getOrDefault("locked", false);
        int battery = (int) device.getOrDefault("battery", 0);
        return String.format("%s - 状态: %s, 锁定: %s, 电池: %d%%",
                device.get("name"), device.get("status"),
                locked ? "已锁定" : "已解锁", battery);
    }

    @McpTool(description = "查询智能锁电池电量")
    public String checkBattery(String deviceId) {
        Map<String, Object> device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        int battery = (int) device.getOrDefault("battery", 0);
        String status = battery > 20 ? "正常" : (battery > 10 ? "低电量" : "需要充电");
        return String.format("%s - 电池: %d%% (%s)", device.get("name"), battery, status);
    }

    @McpTool(description = "设置临时访问密码")
    public String setTemporaryPassword(String deviceId, String password, Integer hours) {
        if (hours == null || hours < 1 || hours > 168) {
            return "有效时长必须在 1-168 小时之间";
        }
        Map<String, Object> device = deviceStore.get(deviceId);
        if (device == null) {
            return "设备不存在: " + deviceId;
        }
        device.put("temporaryPassword", password);
        log.info("临时密码设置成功: {} - {} (有效期{}小时)", deviceId, password, hours);
        return device.get("name") + "临时密码已设置: " + password + " (有效期" + hours + "小时)";
    }
}
