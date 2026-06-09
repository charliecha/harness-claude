#!/bin/bash
# .harness/packs/kotlin/build.sh — Kotlin/Android Build Skill 实现
# 退出码 0 = PASSED，非 0 = FAILED

set -uo pipefail
source "$(dirname "$0")/../../lib.sh"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

ok()   { echo -e "${GREEN}✅ $1${NC}"; }
fail() { echo -e "${RED}❌ $1${NC}"; echo -e "${RED}   $2${NC}"; exit 1; }

echo "=== Build Skill (kotlin) ==="

# Step 1: 项目配置存在（Gradle Kotlin DSL 或 Groovy）
echo "Step 1: gradle config"
if [ -f build.gradle.kts ] || [ -f build.gradle ] || [ -f settings.gradle.kts ] || [ -f settings.gradle ]; then
    ok "gradle config present"
else
    fail "gradle config" "未找到 build.gradle(.kts) 或 settings.gradle(.kts)"
fi

# Step 2: gradle wrapper 存在且可执行
echo "Step 2: gradle wrapper"
if [ ! -x ./gradlew ]; then
    fail "gradlew" "未找到可执行的 ./gradlew（执行 gradle wrapper 生成）"
fi
ok "gradlew executable"

# Step 3: 编译（assembleDebug 或 compileKotlin，按项目类型）
echo "Step 3: gradle compile"
if grep -rq "com.android.application\|com.android.library" --include="*.gradle*" . 2>/dev/null; then
    # Android 项目
    OUTPUT=$(./gradlew assembleDebug --no-daemon -q 2>&1)
else
    # 纯 Kotlin 项目
    OUTPUT=$(./gradlew compileKotlin --no-daemon -q 2>&1)
fi
if [ $? -ne 0 ]; then
    fail "compile" "$OUTPUT"
fi
ok "kotlin compiles"

echo ""
echo -e "${GREEN}Build Skill: PASSED${NC}"
