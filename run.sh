#!/usr/bin/env bash
# 一条本地命令启动：奖学金重算与申诉系统
# 用法：
#   ./run.sh            构建前端 + 打包后端 + 启动（http://localhost:8080）
#   ./run.sh --dev      前端 Vite 热更新(5173) + 后端 Quarkus 开发模式(8080)
#   ./run.sh --reset    删除本地 SQLite 数据与附件后重新启动（清空演示数据）
set -euo pipefail
cd "$(dirname "$0")"

MODE="prod"
RESET=0
for arg in "$@"; do
  case "$arg" in
    --dev) MODE="dev" ;;
    --reset) RESET=1 ;;
    *) echo "未知参数: $arg"; exit 1 ;;
  esac
done

# ---- 1. Java / Maven（优先使用解压在 ~/tools 下的本地工具链） ----
if ! command -v java >/dev/null 2>&1; then
  JDK_DIR="$(ls -d "$HOME"/tools/jdk-17* 2>/dev/null | head -1 || true)"
  if [ -n "$JDK_DIR" ]; then
    export JAVA_HOME="$JDK_DIR"
    export PATH="$JAVA_HOME/bin:$HOME/tools/apache-maven-3.9.9/bin:$PATH"
  fi
fi
command -v java >/dev/null 2>&1 || { echo "未找到 Java 17，请安装或解压到 ~/tools"; exit 1; }

if [ "$RESET" = "1" ]; then
  echo "== 清空本地数据库与附件 =="
  rm -rf data
fi

if [ "$MODE" = "dev" ]; then
  # ---- 开发模式：后端 + 前端分别热更新 ----
  command -v npm >/dev/null 2>&1 || { echo "未找到 npm（开发模式需要 Node.js）"; exit 1; }
  ( cd frontend && npm install && npm run dev ) &
  FRONT_PID=$!
  trap 'kill $FRONT_PID 2>/dev/null || true' EXIT
  if command -v mvn >/dev/null 2>&1; then MVN=mvn; else MVN="$HOME/tools/apache-maven-3.9.9/bin/mvn"; fi
  $MVN quarkus:dev
else
  # ---- 生产模式：先构建前端到 Quarkus 静态资源，再打包启动单一 Jar ----
  if [ ! -f src/main/resources/META-INF/resources/index.html ] || [ "${REBUILD_FRONTEND:-1}" = "1" ]; then
    if command -v npm >/dev/null 2>&1; then
      echo "== 构建 Svelte 前端 =="
      ( cd frontend && npm install --no-audit --no-fund && npm run build )
    else
      echo "未找到 npm，跳过前端构建（若 resources 下已有旧构建将继续使用）"
    fi
  fi

  echo "== 打包 Quarkus 后端（跳过测试） =="
  if command -v mvn >/dev/null 2>&1; then MVN=mvn; else MVN="$HOME/tools/apache-maven-3.9.9/bin/mvn"; fi
  $MVN -q -B -DskipTests package

  mkdir -p data
  echo "== 启动系统：http://localhost:8080 （Ctrl+C 停止） =="
  exec java -jar target/quarkus-app/quarkus-run.jar
fi
