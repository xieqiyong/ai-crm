#!/usr/bin/env bash
set -e

export DISPLAY="${DISPLAY:-:99}"

mkdir -p /app/data /tmp/license-sync-vnc

display_number="${DISPLAY#:}"
if [[ "$display_number" =~ ^[0-9]+$ ]]; then
  mkdir -p /tmp/.X11-unix
  rm -f "/tmp/.X${display_number}-lock" "/tmp/.X11-unix/X${display_number}"
fi

Xvfb "$DISPLAY" -screen 0 1440x900x24 -ac +extension RANDR >/tmp/license-sync-xvfb.log 2>&1 &
xvfb_pid=$!
for _ in {1..50}; do
  if [ -S "/tmp/.X11-unix/X${display_number}" ]; then
    break
  fi
  if ! kill -0 "$xvfb_pid" 2>/dev/null; then
    cat /tmp/license-sync-xvfb.log >&2
    exit 1
  fi
  sleep 0.1
done
if [ ! -S "/tmp/.X11-unix/X${display_number}" ]; then
  echo "Xvfb启动超时" >&2
  exit 1
fi

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
