package com.noinch.mcp.demo.smarthome.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * 设备命令DTO
 */
@Data
public class DeviceCommand {

    private String requestId;
    private String deviceId;
    private String action;              // 操作类型
    private Map<String, Object> params; // 命令相关参数
}