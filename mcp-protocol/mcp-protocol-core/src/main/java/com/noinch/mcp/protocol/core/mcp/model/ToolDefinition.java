package com.noinch.mcp.protocol.core.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP Tool 定义
 * 描述一个可被客户端调用的工具
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {
    /** 工具名称 */
    private String name;
    /** 工具描述 */
    private String description;
    /** 输入参数的 JSON Schema */
    private Map<String, Object> inputSchema;
}
