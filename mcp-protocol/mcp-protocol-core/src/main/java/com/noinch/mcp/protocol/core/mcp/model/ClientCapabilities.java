package com.noinch.mcp.protocol.core.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 客户端能力声明
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClientCapabilities {
    /** 是否支持根目录 */
    private Map<String, Object> roots;
    /** 是否支持采样 */
    private Map<String, Object> sampling;
}
