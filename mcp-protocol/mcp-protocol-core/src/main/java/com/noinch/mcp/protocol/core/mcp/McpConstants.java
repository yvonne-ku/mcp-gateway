package com.noinch.mcp.protocol.core.mcp;

/**
 * MCP 2025-11-25 协议常量
 */
public interface McpConstants {

    String PROTOCOL_VERSION = "2025-11-25";

    // ===== 协议方法名 =====

    /** 初始化 */
    String METHOD_INITIALIZE = "initialize";
    /** 初始化完成通知 */
    String METHOD_INITIALIZED = "notifications/initialized";

    /** 获取工具列表 */
    String METHOD_TOOLS_LIST = "tools/list";
    /** 调用工具 */
    String METHOD_TOOLS_CALL = "tools/call";

    /** 获取资源列表 */
    String METHOD_RESOURCES_LIST = "resources/list";
    /** 读取资源 */
    String METHOD_RESOURCES_READ = "resources/read";

    /** 获取提示列表 */
    String METHOD_PROMPTS_LIST = "prompts/list";
    /** 获取提示 */
    String METHOD_PROMPTS_GET = "prompts/get";

    // ===== SSE 事件类型 =====

    /** SSE endpoint 事件 */
    String SSE_EVENT_ENDPOINT = "endpoint";
    /** SSE 消息事件 */
    String SSE_EVENT_MESSAGE = "message";

    // ===== 服务端能力 =====

    String CAPABILITY_TOOLS = "tools";
    String CAPABILITY_RESOURCES = "resources";
    String CAPABILITY_PROMPTS = "prompts";
}
