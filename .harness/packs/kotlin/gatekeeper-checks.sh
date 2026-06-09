#!/bin/bash
# .harness/packs/kotlin/gatekeeper-checks.sh — Kotlin/Android 专属 gatekeeper 检查
# 由 .harness/gatekeeper.sh source。复用骨架的 check/check_with_output/PASS/FAIL/颜色。

# ────────────────────────────────────────────────
# SECTION: 源码安全检查（Kotlin）
# ────────────────────────────────────────────────

check "no hardcoded secrets" \
    '! grep -rE "(api_key|apikey|api-key|secret|password|token)\s*[:=]\s*\"[^\"]{8,}\"" \
        --include="*.kt" \
        --include="*.kts" \
        --exclude-dir=".git" \
        --exclude-dir=".gradle" \
        --exclude-dir="build" \
        --exclude-dir=".idea" \
        --exclude-dir="third_party" \
        . 2>/dev/null'

# 无裸 println()（除非有显式 // noqa 豁免）
check "no bare println() in non-test code" \
    '! grep -rn --include="*.kt" \
        --exclude-dir=".git" --exclude-dir=".gradle" --exclude-dir="build" \
        --exclude-dir="test" --exclude-dir="androidTest" \
        --exclude-dir="third_party" \
        "^[^/]*\bprintln(" . 2>/dev/null | grep -v "// noqa"'

# 不允许 GlobalScope.launch（应使用 viewModelScope/lifecycleScope/结构化并发）
check "no GlobalScope.launch (use structured concurrency)" \
    '! grep -rn --include="*.kt" \
        --exclude-dir=".git" --exclude-dir=".gradle" --exclude-dir="build" \
        --exclude-dir="third_party" \
        "GlobalScope\.launch" . 2>/dev/null | grep -v "// noqa"'

# 不允许 !! 强制解包（除测试代码）
check "no !! force-unwrap in non-test code" \
    '! grep -rn --include="*.kt" \
        --exclude-dir=".git" --exclude-dir=".gradle" --exclude-dir="build" \
        --exclude-dir="test" --exclude-dir="androidTest" \
        --exclude-dir="third_party" \
        "!!" . 2>/dev/null | grep -v "// noqa" | grep -vE "^\s*//"'

# ────────────────────────────────────────────────
# SECTION: 静态分析
# ────────────────────────────────────────────────

# Locate gradlew: prefer root, then first subdirectory (e.g. android-llm-app/).
GRADLEW=""
GRADLE_DIR="."
if [ -x ./gradlew ]; then
    GRADLEW="./gradlew"
else
    SUBDIR_GRADLEW=$(find . -maxdepth 2 -name gradlew 2>/dev/null | head -1)
    if [ -n "$SUBDIR_GRADLEW" ] && [ -x "$SUBDIR_GRADLEW" ]; then
        GRADLEW="$SUBDIR_GRADLEW"
        GRADLE_DIR=$(dirname "$SUBDIR_GRADLEW")
    fi
fi

if [ -n "$GRADLEW" ]; then
    GRADLE_CMD="(cd ${GRADLE_DIR} && ./gradlew"
    # ktlint（如已配置）
    if grep -rq "ktlint" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "ktlint clean" "${GRADLE_CMD} ktlintCheck --no-daemon -q)"
    else
        echo -e "${YELLOW}⚠️  ktlint not configured — skipping (推荐添加 org.jlleitschuh.gradle.ktlint plugin)${NC}"
    fi

    # detekt（如已配置）
    if grep -rq "detekt" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "detekt clean" "${GRADLE_CMD} detekt --no-daemon -q)"
    else
        echo -e "${YELLOW}⚠️  detekt not configured — skipping (推荐添加 io.gitlab.arturbosch.detekt plugin)${NC}"
    fi
else
    echo -e "${RED}❌ gradlew not executable${NC}"
    FAIL=$((FAIL + 1))
    FAILED_ITEMS+=("gradlew not executable")
fi

# ────────────────────────────────────────────────
# SECTION: 编译与测试
# ────────────────────────────────────────────────

if [ -n "$GRADLEW" ]; then
    GRADLE_CMD="(cd ${GRADLE_DIR} && ./gradlew"
    # 编译
    if grep -rq "com.android.application\|com.android.library" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "android assembleDebug succeeds" \
            "${GRADLE_CMD} assembleDebug --no-daemon -q)"
        TEST_TASK="testDebugUnitTest"
    else
        check_with_output "compileKotlin succeeds" \
            "${GRADLE_CMD} compileKotlin --no-daemon -q)"
        TEST_TASK="test"
    fi

    # 测试 + 覆盖率（阈值从 .harness-config.json）
    THRESHOLD=$(harness_get coverage_threshold)
    check_with_output "gradle ${TEST_TASK} passes" "${GRADLE_CMD} ${TEST_TASK} --no-daemon -q)"

    # 覆盖率验证（kover 优先，jacoco 兜底）
    if grep -rq "id(\"org.jetbrains.kotlinx.kover\")\|kotlinx-kover" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "kover coverage >= ${THRESHOLD}%" \
            "${GRADLE_CMD} koverVerify --no-daemon -q)"
    elif grep -rq "jacoco" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "jacoco coverage >= ${THRESHOLD}%" \
            "${GRADLE_CMD} jacocoTestCoverageVerification --no-daemon -q)"
    else
        echo -e "${YELLOW}⚠️  未配置 kover/jacoco — 跳过覆盖率验证（强烈建议添加 kover）${NC}"
    fi
fi
