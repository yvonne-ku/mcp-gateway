package com.noinch.mcp.demo.smarthome.core.model;


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

    private final String CN_Name;
    private final String EN_Name;

    DeviceType(String CN_Name, String EN_Name){
        this.CN_Name = CN_Name;
        this.EN_Name = EN_Name;
    }
}
