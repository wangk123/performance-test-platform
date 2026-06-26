#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${1:-9104}"
DSN="${2:-user:pass@(127.0.0.1:3306)/}"
BINARY="${ROOT}/mysqld_exporter"
if [[ ! -x "${BINARY}" ]]; then
  echo "请将 mysqld_exporter 二进制放到 ${ROOT}/mysqld_exporter"
  exit 1
fi
export DATA_SOURCE_NAME="${DSN}"
nohup "${BINARY}" --web.listen-address=":${PORT}" > "${ROOT}/mysqld_exporter.log" 2>&1 &
echo $! > "${ROOT}/mysqld_exporter.pid"
echo "mysqld_exporter started on :${PORT}"
