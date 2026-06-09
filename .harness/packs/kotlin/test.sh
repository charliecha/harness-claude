#!/bin/bash
# .harness/packs/kotlin/test.sh — Kotlin/Android Test Skill 实现
# 退出码 0 = PASSED，非 0 = FAILED

set -uo pipefail
source "$(dirname "$0")/../../lib.sh"

THRESHOLD=$(harness_get coverage_threshold)

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

ok()   { echo -e "${GREEN}✅ $1${NC}"; }
fail() { echo -e "${RED}❌ $1${NC}"; echo -e "${RED}   $2${NC}"; exit 1; }

echo "=== Test Skill (kotlin) ==="

# Step 1: 运行测试（JVM 单元测试）
echo "Step 1: gradle test"
if [ ! -x ./gradlew ]; then
    fail "gradlew" "未找到可执行的 ./gradlew"
fi

# Android 项目用 testDebugUnitTest，纯 Kotlin 项目用 test
if grep -rq "com.android.application\|com.android.library" --include="*.gradle*" . 2>/dev/null; then
    TEST_TASK="testDebugUnitTest"
else
    TEST_TASK="test"
fi

OUTPUT=$(./gradlew "$TEST_TASK" --no-daemon -q 2>&1)
if [ $? -ne 0 ]; then
    echo "$OUTPUT"
    fail "tests" "测试失败"
fi
ok "all tests passed"

# Step 2: 覆盖率（jacoco/kover，二选一；都没配则警告但不阻断）
echo "Step 2: coverage (jacoco/kover)"
if grep -rq "jacoco\|kover" --include="*.gradle*" . 2>/dev/null; then
    COV_TASK=""
    grep -rq "id(\"org.jetbrains.kotlinx.kover\")\|kotlinx-kover" --include="*.gradle*" . 2>/dev/null && COV_TASK="koverVerify"
    [ -z "$COV_TASK" ] && grep -rq "jacoco" --include="*.gradle*" . 2>/dev/null && COV_TASK="jacocoTestCoverageVerification"

    if [ -n "$COV_TASK" ]; then
        OUTPUT=$(./gradlew "$COV_TASK" --no-daemon -q 2>&1)
        if [ $? -ne 0 ]; then
            echo "$OUTPUT"
            fail "coverage baseline" "覆盖率低于 ${THRESHOLD}% 基线（请在 build.gradle.kts 中配置 ${THRESHOLD}% 阈值）"
        fi
        ok "coverage ≥ ${THRESHOLD}%"
    else
        echo -e "${RED}❌ 检测到 jacoco/kover 配置但任务名未识别${NC}"
        exit 1
    fi
else
    echo -e "\033[1;33m⚠️  未配置 jacoco/kover，跳过覆盖率检查（建议添加 kover）${NC}"
fi

echo ""
echo -e "${GREEN}Test Skill: PASSED${NC}"
