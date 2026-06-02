package com.noinch.mcp.protocol.core.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 Error Response（完整响应，包含 id）
 * {"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcErrorResponse {
    private String jsonrpc = "2.0";
    private Object id;
    private JsonRpcError error;
}
