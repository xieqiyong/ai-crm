#!/usr/bin/env bash
set -e

export DISPLAY="${DISPLAY:-:99}"

mkdir -p /app/data /tmp/license-sync-vnc

Xvfb "$DISPLAY" -screen 0 1440x900x24 -ac +extension RANDR >/tmp/license-sync-xvfb.log 2>&1 &
fluxbox -display "$DISPLAY" >/tmp/license-sync-fluxbox.log 2>&1 &

if [ -z "${VNC_PASSWORD:-}" ]; then
  echo "VNC_PASSWORD不能为空"
  exit 1
fi

x11vnc -storepasswd "$VNC_PASSWORD" /tmp/license-sync-vnc/passwd >/dev/null
x11vnc -display "$DISPLAY" -forever -shared -rfbport 5900 \
  -rfbauth /tmp/license-sync-vnc/passwd >/tmp/license-sync-x11vnc.log 2>&1 &
websockify --web=/usr/share/novnc 6080 localhost:5900 >/tmp/license-sync-novnc.log 2>&1 &

exec "$@"
