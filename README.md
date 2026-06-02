# MCP-Gateway

### 1. demo-smart-phone（基于 Spring AI 提供的 MCP 协议，实现的 MCP Server And MCP Client）
- smart-home-core：提供了智能家居场景的实体类
- smart-home-server： 进行智能家居场景 Tools 的定义和对外暴露
  - 通过 @Tool 定义工具
  - 通过 ToolCallbackProvider 注册工具到应用上下文
  - smart-home-server-light 和 smart-home-server-lock 模块分别作为独立的 MCP Server 启动，
  - 对外暴露 SSE 连接端口，等待客户端建立 SSE 连接，实现工具的推送
- smart-home-client
  - 客户端通过配置地 SSE 端点路径，与服务端建立 SSE 长连接 
  - 连接建立后，服务端可主动向客户端推送 Tools 工具 
  - 客户端通过 AsyncMcpToolCallbackProvider 把服务端推送的远程工具，包装成本地可调用的 ToolCallback 
  - 客户端可以像调用本地工具一样，无缝调用远程服务端的工具