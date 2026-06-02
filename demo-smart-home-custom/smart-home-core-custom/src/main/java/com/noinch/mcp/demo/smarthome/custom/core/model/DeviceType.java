package com.noinch.mcp.demo.smarthome.custom.core.model;

import lombok.Getter;

/**
 * 设备类型枚举
 */
@Getter
public enum DeviceType {

    LIGHT("灯光设备", "light"),
    CLIMATE("温控设备", "climate"),
    MEDIA("媒体设备", "media"),
    CURTAIN("窗帘设备", "curtain"),
    LOCK("锁设备", "lock");

    private final String cnName;
    private final String enName;

    DeviceType(String cnName, String enName) {
        this.cnName = cnName;
        this.enName = enName;
    }
}
