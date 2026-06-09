---
description: Team feature orchestrator — harness state machine controls phases, ECC orch-add-feature handles execution.
---

# /team-orch-add-feature

**控制层** 复用 `.harness/workflow.sh` 六阶段状态机。
**执行层** 完全委托 ECC 的 `/orch-add-feature` 流程（Research → Plan → TDD → Review → Commit）。

两层职责分离：
- **harness 只管"做到哪了"**（持久状态、产物路径、push 门禁、文档模板）
- **ECC 只管"怎么做"**（研究、规划、编码、审查、提交）

## 文档模板

三份产物模板独立维护在 `docs/templates/`：

| 产物 | 模板 | 何时产出 |
|------|------|---------|
| 需求文档 | [docs/templates/FR-template.md](../../docs/templates/FR-template.md) | Phase 1 requirements |
| 架构决策 | [docs/templates/ADR-template.md](../../docs/templates/ADR-template.md) | Phase 2 architecture |
| QA 审查 | [docs/templates/RV-template.md](../../docs/templates/RV-template.md) | Phase 5 qa-review |

ECC 模型在每个阶段产出文档前，**必须读取对应模板**，按模板章节结构填充。模板内每节都标注"强制要求"，不得省略。

## 流程

| Phase | harness 控制层 | ECC 执行层 | Gate |
|-------|----------------|-----------|------|
| 1. requirements | `workflow.sh start <feature>` | 调用 `/orch-add-feature` 完成 Research，按 FR-template 产出 `docs/requirements/FR-XXX.md` | 用户「确认」FR |
| — | `set-artifact requirements docs/requirements/FR-XXX.md` <br> `advance architecture` | — | — |
| 2. architecture | — | 继续 `/orch-add-feature` 的 Plan 阶段，按 ADR-template 产出 `docs/architecture/ADR-XXX.md`（含 task_list） | 用户「确认」ADR |
| — | `set-artifact architecture docs/architecture/ADR-XXX.md` <br> `advance dev` | — | — |
| 3. dev | — | 继续 `/orch-add-feature` 的 TDD 阶段，按 ADR 的 task_list 写代码 + 测试 | — |
| — | `advance gatekeeper` | — | — |
| 4. gatekeeper | `bash .harness/gatekeeper.sh` | — | exit 0 自动通过（hook 触发 `gate-pass`） |
| — | `advance qa-review` | — | — |
| 5. qa-review | — | 继续 `/orch-add-feature` 的 Review 阶段，按 RV-template 产出 `docs/reviews/RV-XXX.md` | Critical = 0 |
| — | `set-artifact review docs/reviews/RV-XXX.md` <br> `advance pm-acceptance` | — | — |
| 6. pm-acceptance | — | 对照 FR 逐条验收 | 用户「确认」验收 |
| — | `complete` | — | — |

## 控制 vs 执行的边界

**harness 控制层负责**：
- 启动/推进/结束 feature 生命周期
- 持久化产物路径到 `.workflow-state.json`
- 维护 `docs/templates/` 下的三份模板
- `gatekeeper.sh` 安检（编译/测试/冒烟）
- `check-phase.sh` 在 push 前拦截

**ECC 执行层负责**：
- 代码探索、需求理解、架构设计
- TDD 循环（RED → GREEN → REFACTOR）
- 调用 code-reviewer / security-reviewer / 语言 reviewer
- 按模板填充 FR / ADR / RV 文档内容
- 提交 commit message

**模型不做的事**：
- 不直接写 `.workflow-state.json`，全部走 `workflow.sh`
- 不绕过 `/orch-add-feature` 自己研究/规划/写代码
- 不在 ECC 流程中重新实现 gatekeeper 检查
- 不脱离模板自由发挥文档结构

## 执行步骤

1. **启动**：`bash .harness/workflow.sh start <feature-slug>`
2. **委托 ECC**：调用 `/orch-add-feature <用户需求>`，让其按 Research → Plan → TDD → Review 顺序执行
3. **模板加载**：每次产出文档前，先 Read 对应模板（FR/ADR/RV），严格按章节填充
4. **状态同步**：ECC 产出每份文档后，立即回到主流程执行对应的 `set-artifact` + `advance`，并更新 `docs/{requirements,architecture,reviews}/INDEX.md`
5. **Gate 1（FR 后）**：present FR，等用户「确认」→ 才 `advance architecture`
6. **Gate 2（ADR 后）**：present ADR，等用户「确认」→ 才 `advance dev`
7. **Gatekeeper**：`bash .harness/gatekeeper.sh` 通过后 hook 自动调 `gate-pass` → `advance qa-review`
8. **Gate 3（RV 后）**：present RV，等用户「确认」→ 才 `advance pm-acceptance`
9. **PM 验收**：等用户「接受」→ 才 `bash .harness/workflow.sh complete`

## 关键约束

- **ECC 流程内嵌的 GATE 1/2 由主流程的 Gate 1/2/3 覆盖** —— 不要让 ECC 在内部又问一次"确认"。统一在 harness 阶段切换点询问。
- **语言专属 reviewer 由 ECC 自己选** —— `/orch-add-feature` 会自动检测项目语言，调用对应的语言 reviewer，本 command 不需要硬编码。
- **`workflow.sh complete` 必须由主流程执行** —— 不得委托给任何 agent。
- **模板的"强制要求"具有约束力** —— 文档每节标注的强制要求 ECC 必须遵守，不得跳过或简化。

## 与 /orch-add-feature 的区别

| | `/orch-add-feature` | `/team-orch-add-feature` |
|---|---|---|
| 状态持久化 | 仅 in-session | `.workflow-state.json` |
| 产物文件 | 不写 FR/ADR/RV | 按模板强制生成 |
| 文档格式 | 模型自由发挥 | `docs/templates/` 锁定结构 |
| Push 拦截 | 无 | `check-phase.sh` |
| Gatekeeper 安检 | 无 | `gatekeeper.sh` |
| PM 验收 | 无 | 对照 FR 逐条 |
| 团队可见性 | 低（对话流） | 高（文件 + state） |

适用：团队协作、跨会话开发、需要审计跟踪的场景。
单人探索、短期 PoC 直接用 `/orch-add-feature` 即可。
