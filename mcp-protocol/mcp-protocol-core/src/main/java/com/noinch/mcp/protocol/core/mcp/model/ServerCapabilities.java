package com.noinch.mcp.protocol.core.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 服务器能力声明
 * 告知客户端服务器支持哪些功能
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerCapabilities {
    /** 是否支持工具 */
    private Map<String, Object> tools;
    /** 是否支持资源 */
    private Map<String, Object> resources;
    /** 是否支持提示 */
    private Map<String, Object> prompts;
}
