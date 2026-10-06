#!/usr/bin/env bash
# 一键运行后端测试（含申诉重算 / 自审拦截 / 发放补退 三个关键检查点）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS="$HOME/tools"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$TOOLS/jdk17" ]; then export JAVA_HOME="$TOOLS/jdk17"; fi
export PATH="$JAVA_HOME/bin:$PATH"
if command -v mvn >/dev/null 2>&1; then MVN="$(command -v mvn)"; else MVN="$TOOLS/apache-maven-3.9.9/bin/mvn"; fi
cd "$ROOT/backend"
exec "$MVN" -B test
