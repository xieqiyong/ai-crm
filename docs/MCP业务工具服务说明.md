# MCP业务工具服务说明

## 模块定位

`crm-mcp` 是智能营销管理系统对外暴露业务数据工具的独立服务，不再挂载到 `crm-web` 主后端进程中。

该服务只做真实业务数据查询，不做数据写入，不做大模型编排，不依赖 `crm-agent-runtime`，也不依赖 `crm-auth`。这样可以隔离 Spring AI MCP 与 AgentScope 的底层依赖，避免 victools、MCP SDK、Jackson 版本冲突。

## 服务入口

默认入口：

```text
http://<crm-mcp-host>:8091/mcp
```

部署配置：

```env
CRM_MCP_PORT=8091
CRM_MCP_SERVER_ENABLED=true
CRM_MCP_SERVER_NAME=crm-business-mcp
CRM_MCP_SERVER_VERSION=0.0.1
CRM_MCP_SERVER_PROTOCOL=STATELESS
CRM_MCP_SERVER_ENDPOINT=/mcp
CRM_MCP_ACCESS_TOKEN=
```

## 鉴权方式

`CRM_MCP_ACCESS_TOKEN` 为空时不启用服务令牌校验，适合本地调试。

当前默认跳过 MCP 鉴权，后续需要启用时配置 `CRM_MCP_ACCESS_TOKEN`，客户端请求 MCP 入口时携带任意一种请求头：

```text
X-CRM-MCP-TOKEN: <MCP访问令牌>
Authorization: Bearer <MCP访问令牌>
```

由于 `crm-mcp` 是独立服务，不读取 CRM 登录态。工具调用时必须显式传入 `tenantId`、`userId` 和 `dataScope`，由调用方把当前用户上下文转交给 MCP 工具。

## 已封装工具

| 工具名称 | 作用 |
| --- | --- |
| `crm_opportunity_page` | 分页查询商机列表，支持关键词、商机阶段、客户过滤 |
| `crm_opportunity_detail` | 查询商机详情，包含客户、金额、阶段、产品明细、负责人 |
| `crm_opportunity_customer_followup_overview` | 按商机读取商机、关联客户、商机跟进、客户跟进，形成销售全景上下文 |
| `crm_customer_page` | 分页查询客户列表，支持关键词和客户状态过滤 |
| `crm_customer_detail` | 查询客户详情，包含基础信息、状态、负责人、AI总结 |
| `crm_followup_page` | 分页查询跟进记录，支持对象类型、对象编号、跟进类型、关键词过滤 |
| `crm_followup_detail` | 查询单条跟进记录详情 |

## 数据输出约束

- 所有 ID 字段统一按字符串返回，避免 JavaScript 或大模型客户端出现 Long 精度丢失。
- 工具入参中的 `tenantId`、`userId`、客户编号、商机编号、跟进编号等雪花 ID 也统一按字符串传入。
- 跟进记录同时返回 `contentText` 和 `contentHtml`，方便大模型分析和前端展示。
- 每个工具都会返回 `usageRule`，要求调用方只能基于真实 CRM 数据回答。
- 分页默认每页 10 条，最大 50 条，避免一次性拉取过多业务数据。

## 数据权限约束

工具调用会复用现有应用服务的数据权限逻辑：

- `SELF`：只查询本人负责的数据。
- `ALL`：查询当前租户内全部数据。

独立 MCP 服务当前安全收口，只接受 `ALL` 和 `SELF`。传入其他值会按 `SELF` 处理，避免外部产品绕过部门权限细节。

没有显式传入租户和用户上下文时，工具会直接拒绝调用。

## 启动方式

本地只启动 MCP 服务：

```bash
cd backend
mvn -DskipTests -pl crm-mcp -am package
java -jar crm-mcp/target/crm-mcp-0.0.1-SNAPSHOT.jar
```

Docker Compose 部署时会额外启动 `crm-mcp` 容器：

```bash
docker compose up -d crm-mcp
```
