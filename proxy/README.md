# Proxy 模块 — MCP 网关数据面

## 职责

Proxy 是网关的**数据面**，面向外部员工提供工具调用 API。它不做实际工具调用，而是：

1. **鉴权** — 校验请求中的 API Key 是否有效、未过期、有权限
2. **路由** — 将请求转发给内部的 `mcp-client` 服务
3. **日志** — 记录每次调用到 `api_call_logs` 表

## API

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tools/{serviceId}/call` | 调用工具（需 Bearer token） |
| GET | `/api/tools/{serviceId}/list` | 获取工具列表（需 Bearer token） |

## 调用链路

```
外部请求 (curl / Claude Desktop / CI)
    │
    ▼
Proxy  (鉴权 → 日志 → 转发)
    │
    ▼  (内网 HTTP)
mcp-client  (持有 McpClient，与后端 MCP Server 保持 SSE 连接)
    │
    ▼
MCP Server
```

## 配置

```yaml
server:
  port: 8080

jdt:
  mcp:
    proxy:
      timeout: 300s
      connectTimeout: 5s
      readTimeout: 30s
      mcpClientUrl: http://localhost:8085    # mcp-client 地址
```

## 无状态

Proxy 不持有任何 McpClient，不建立任何 SSE 连接。所有工具调用通过 HTTP 委托给 `mcp-client`。可按请求量自由水平扩缩。
