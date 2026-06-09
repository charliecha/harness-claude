---
description: Team feature orchestrator — harness state machine controls phases, ECC orch-add-feature handles execution.
---

# /team-orch-add-feature

**控制层** 复用 `.harness/workflow.sh` 六阶段状态机。
**执行层** 完全委托 ECC 的 `/orch-add-feature` 流程（Research → Plan → TDD → Review → Commit）。

两层职责分离：
- **harness 只管"做到哪了"**（持久状态、产物路径、push 门禁）
- **ECC 只管"怎么做"**（研究、规划、编码、审查、提交）

## 流程

| Phase | harness 控制层 | ECC 执行层 | Gate |
|-------|----------------|-----------|------|
| 1. requirements | `workflow.sh start <feature>` | 调用 `/orch-add-feature` 完成 Research + 产出 `docs/requirements/FR-XXX.md` | 用户「确认」FR |
| — | `set-artifact requirements docs/requirements/FR-XXX.md` <br> `advance architecture` | — | — |
| 2. architecture | — | 继续 `/orch-add-feature` 的 Plan 阶段，产出 `docs/architecture/ADR-XXX.md` | 用户「确认」ADR |
| — | `set-artifact architecture docs/architecture/ADR-XXX.md` <br> `advance dev` | — | — |
| 3. dev | — | 继续 `/orch-add-feature` 的 TDD 阶段，写代码 + 测试 | — |
| — | `advance gatekeeper` | — | — |
| 4. gatekeeper | `bash .harness/gatekeeper.sh` | — | exit 0 自动通过（hook 触发 `gate-pass`） |
| — | `advance qa-review` | — | — |
| 5. qa-review | — | 继续 `/orch-add-feature` 的 Review 阶段，产出 `docs/reviews/RV-XXX.md` | Critical = 0 |
| — | `set-artifact review docs/reviews/RV-XXX.md` <br> `advance pm-acceptance` | — | — |
| 6. pm-acceptance | — | 对照 FR 逐条验收 | 用户「确认」验收 |
| — | `complete` | — | — |

## 控制 vs 执行的边界

**harness 控制层负责**：
- 启动/推进/结束 feature 生命周期
- 持久化产物路径到 `.workflow-state.json`
- `gatekeeper.sh` 安检（编译/测试/冒烟）
- `check-phase.sh` 在 push 前拦截

**ECC 执行层负责**：
- 代码探索、需求理解、架构设计
- TDD 循环（RED → GREEN → REFACTOR）
- 调用 code-reviewer / security-reviewer / 语言 reviewer
- 产出 FR / ADR / RV 文档内容
- 提交 commit message

**模型不做的事**：
- 不直接写 `.workflow-state.json`，全部走 `workflow.sh`
- 不绕过 `/orch-add-feature` 自己研究/规划/写代码
- 不在 ECC 流程中重新实现 gatekeeper 检查

## 执行步骤

1. **启动**：`bash .harness/workflow.sh start <feature-slug>`
2. **委托 ECC**：调用 `/orch-add-feature <用户需求>`，让其按 Research → Plan → TDD → Review 顺序执行
3. **状态同步**：ECC 产出每份文档后，立即回到主流程执行对应的 `set-artifact` + `advance`
4. **Gate 1（FR 后）**：present FR，等用户「确认」→ 才 `advance architecture`
5. **Gate 2（ADR 后）**：present ADR，等用户「确认」→ 才 `advance dev`
6. **Gatekeeper**：`bash .harness/gatekeeper.sh` 通过后 hook 自动调 `gate-pass` → `advance qa-review`
7. **Gate 3（RV 后）**：present RV，等用户「确认」→ 才 `advance pm-acceptance`
8. **PM 验收**：按 CLAUDE.md 的 pm-acceptance 规范，等用户「接受」→ 才 `bash .harness/workflow.sh complete`

## 关键约束

- **ECC 流程内嵌的 GATE 1/2 由主流程的 Gate 1/2/3 覆盖**——不要让 ECC 在内部又问一次"确认"。统一在 harness 阶段切换点询问。
- **agent 调用规范**：调用 `requirement-analyst` / `architect` / `qa-reviewer` / `pm-planner` 时，按 CLAUDE.md 规定注入 spec 全文，不要简化。
- **语言专属 reviewer 由 ECC 自己选**：`/orch-add-feature` 会自动检测项目语言，调用对应的语言 reviewer，本 command 不需要硬编码。
- **`workflow.sh complete` 必须由主流程执行**，不得委托给 `pm-planner` agent（见 CLAUDE.md）。

## 与 /orch-add-feature 的区别

| | `/orch-add-feature` | `/team-orch-add-feature` |
|---|---|---|
| 状态持久化 | ✗ 仅 in-session | ✓ `.workflow-state.json` |
| 产物文件 | ✗ 不写 FR/ADR/RV | ✓ 强制生成并索引 |
| Push 拦截 | ✗ 无 | ✓ `check-phase.sh` |
| Gatekeeper 安检 | ✗ 无 | ✓ `gatekeeper.sh` |
| PM 验收 | ✗ 无 | ✓ `pm-planner` 对照 FR |
| 团队可见性 | 低（对话流） | 高（文件 + state） |

适用：团队协作、跨会话开发、需要审计跟踪的场景。
单人探索、短期 PoC 直接用 `/orch-add-feature` 即可。
