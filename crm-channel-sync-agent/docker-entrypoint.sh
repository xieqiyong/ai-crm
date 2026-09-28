#!/usr/bin/env bash
set -e

export DISPLAY="${DISPLAY:-:99}"

mkdir -p /app/data /tmp/crm-channel-sync-vnc

Xvfb "$DISPLAY" -screen 0 1440x900x24 -ac +extension RANDR >/tmp/crm-channel-sync-xvfb.log 2>&1 &
fluxbox -display "$DISPLAY" >/tmp/crm-channel-sync-fluxbox.log 2>&1 &

if [ -z "${VNC_PASSWORD:-}" ]; then
  echo "VNC_PASSWORD不能为空"
  exit 1
fi

x11vnc -storepasswd "$VNC_PASSWORD" /tmp/crm-channel-sync-vnc/passwd >/dev/null
x11vnc -display "$DISPLAY" -forever -shared -rfbport 5900 \
  -rfbauth /tmp/crm-channel-sync-vnc/passwd >/tmp/crm-channel-sync-x11vnc.log 2>&1 &
websockify --web=/usr/share/novnc 6080 localhost:5900 >/tmp/crm-channel-sync-novnc.log 2>&1 &

exec "$@"
