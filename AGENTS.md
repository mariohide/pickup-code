# AGENTS.md

项目文档的总入口。在本仓库工作的编码助手（Claude Code、Gemini 等）先读这里，其他文档都从这里找。产品行为以 `specs/` 为准；工作方式与约定以本文件为准。

## 项目

自用的取件码 Android 应用（包名 `app.pickup`）：从取件短信里解析取件码，按取件地点分组成待取清单，新包裹到时发通知。严格离线，不申请网络权限。开源，GPL-3.0。

## 文档

| 文档 | 内容 |
|---|---|
| `AGENTS.md` | 本文件：项目概况、工作约定、构建约束、架构主干、界面约定、发版与提交规范 |
| `README.md` | 给人看的项目介绍、特性与许可证 |
| `specs/pickup_list.md` | 取件清单页的规格 |
| `specs/message_source.md` | 消息来源（短信）、解析、去重、权限、新包裹通知的规格 |
| `specs/settings.md` | 设置页的规格 |
| `docs/progress.md` | 开发进度：已实现的规格、界面定稿（含试过又放弃的做法）、待办 |
| `docs/screenshots/` | README 截图。用演示数据拍：临时给 debug 包加 `.demo` 后缀、在数据库里写入假包裹，装成独立应用，不碰真实数据；拍完卸载并撤销改动 |
| `.github/workflows/release.yml` | CI：打 tag 或手动触发时跑单测并发版 |
| `LICENSE` | GPL-3.0 全文 |

## 工作方式

- 用中文交流；文档与代码注释也用中文，沿用现有写法。
- 规格先行：改行为先改 `specs/`，再改代码；规格里的决定注明日期。
- 进度、界面定稿与待办记在 `docs/progress.md`，不要只留在助手自己的记忆里。用户说「记录进度」时更新它。
- 改完界面：编译、跑单测、装到测试机并启动，由用户在真机上看。不主动截图、录屏或抽帧，截图读图很费 token；只有用户要求，或必须截图才能定位用户报的具体问题时才截。汇报时说清哪些没在真机上看过。

## 环境与构建

依赖版本见 `gradle/libs.versions.toml`，这里只记构建文件里看不出的约束。本机路径、测试机等个人环境不进仓库，各自记在不提交的本地文件里（Claude Code 用 `CLAUDE.local.md`）。

- 需要 JDK 21。
- 用 Compose Material3 预发布版（含 Expressive）：它与 hilt-lifecycle-viewmodel-compose 要求 compileSdk ≥ 37；AGP 9.4 要求 Gradle ≥ 9.6。
- 图标用完整的 material-icons-extended，release 包靠 R8 裁剪。
- 常用命令：
  - 单测：`./gradlew :app:testDebugUnitTest`
  - 装到测试机并启动：`./gradlew :app:installDebug && adb shell am force-stop app.pickup && adb shell am start -n app.pickup/.MainActivity`
  - 测试机上装的若是正式签名的版本（如从 GitHub Release 下载），`installDebug` 会因签名不符失败。不要卸载，卸载会清掉数据；改为本地用正式密钥打 release 包覆盖安装（见下）。
- release 包：
  - 签名读 `keystore.properties` 或 CI 环境变量，本地没配时退回 debug 签名。
  - R8 混淆不需要额外保留规则：NavKey 序列化靠类名反射，kotlinx-serialization 自带的规则已保留 `INSTANCE` 与 `serializer()`。
  - 手动装 release 包要用 `adb install --no-incremental`：默认的增量安装会跳过预编译，应用全程解释执行，动画明显卡顿。装好后启动一次，再 `adb shell cmd package compile -m speed-profile -f app.pickup` 按基线配置文件预编译。

## 架构

只记主干，细节看代码与规格。

- NiA 风格的单向数据流：每页单一 UiState；仓库直接抛出异常，ViewModel 在动作协程里捕获。
- 导航在 `ui/PickupApp.kt`（Navigation 3，每页一个 NavKey）。取件清单的 ViewModel 在导航时不销毁，去其他页面前调 `leave()` 重新归类（pickup_list §7）。
- 短信进清单有两条路：进入页面、回到前台、下拉刷新时扫描收件箱（`SmsScanRepository`）；新短信到达时由 `notification/SmsReceivedReceiver` 即时导入并发通知（`PickupNotifier`）。两条路共用 `PickupSmsParser` 解析与 `ParcelDeduplicator` 去重。
- `notification/SmsNotificationListener`（通知使用权）撤掉默认短信应用为取件短信发的重复通知。

## 界面约定

- 直接用 Material 3 Expressive：优先选 Expressive 组件与 MotionScheme，不必回避 `@ExperimentalMaterial3ExpressiveApi`。
- 克制：列表里重复出现的元素会把强调样式放大 N 倍。用户看过第一版后评价「眼花缭乱」。
  - 重复的卡片内控件用 tonal、text 按钮和低对比色。
  - 不加纯装饰的图标、分隔线、自明的标签。
  - Emphasized 字体只留给全屏唯一的重点（如待取件数）。
- 按钮口径（全应用一致，2026-10-23 定）：按点了之后会不会离开本应用来定。在本应用里完成的（含弹系统权限窗）写「开启」等动词；跳到系统设置等本应用以外页面的写「去设置」，后面带外跳图标 `Icons.Rounded.ArrowOutward`，句中链接同样带这个图标。不写「去开启」：「去」暗示跳转。
- 配色：页面背景用 `surface`；取件卡片容器用 `surfaceContainerLow`，已取区靠整体透明度弱化，不换容器色；设置页分段列表项容器用 `surfaceContainer`（`SegmentedListItem` 默认是 `surface`，要显式指定）。不靠改页面底色制造对比。
- 界面以真机为准。Figma 文件（https://www.figma.com/design/o202auUlAwNL6V1wD8H2mQ ）里：「图标」页是启动图标与通知图标的设计稿；「首次引导」页是该功能实现前的原型（2026-10-23），实现后以真机为准。已实现的界面不在 Figma 里维护，不要去同步。

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
