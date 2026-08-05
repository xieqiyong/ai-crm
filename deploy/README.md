# 智能营销管理系统 Docker 部署说明

## 一、当前部署原则

当前部署只管这几个服务：

- `crm-backend`：主后端。
- `crm-mcp`：CRM MCP 工具服务。
- `crm-frontend`：前端 Nginx。
- `crm-postgres`：内置 PostgreSQL。
- `crm-redis`：内置 Redis。
- `crm-minio`：内置 MinIO。

Python Runtime 不在当前部署链路内。

后端业务配置全部走 Nacos。`.env` 只保留 Docker 启动和 Nacos 引导必需项。

## 二、目录约定

建议服务器固定部署目录：

```bash
/app/builds/products/crm/crm-app
```

不要每次升级都换运行目录，否则容易把 `data`、`logs`、`uploads` 分散到多个目录。

运行目录核心结构：

```text
crm-app
├── .env
├── docker-compose.yml
├── images
│   └── crm-images.tar
├── nacos
│   ├── crm.yaml
│   └── crm-mcp.yaml
├── scripts
│   ├── deploy-offline.sh
│   └── import-nacos-config.sh
├── data
├── logs
└── uploads
```

## 三、首次部署

### 1. 构建离线包

Jenkins 或构建机需要：

- JDK 21
- Maven
- Node.js 和 npm
- Docker
- Git

执行：

```bash
bash deploy/scripts/build-offline-package.sh
```

输出文件：

```text
deploy/output/crm-分支-release.tar.gz
```

离线包内包含：

- `crm-backend` 镜像
- `crm-mcp` 镜像
- `crm-frontend` 镜像
- PostgreSQL、Redis、MinIO 镜像
- `docker-compose.yml`
- `.env`
- `nacos/crm.yaml`
- `nacos/crm-mcp.yaml`
- 部署脚本

### 2. 上传并解压到固定目录

```bash
mkdir -p /app/builds/products/crm/crm-app
tar -xzf crm-main-release.tar.gz -C /app/builds/products/crm/crm-app --strip-components=1
cd /app/builds/products/crm/crm-app
```

### 3. 修改 `.env`

重点确认：

```env
CRM_VERSION=当前镜像版本
CRM_FRONTEND_PORT=8088
CRM_DB_NAME=crm
CRM_DB_USERNAME=app_user
CRM_DB_PASSWORD=数据库容器密码
CRM_REDIS_PASSWORD=Redis容器密码
CRM_MINIO_ACCESS_KEY=minioadmin
CRM_MINIO_SECRET_KEY=MinIO容器密码
CRM_NACOS_ENABLED=true
CRM_NACOS_SERVER_ADDR=192.168.50.105:8848
CRM_NACOS_GROUP=DEFAULT_GROUP
CRM_NACOS_DATA_ID=crm.yaml
CRM_MCP_NACOS_DATA_ID=crm-mcp.yaml
```

### 4. 修改 Nacos 配置文件

```bash
vi nacos/crm.yaml
vi nacos/crm-mcp.yaml
```

注意：

- `nacos/crm.yaml` 里的 `spring.datasource.password` 要和 `.env` 里的 `CRM_DB_PASSWORD` 一致。
- `nacos/crm.yaml` 里的 `spring.data.redis.password` 要和 `.env` 里的 `CRM_REDIS_PASSWORD` 一致。
- `nacos/crm.yaml` 里的 MinIO 密钥要和 `.env` 里的 `CRM_MINIO_ACCESS_KEY`、`CRM_MINIO_SECRET_KEY` 一致。
- 企微、火山、RAG、Agent、大模型等业务配置都写到 `nacos/crm.yaml`。

### 5. 启动

```bash
sh scripts/deploy-offline.sh
```

脚本会按顺序执行：

1. `docker load -i images/crm-images.tar`
2. 创建 `data`、`logs`、`uploads` 目录
3. 导入 `nacos/crm.yaml` 和 `nacos/crm-mcp.yaml`
4. 执行：

```bash
docker compose up -d --no-build --pull never
```

`--pull never` 表示离线环境禁止自动拉镜像。

### 6. 验证

```bash
docker compose ps
docker compose logs -f crm-backend
docker compose logs -f crm-mcp
```

后端日志看到下面内容说明 Nacos 已生效：

```text
Nacos配置读取完成
Nacos配置加载完成
Started CrmWebApplication
```

## 四、第二次及后续增量部署

增量部署只更新应用镜像，不动数据库数据目录。

### 方案 A：本地打三应用镜像并上传

Windows 本地执行：

```powershell
.\deploy\scripts\build-app-images.ps1 crm-v2 -DeployRemote -RemotePath /app/builds/products/crm -RemoteDeployDir /app/builds/products/crm/crm-app
```

如果不想每次上传和远程重启都输入密码，建议先配置 SSH Key 免密。

本地生成密钥：

```powershell
ssh-keygen -t ed25519 -f $env:USERPROFILE\.ssh\crm_deploy_ed25519
```

把公钥写入服务器：

```powershell
type $env:USERPROFILE\.ssh\crm_deploy_ed25519.pub | ssh root@192.168.50.105 "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys"
```

之后使用私钥打包、上传并部署：

```powershell
.\deploy\scripts\build-app-images.ps1 crm-v2 -DeployRemote -SshKey "$env:USERPROFILE\.ssh\crm_deploy_ed25519" -RemotePath /app/builds/products/crm -RemoteDeployDir /app/builds/products/crm/crm-app
```

也可以设置环境变量，后续命令不用重复写 `-SshKey`：

```powershell
$env:CRM_DEPLOY_SSH_KEY="$env:USERPROFILE\.ssh\crm_deploy_ed25519"
.\deploy\scripts\build-app-images.ps1 crm-v2 -DeployRemote -RemotePath /app/builds/products/crm -RemoteDeployDir /app/builds/products/crm/crm-app
```

脚本会构建并上传：

- `crm-backend-crm-v2.tar`
- `crm-mcp-crm-v2.tar`
- `crm-frontend-crm-v2.tar`
- `load-app-images.sh`
- `restart-crm-app.sh`
- `deploy-crm-app.sh`

然后在服务器执行：

```bash
cd /app/builds/products/crm
sh deploy-crm-app.sh crm-v2 /app/builds/products/crm/crm-app
```

脚本会：

1. 加载三个应用镜像。
2. 修改运行目录 `.env` 里的 `CRM_VERSION`。
3. 重启 `crm-backend`、`crm-mcp`、`crm-frontend`。

内部重启命令：

```bash
docker compose up -d --no-deps --force-recreate --no-build --pull never crm-backend crm-mcp crm-frontend
```

### 方案 B：手动上传三个应用镜像

把三个镜像 tar 上传到服务器后执行：

```bash
cd /app/builds/products/crm

docker load -i crm-backend-crm-v2.tar
docker load -i crm-mcp-crm-v2.tar
docker load -i crm-frontend-crm-v2.tar

cd /app/builds/products/crm/crm-app
sed -i 's/^CRM_VERSION=.*/CRM_VERSION=crm-v2/' .env

docker compose up -d --no-deps --force-recreate --no-build --pull never crm-backend crm-mcp crm-frontend
docker compose ps
```

### 如果 Nacos 配置也变了

修改：

```bash
vi nacos/crm.yaml
vi nacos/crm-mcp.yaml
```

导入：

```bash
sh scripts/import-nacos-config.sh
```

然后重启对应服务：

```bash
docker compose up -d --no-deps --force-recreate --no-build --pull never crm-backend crm-mcp
```

说明：当前 Nacos 是启动加载，不是运行时热刷新。改 Nacos 后需要重启 Java 服务。

## 五、常用命令

```bash
docker compose ps
docker compose logs -f crm-backend
docker compose logs -f crm-mcp
docker compose logs -f crm-frontend
docker compose restart crm-backend
docker compose restart crm-mcp
docker compose restart crm-frontend
docker compose down
```

## 六、故障处理

### 1. 提示自动拉镜像

所有启动命令必须带：

```bash
--pull never
```

如果提示镜像不存在，先确认本地镜像：

```bash
docker images | grep crm
docker images | grep docker.1ms.run
```

### 2. Redis 或 MinIO 镜像 tag 不一致

如果本地只有官方 tag，可以补 tag：

```bash
docker tag redis:7-alpine docker.1ms.run/redis:7-alpine
docker tag postgres:16-alpine docker.1ms.run/postgres:16-alpine
docker tag minio/minio:latest docker.1ms.run/minio/minio:latest
```

### 3. Nacos 没读到配置

检查：

```bash
curl 'http://192.168.50.105:8848/nacos/v1/cs/configs?dataId=crm.yaml&group=DEFAULT_GROUP'
curl 'http://192.168.50.105:8848/nacos/v1/cs/configs?dataId=crm-mcp.yaml&group=DEFAULT_GROUP'
```

### 4. 后端连接数据库失败

检查 `.env` 和 Nacos 是否一致：

- `.env` 的 `CRM_DB_PASSWORD`
- `nacos/crm.yaml` 的 `spring.datasource.password`
- `nacos/crm-mcp.yaml` 的 `spring.datasource.password`

## 七、保留和删除的脚本

当前保留：

- `scripts/build-offline-package.sh`：构建全量离线包。
- `scripts/deploy-offline.sh`：首次部署或全量离线启动。
- `scripts/import-nacos-config.sh`：导入 Nacos 配置。
- `scripts/build-app-images.ps1`：Windows 本地构建三应用镜像，用于增量部署。

当前部署只保留 Nacos 模式。
