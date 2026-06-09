# Kotlin/Android 专属红线

本文件列出 Kotlin/Android 项目专属的硬性规则。通用规则见 `.harness/rules/common.md`。

## 编译与测试

- 修改任何 `.kt` / `.kts` 文件后，必须执行 **Build Skill**（`bash .claude/skills/build.sh`）验证通过
- 提交代码前必须执行 **Test Skill**（`bash .claude/skills/test.sh`）验证通过
- Android 项目使用 `assembleDebug` + `testDebugUnitTest`；纯 Kotlin 项目使用 `compileKotlin` + `test`

## 并发与协程

- 禁止使用 `GlobalScope.launch` —— 必须使用 `viewModelScope` / `lifecycleScope` / `coroutineScope` 等结构化并发
- 禁止在主线程进行 I/O / 数据库 / 网络操作（必须切换到 `Dispatchers.IO` 或 `Dispatchers.Default`）
- 协程 `CancellationException` 不得吞掉，必须 rethrow

## 空安全

- 非测试代码禁止使用 `!!` 强制解包 —— 必须用 `?.let`、`?:`、`requireNotNull(..., msg)` 或 sealed class
- 公开 API 的可空性必须在类型签名中显式表达（`String?` vs `String`）

## 日志规范

- 所有日志必须使用 `android.util.Log` / `Timber` / SLF4J，禁止裸 `println(...)` 输出运行时信息
- 测试代码中可使用 `println()`（自动豁免）

## 代码质量

- 不得引入 `ktlintCheck` 报出的错误（若已配置 ktlint plugin）
- 不得引入 `detekt` 报出的错误（若已配置 detekt plugin）
- 优先使用 `data class` / `sealed class` 表达不可变结构，避免 var + setter

## Android 特定

- Activity / Fragment 不得直接持有网络客户端 / Repository —— 走 ViewModel + DI
- 资源字符串必须放 `strings.xml`，不得硬编码在 Compose / XML 中（除非是技术常量）
- 权限敏感操作（位置、相机、麦克风、文件）必须在 Manifest 声明 + runtime 申请

## 工具链

- 必须本地安装：JDK 17+、Android SDK（Android 项目）、Gradle wrapper 已生成
- 推荐插件（gatekeeper 会软跳过未配置项）：
  - `org.jlleitschuh.gradle.ktlint`（格式化）
  - `io.gitlab.arturbosch.detekt`（静态分析）
  - `org.jetbrains.kotlinx.kover`（覆盖率，优于 jacoco）
- 项目必须有 `build.gradle.kts` 或 `build.gradle`，并提交 `gradlew` 到仓库
