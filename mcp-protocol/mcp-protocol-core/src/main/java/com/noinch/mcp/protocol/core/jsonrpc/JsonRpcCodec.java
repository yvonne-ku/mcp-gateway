package com.noinch.mcp.protocol.core.jsonrpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.util.Map;
import java.util.Optional;

/**
 * JSON-RPC 2.0 消息编解码器
 * 负责将 JSON 字符串解析为对应的消息类型，或将消息对象序列化为 JSON 字符串
 */
public class JsonRpcCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new Jdk8Module())   // 支持 Java8 新类型
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);   // 遇到 JSON 里多余的未知字段，不报错、直接忽略

    /**
     * 将 JSON 字符串解析为 JsonRpcRequest 或 JsonRpcNotification
     * 根据是否有 id 字段区分：
     * - 有 id → JsonRpcRequest
     * - 无 id → JsonRpcNotification
     */
    @SuppressWarnings("unchecked")
    public static Object parse(String json) {
        try {
            Map<String, Object> raw = MAPPER.readValue(json, Map.class);
            if (raw.containsKey("id")) {
                return MAPPER.readValue(json, JsonRpcRequest.class);
            } else {
                return MAPPER.readValue(json, JsonRpcNotification.class);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON-RPC message: " + json, e);
        }
    }

    /**
     * 将请求 ID 和结果包装为成功响应 JSON 字符串
     */
    public static String successResponse(Object id, Object result) {
        return toJson(JsonRpcResponse.builder().id(id).result(result).build());
    }

    /**
     * 将请求 ID 和错误包装为错误响应 JSON 字符串
     */
    public static String errorResponse(Object id, JsonRpcError error) {
        return toJson(JsonRpcResponse.builder().id(id).error(error).build());
    }

    /**
     * 对象序列化为 JSON 字符串
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /**
     * JSON 字符串解析为指定类型
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * 将 JSON 字符串解析为 Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseToMap(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON: " + json, e);
        }
    }
}
