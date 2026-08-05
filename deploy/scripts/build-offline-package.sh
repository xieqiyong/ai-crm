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
mkdir -p "$BUILD_DIR/backend" "$BUILD_DIR/mcp" "$BUILD_DIR/frontend" "$PACKAGE_DIR/images" "$PACKAGE_DIR/scripts" "$PACKAGE_DIR/sql" "$PACKAGE_DIR/docs" "$PACKAGE_DIR/nacos" "$OUTPUT_DIR"

echo "构建后端 Jar"
cd "$ROOT_DIR/backend"
mvn -DskipTests clean package

BACKEND_JAR="$(find "$ROOT_DIR/backend/crm-web/target" -maxdepth 1 -name 'crm-web-*.jar' ! -name '*.original' | head -n 1)"
if [ -z "$BACKEND_JAR" ]; then
  echo "未找到后端 Jar：backend/crm-web/target/crm-web-*.jar"
  exit 1
fi

MCP_JAR="$(find "$ROOT_DIR/backend/crm-mcp/target" -maxdepth 1 -name 'crm-mcp-*.jar' ! -name '*.original' | head -n 1)"
if [ -z "$MCP_JAR" ]; then
  echo "未找到MCP服务 Jar：backend/crm-mcp/target/crm-mcp-*.jar"
  exit 1
fi

cp "$BACKEND_JAR" "$BUILD_DIR/backend/app.jar"
cp "$DEPLOY_DIR/docker/backend/Dockerfile" "$BUILD_DIR/backend/Dockerfile"
cp "$MCP_JAR" "$BUILD_DIR/mcp/app.jar"
cp "$DEPLOY_DIR/docker/mcp/Dockerfile" "$BUILD_DIR/mcp/Dockerfile"

echo "构建前端 Dist"
run_frontend_install
npm run build

cp -R "$ROOT_DIR/frontend/dist" "$BUILD_DIR/frontend/dist"
cp "$DEPLOY_DIR/docker/frontend/Dockerfile" "$BUILD_DIR/frontend/Dockerfile"
cp "$DEPLOY_DIR/docker/frontend/nginx.conf" "$BUILD_DIR/frontend/nginx.conf"
cp "$DEPLOY_DIR/docker/frontend/write-runtime-config.sh" "$BUILD_DIR/frontend/write-runtime-config.sh"

echo "构建 Docker 镜像"
docker build -t "crm-backend:$CRM_VERSION" "$BUILD_DIR/backend"
docker build -t "crm-mcp:$CRM_VERSION" "$BUILD_DIR/mcp"
docker build -t "crm-frontend:$CRM_VERSION" "$BUILD_DIR/frontend"

echo "准备中间件镜像"
docker image inspect docker.1ms.run/postgres:16-alpine >/dev/null 2>&1 || docker pull docker.1ms.run/postgres:16-alpine
docker image inspect docker.1ms.run/redis:7-alpine >/dev/null 2>&1 || docker pull docker.1ms.run/redis:7-alpine
docker image inspect docker.1ms.run/minio/minio:latest >/dev/null 2>&1 || docker pull docker.1ms.run/minio/minio:latest

echo "保存离线镜像"
docker save -o "$IMAGE_ARCHIVE" \
  "crm-backend:$CRM_VERSION" \
  "crm-mcp:$CRM_VERSION" \
  "crm-frontend:$CRM_VERSION" \
  docker.1ms.run/postgres:16-alpine \
  docker.1ms.run/redis:7-alpine \
  docker.1ms.run/minio/minio:latest

cp "$DEPLOY_DIR/docker-compose.yml" "$PACKAGE_DIR/docker-compose.yml"
cp "$DEPLOY_DIR/env.example" "$PACKAGE_DIR/env.example"
cp "$DEPLOY_DIR/scripts/deploy-offline.sh" "$PACKAGE_DIR/scripts/deploy-offline.sh"
cp "$DEPLOY_DIR/scripts/import-nacos-config.sh" "$PACKAGE_DIR/scripts/import-nacos-config.sh"
cp "$DEPLOY_DIR/README.md" "$PACKAGE_DIR/README.md"
cp "$DEPLOY_DIR/nacos/"*.yaml "$PACKAGE_DIR/nacos/"
cp "$DEPLOY_DIR/sql/"*.sql "$PACKAGE_DIR/sql/"
cp "$ROOT_DIR/docs/deploy/NACOS_CONFIG.md" "$PACKAGE_DIR/docs/"
cp "$ROOT_DIR/docs/sql/20260729-knowledge-versioning.sql" "$PACKAGE_DIR/sql/"
cp "$ROOT_DIR/docs/knowledge-versioning-blue-green-index.md" "$PACKAGE_DIR/docs/"
chmod +x "$PACKAGE_DIR/scripts/"*.sh

CRM_DB_PASSWORD_VALUE="${CRM_DB_PASSWORD:-$(random_text)}"
CRM_REDIS_PASSWORD_VALUE="${CRM_REDIS_PASSWORD:-$(random_text)}"
CRM_MINIO_SECRET_KEY_VALUE="${CRM_MINIO_SECRET_KEY:-$(random_text)}"

cat > "$PACKAGE_DIR/.env" <<EOF
# ============================================================
# 智能营销管理系统 Nacos 模式环境变量
# 业务配置全部放到 Nacos，这里只保留 Docker 和 Nacos 启动必需项
# ============================================================

CRM_VERSION=$CRM_VERSION
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-crm}
CRM_TIMEZONE=${CRM_TIMEZONE:-Asia/Shanghai}

CRM_FRONTEND_PORT=${CRM_FRONTEND_PORT:-80}
CRM_FRONTEND_API_BASE_URL=${CRM_FRONTEND_API_BASE_URL:-}
CRM_FRONTEND_API_TIMEOUT=${CRM_FRONTEND_API_TIMEOUT:-30000}
CRM_ASSISTANT_TYPEWRITER_INTERVAL=${CRM_ASSISTANT_TYPEWRITER_INTERVAL:-12}
CRM_ASSISTANT_TYPEWRITER_STEP=${CRM_ASSISTANT_TYPEWRITER_STEP:-4}

CRM_DB_NAME=${CRM_DB_NAME:-crm}
CRM_DB_USERNAME=${CRM_DB_USERNAME:-app_user}
CRM_DB_PASSWORD=$CRM_DB_PASSWORD_VALUE
CRM_REDIS_PASSWORD=$CRM_REDIS_PASSWORD_VALUE
CRM_MINIO_ACCESS_KEY=${CRM_MINIO_ACCESS_KEY:-minioadmin}
CRM_MINIO_SECRET_KEY=$CRM_MINIO_SECRET_KEY_VALUE

CRM_JAVA_OPTS=${CRM_JAVA_OPTS:-"-Xms512m -Xmx1024m -Dfile.encoding=UTF-8"}
CRM_MCP_JAVA_OPTS=${CRM_MCP_JAVA_OPTS:-}

CRM_NACOS_ENABLED=true
CRM_NACOS_SERVER_ADDR=${CRM_NACOS_SERVER_ADDR:-192.168.50.105:8848}
CRM_NACOS_NAMESPACE=${CRM_NACOS_NAMESPACE:-}
CRM_NACOS_GROUP=${CRM_NACOS_GROUP:-DEFAULT_GROUP}
CRM_NACOS_DATA_ID=${CRM_NACOS_DATA_ID:-crm.yaml}
CRM_MCP_NACOS_DATA_ID=${CRM_MCP_NACOS_DATA_ID:-crm-mcp.yaml}
CRM_NACOS_SHARED_DATA_IDS=${CRM_NACOS_SHARED_DATA_IDS:-}
CRM_NACOS_USERNAME=${CRM_NACOS_USERNAME:-}
CRM_NACOS_PASSWORD=${CRM_NACOS_PASSWORD:-}
CRM_NACOS_TIMEOUT_MS=${CRM_NACOS_TIMEOUT_MS:-5000}
CRM_NACOS_FAIL_FAST=${CRM_NACOS_FAIL_FAST:-true}
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
