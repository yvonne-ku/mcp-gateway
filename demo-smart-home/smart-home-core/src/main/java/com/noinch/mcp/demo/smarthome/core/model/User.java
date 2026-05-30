package com.noinch.mcp.demo.smarthome.core.model;

import lombok.Data;

import java.util.List;

@Data
public class User {

    private String id;
    private String name;
    private String email;
    private String phone;

    private List<String> deviceIds; // 用户可以操作的设备 id 列表
}
