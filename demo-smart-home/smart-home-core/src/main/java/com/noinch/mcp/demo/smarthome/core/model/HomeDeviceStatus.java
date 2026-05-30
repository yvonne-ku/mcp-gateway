package com.noinch.mcp.demo.smarthome.core.model;


import lombok.Getter;

/**
 * 设备状态枚举
 */
@Getter
public enum HomeDeviceStatus {

    ONLINE("在线"),
    OFFLINE("离线"),
    ERROR("故障"),
    MAINTENANCE("维护中"),
    CONNECTING("连接中");

    private final String desc;

    HomeDeviceStatus(String desc) {
        this.desc = desc;
    }
}
