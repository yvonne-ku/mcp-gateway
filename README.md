# MCP-Gateway

### 1. demo-smart-phone（基于 Spring AI MCP 协议栈实现的 MCP Server 与 MCP Client）
- smart-home-core：智能家居场景的实体类定义
- smart-home-server：定义并注册智能家居工具
  - 通过 @Tool 注解声明工具方法
  - 通过 ToolCallbackProvider 将工具注册到 Spring 应用上下文
  - smart-home-server-light 和 smart-home-server-lock 分别作为独立 MCP Server 启动
  - 对外暴露 SSE 端点，等待客户端连接后推送工具列表
- smart-home-client
  - 通过配置的 SSE 端点路径与服务端建立 SSE 长连接
  - 连接建立后接收服务端主动推送的工具列表
  - 通过 AsyncMcpToolCallbackProvider 将远程工具包装为本地 ToolCallback
  - 对上层应用透明，调用远程工具如同调用本地工具

### 2. mcp-protocol-20251125（自研 MCP 协议实现，遵循 2025-11-25 稳定版规范）
- **mcp-protocol-core**：JSON-RPC 2.0 编解码、MCP 模型定义、McpToolRegistry 注册中心、@McpTool 注解
- **mcp-server-starter**：基于 Servlet MVC + SSE 的 MCP Server 自动配置
  - GET /mcp → 建立 SSE 长连接，响应头返回 Mcp-Session-Id
  - POST /mcp → 接收 JSON-RPC 请求（initialize / tools/list / tools/call / ping），处理完通过 SSE 事件推送响应
  - DELETE /mcp → 终止会话，清理服务端资源
  - 支持 Origin 白名单校验（防 DNS rebinding）
  - 自动发现并注册 @McpTool 注解的工具方法
- **mcp-client-starter**：基于 WebClient + SSE 的 MCP Client 自动配置
  - 从配置文件读取 MCP Server 列表，自动建立连接并进行 initialize/initialized 三次握手
  - 支持 listTools / callTool 等标准 MCP 方法调用
  - 通过 Map\<String, McpClient\> 暴露给业务模块，按服务名索引

### 3. demo-smart-home-custom（自研 mcp-protocol-20251125 的智能家居演示应用）
- **smart-home-core-custom**：智能家居场景实体类（设备、命令、状态枚举等）
- **light-server-custom**：灯光 MCP Server（端口 8083）
  - 通过 @McpTool 定义灯光控制工具（开关、亮度调节、颜色设置等）
- **lock-server-custom**：门锁 MCP Server（端口 8084）
  - 通过 @McpTool 定义门锁控制工具（开关锁、电池查询、临时密码等）
- **client-custom**：统一 MCP Client（端口 8080）
  - 自动连接 light-server 和 lock-server，获取远程工具列表
  - 对外暴露 REST API（/api/tool/list、/api/tool/call、/api/tool/search）
  - 根据工具名或服务名路由到对应的 MCP Server 执行工具调用

### 4. auth（认证鉴权模块）

基于 **JWT 双 Token + Redis 黑白名单** 的 WebFlux 认证鉴权模块，以 WebFilter 形式嵌入网关请求链，对 Management 和 Proxy 模块提供统一鉴权能力。

| 端点 | 功能 |
|---|---|
| `POST /api/auth/login` | 签发 Access Token（2h）+ Refresh Token（3d），Refresh Token 写入 Redis 白名单 |
| `POST /api/auth/logout` | Access Token 加入 Redis 黑名单，Refresh Token 从白名单删除 |
| `POST /api/auth/refresh` | 校验 Refresh Token（JWT + Redis 白名单），签发新双 Token |

**鉴权链路**：停用开关 → 路径白名单 → IP 白名单 → JWT 校验 → Redis 黑名单 → 放行

详见 [auth/README.md](auth/README.md)。