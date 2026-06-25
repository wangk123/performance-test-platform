#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-9121}"
ADDR="${2:-redis://127.0.0.1:6379}"
BINARY="${ROOT}/redis_exporter"
if [[ ! -x "${BINARY}" ]]; then
  echo "请将 redis_exporter 二进制放到 ${ROOT}/redis_exporter"
  exit 1
fi
nohup "${BINARY}" --web.listen-address=":${PORT}" --redis.addr="${ADDR}" > "${ROOT}/redis_exporter.log" 2>&1 &
echo $! > "${ROOT}/redis_exporter.pid"
echo "redis_exporter started on :${PORT}"
