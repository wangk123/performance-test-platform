#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-9100}"
BINARY="${ROOT}/node_exporter"
if [[ ! -x "${BINARY}" ]]; then
  echo "请将 node_exporter 二进制放到 ${ROOT}/node_exporter"
  exit 1
fi
nohup "${BINARY}" --web.listen-address=":${PORT}" > "${ROOT}/node_exporter.log" 2>&1 &
echo $! > "${ROOT}/node_exporter.pid"
echo "node_exporter started on :${PORT}"
