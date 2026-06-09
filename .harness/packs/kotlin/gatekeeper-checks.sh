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
        . 2>/dev/null'

# 无裸 println()（除非有显式 // noqa 豁免）
check "no bare println() in non-test code" \
    '! grep -rn --include="*.kt" \
        --exclude-dir=".git" --exclude-dir=".gradle" --exclude-dir="build" \
        --exclude-dir="test" --exclude-dir="androidTest" \
        "^[^/]*\bprintln(" . 2>/dev/null | grep -v "// noqa"'

# 不允许 GlobalScope.launch（应使用 viewModelScope/lifecycleScope/结构化并发）
check "no GlobalScope.launch (use structured concurrency)" \
    '! grep -rn --include="*.kt" \
        --exclude-dir=".git" --exclude-dir=".gradle" --exclude-dir="build" \
        "GlobalScope\.launch" . 2>/dev/null | grep -v "// noqa"'

# 不允许 !! 强制解包（除测试代码）
check "no !! force-unwrap in non-test code" \
    '! grep -rn --include="*.kt" \
        --exclude-dir=".git" --exclude-dir=".gradle" --exclude-dir="build" \
        --exclude-dir="test" --exclude-dir="androidTest" \
        "!!" . 2>/dev/null | grep -v "// noqa" | grep -vE "^\s*//"'

# ────────────────────────────────────────────────
# SECTION: 静态分析
# ────────────────────────────────────────────────

if [ -x ./gradlew ]; then
    # ktlint（如已配置）
    if grep -rq "ktlint" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "ktlint clean" './gradlew ktlintCheck --no-daemon -q'
    else
        echo -e "${YELLOW}⚠️  ktlint not configured — skipping (推荐添加 org.jlleitschuh.gradle.ktlint plugin)${NC}"
    fi

    # detekt（如已配置）
    if grep -rq "detekt" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "detekt clean" './gradlew detekt --no-daemon -q'
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

if [ -x ./gradlew ]; then
    # 编译
    if grep -rq "com.android.application\|com.android.library" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "android assembleDebug succeeds" \
            './gradlew assembleDebug --no-daemon -q'
        TEST_TASK="testDebugUnitTest"
    else
        check_with_output "compileKotlin succeeds" \
            './gradlew compileKotlin --no-daemon -q'
        TEST_TASK="test"
    fi

    # 测试 + 覆盖率（阈值从 .harness-config.json）
    THRESHOLD=$(harness_get coverage_threshold)
    check_with_output "gradle ${TEST_TASK} passes" "./gradlew ${TEST_TASK} --no-daemon -q"

    # 覆盖率验证（kover 优先，jacoco 兜底）
    if grep -rq "id(\"org.jetbrains.kotlinx.kover\")\|kotlinx-kover" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "kover coverage >= ${THRESHOLD}%" \
            './gradlew koverVerify --no-daemon -q'
    elif grep -rq "jacoco" --include="*.gradle*" . 2>/dev/null; then
        check_with_output "jacoco coverage >= ${THRESHOLD}%" \
            './gradlew jacocoTestCoverageVerification --no-daemon -q'
    else
        echo -e "${YELLOW}⚠️  未配置 kover/jacoco — 跳过覆盖率验证（强烈建议添加 kover）${NC}"
    fi
fi
