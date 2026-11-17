# DDR Chart Viewer

一个使用 Kotlin 和 Jetpack Compose 实现的原生 Android DDR 谱面浏览器，参考 [IIDX Chart Viewer](https://github.com/HarroYuz/iidx-chart-viewer) 的结构、登录方式和本地播放器体验。

当前版本：`0.1.0`（GitHub Release 标签：`v0.1.0`）。

## 功能

- **曲库**：从 [3icecream 难度表](https://3icecream.com/difficulty_list/15) 及其共享曲目数据解析曲名、别名、版本、删除状态、等级与参考定数，覆盖 SP / DP、Lv.1–19。
- **参考定数**：显示原站的「等级 + tier」，例如大号 `11` 与同一基线的小号 `.05` 合为 `11.05`；`tier = 1` 或未收录时只显示整数等级。小数只是社区难度参考，不是官方定数，也不参与 Skill 计算。
- **曲库界面**：沿用 IIDX 的浅色紫色主题、侧边菜单与紧凑卡片，同时保留曲绘。难度块左下显示 Flare `I–IX / EX`（没有 Flare 时留空），右下显示已有成绩的评级。
- **BJMania**：应用内 WebView 登录，同步 DDR 分数、评级、Clear、达成时间、Flare 等级。登录会话沿用参考项目的持久 Cookie、CSRF 与一次认证恢复机制。
- **Skill**：复现 BJMania 的 Flare Skill 对照表、Skill 等级、年代分组、对象曲选择与下限；支持 ALL / IN TARGET / NOT IN TARGET 筛选。SP、DP 独立统计，每种模式最多 90 张对象谱面。
- **播放器**：原生 Canvas 渲染 4 / 8 轨道，支持 BPM 变化、STOP、DELAY、长按、Roll、Shock、播放/暂停、进度拖动、谱面拖动、前后小节、Hi-Speed、慢速播放、Mirror、Reverse、DP Flip 与固定流速。
- **播放反馈**：普通音符到达判定线时消失并短暂点亮判定箭头；长按头固定在判定线，尾部结束后消失。DP 八轨等间距，左右板间不增加空隙；SP 使用相同轨距的居中四轨。
- **曲绘**：列表、曲目详情、已关联成绩和播放器显示曲绘。以曲目 ID 为键，首次成功下载后永久保存在应用私有文件目录，已有 ID 不再请求网络；每日曲库更新不会重取曲绘，同一 ID 的并发请求只下载一次。
- **本地使用**：曲库与成绩持久保存，谱面按需缓存；提供搜索、等级筛选、收藏和删除曲开关。

## 安装与使用

需要 Android 8.0（API 26）或更新版本。首次打开时联网获取曲库；以后可以直接使用已缓存的数据。

1. 点击顶部 SP / DP 按钮切换模式；搜索曲名，或展开搜索栏旁的筛选选择等级、收藏与删除曲。
2. 点击曲目打开详情，点击难度卡片播放对应谱面。
3. 通过左上角菜单进入「成绩」或「设置」登录 BJMania。登录成功后自动同步，也可以手动再次同步。
4. 在「Skill」查看总分、等级、CLASSIC / WHITE / GOLD 汇总及对象曲。切换 SP / DP 会同时切换成绩与 Skill。

播放器不含歌曲音频，也不会下载音乐。原站缺少音符数据时会明确提示，并提供原站谱面和视频入口，不生成猜测谱面。暂不播放包含 WARP 或负 BPM / 负停拍的时序。

## 更新与缓存

| 数据 | 更新策略 | 位置 |
| --- | --- | --- |
| 曲库、等级、参考定数 | WorkManager 每 24 小时检查；启动时补查过期数据；支持手动刷新 | 应用私有文件 |
| BJMania 成绩、曲目库 | 登录成功或手动同步；同步完成后重算 Skill | 应用私有文件 |
| 谱面音符 | 打开时按需获取；缓存超过 24 小时尝试刷新；网络失败可继续使用旧谱面 | 应用缓存，最多约 100 MiB |
| 曲绘 | 按曲目 ID 首次获取，成功缓存后不再请求或校验更新 | 应用私有文件，无自动过期 |
| 收藏、SP/DP、流速 | 操作后保存 | 本机设置 |

Android 的省电策略可能延后后台任务，因此「每天更新」是周期调度和前台补查，不保证固定时刻执行。更新曲库会校验 SP / DP 的全部 38 个难度页，全部成功后原子替换快照；失败不覆盖旧数据。

曲绘失败会显示占位图，失败请求在当前进程中退避五分钟；失败的图片不会登记为已缓存。清除应用数据或卸载应用会同时移除曲绘缓存，之后需要重新下载。Android 的「清除缓存」不会移除已保存的曲绘。

## BJMania 数据规则

- 使用 `GetDdrScores` 的 DDR 字段，不沿用 IIDX 的 EX SCORE / MISS COUNT 含义。
- 成绩和 3icecream 的谱面编号均为 0–8：SP 为 0–4，DP 为 5–8。BJMania 音乐数据库的等级数组为 10 项，包含 DP-BEGINNER 占位；DP 成绩查等级时跳过该占位。
- Skill 使用 BJMania 的 **整数等级 / Flare 查表值**。CLASSIC 为 BJM `series <= 13`，WHITE 为 `14..17`，GOLD 为 `>=18`。
- 每个模式、每个年代按 Skill、分数、达成时间依次降序，在 `clear_kind >= 3` 的成绩中取前 30 张。完全同分同时间时以曲目 ID 和谱面槽位保持稳定顺序。
- 未知 Flare、缺少音乐数据库记录或未知等级保留在成绩列表中，但不猜测 Skill。相同谱面重复记录不会占用多个对象曲名额。
- 曲名经 HTML 实体解码、NFKC 和大小写/空白规范化后作唯一匹配；展示在谱面上的成绩还要求等级一致。模糊匹配不自动绑定，未关联成绩仍可查看。等级一致不能证明两个来源的音符编排完全相同。

Skill 与 IN TARGET 是按 BJMania 前端规则本地重建的结果，不是独立的官方 Skill API。规则来源、字段映射和已知边界见 [数据源说明](docs/data-sources.md)。

## 构建

需要 JDK 17、Android SDK 36。可使用 Android Studio 打开 `android/`，或设置 `ANDROID_HOME`；也可以创建不提交的 `android/local.properties`：

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

```bash
./gradlew -p android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

调试 APK：`android/app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 也会运行测试、Lint、构建并保存 APK artifact。

```bash
./gradlew -p android :app:assembleRelease
```

Release 启用 R8 和资源压缩。本项目复用 IIDX Chart Viewer 的签名：在本机通过安全方式将 IIDX 项目的 `android/signing/` 复制到本项目相同目录，保留 `signing.properties` 和其引用的 keystore。存在此配置时，Debug 和 Release 均使用同一签名；不含配置的环境中，Debug 使用本机调试签名，Release 输出未签名 APK。

已配置共享签名时，Release APK 位于 `android/app/build/outputs/apk/release/app-release.apk`。`android/signing/`、keystore 和本机 SDK 路径均被 Git 忽略，不能提交或上传到 Release；换机器构建同签名版本时需单独安全复制签名目录。GitHub Actions 不包含该签名，其 Debug artifact 仅用于验证。

## 验证公开数据

单元测试使用合成数据和 Flare 对照表，不需要账号。额外的来源检查只读取公开曲库、难度页和谱面，不读取个人成绩：

```bash
python3 tools/fetch_public_fixtures.py --output /tmp/ddr-public-fixtures
./gradlew -p android :app:auditSources -PsourceFixtures=/tmp/ddr-public-fixtures
```

公开快照不随源码提交；检查数量会随数据源更新而变化。验证记录见 [测试记录](docs/verification.md)。

## 项目结构

```text
android/app/src/main/java/com/harroyuz/ddrchartviewer/
├── app/              # ViewModel、应用入口、每日后台任务
├── data/
│   ├── local/        # 原子快照与按 ID 持久保存的曲绘缓存
│   └── remote/       # 3icecream 解析器、BJMania 会话与 protobuf
├── domain/           # 谱面模型、时间轴、Skill、跨来源曲目匹配
└── ui/               # Compose 曲库、成绩、Skill、曲绘、登录与播放器
```

## 数据与许可

源代码沿用 IIDX Chart Viewer 的 [GNU GPL v3 LICENSE](LICENSE)。参考项目的 Gradle wrapper 和 BJMania 会话实现经过复用与适配。

曲名、曲绘、谱面、成绩及第三方网站内容属于各自权利人，不因源码许可证而改变许可。应用运行时从 [3icecream](https://3icecream.com) 与 [BJMania](https://u.bjmania.com) 读取所需数据，不将个人成绩上传到其他服务；关闭了系统云备份和设备迁移数据提取。退出登录会清除应用中的成绩快照与 BJMania 会话，公开曲库、曲绘和收藏保留。

项目地址：[HarroYuz/ddr-chart-viewer](https://github.com/HarroYuz/ddr-chart-viewer)。
