# AGENTS.md

给在本仓库工作的编码助手的约定。项目进度、环境与待办见 `docs/progress.md`，产品规格见 `specs/`。

## 发版

用户说「发版」时，打 tag 并推送，不用再确认。CI 见到 tag 会跑单测、构建并发布正式版（`.github/workflows/release.yml`）。

1. tag 为 `v` + `versionName`（如 `v0.1.0`）。
2. 版本号按语义化版本（SemVer），看自上个版本 tag 以来的提交：
   - 有不兼容改动（如数据库无法迁移、数据格式不兼容）升主版本号；1.0 之前改升次版本号。
   - 有新功能（`feat`）升次版本号。
   - 只有修复、性能优化等（`fix`、`perf`）升修订号。
   - 只有文档、CI、测试改动时，先问用户是否还要发版。
3. 每次发版 `versionCode` 加一。改 `app/build.gradle.kts` 的版本号并提交后再打 tag。还没有版本 tag 时，首个版本直接用当前的 `versionName` 与 `versionCode`，不改。
4. 推送 main 与 tag 后跟进 CI 结果。

## 提交信息

- 首行是简短摘要，中文约 25 字以内、整行不超过约 50 个字符，免得被折叠；沿用 `type(scope): 摘要` 前缀（如 `fix(notification):`、`ci:`、`docs:`）。
- 首行只写一件事；细节、原因空一行后写在正文，多项用「- 」分条。首行写不下，就把细节挪进正文或拆成几个提交。
