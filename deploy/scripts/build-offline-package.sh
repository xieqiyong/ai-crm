#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DEPLOY_DIR="$ROOT_DIR/deploy"
OUTPUT_DIR="$DEPLOY_DIR/output"
BUILD_DIR="$DEPLOY_DIR/.build"

if command -v git >/dev/null 2>&1; then
  GIT_SHORT_SHA="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || true)"
else
  GIT_SHORT_SHA=""
fi

if [ -z "${CRM_VERSION:-}" ]; then
  if [ -n "$GIT_SHORT_SHA" ]; then
    CRM_VERSION="$(date +%Y%m%d%H%M%S)-$GIT_SHORT_SHA"
  else
    CRM_VERSION="$(date +%Y%m%d%H%M%S)"
  fi
fi

PACKAGE_NAME="intelligent-marketing-crm-$CRM_VERSION"
if [ -n "${CRM_PACKAGE_FILE_NAME:-}" ]; then
  PACKAGE_FILE_NAME="$CRM_PACKAGE_FILE_NAME"
else
  PACKAGE_BRANCH="${CRM_PACKAGE_BRANCH:-$CRM_VERSION}"
  PACKAGE_BRANCH="${PACKAGE_BRANCH#crm-}"
  PACKAGE_BRANCH="${PACKAGE_BRANCH%-[0-9]*}"
  PACKAGE_FILE_NAME="crm-$PACKAGE_BRANCH-release.tar.gz"
fi
if [[ "$PACKAGE_FILE_NAME" != *.tar.gz ]]; then
  PACKAGE_FILE_NAME="$PACKAGE_FILE_NAME.tar.gz"
fi
PACKAGE_DIR="$OUTPUT_DIR/$PACKAGE_NAME"
PACKAGE_ARCHIVE="$OUTPUT_DIR/$PACKAGE_FILE_NAME"
IMAGE_ARCHIVE="$PACKAGE_DIR/images/crm-images.tar"

random_text() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 24
    return
  fi
  date +%s%N | sha256sum | awk '{print $1}'
}

run_frontend_install() {
  cd "$ROOT_DIR/frontend"
  if [ -f package-lock.json ]; then
    npm ci
  else
    npm install
  fi
}

echo "开始构建智能营销管理系统离线包：$CRM_VERSION"

rm -rf "$BUILD_DIR" "$PACKAGE_DIR"
rm -f "$PACKAGE_ARCHIVE" "$PACKAGE_ARCHIVE.sha256"
mkdir -p "$BUILD_DIR/backend" "$BUILD_DIR/frontend" "$PACKAGE_DIR/images" "$PACKAGE_DIR/scripts" "$PACKAGE_DIR/sql" "$PACKAGE_DIR/docs" "$OUTPUT_DIR"

echo "构建后端 Jar"
cd "$ROOT_DIR/backend"
mvn -DskipTests clean package

BACKEND_JAR="$(find "$ROOT_DIR/backend/crm-web/target" -maxdepth 1 -name 'crm-web-*.jar' ! -name '*.original' | head -n 1)"
if [ -z "$BACKEND_JAR" ]; then
  echo "未找到后端 Jar：backend/crm-web/target/crm-web-*.jar"
  exit 1
fi

cp "$BACKEND_JAR" "$BUILD_DIR/backend/app.jar"
cp "$DEPLOY_DIR/docker/backend/Dockerfile" "$BUILD_DIR/backend/Dockerfile"

echo "构建前端 Dist"
run_frontend_install
npm run build

cp -R "$ROOT_DIR/frontend/dist" "$BUILD_DIR/frontend/dist"
cp "$DEPLOY_DIR/docker/frontend/Dockerfile" "$BUILD_DIR/frontend/Dockerfile"
cp "$DEPLOY_DIR/docker/frontend/nginx.conf" "$BUILD_DIR/frontend/nginx.conf"
cp "$DEPLOY_DIR/docker/frontend/write-runtime-config.sh" "$BUILD_DIR/frontend/write-runtime-config.sh"

echo "准备 Python AI Runtime"
mkdir -p "$BUILD_DIR/ai-runtime"
cp "$ROOT_DIR/crm-ai-runtime/requirements.txt" "$BUILD_DIR/ai-runtime/requirements.txt"
cp "$ROOT_DIR/crm-ai-runtime/Dockerfile" "$BUILD_DIR/ai-runtime/Dockerfile"
cp -R "$ROOT_DIR/crm-ai-runtime/app" "$BUILD_DIR/ai-runtime/app"

echo "构建 Docker 镜像"
docker build -t "crm-backend:$CRM_VERSION" "$BUILD_DIR/backend"
docker build -t "crm-frontend:$CRM_VERSION" "$BUILD_DIR/frontend"
docker build -t "crm-ai-runtime:$CRM_VERSION" "$BUILD_DIR/ai-runtime"

echo "准备中间件镜像"
docker image inspect docker.1ms.run/postgres:16-alpine >/dev/null 2>&1 || docker pull docker.1ms.run/postgres:16-alpine
docker image inspect docker.1ms.run/redis:7-alpine >/dev/null 2>&1 || docker pull docker.1ms.run/redis:7-alpine
docker image inspect docker.1ms.run/minio/minio:latest >/dev/null 2>&1 || docker pull docker.1ms.run/minio/minio:latest

echo "保存离线镜像"
docker save -o "$IMAGE_ARCHIVE" \
  "crm-backend:$CRM_VERSION" \
  "crm-frontend:$CRM_VERSION" \
  "crm-ai-runtime:$CRM_VERSION" \
  docker.1ms.run/postgres:16-alpine \
  docker.1ms.run/redis:7-alpine \
  docker.1ms.run/minio/minio:latest

cp "$DEPLOY_DIR/docker-compose.yml" "$PACKAGE_DIR/docker-compose.yml"
cp "$DEPLOY_DIR/env.example" "$PACKAGE_DIR/env.example"
cp "$DEPLOY_DIR/scripts/deploy-offline.sh" "$PACKAGE_DIR/scripts/deploy-offline.sh"
cp "$DEPLOY_DIR/README.md" "$PACKAGE_DIR/README.md"
cp "$DEPLOY_DIR/sql/"*.sql "$PACKAGE_DIR/sql/"
cp "$ROOT_DIR/docs/sql/20260729-knowledge-versioning.sql" "$PACKAGE_DIR/sql/"
cp "$ROOT_DIR/docs/knowledge-versioning-blue-green-index.md" "$PACKAGE_DIR/docs/"
chmod +x "$PACKAGE_DIR/scripts/"*.sh

CRM_DB_PASSWORD_VALUE="${CRM_DB_PASSWORD:-$(random_text)}"
CRM_REDIS_PASSWORD_VALUE="${CRM_REDIS_PASSWORD:-$(random_text)}"
CRM_JWT_SECRET_VALUE="${CRM_JWT_SECRET:-$(random_text)}"
CRM_MINIO_SECRET_KEY_VALUE="${CRM_MINIO_SECRET_KEY:-$(random_text)}"
CRM_AI_INTERNAL_TOKEN_VALUE="${CRM_AI_INTERNAL_TOKEN:-$(random_text)}"

cat > "$PACKAGE_DIR/.env" <<EOF
# ============================================================
# 智能营销管理系统离线部署环境变量
# 此文件由离线包构建脚本生成，可在启动前按实际环境修改
# 生产环境请妥善保管数据库、Redis、JWT、MinIO和内部通信密钥
# ============================================================

# -------------------- 发布与基础设置 --------------------
# 镜像版本号，必须与离线包内镜像标签保持一致
CRM_VERSION=$CRM_VERSION
# Docker Compose项目名称，用于隔离容器和网络
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-crm}
# 容器时区
CRM_TIMEZONE=${CRM_TIMEZONE:-Asia/Shanghai}

# -------------------- 前端访问设置 --------------------
# 前端对外暴露端口
CRM_FRONTEND_PORT=${CRM_FRONTEND_PORT:-80}
# 浏览器请求后台的基础地址；同域反向代理时保持为空
CRM_FRONTEND_API_BASE_URL=${CRM_FRONTEND_API_BASE_URL:-}
# 前端普通接口超时时间，单位毫秒
CRM_FRONTEND_API_TIMEOUT=${CRM_FRONTEND_API_TIMEOUT:-30000}
# AI回答打字机刷新间隔，单位毫秒
CRM_ASSISTANT_TYPEWRITER_INTERVAL=${CRM_ASSISTANT_TYPEWRITER_INTERVAL:-12}
# AI回答每次追加的字符数量
CRM_ASSISTANT_TYPEWRITER_STEP=${CRM_ASSISTANT_TYPEWRITER_STEP:-4}

# -------------------- PostgreSQL数据库 --------------------
# 数据库名称
CRM_DB_NAME=${CRM_DB_NAME:-crm}
# 数据库业务账号
CRM_DB_USERNAME=${CRM_DB_USERNAME:-app_user}
# 数据库业务密码，已由构建脚本生成或使用外部传入值
CRM_DB_PASSWORD=$CRM_DB_PASSWORD_VALUE
# JPA仅用于DDL初始化和演进；首装可用update，严格生产环境建议改为validate
CRM_JPA_DDL_AUTO=${CRM_JPA_DDL_AUTO:-update}

# -------------------- Druid连接池 --------------------
# 启动时初始化连接数
CRM_DRUID_INITIAL_SIZE=${CRM_DRUID_INITIAL_SIZE:-1}
# 最小空闲连接数
CRM_DRUID_MIN_IDLE=${CRM_DRUID_MIN_IDLE:-1}
# 最大活动连接数
CRM_DRUID_MAX_ACTIVE=${CRM_DRUID_MAX_ACTIVE:-20}
# 获取连接最大等待时间，单位毫秒
CRM_DRUID_MAX_WAIT=${CRM_DRUID_MAX_WAIT:-60000}
# 空闲连接检测间隔，单位毫秒
CRM_DRUID_EVICTION_INTERVAL=${CRM_DRUID_EVICTION_INTERVAL:-60000}
# 连接最小空闲时间，单位毫秒
CRM_DRUID_MIN_EVICTABLE_IDLE_TIME=${CRM_DRUID_MIN_EVICTABLE_IDLE_TIME:-300000}
# 数据库连接有效性检测SQL
CRM_DRUID_VALIDATION_QUERY=${CRM_DRUID_VALIDATION_QUERY:-select 1}

# -------------------- Redis会话与缓存 --------------------
# 是否启用Redis
CRM_REDIS_ENABLED=${CRM_REDIS_ENABLED:-true}
# Redis密码，已由构建脚本生成或使用外部传入值
CRM_REDIS_PASSWORD=$CRM_REDIS_PASSWORD_VALUE
# Redis逻辑库编号
CRM_REDIS_DATABASE=${CRM_REDIS_DATABASE:-1}

# -------------------- Java服务与认证 --------------------
# JVM启动参数
CRM_JAVA_OPTS=${CRM_JAVA_OPTS:--Xms512m -Xmx1024m -Dfile.encoding=UTF-8}
# 单文件上传上限
CRM_UPLOAD_MAX_FILE_SIZE=${CRM_UPLOAD_MAX_FILE_SIZE:-10MB}
# 单次请求上传总大小上限
CRM_UPLOAD_MAX_REQUEST_SIZE=${CRM_UPLOAD_MAX_REQUEST_SIZE:-12MB}
# JWT签名密钥，已由构建脚本生成或使用外部传入值
CRM_JWT_SECRET=$CRM_JWT_SECRET_VALUE
# 登录有效期，单位秒
CRM_JWT_TTL_SECONDS=${CRM_JWT_TTL_SECONDS:-86400}
# 忘记密码令牌有效期，单位秒
CRM_PASSWORD_RESET_TTL_SECONDS=${CRM_PASSWORD_RESET_TTL_SECONDS:-900}
# 是否在接口响应中直接返回重置令牌，生产环境必须为false
CRM_PASSWORD_RESET_EXPOSE_TOKEN=${CRM_PASSWORD_RESET_EXPOSE_TOKEN:-false}

# -------------------- 日志与本地文件 --------------------
# 后台日志级别
CRM_LOG_LEVEL=${CRM_LOG_LEVEL:-INFO}
# 日志保留天数
CRM_LOG_MAX_HISTORY=${CRM_LOG_MAX_HISTORY:-30}
# 单个日志文件最大大小
CRM_LOG_MAX_FILE_SIZE=${CRM_LOG_MAX_FILE_SIZE:-100MB}
# 渠道资料在容器内的保存目录
CRM_CHANNEL_UPLOAD_DIR=${CRM_CHANNEL_UPLOAD_DIR:-/app/uploads/channel}
# 通用附件在容器内的保存目录
CRM_STORAGE_UPLOAD_DIR=${CRM_STORAGE_UPLOAD_DIR:-/app/uploads}
# 本地附件对外访问路径
CRM_STORAGE_PUBLIC_PATH=${CRM_STORAGE_PUBLIC_PATH:-/uploads}

# -------------------- 企业微信客户同步 --------------------
# 企业微信接口地址，通常无需修改
CRM_WECOM_BASE_URL=${CRM_WECOM_BASE_URL:-https://qyapi.weixin.qq.com}
# 企业微信接口连接超时，单位毫秒
CRM_WECOM_CONNECT_TIMEOUT_MS=${CRM_WECOM_CONNECT_TIMEOUT_MS:-5000}
# 企业微信接口读取超时，单位毫秒
CRM_WECOM_READ_TIMEOUT_MS=${CRM_WECOM_READ_TIMEOUT_MS:-30000}
# 定时任务扫描间隔，单位毫秒；每个企业实际同步频率在渠道页面配置
CRM_WECOM_SCHEDULE_DELAY_MS=${CRM_WECOM_SCHEDULE_DELAY_MS:-60000}
# 服务启动后首次扫描延迟，单位毫秒
CRM_WECOM_SCHEDULE_INITIAL_DELAY_MS=${CRM_WECOM_SCHEDULE_INITIAL_DELAY_MS:-30000}
# 是否补充读取企业微信客户详情，用于获取完整跟进人、标签和对外资料
CRM_WECOM_FETCH_DETAIL_ENABLED=${CRM_WECOM_FETCH_DETAIL_ENABLED:-true}
# 是否读取企业微信客户标签库，用于把标签ID转换成标签名称
CRM_WECOM_FETCH_TAGS_ENABLED=${CRM_WECOM_FETCH_TAGS_ENABLED:-true}

# -------------------- Java Agent联网搜索 --------------------
# 是否启用企业公开信息搜索
CRM_AGENT_WEB_SEARCH_ENABLED=${CRM_AGENT_WEB_SEARCH_ENABLED:-false}
# 搜索提供方，目前支持searxng
CRM_AGENT_WEB_SEARCH_PROVIDER=${CRM_AGENT_WEB_SEARCH_PROVIDER:-searxng}
# 搜索服务地址
CRM_AGENT_WEB_SEARCH_ENDPOINT=${CRM_AGENT_WEB_SEARCH_ENDPOINT:-}
# 搜索服务密钥
CRM_AGENT_WEB_SEARCH_API_KEY=${CRM_AGENT_WEB_SEARCH_API_KEY:-}
# 搜索请求超时时间，单位毫秒
CRM_AGENT_WEB_SEARCH_TIMEOUT_MS=${CRM_AGENT_WEB_SEARCH_TIMEOUT_MS:-8000}
# 单次搜索最多返回结果数
CRM_AGENT_WEB_SEARCH_MAX_RESULTS=${CRM_AGENT_WEB_SEARCH_MAX_RESULTS:-5}
# 是否抓取搜索结果详情页
CRM_AGENT_WEB_SEARCH_FETCH_DETAIL=${CRM_AGENT_WEB_SEARCH_FETCH_DETAIL:-true}
# 单次最多抓取的详情页数量
CRM_AGENT_WEB_SEARCH_DETAIL_LIMIT=${CRM_AGENT_WEB_SEARCH_DETAIL_LIMIT:-3}

# -------------------- Agent Token额度 --------------------
# 未单独分配额度时的用户每日默认Token上限
CRM_AGENT_TOKEN_DAILY_LIMIT=${CRM_AGENT_TOKEN_DAILY_LIMIT:-100000}
# 调用前为模型输出预留的Token数量
CRM_AGENT_TOKEN_RESERVE_OUTPUT_TOKENS=${CRM_AGENT_TOKEN_RESERVE_OUTPUT_TOKENS:-2048}

# -------------------- Java与Python AI运行时通信 --------------------
# 两个服务之间的内部鉴权令牌，已由构建脚本生成或使用外部传入值
CRM_AI_INTERNAL_TOKEN=$CRM_AI_INTERNAL_TOKEN_VALUE
# Java调用Python AI运行时的超时时间，单位毫秒
CRM_AI_RUNTIME_TIMEOUT_MS=${CRM_AI_RUNTIME_TIMEOUT_MS:-90000}

# -------------------- Python AI运行时 --------------------
# Python服务日志级别
CRM_AI_LOG_LEVEL=${CRM_AI_LOG_LEVEL:-INFO}
# Python调用大模型的超时时间，单位秒
CRM_AI_LLM_TIMEOUT_SECONDS=${CRM_AI_LLM_TIMEOUT_SECONDS:-60}
# 是否启用Python联网搜索
CRM_AI_WEB_SEARCH_ENABLED=${CRM_AI_WEB_SEARCH_ENABLED:-false}
# Python联网搜索提供方
CRM_AI_WEB_SEARCH_PROVIDER=${CRM_AI_WEB_SEARCH_PROVIDER:-searxng}
# Python联网搜索服务地址
CRM_AI_WEB_SEARCH_ENDPOINT=${CRM_AI_WEB_SEARCH_ENDPOINT:-}
# Python联网搜索服务密钥
CRM_AI_WEB_SEARCH_API_KEY=${CRM_AI_WEB_SEARCH_API_KEY:-}
# Python联网搜索超时时间，单位秒
CRM_AI_WEB_SEARCH_TIMEOUT_SECONDS=${CRM_AI_WEB_SEARCH_TIMEOUT_SECONDS:-8}
# Python单次搜索最多返回结果数
CRM_AI_WEB_SEARCH_MAX_RESULTS=${CRM_AI_WEB_SEARCH_MAX_RESULTS:-5}

# -------------------- LangGraph Checkpoint --------------------
# 是否持久化LangGraph运行状态
CRM_AI_CHECKPOINT_ENABLED=${CRM_AI_CHECKPOINT_ENABLED:-false}
# Checkpoint后端，可选memory或postgres
CRM_AI_CHECKPOINT_BACKEND=${CRM_AI_CHECKPOINT_BACKEND:-memory}
# PostgreSQL连接串，使用postgres后端时必填
CRM_AI_CHECKPOINT_POSTGRES_URI=${CRM_AI_CHECKPOINT_POSTGRES_URI:-}
# 是否自动初始化Checkpoint表
CRM_AI_CHECKPOINT_AUTO_SETUP=${CRM_AI_CHECKPOINT_AUTO_SETUP:-true}
# Trace是否采集完整输入输出，生产环境建议关闭
CRM_AI_TRACE_CAPTURE_PAYLOAD=${CRM_AI_TRACE_CAPTURE_PAYLOAD:-false}

# -------------------- LangSmith链路追踪 --------------------
# 是否启用LangSmith Trace
LANGSMITH_TRACING=${LANGSMITH_TRACING:-false}
# LangSmith访问密钥
LANGSMITH_API_KEY=${LANGSMITH_API_KEY:-}
# LangSmith项目名称
LANGSMITH_PROJECT=${LANGSMITH_PROJECT:-crm-ai-runtime}
# LangSmith服务地址
LANGSMITH_ENDPOINT=${LANGSMITH_ENDPOINT:-https://api.smith.langchain.com}
# 是否隐藏Trace输入内容
LANGSMITH_HIDE_INPUTS=${LANGSMITH_HIDE_INPUTS:-false}
# 是否隐藏Trace输出内容
LANGSMITH_HIDE_OUTPUTS=${LANGSMITH_HIDE_OUTPUTS:-false}
# 是否启用LangGraph严格消息序列化
LANGGRAPH_STRICT_MSGPACK=${LANGGRAPH_STRICT_MSGPACK:-true}

# -------------------- Nacos配置中心 --------------------
# 是否启用Nacos
CRM_NACOS_ENABLED=${CRM_NACOS_ENABLED:-false}
# Nacos服务地址
CRM_NACOS_SERVER_ADDR=${CRM_NACOS_SERVER_ADDR:-localhost:8848}
# Nacos命名空间，默认公共命名空间时保持为空
CRM_NACOS_NAMESPACE=${CRM_NACOS_NAMESPACE:-}
# Nacos配置分组
CRM_NACOS_GROUP=${CRM_NACOS_GROUP:-DEFAULT_GROUP}

# -------------------- MinIO对象存储 --------------------
# 是否启用MinIO附件存储
CRM_MINIO_ENABLED=${CRM_MINIO_ENABLED:-false}
# MinIO服务端地址
CRM_MINIO_ENDPOINT=${CRM_MINIO_ENDPOINT:-http://crm-minio:9000}
# MinIO文件公开访问地址
CRM_MINIO_PUBLIC_URL=${CRM_MINIO_PUBLIC_URL:-/minio}
# MinIO访问账号
CRM_MINIO_ACCESS_KEY=${CRM_MINIO_ACCESS_KEY:-minioadmin}
# MinIO访问密钥，已由构建脚本生成或使用外部传入值
CRM_MINIO_SECRET_KEY=$CRM_MINIO_SECRET_KEY_VALUE
# 附件存储桶名称
CRM_MINIO_BUCKET=${CRM_MINIO_BUCKET:-crm}
# 是否允许存储桶公开读取
CRM_MINIO_PUBLIC_READ=${CRM_MINIO_PUBLIC_READ:-true}

# -------------------- Elasticsearch --------------------
# 是否启用Elasticsearch
CRM_ES_ENABLED=${CRM_ES_ENABLED:-true}
# Elasticsearch地址
CRM_ES_URIS=${CRM_ES_URIS:-http://host.docker.internal:9200}
# Elasticsearch账号
CRM_ES_USERNAME=${CRM_ES_USERNAME:-}
# Elasticsearch密码
CRM_ES_PASSWORD=${CRM_ES_PASSWORD:-}

# -------------------- Kafka --------------------
# Kafka地址，多个节点使用英文逗号分隔
CRM_KAFKA_BOOTSTRAP_SERVERS=${CRM_KAFKA_BOOTSTRAP_SERVERS:-host.docker.internal:9092}

# -------------------- 知识库异步入库 --------------------
# 文档入库线程池核心线程数
CRM_KB_INGEST_CORE_SIZE=${CRM_KB_INGEST_CORE_SIZE:-2}
# 文档入库线程池最大线程数
CRM_KB_INGEST_MAX_SIZE=${CRM_KB_INGEST_MAX_SIZE:-4}
# 文档入库等待队列长度
CRM_KB_INGEST_QUEUE_CAPACITY=${CRM_KB_INGEST_QUEUE_CAPACITY:-100}
# 超过该分钟数仍未结束的任务视为遗留任务
CRM_KB_INGEST_STALE_TASK_MINUTES=${CRM_KB_INGEST_STALE_TASK_MINUTES:-120}

# -------------------- 知识库混合检索 --------------------
# Milvus向量召回候选数量
CRM_KB_SEARCH_VECTOR_CANDIDATES=${CRM_KB_SEARCH_VECTOR_CANDIDATES:-20}
# Elasticsearch关键词召回候选数量
CRM_KB_SEARCH_KEYWORD_CANDIDATES=${CRM_KB_SEARCH_KEYWORD_CANDIDATES:-20}
# PostgreSQL降级召回候选数量
CRM_KB_SEARCH_DATABASE_CANDIDATES=${CRM_KB_SEARCH_DATABASE_CANDIDATES:-20}
# 向量召回RRF权重
CRM_KB_SEARCH_VECTOR_WEIGHT=${CRM_KB_SEARCH_VECTOR_WEIGHT:-0.55}
# 关键词召回RRF权重
CRM_KB_SEARCH_KEYWORD_WEIGHT=${CRM_KB_SEARCH_KEYWORD_WEIGHT:-0.35}
# 数据库降级召回RRF权重
CRM_KB_SEARCH_DATABASE_WEIGHT=${CRM_KB_SEARCH_DATABASE_WEIGHT:-0.10}
# RRF平滑常数
CRM_KB_SEARCH_RRF_K=${CRM_KB_SEARCH_RRF_K:-60}
# 归一化融合分最低阈值，0表示不过滤
CRM_KB_SEARCH_MIN_SCORE=${CRM_KB_SEARCH_MIN_SCORE:-0}
# true表示PG仅在Milvus和ES都无结果时参与降级
CRM_KB_SEARCH_DATABASE_FALLBACK_ONLY=${CRM_KB_SEARCH_DATABASE_FALLBACK_ONLY:-true}

# -------------------- 文档切分与Embedding --------------------
# 单个知识分片最大字符数
CRM_KB_CHUNK_MAX_CHARS=${CRM_KB_CHUNK_MAX_CHARS:-900}
# 相邻知识分片重叠字符数
CRM_KB_CHUNK_OVERLAP_CHARS=${CRM_KB_CHUNK_OVERLAP_CHARS:-120}
# 是否生成向量并写入Milvus
CRM_KB_EMBEDDING_ENABLED=${CRM_KB_EMBEDDING_ENABLED:-true}
# OpenAI兼容Embedding接口地址
CRM_KB_EMBEDDING_BASE_URL=${CRM_KB_EMBEDDING_BASE_URL:-http://host.docker.internal:11434/v1}
# Embedding接口密钥，Ollama通常保持为空
CRM_KB_EMBEDDING_API_KEY=${CRM_KB_EMBEDDING_API_KEY:-}
# Embedding模型名称
CRM_KB_EMBEDDING_MODEL=${CRM_KB_EMBEDDING_MODEL:-bge-m3:latest}
# 向量维度，0表示读取模型返回维度
CRM_KB_EMBEDDING_DIMENSIONS=${CRM_KB_EMBEDDING_DIMENSIONS:-0}
# Embedding请求超时时间，单位毫秒
CRM_KB_EMBEDDING_TIMEOUT_MS=${CRM_KB_EMBEDDING_TIMEOUT_MS:-30000}

# -------------------- 知识库Elasticsearch索引 --------------------
# 知识分片基础索引名称
CRM_KB_ES_INDEX=${CRM_KB_ES_INDEX:-crm_knowledge_chunk}
# 知识库ES请求超时时间，单位毫秒
CRM_KB_ES_TIMEOUT_MS=${CRM_KB_ES_TIMEOUT_MS:-10000}

# -------------------- 知识库Milvus索引 --------------------
# 是否启用Milvus向量库
CRM_KB_MILVUS_ENABLED=${CRM_KB_MILVUS_ENABLED:-true}
# Milvus服务地址
CRM_KB_MILVUS_ENDPOINT=${CRM_KB_MILVUS_ENDPOINT:-http://host.docker.internal:19530}
# Milvus鉴权令牌
CRM_KB_MILVUS_TOKEN=${CRM_KB_MILVUS_TOKEN:-}
# Milvus数据库名称，使用default时保持为空
CRM_KB_MILVUS_DATABASE=${CRM_KB_MILVUS_DATABASE:-}
# 知识分片基础集合名称
CRM_KB_MILVUS_COLLECTION=${CRM_KB_MILVUS_COLLECTION:-crm_knowledge_chunk}
# Milvus请求超时时间，单位毫秒
CRM_KB_MILVUS_TIMEOUT_MS=${CRM_KB_MILVUS_TIMEOUT_MS:-10000}

# -------------------- 知识库变更补偿与Kafka --------------------
# Outbox变更补偿扫描间隔，单位毫秒
CRM_KB_CHANGE_RECONCILE_DELAY_MS=${CRM_KB_CHANGE_RECONCILE_DELAY_MS:-3000}
# 应用启动后首次补偿扫描延迟，单位毫秒
CRM_KB_CHANGE_RECONCILE_INITIAL_DELAY_MS=${CRM_KB_CHANGE_RECONCILE_INITIAL_DELAY_MS:-10000}
# 是否通过Kafka发布知识库变更事件
CRM_KB_KAFKA_ENABLED=${CRM_KB_KAFKA_ENABLED:-false}
# 知识库变更Topic
CRM_KB_KAFKA_TOPIC=${CRM_KB_KAFKA_TOPIC:-crm-knowledge-change}
# 知识库索引消费者组
CRM_KB_KAFKA_GROUP_ID=${CRM_KB_KAFKA_GROUP_ID:-crm-knowledge-indexer}
# Outbox单次发布最大数量
CRM_KB_KAFKA_PUBLISH_BATCH_SIZE=${CRM_KB_KAFKA_PUBLISH_BATCH_SIZE:-100}
# Outbox发布扫描间隔，单位毫秒
CRM_KB_KAFKA_PUBLISH_DELAY_MS=${CRM_KB_KAFKA_PUBLISH_DELAY_MS:-1000}
# 应用启动后首次Outbox发布延迟，单位毫秒
CRM_KB_KAFKA_PUBLISH_INITIAL_DELAY_MS=${CRM_KB_KAFKA_PUBLISH_INITIAL_DELAY_MS:-5000}
# Kafka消息发送超时时间，单位毫秒
CRM_KB_KAFKA_SEND_TIMEOUT_MS=${CRM_KB_KAFKA_SEND_TIMEOUT_MS:-10000}
EOF

cat > "$PACKAGE_DIR/VERSION" <<EOF
CRM_VERSION=$CRM_VERSION
BUILD_TIME=$(date '+%Y-%m-%d %H:%M:%S')
GIT_SHORT_SHA=$GIT_SHORT_SHA
EOF

echo "压缩离线包"
cd "$OUTPUT_DIR"
tar -czf "$PACKAGE_FILE_NAME" "$PACKAGE_NAME"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$PACKAGE_FILE_NAME" > "$PACKAGE_FILE_NAME.sha256"
fi

echo "离线包已生成：$PACKAGE_ARCHIVE"
