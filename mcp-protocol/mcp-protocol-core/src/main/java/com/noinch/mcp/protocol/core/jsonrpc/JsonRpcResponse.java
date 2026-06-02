package com.noinch.mcp.protocol.core.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 Response 消息（成功）
 * {"jsonrpc":"2.0","id":1,"result":{...}}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse {
    private String jsonrpc = "2.0";
    private Object id;
    private Object result;
}
