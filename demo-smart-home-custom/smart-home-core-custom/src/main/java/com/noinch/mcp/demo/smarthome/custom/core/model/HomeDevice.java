package com.noinch.mcp.demo.smarthome.custom.core.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智慧家居设备业务层聚合根
 */
@Data
public class HomeDevice {

    // 设备实例信息
    private String id;
    private String speciName;
    private HomeDeviceStatus status;
    private LocalDateTime lastActiveTime;

    // deviceID -> Device 类
    private String deviceId;
    private String deviceName;
    private DeviceType deviceType;
    private final Map<String, Object> deviceProperties = new LinkedHashMap<>();

    // homeId -> Home 类
    private String homeId;
}
