package com.noinch.mcp.protocol.core.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具调用结果
 * content 包含一个或多个内容片段，每个片段有 type 和 text（或其他字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallToolResult {
    /** 结果内容列表 */
    private List<Map<String, Object>> content;
    /** 是否出错 */
    private Boolean isError;

    public static CallToolResult success(String text) {
        return CallToolResult.builder()
                .content(List.of(Map.of("type", "text", "text", text)))
                .isError(false)
                .build();
    }

    public static CallToolResult error(String text) {
        return CallToolResult.builder()
                .content(List.of(Map.of("type", "text", "text", text)))
                .isError(true)
                .build();
    }
}
