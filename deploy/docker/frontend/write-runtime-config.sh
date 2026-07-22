#!/bin/sh
set -eu

cat > /usr/share/nginx/html/config.js <<EOF
window.__CRM_CONFIG__ = {
  API_BASE_URL: '${CRM_FRONTEND_API_BASE_URL:-}',
  API_TIMEOUT: ${CRM_FRONTEND_API_TIMEOUT:-30000},
  APP_ENV: '${CRM_FRONTEND_APP_ENV:-production}',
}
EOF
