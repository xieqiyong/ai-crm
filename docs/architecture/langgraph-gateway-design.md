# Gateway 与 LangGraph AI Runtime 架构设计

## 目标

AI 模块从 CRM 主业务中拆出，形成独立运行时。Java 侧继续负责真实业务数据、权限、配置管理、审计和持久化；Python 侧负责 LangGraph 智能体编排、工具调用、记忆、RAG 和模型交互。

## 服务拆分

```text
crm-frontend
  -> crm-gateway
       -> crm-web
       -> crm-ai-runtime
       -> crm-mcp
```

| 服务 | 职责 |
| --- | --- |
| crm-frontend | 前端页面，只访问统一入口 |
| crm-gateway | 独立网关，统一转发业务接口、AI接口、MCP接口、SSE流式响应 |
| crm-web | CRM业务服务，负责用户、权限、线索、客户、商机、跟进、知识库、配置管理 |
| crm-ai-runtime | Python FastAPI + LangGraph，负责智能体运行、编排、工具调用、记忆、Trace |
| crm-mcp | CRM业务能力MCP服务，给其他产品线或外部智能体复用 |

## Nacos 设计

Nacos 分两类能力使用：

1. 配置中心
   - Java 服务启动时读取 `crm.yaml`、`crm-mcp.yaml`、`crm-gateway.yaml`
   - 业务配置继续集中放 Nacos

2. 注册中心
   - `crm-web` 注册服务名：`crm-web`
   - `crm-mcp` 注册服务名：`crm-mcp`
   - `crm-gateway` 注册服务名：`crm-gateway`
   - `crm-ai-runtime` 注册服务名：`crm-ai-runtime`

当前没有引入 Spring Cloud Alibaba。原因是项目使用 Spring Boot 4，Spring Cloud Alibaba 与 Boot 4 的版本兼容风险较高。现在采用 `nacos-client` 做轻量注册发现：

- Java 服务通过 `crm-common` 内的注册器注册到 Nacos
- Python 服务通过 Nacos HTTP OpenAPI 注册并发送心跳
- Gateway 优先从 Nacos 发现服务，发现失败时使用 fallback URL

## Gateway 路由

| 入口 | 目标服务 |
| --- | --- |
| `/api/ai/**` | `crm-ai-runtime`，重写为 `/internal/ai/**` |
| `/internal/ai/**` | `crm-ai-runtime` |
| `/api/**` | `crm-web` |
| `/uploads/**` | `crm-web` |
| `/mcp/**` | `crm-mcp` |

Gateway 代理保留流式响应，不把 SSE 一次性缓冲成完整文本。

## LangGraph Runtime 分层

```text
crm-ai-runtime/app
  api                FastAPI入口
  graphs             LangGraph场景图
  runtime            运行上下文构建
  agents             智能体配置读取
  skills             Skill解析和提示词注入
  mcp                MCP服务配置解析
  tools              内置工具注册
  memory             记忆接口
  function_call      结构化输出和函数调用约束
  platform           数据库、Nacos等基础设施
```

## 当前已落地的运行流程

```text
Java/前端请求
  -> crm-gateway
  -> crm-ai-runtime
  -> RuntimeContextBuilder
       -> 加载场景智能体
       -> 加载Skills
       -> 加载MCP配置
       -> 加载可用工具
       -> 预留Memory
  -> LangGraph GraphRegistry
  -> 具体场景Graph
  -> 返回结构化结果和事件
```

## 智能体配置来源

当前支持两种方式：

1. `request`
   - Java 侧把 agent、skills、mcps 随请求传给 Python
   - 这是默认模式，兼容当前系统

2. `database`
   - Python 通过同一个 PostgreSQL 读取 `agents`、`agent_skill`、`agent_mcp`
   - 开关：`CRM_AI_AGENT_CONFIG_SOURCE=database`
   - 连接：`CRM_AI_DATABASE_ENABLED=true`、`CRM_AI_DATABASE_URI=postgresql://...`

注意：Python 只读取表，不负责建表和改表。

## 后续迭代顺序

1. 线索分析切到 gateway -> crm-ai-runtime
2. AI客户深度总结迁移到 LangGraph
3. AI营销助手迁移到 LangGraph，并接入 SSE 事件协议
4. 接入 MCP Client，让 Python runtime 动态挂载 CRM MCP 工具
5. 设计 Memory 表和会话消息表，由 Java 负责DDL，Python负责读写
6. 接入 LangSmith Trace，并把 runId、conversationId、traceId 串起来
7. 逐步下线 Java AgentScope 入口，保留兼容开关
