# Docker 离线部署说明

## 一、目录说明

```text
deploy
├── docker-compose.yml
├── env.example
├── docker
│   ├── backend
│   └── frontend
└── scripts
    ├── build-offline-package.sh
    └── deploy-offline.sh
```

## 二、Jenkins 打离线包

Jenkins 机器需要提前安装：

- JDK 21
- Maven
- Node.js 和 npm
- Docker
- Git

在 Jenkins 构建步骤中执行：

```bash
bash deploy/scripts/build-offline-package.sh
```

也可以直接参考 `deploy/Jenkinsfile` 配置流水线，流水线会固定拉取当前项目仓库：

```text
http://192.168.50.96:8888/xieqy/crm.git
```

当前 Jenkins Pipeline 只保留一个参数：

```text
branch  Git 分支，默认 main
```

流水线固定使用：

```text
JAVA_HOME=/root/liusu/jdk-21.0.1
JDK_VERSION=21
NODE_VERSION=22.22.2
NODE_ENV=production
GIT_CREDENTIALS_ID=liusu
```

流水线产物会归档到 Jenkins，同时复制到：

```text
/app/builds/products/crm
```

脚本会自动完成：

1. 构建后端 `crm-web` Jar。
2. 构建前端 Vite 静态文件。
3. 构建 `crm-backend` Docker 镜像。
4. 构建 `crm-frontend` Docker 镜像。
5. 拉取并打包 PostgreSQL、Redis 镜像。
6. 生成可离线部署的压缩包。

离线包输出位置：

```text
deploy/output/crm-分支-release.tar.gz
```

Jenkins 默认会输出类似：

```text
crm-main-release.tar.gz
crm-dev-release.tar.gz
crm-feature-ai-release.tar.gz
```

如果需要指定内部镜像版本号：

```bash
CRM_VERSION=1.0.0 bash deploy/scripts/build-offline-package.sh
```

如果只想指定最外层压缩包名称：

```bash
CRM_PACKAGE_FILE_NAME=crm-main-release.tar.gz bash deploy/scripts/build-offline-package.sh
```

如果需要 Jenkins 注入生产密钥：

```bash
CRM_VERSION=1.0.0 \
CRM_DB_PASSWORD='数据库密码' \
CRM_REDIS_PASSWORD='Redis密码' \
CRM_JWT_SECRET='至少32位JWT密钥' \
bash deploy/scripts/build-offline-package.sh
```

不传这些变量时，脚本会自动生成随机值并写入离线包内的 `.env`。

## 三、服务器离线部署

目标服务器需要提前安装：

- Docker
- Docker Compose 插件，或 `docker-compose`

上传离线包到服务器后执行：

```bash
tar -xzf crm-main-release.tar.gz
cd intelligent-marketing-crm-内部版本号
bash scripts/deploy-offline.sh
```

脚本会自动完成：

1. 加载离线 Docker 镜像。
2. 创建数据目录。
3. 启动 PostgreSQL。
4. 启动 Redis。
5. 启动后端服务。
6. 启动前端 Nginx 服务。

默认访问地址：

```text
http://服务器IP/
```

端口由 `.env` 中的 `CRM_FRONTEND_PORT` 控制，默认是 `80`。

## 四、部署包内关键文件

离线包解压后结构如下：

```text
intelligent-marketing-crm-内部版本号
├── .env
├── VERSION
├── docker-compose.yml
├── env.example
├── images
│   └── crm-images.tar
├── scripts
│   └── deploy-offline.sh
└── README.md
```

### `.env`

生产环境主要修改这些配置：

```text
CRM_FRONTEND_PORT=80
CRM_DB_PASSWORD=数据库密码
CRM_REDIS_PASSWORD=Redis密码
CRM_JWT_SECRET=至少32位JWT密钥
CRM_JAVA_OPTS=-Xms512m -Xmx1024m -Dfile.encoding=UTF-8
CRM_LOG_LEVEL=INFO
```

### 数据目录

部署脚本会在当前目录创建：

```text
data/postgres
data/redis
data/backend
logs/backend
uploads
```

这些目录是运行数据和日志，升级时不要删除。

## 五、升级部署

上传新的离线包，解压后执行：

```bash
cd intelligent-marketing-crm-新内部版本号
bash scripts/deploy-offline.sh
```

因为 Compose 项目名固定为 `crm`，新版本会复用同一组容器名称和数据卷目录。正式生产环境建议将历史版本包保留一段时间，方便回滚。

如果只想重新启动，不重新加载镜像：

```bash
bash scripts/deploy-offline.sh --no-load
```

## 六、常用运维命令

在离线包目录内执行：

```bash
docker compose ps
docker compose logs -f crm-backend
docker compose logs -f crm-frontend
docker compose restart crm-backend
docker compose down
```

如果服务器使用旧版 `docker-compose`：

```bash
docker-compose ps
docker-compose logs -f crm-backend
docker-compose restart crm-backend
docker-compose down
```

## 七、注意事项

- 目标服务器不需要联网拉镜像，镜像已包含在 `images/crm-images.tar`。
- 目标服务器必须保留 `data`、`logs`、`uploads` 目录。
- PostgreSQL 和 Redis 默认只在 Docker 网络内访问，不暴露到公网。
- 前端通过 Nginx 代理 `/api` 到后端服务。
- 首次启动后，如果系统没有超级管理员，会进入初始化页面。
- 生产环境必须修改 `.env` 里的密码和 `CRM_JWT_SECRET`。
