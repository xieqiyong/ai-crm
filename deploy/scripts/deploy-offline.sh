#!/usr/bin/env bash
set -euo pipefail

RELEASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE_ARCHIVE="$RELEASE_DIR/images/crm-images.tar"

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "$@"
    return
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
    return
  fi
  echo "未找到 Docker Compose，请先安装 docker compose 插件或 docker-compose"
  exit 1
}

LOAD_IMAGES=true
if [ "${1:-}" = "--no-load" ]; then
  LOAD_IMAGES=false
fi

cd "$RELEASE_DIR"
export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-crm}"

if [ ! -f .env ]; then
  cp env.example .env
  echo "已生成 .env，请按生产环境修改后重新执行部署脚本"
  exit 1
fi

mkdir -p data/postgres data/redis data/minio data/backend logs/backend logs/mcp logs/gateway uploads

if [ "$LOAD_IMAGES" = true ]; then
  if [ ! -f "$IMAGE_ARCHIVE" ]; then
    echo "未找到离线镜像包：$IMAGE_ARCHIVE"
    exit 1
  fi
  echo "加载离线镜像"
  docker load -i "$IMAGE_ARCHIVE"
fi

echo "导入 Nacos 配置"
sh scripts/import-nacos-config.sh

echo "启动智能营销管理系统"
compose up -d --no-build --pull never

echo "当前容器状态"
compose ps

echo "部署完成，前端访问端口以 .env 中 CRM_FRONTEND_PORT 为准"
