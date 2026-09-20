#!/usr/bin/env bash
# 后端打包+重启一键脚本（固化历史踩坑，勿手工改用其它方式启动）：
#   1. 系统裸 java 是 Java 8，必须用 JDK17 字面路径，且 shell 里 JAVA_HOME=x $JAVA_HOME/... 展开顺序有坑，故全部写字面路径
#   2. 启动 cwd 必须是仓库根——storage.root=./storage 是相对路径，DB stored_path 存相对串，
#      cwd 漂移（如 bootRun cwd=backend/）会造成双 storage 分裂、读脚本报 failed to read script file
#   3. nohup 常驻启动，日志重定向到仓库根 backend-run.log（相对重定向依赖 cwd，同样要求 cwd=仓库根）
#   4. 启动约 28-50s，探活以 /api/projects 未带 token 返回 401 为存活信号，别太早判死
#   5. curl 探活必须 --noproxy '*'（系统代理 7897 会拦 localhost，返回假 502）
# 用法：./deploy/redeploy-backend.sh [--skip-build]   （--skip-build：后端无改动，仅重启）
set -euo pipefail

JDK_JAVA=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home/bin/java
JDK_HOME=/Users/wangk/Documents/config/jdk-17.0.17+10/Contents/Home
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$REPO_ROOT/backend/build/libs/backend-0.1.0-SNAPSHOT.jar"
LOG="$REPO_ROOT/backend-run.log"
PORT=8080

cd "$REPO_ROOT"

if [[ "${1:-}" != "--skip-build" ]]; then
    echo "==> 打包 :backend:bootJar（约 25s）"
    JAVA_HOME="$JDK_HOME" "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :backend:bootJar
fi

if [[ ! -f "$JAR" ]]; then
    echo "错误：$JAR 不存在，请去掉 --skip-build 先打包" >&2
    exit 1
fi

echo "==> 停止旧进程"
pkill -f "backend-0.1.0-SNAPSHOT.jar" 2>/dev/null || true
for _ in $(seq 1 60); do
    pgrep -f "backend-0.1.0-SNAPSHOT.jar" >/dev/null 2>&1 || break
    sleep 1
done
if pgrep -f "backend-0.1.0-SNAPSHOT.jar" >/dev/null 2>&1; then
    echo "错误：旧进程 60s 内未退出（Spring 优雅关闭偏慢），确认无事后可 kill -9：pgrep -f backend-0.1.0-SNAPSHOT.jar" >&2
    exit 1
fi

echo "==> 启动新进程（cwd=仓库根，JDK17 字面路径，nohup 常驻）"
nohup "$JDK_JAVA" -jar "$JAR" > "$LOG" 2>&1 &
disown

echo "==> 探活 http://localhost:$PORT/api/projects（预期 401=存活，最长等 90s）"
for _ in $(seq 1 45); do
    code=$(curl -s --noproxy '*' -o /dev/null -w '%{http_code}' "http://localhost:$PORT/api/projects" || true)
    if [[ "$code" == "401" || "$code" == "200" ]]; then
        echo "==> 部署完成（http_code=${code}），日志：$LOG"
        exit 0
    fi
    sleep 2
done

echo "错误：90s 内未探活成功，最后 http_code=${code:-none}，排查：tail -100 $LOG" >&2
exit 1
