#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-9308}"
KAFKA_SERVER="${2:-127.0.0.1:9092}"
BINARY="${ROOT}/kafka_exporter"
if [[ ! -x "${BINARY}" ]]; then
  echo "请将 kafka_exporter 二进制放到 ${ROOT}/kafka_exporter"
  exit 1
fi
nohup "${BINARY}" --web.listen-address=":${PORT}" --kafka.server="${KAFKA_SERVER}" > "${ROOT}/kafka_exporter.log" 2>&1 &
echo $! > "${ROOT}/kafka_exporter.pid"
echo "kafka_exporter started on :${PORT}"
