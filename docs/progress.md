# 开发进度

自用取件码 Android 应用（包名 `app.pickup`），2026-10-22 起步。规格先行，见 `specs/`。

> 最近更新：2026-10-22（新短信即时通知，撤掉短信应用的重复提醒）

## 环境

- 构建用 JDK 21：`JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.12.1/libexec/openjdk.jdk/Contents/Home`。
- 直接用 Compose Material3 预发布版（1.5.0-beta02，含 Expressive）。它与 hilt-lifecycle-viewmodel-compose 1.4.0 要求 compileSdk ≥ 37；AGP 9.4.1 要求 Gradle ≥ 9.6（当前 9.8.2）。
- 图标：material-icons-extended 1.7.8（完整图标集），release 包靠 R8 裁剪（2026-10-22 定）。
- 导航：Navigation 3 1.2.0 + lifecycle-viewmodel-navigation3；lifecycle 统一 2.11.0；kotlinx-serialization 1.12.0（NavKey 序列化）。
- 测试机：Pixel 2 XL（LineageOS，API 35）。
- release 包：R8 混淆不需要额外保留规则。NavKey 序列化靠类名反射，kotlinx-serialization 自带的规则已保留 `INSTANCE` 与 `serializer()`，类名在同一个包内一致；真机已验证设置页在进程被杀后能恢复（2026-10-22）。签名读 `keystore.properties` 或 CI 环境变量，本地没配时退回 debug 签名。
- 发版：GitHub Actions（`.github/workflows/release.yml`）只在打 tag 或手动触发时运行，先跑单测；打 tag 发正式版，手动触发把所选分支构建为预发布的 latest。推送到 main 不触发（2026-10-23 定）。
  - **用户说「发版」时**：打 tag 并推送（2026-10-23 定）。tag 为 `v` + `versionName`（如 `v0.1.0`）；这个 tag 已存在时，先把 `app/build.gradle.kts` 的 `versionName` 末位加一、`versionCode` 加一并提交，再打新 tag。推送 main 与 tag 后看 CI 结果。
- 版本管理：git，主分支 `main`，暂无远端（2026-10-22 初始化）。

## 架构

- NiA 风格的单向数据流：每页单一 UiState；仓库直接抛出异常，ViewModel 在动作协程里捕获；不设全局兜底提示。
- 导航在 `ui/PickupApp.kt`，每页一个 NavKey，配 ViewModel 装饰器。
- 取件清单的 ViewModel 在导航时不销毁，所以去其他页面前调 `leave()` 重新归类（pickup_list §7）。
- 不再内置示例数据（2026-10-22 去掉）：清单里只有从短信解析出的包裹。
- 短信扫描：`SmsInbox`（ContentResolver 读收件箱）→ `PickupSmsParser`（纯规则解析）→ `ParcelDeduplicator` → `SmsScanRepository` 在一个事务里入库并推进 `scan_cursor`。数据库 v2，用 AutoMigration 从 v1 升级。
- 短信权限放在界面层：`PickupListRoute` 在 `LifecycleStartEffect` 里检查权限，并请求权限或静默扫描；横幅状态不进 UiState。
- 新短信：`notification/SmsReceivedReceiver`（`SMS_RECEIVED` 广播）→ `MessageScanRepository.importReceivedSms` 解析去重入库 → `PickupNotifier` 发新包裹通知，再顺带 `scanSms()` 推进扫描位置。`notification/SmsNotificationListener`（通知使用权）撤掉默认短信应用为取件短信发的通知。

## 已实现

| 规格 | 范围 | 备注 |
|---|---|---|
| `specs/pickup_list.md` | 全部（通知来源除外） | 进入页面或回到前台时静默扫描；下拉刷新时扫描并重新归类，刷新态至少 700 毫秒；扫描失败有提示条；短信权限有横幅 |
| `specs/message_source.md` | 短信来源、解析、去重、权限、新包裹通知 | 2026-10-22 新写，数值已确认，用着有问题再调；§6 的接收器与通知监听没有单测，需真机验证 |
| `specs/settings.md` | 全部 | 清单顶栏齿轮 → 设置页，「危险操作 · 清空已取数据」 |

单测 51 条全部通过：清单 builder 6 条、清单 ViewModel 13 条、设置 ViewModel 5 条、短信解析 19 条、真实短信语料 2 条（21 个样例，取自 PickupCodeGrabber，MIT 许可）、去重 6 条。共享的假仓库在 `app/src/test/.../data/`（`FakeParcelRepository`、`FakeMessageScanRepository`）。`SmsScanRepository` 依赖 Room 和 ContentResolver，没有单测，需要在真机上验证。

## 界面定稿（2026-10-22）

细节已写进规格和代码注释，这里只列结论，以及试过又放弃的做法。

- **卡片**：取件码用 6 个小灰点遮挡，按钮用 primaryContainer tonal 样式（既有主色语义与对比度，又避免长列表中多个卡片重复实心 primary 造成视觉压迫，2026-10-22 改），底部一行次要信息（左「尾号 · 快递公司」，右到件日期，outline 色），卡片内不加分隔线。同组卡片用 Expressive 分段形状：首尾 16dp 大圆角，中间小圆角，2dp 窄缝。
- **揭码**：码始终参与测量，圆点叠放在上面，揭码前后卡片高度不变，不写死高度。动画只是一个不回弹的短交叉淡化。
- **首次加载**：骨架屏（`PickupListSkeleton`），按真实布局排出汇总、两组组头和 3 + 2 张分段卡片，高度与内容一致；占位块明暗呼吸，不用扫光（2026-10-22 定）。
  - 放弃：居中的 Expressive LoadingIndicator。
- **汇总**：一行「4 件待取」，数字用大字，不做数字滚动动画。
  - 放弃：右侧的说明文字「查看取件码即标记已取」，它和组头的「查看全部取件码」叠在同一侧，顶部显得杂乱（2026-10-22 去掉，DataStore 随之移除）。
- **组头**：地点名 +「（N 件）」，不加地点图标；地点名用 titleMediumEmphasized 强化分组层级（2026-10-22 改）；「查看全部取件码」是标准眼睛图标按钮（无底色，低强调，带 IconButtonDefaults.shapes()）。按 MD3 对齐线：左边文字、右边图标各贴一条 16dp 对齐线，眼睛图标右缘与卡片右缘、顶栏齿轮同一条竖线，触控区伸进留白，同 M3 顶栏（2026-10-22 定）。
  - 放弃：20dp 地点图标（与眼睛不对称，且是装饰）；地点图标放大到 24dp（太大）；浅色圆底的眼睛按钮（中等强调，与卡片按钮同档，每组一个会抢眼）。
- **顶栏**：M3 `enterAlwaysScrollBehavior`，往下滚收起、往上滚出现。内容滚到下方时，顶栏和状态栏是 85% 不透明的 surfaceContainer；列表直接用 Scaffold 的 padding 作为 contentPadding。
  - 放弃：Gmail 式「顶栏和内容一起滚进状态栏遮罩下面」，状态栏会闪一下。
- **下拉刷新指示器**：始终待在内容区内，随下拉淡入、放大并跟手下移一小段，使用 ContainedLoadingIndicator。
  - 放弃：默认的从顶栏下沿滑出（像从地下钻出来）；iOS 式整个列表下移（不采用）。
- **配色约定**：页面背景用 `surface`，卡片容器统一用 `surfaceContainerLow`（2026-10-22 定）；已取区通过整体透明度弱化（alpha = 0.72f）降低对比度，不再反向调换容器色。
- **形态族统一**：全局按钮与图标按钮（含顶栏、重试、确认对话框）均显式声明 `ButtonDefaults.shapes()` / `IconButtonDefaults.shapes()`，支持 Expressive 触控形变（2026-10-22 定）。
- **图标**：以瓦楞纸盒为灵感，选「立体纸箱」（2026-10-22 定）。启动图标是自适应图标：米色背景、三档牛皮纸色的三分之四视角纸箱加封箱胶带，并有单色层供主题图标使用；通知图标与单色层是同一个剪影。设计稿在 Figma 原型文件的「图标」页（组件「启动图标前景/E」「单色图标/E」），资源按设计稿导出的路径写成。
  - 放弃：包裹 + 遮码点、快递柜、取件码票签（第一轮）；正面封箱、开箱（第二轮）。
- Figma 原型已弃用，界面以真机为准。

## 待决 / 待办

- [x] 清单卡片容器统一为 `surfaceContainerLow`（符合页面 `surface`、卡片 `surfaceContainerLow` 配色约定）；已取区通过整体透明度弱化（alpha = 0.72f）降低对比度（2026-10-22 定）。
- [ ] 分组排序在主线程；数据量到上千件时再加 `flowOn(Dispatchers.Default)`。
- 短信解析：真机核对暂无问题；以后遇到识别错的短信，再把样例补进 `PickupSmsParserTest`。
- [ ] 真机验证新包裹通知（message_source §6）：来一条真实取件短信，确认只剩本应用一条通知、短信应用的提示音被停掉；三星信息若另发群组摘要通知，看是否残留。
- [ ] 催取短信现在不再提醒（被撤掉、去重后也不发通知），用一段时间后看是否需要对仍待取的包裹再提醒一次。
- [ ] 通知来源（淘宝、拼多多等 App 推送）：通知使用权和 `SmsNotificationListener` 已有，届时在其中加对这些 App 的解析。
- [ ] READ_SMS、RECEIVE_SMS 和通知使用权都是受限设置：用 adb/Gradle 安装没问题；以后如果从其他渠道侧载，可能需要先在系统设置里「允许受限设置」。
