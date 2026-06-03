package com.noinch.mcp.protocol.core.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 Error 对象
 * {"code":-32601,"message":"Method not found","data":null}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcError {
    private int code;
    private String message;
    private Object data;

    public static JsonRpcError methodNotFound(String data) {
        return new JsonRpcError(-32601, "Method not found", data);
    }

    public static JsonRpcError invalidParams(String data) {
        return new JsonRpcError(-32602, "Invalid params", data);
    }

    public static JsonRpcError internalError(String data) {
        return new JsonRpcError(-32603, "Internal error", data);
    }

    public static JsonRpcError parseError(String data) {
        return new JsonRpcError(-32700, "Parse error", data);
    }

    public static JsonRpcError invalidRequest(String data) {
        return new JsonRpcError(-32600, "Invalid Request", data);
    }
}
