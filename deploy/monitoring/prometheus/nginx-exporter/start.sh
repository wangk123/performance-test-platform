#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-9113}"
STATUS_URL="${2:-http://127.0.0.1/stub_status}"
BINARY="${ROOT}/nginx-prometheus-exporter"
if [[ ! -x "${BINARY}" ]]; then
  echo "请将 nginx-prometheus-exporter 二进制放到 ${ROOT}/nginx-prometheus-exporter"
  exit 1
fi
nohup "${BINARY}" -nginx.scrape-uri="${STATUS_URL}" -web.listen-address=":${PORT}" > "${ROOT}/nginx_exporter.log" 2>&1 &
echo $! > "${ROOT}/nginx_exporter.pid"
echo "nginx exporter started on :${PORT}"
