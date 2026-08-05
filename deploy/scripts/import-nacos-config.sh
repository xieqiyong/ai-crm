#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
NACOS_DIR="$DEPLOY_DIR/nacos"
ENV_FILE="$DEPLOY_DIR/.env"

read_env_value() {
  local key="$1"
  local default_value="$2"
  if [ -f "$ENV_FILE" ]; then
    local value
    value="$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d '=' -f 2- || true)"
    if [ -n "$value" ]; then
      printf '%s' "$value"
      return
    fi
  fi
  printf '%s' "$default_value"
}

normalize_addr() {
  local value="$1"
  if [[ "$value" == http://* || "$value" == https://* ]]; then
    printf '%s' "$value"
    return
  fi
  printf 'http://%s' "$value"
}

NACOS_ADDR="$(normalize_addr "${CRM_NACOS_SERVER_ADDR:-${NACOS_ADDR:-$(read_env_value CRM_NACOS_SERVER_ADDR "127.0.0.1:8848")}}")"
NACOS_GROUP="${CRM_NACOS_GROUP:-${NACOS_GROUP:-$(read_env_value CRM_NACOS_GROUP "DEFAULT_GROUP")}}"
NACOS_NAMESPACE="${CRM_NACOS_NAMESPACE:-${NACOS_NAMESPACE:-$(read_env_value CRM_NACOS_NAMESPACE "")}}"
NACOS_USERNAME="${CRM_NACOS_USERNAME:-${NACOS_USERNAME:-$(read_env_value CRM_NACOS_USERNAME "")}}"
NACOS_PASSWORD="${CRM_NACOS_PASSWORD:-${NACOS_PASSWORD:-$(read_env_value CRM_NACOS_PASSWORD "")}}"
CRM_DATA_ID="${CRM_NACOS_DATA_ID:-$(read_env_value CRM_NACOS_DATA_ID "crm.yaml")}"
CRM_MCP_DATA_ID="${CRM_MCP_NACOS_DATA_ID:-$(read_env_value CRM_MCP_NACOS_DATA_ID "crm-mcp.yaml")}"

if ! command -v curl >/dev/null 2>&1; then
  echo "未找到curl，请先安装curl"
  exit 1
fi

get_access_token() {
  if [ -z "$NACOS_USERNAME" ]; then
    return
  fi
  local response
  response="$(curl -sS -X POST "$NACOS_ADDR/nacos/v1/auth/users/login" \
    --data-urlencode "username=$NACOS_USERNAME" \
    --data-urlencode "password=$NACOS_PASSWORD")"
  local token
  token="$(printf '%s' "$response" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  if [ -z "$token" ]; then
    echo "Nacos登录失败：$response"
    exit 1
  fi
  printf '%s' "$token"
}

ACCESS_TOKEN="$(get_access_token)"

publish_config() {
  local data_id="$1"
  local file_path="$2"
  if [ ! -f "$file_path" ]; then
    echo "配置文件不存在：$file_path"
    exit 1
  fi

  echo "导入Nacos配置：dataId=$data_id，group=$NACOS_GROUP，file=$file_path"

  local args=(
    -sS
    -X POST
    "$NACOS_ADDR/nacos/v1/cs/configs"
    --data-urlencode "dataId=$data_id"
    --data-urlencode "group=$NACOS_GROUP"
    --data-urlencode "type=yaml"
    --data-urlencode "content@$file_path"
  )

  if [ -n "$NACOS_NAMESPACE" ]; then
    args+=(--data-urlencode "tenant=$NACOS_NAMESPACE")
  fi
  if [ -n "$ACCESS_TOKEN" ]; then
    args+=(--data-urlencode "accessToken=$ACCESS_TOKEN")
  fi

  local result
  result="$(curl "${args[@]}")"
  if [ "$result" != "true" ]; then
    echo "Nacos配置导入失败：dataId=$data_id，响应=$result"
    exit 1
  fi
  echo "导入完成：$data_id"
}

publish_config "$CRM_DATA_ID" "$NACOS_DIR/crm.yaml"
publish_config "$CRM_MCP_DATA_ID" "$NACOS_DIR/crm-mcp.yaml"

echo "Nacos配置导入完成"
