#!/usr/bin/env bash
# 一键启动：准备本地 JDK/Maven/Node 环境 → 构建前端 → 同步到后端静态资源 → 启动 Quarkus（单端口 8080）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS="$HOME/tools"

# ---------- 环境自举（如本机没有 java/mvn，使用 ~/tools 下的便携版） ----------
if [ -z "${JAVA_HOME:-}" ] && [ -d "$TOOLS/jdk17" ]; then
  export JAVA_HOME="$TOOLS/jdk17"
fi
if [ ! -x "${JAVA_HOME:-/nonexistent}/bin/java" ]; then
  echo "[run.sh] 未发现可用 JDK（需要 17+）。请安装 JDK 17 或放置到 ~/tools/jdk17" >&2
  exit 1
fi
MVN="$TOOLS/apache-maven-3.9.9/bin/mvn"
if ! command -v mvn >/dev/null 2>&1; then
  if [ -x "$MVN" ]; then
    MVN_BIN="$MVN"
  else
    echo "[run.sh] 未发现 Maven。请安装 Maven 3.9+ 或放置到 ~/tools/apache-maven-3.9.9" >&2
    exit 1
  fi
else
  MVN_BIN="$(command -v mvn)"
fi
export PATH="$JAVA_HOME/bin:$PATH"

BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"
STATIC="$BACKEND/src/main/resources/META-INF/resources"

echo "[run.sh] 1/3 构建前端…"
cd "$FRONTEND"
if [ ! -d node_modules ]; then
  npm install --no-audit --no-fund
fi
npm run build

echo "[run.sh] 2/3 同步前端产物到后端静态目录…"
rm -rf "$STATIC"
mkdir -p "$STATIC"
cp -r "$FRONTEND"/dist/* "$STATIC/"

echo "[run.sh] 3/3 启动 Quarkus（dev 模式，数据保存在 backend/data/scholarship.db）…"
cd "$BACKEND"
mkdir -p data
exec "$MVN_BIN" quarkus:dev -Dquarkus.http.host=0.0.0.0
