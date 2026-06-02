package com.noinch.mcp.protocol.core.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON-RPC 2.0 Response 对象
 * <p>
 * 规范要求：响应必须包含 result 或 error 成员，但两者不能同时出现。
 * 序列化时使用 {@link JsonInclude.Include#NON_NULL} 保证为 null 的成员不会出现在 JSON 中。
 * <p>
 * 成功响应示例：{"jsonrpc":"2.0","id":1,"result":{"temperature":25}}
 * 错误响应示例：{"jsonrpc":"2.0","id":1,"error":{"code":-32601,"message":"Method not found"}}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse {
    private String jsonrpc = "2.0";
    private Object id;
    private Object result;
    private JsonRpcError error;
}
