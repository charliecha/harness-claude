# harness-claude

本仓库是 `harness-scaffold` 的**演示项目**，展示如何将六阶段 AI 工程工作流引入一个真实项目（Python 后端 + Android LLM App）。

## 快速引入 harness-scaffold

### 全新项目

```bash
# 1. 把 scaffold clone 为 .harness/
cd /path/to/your-project
git clone https://github.com/charliecha/harness-scaffold.git .harness

# 2. 初始化（语言必填，可选项目名和覆盖率阈值）
bash .harness/init.sh --lang=python --name=my-app

# 3. 完成。项目根下会新增：
#    .harness/                    ← 框架本体（不要手改）
#    .harness-config.json         ← 项目配置（language / coverage_threshold）
#    .claude/                     ← Claude Code 配置（hooks + agents + skills）
#    .workflow-state.json         ← 工作流状态机（phase=idle）
#    CLAUDE.md                    ← 项目级 AI 工程准则
#    scripts/smoke_test.sh        ← 冒烟测试占位符（exit 0，项目自行实现）
#    docs/{requirements,architecture,reviews}/INDEX.md
```

支持的语言 pack：`go` / `python` / `kotlin`（其余见 `.harness/packs/`）

### init.sh 参数

| 参数 | 必填 | 说明 |
|---|---|---|
| `--lang=<language>` | ✅ | 语言 pack，必须在 `.harness/packs/` 下存在 |
| `--name=<project-name>` | | 项目名，写入 CLAUDE.md 标题；默认取当前目录名 |
| `--coverage=<n>` | | 覆盖率阈值（%），默认 80 |
| `--force` | | 覆盖已存在的目标文件 |

### 升级已有项目

```bash
# 一次性：添加 scaffold 远程
git remote add harness-scaffold https://github.com/charliecha/harness-scaffold.git

# 每次升级
git fetch harness-scaffold
git subtree pull --prefix=.harness harness-scaffold main --squash
```

升级只同步 `.harness/` 目录，不影响 `.harness-config.json`、`.claude/`、`CLAUDE.md`、`.workflow-state.json`。

---

## 六阶段工作流

两种使用方式，效果等价，选其一即可。

### 方式一：AI 全自动（推荐）

在 Claude Code 中输入：

```
/team-orch-add-feature <功能描述>
```

Claude 会自动驱动全部六个阶段——产出需求文档、架构决策、编写代码、执行 gatekeeper 安检、QA 审查，直到 PM 验收。每个关键节点（FR / ADR / PM 验收）会暂停等待人工确认后再继续。

### 方式二：手动逐步推进

```bash
# 启动新功能
bash .harness/workflow.sh start <feature-name>

# 查看当前状态
bash .harness/workflow.sh status

# 依次推进各阶段
bash .harness/workflow.sh advance architecture   # requirements → architecture
bash .harness/workflow.sh advance dev            # architecture → dev
bash .harness/workflow.sh advance gatekeeper     # dev → gatekeeper
bash .harness/gatekeeper.sh                      # 安检（通过后自动 gate-pass）
bash .harness/workflow.sh advance qa-review      # 需 gatekeeper_passed=true
bash .harness/workflow.sh advance pm-acceptance  # 需 review 产物存在
bash .harness/workflow.sh complete               # 完成
```

### 阶段与产物

| 阶段 | 产物 |
|---|---|
| requirements | `docs/requirements/FR-XXX.md` |
| architecture | `docs/architecture/ADR-XXX.md` |
| dev | 源码 + 测试（build + test PASSED） |
| gatekeeper | exit 0（自动推进） |
| qa-review | `docs/reviews/RV-XXX.md` |
| pm-acceptance | 验收结论（用户确认后 complete） |

完整文档见 [.harness/README.md](.harness/README.md)。

---

## 本仓库内容

- `server/` — Python FastAPI 后端（加密报价、接口统计、健康检查）
- `android-llm-app/` — Android LLM 推理 App（llama.cpp + Kotlin + Compose）
- `docs/` — 需求（FR）/ 架构决策（ADR）/ QA 审查（RV）文档
- `.claude/rules/ecc/` — ECC 多语言编码规则（19 个语言/领域目录）
