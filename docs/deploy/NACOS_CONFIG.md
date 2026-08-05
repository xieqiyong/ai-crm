# Nacos 配置中心接入说明

## 第一版范围

当前版本支持主后端 `crm-web` 和独立 MCP 服务 `crm-mcp` 在启动阶段从 Nacos 读取配置，并注入到 Spring 配置体系中。

本版本不是运行时热刷新。原因是当前项目大量配置使用 `@Value` 字段注入，运行中修改 Nacos 后不会自动重绑这些字段。需要热刷新时，应把可热更新配置单独封装成配置对象，再做刷新机制。

## Nacos 配置

主后端在 Nacos 中创建配置：

- Data ID：`crm.yaml`
- Group：`DEFAULT_GROUP`
- 配置格式：`YAML`
- 内容参考：[crm.yaml](../../deploy/nacos/crm.yaml)

MCP 服务在 Nacos 中创建配置：

- Data ID：`crm-mcp.yaml`
- Group：`DEFAULT_GROUP`
- 配置格式：`YAML`
- 内容参考：[crm-mcp.yaml](../../deploy/nacos/crm-mcp.yaml)

可以通过脚本直接导入：

```bash
CRM_NACOS_SERVER_ADDR=192.168.50.105:8848 \
CRM_NACOS_GROUP=DEFAULT_GROUP \
CRM_NACOS_DATA_ID=crm.yaml \
CRM_MCP_NACOS_DATA_ID=crm-mcp.yaml \
sh deploy/scripts/import-nacos-config.sh
```

如果 Nacos 开启了鉴权：

```bash
CRM_NACOS_SERVER_ADDR=192.168.50.105:8848 \
CRM_NACOS_GROUP=DEFAULT_GROUP \
CRM_NACOS_USERNAME=nacos \
CRM_NACOS_PASSWORD=你的密码 \
sh deploy/scripts/import-nacos-config.sh
```

如果使用非 public 命名空间，需要传命名空间编号：

```bash
CRM_NACOS_NAMESPACE=命名空间编号 sh deploy/scripts/import-nacos-config.sh
```

脚本会把 [crm.yaml](../../deploy/nacos/crm.yaml) 发布到 `CRM_NACOS_DATA_ID`，把 [crm-mcp.yaml](../../deploy/nacos/crm-mcp.yaml) 发布到 `CRM_MCP_NACOS_DATA_ID`。

如果有公共配置，可以创建共享配置，并通过：

```env
CRM_NACOS_SHARED_DATA_IDS=common.yaml:DEFAULT_GROUP
```

多个共享配置用英文逗号隔开，后面的配置会覆盖前面的同名配置，主配置 `CRM_NACOS_DATA_ID` 会覆盖共享配置。

## `.env` 保留项

Nacos 模式下，`.env` 只保留 Docker 启动和 Nacos 引导需要的配置：

```env
CRM_VERSION=当前镜像版本
COMPOSE_PROJECT_NAME=crm
CRM_TIMEZONE=Asia/Shanghai
CRM_FRONTEND_PORT=80
CRM_DB_NAME=crm
CRM_DB_USERNAME=app_user
CRM_DB_PASSWORD=数据库容器密码
CRM_REDIS_PASSWORD=Redis容器密码
CRM_MINIO_ACCESS_KEY=minioadmin
CRM_MINIO_SECRET_KEY=MinIO容器密码
CRM_NACOS_ENABLED=true
CRM_NACOS_SERVER_ADDR=192.168.50.105:8848
CRM_NACOS_NAMESPACE=
CRM_NACOS_GROUP=DEFAULT_GROUP
CRM_NACOS_DATA_ID=crm.yaml
CRM_MCP_NACOS_DATA_ID=crm-mcp.yaml
CRM_NACOS_USERNAME=
CRM_NACOS_PASSWORD=
CRM_NACOS_TIMEOUT_MS=5000
CRM_NACOS_FAIL_FAST=true
```

数据库、Redis、MinIO、火山、知识库、智能体等配置可以放到 Nacos。

新环境直接复制模板：

```bash
cp env.example .env
```

然后只改 `.env` 里的镜像版本、端口、Nacos 地址，以及内置 PostgreSQL、Redis、MinIO 容器账号密码。

## 优先级

配置优先级从高到低：

1. 命令行参数
2. Nacos 配置
3. 系统环境变量和 Docker 环境变量
4. `application.yml` 默认配置

这样处理是为了避免 docker-compose 中遗留的空环境变量覆盖 Nacos 配置。启用 Nacos 后，业务配置以 Nacos 为准，`.env` 主要保留镜像版本、时区和 Nacos 连接参数。

如果线上临时必须覆盖某个 Nacos 配置，优先使用启动命令行参数，或者直接修改 Nacos 后重启服务。

## 启动

首次部署执行：

```bash
sh scripts/deploy-offline.sh
```

如果镜像已经加载，只需要重新导入 Nacos 配置并重启 Java 服务：

```bash
sh scripts/import-nacos-config.sh
docker compose up -d --no-deps --force-recreate --no-build --pull never crm-backend crm-mcp
```

`--pull never` 用于禁止离线环境自动拉取镜像。

## 失败策略

`CRM_NACOS_FAIL_FAST=true` 时，Nacos 读取失败会阻止应用启动，适合生产环境。

`CRM_NACOS_FAIL_FAST=false` 时，Nacos 读取失败会打印错误并继续使用本地默认配置，适合本地调试。
