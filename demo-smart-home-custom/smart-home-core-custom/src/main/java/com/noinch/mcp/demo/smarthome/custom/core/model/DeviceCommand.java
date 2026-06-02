package com.noinch.mcp.demo.smarthome.custom.core.model;

import lombok.Data;

import java.util.Map;

/**
 * 设备命令 DTO
 */
@Data
public class DeviceCommand {

    private String requestId;
    private String deviceId;
    private String action;
    private Map<String, Object> params;
}
