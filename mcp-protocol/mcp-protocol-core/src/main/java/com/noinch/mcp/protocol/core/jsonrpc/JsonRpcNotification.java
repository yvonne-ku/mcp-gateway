package com.noinch.mcp.protocol.core.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * JSON-RPC 2.0 Notification 消息（无 id）
 * {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcNotification {
    private String jsonrpc = "2.0";
    private String method;
    private Map<String, Object> params;
}
