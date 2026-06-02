package com.noinch.mcp.protocol.core.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * JSON-RPC 2.0 Request 消息
 * {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcRequest {
    private String jsonrpc = "2.0";
    private Object id;
    private String method;
    private Map<String, Object> params;

    public JsonRpcRequest(Object id, String method, Map<String, Object> params) {
        this.jsonrpc = "2.0";
        this.id = id;
        this.method = method;
        this.params = params;
    }

    public boolean isNotification() {
        return id == null;
    }
}
