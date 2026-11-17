# 验证记录

日期：2026-11-17。环境：JDK 17、Android SDK 36、Android API 36.1 模拟器。

## 本地构建与测试

```bash
./gradlew -p android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

- 40 项单元测试通过，0 失败。
- Android Lint：0 error；仍有依赖新版本、WebView JavaScript 使用和 KTX 写法等建议。登录页需要 JavaScript；未增加原生 JavaScript bridge，也未将日志输出设为默认开启。
- Debug APK 构建成功，可安装。
- `v0.1.0` Release APK 构建成功，启用 R8/资源压缩，并使用用户指定的 IIDX 共享签名。
- `apksigner verify` 检查通过，Debug / Release 的证书 SHA-256 均与 IIDX keystore 的证书一致；APK manifest 为 `versionName=0.1.0`、`versionCode=1`，Release 未开启调试模式。签名目录已被 Git 忽略。

测试覆盖 JSON 提取与异常格式、SP/DP 槽位、参考定数缺失语义、单谱面 BPM 覆盖、长按匹配、停拍与延迟边界、时间轴双向换算、gRPC 帧/trailer/截断、未知 protobuf 字段、Skill 90 张分组、失败成绩排除、同分排序、重复成绩、未知曲目保留和曲名匹配。

曲绘专门验证：重复读取、仓库对象重建后读取、20 个同 ID 并发请求均只下载一次；失败可重试；无效图片不写入缓存；非法 ID 不能逃出缓存目录。

## 公开数据检查

使用 `tools/fetch_public_fixtures.py` 获取快照后运行 `:app:auditSources`：

- 3icecream：1,534 首曲目、11,549 张谱面（含删除曲）。
- BJMania MDX 公共数据库：1,461 首曲目，1,447 个 3icecream 曲名匹配到唯一 BJM 记录。
- SP / DP、Lv.1–19 共 38 个难度页均可解析，曲目等级与曲库一致。
- 4 首真实曲目的 34 张 SP/DP 谱面均可解析，共 15,835 个音符对象；包含恒定 BPM、密集变速、停拍、长按和 Shock 样本。

这些数量是当日源数据快照，不是永久覆盖率，也不表示未收录的谱面一定不存在。

首次 GitHub Actions 在 SDK 安装阶段因旧的 `tools` 包已不可用而失败，尚未进入编译。工作流已改为只在 setup-android 阶段安装 `platform-tools`，随后显式安装 Android 36 SDK。

## 模拟器检查

- 首次联网同步完成，默认稼动曲列表显示 SP 1,484 首、DP 1,483 首。
- 曲绘实际下载到应用私有目录，列表与播放器可显示。
- `#OurMemories` SP EXPERT 显示 400 个音符对象，DP EXPERT 显示 420 个音符对象；播放进度正常推进，四轨/八轨及长按正确呈现。
- 进度条定位后继续播放，能够显示对应位置的音符。
- 关闭模拟器 Wi-Fi 和移动数据、强制停止并重新启动，曲库、DP 选择和已有曲绘可恢复。
- 通过注入明确标为 `UI TEST FIXTURE` 的本地合成成绩检查成绩与 Skill 界面：Lv.12 / Flare IX 显示 Skill 785、WHITE 1/30、总对象数 1/90、等级 NONE +；该数据不来自真实账号。

## BJMania 登录后同步失败修复

- 用户在模拟器中亲自登录后复现：身份验证与成绩请求均返回 HTTP 200，但应用因 protobuf 消息中的 `status=1` 拒绝写入成绩，界面仍显示「尚未同步账户」。
- 原因是把消息的 `status` 误当成「非零即失败」的返回码。已对照 BJMania 当前公开前端：`getDdrScores()` 直接读取 `ddr_scores`，不使用该规则。现已移除错误拦截，HTTP、gRPC trailer 和 protobuf 边界校验继续生效。
- 新增合成回归用例，覆盖 `status=1` 与 `grpc-status=0` 的成绩响应，以及 `status=1` 不能掩盖 gRPC 认证失败；没有把真实账号资料或成绩保存到测试 fixture。
- 修复后复用同一登录会话成功同步，成绩和 Flare 写入本地快照，SP / DP 的 Skill 与 IN TARGET 均显示对象谱面；覆盖安装后的会话可恢复。此项验证不等同于与网站逐条对账。
- 将同签名、启用 R8 的 Release APK 覆盖安装到模拟器后再次同步成功，设置页同步时间更新；该 APK 将作为 Draft Release 附件。

## 难度显示、曲库样式与播放器调整

- 曲库、曲目详情、成绩和播放器共用难度组件，整数与两位小数同基线显示，小数使用较小字号。验证 `.05` 前导零、`.00` 和缺少定数时只显示整数。
- 曲库采用 IIDX 的浅色紫色主题、侧边菜单、折叠筛选和紧凑卡片，同时保留缓存曲绘。模拟器核对 SP 五难度排列、Flare 罗马数字左下角、评级右下角，以及没有 Flare 时不显示角标。
- 在手机宽度和宽屏宽度下测试 DP 八轨相邻中心距离一致，包括左右板交界；SP 轨距与 DP 相同，四轨整体居中。
- 普通音符在判定时刻隐藏，长按头在持续区间固定于判定线，尾部结束后隐藏；测试停拍与延迟时相同 beat 对应的不同判定时刻，以及向前拖动后音符恢复。
- 模拟器使用真实 SP / DP EXPERT 谱面检查播放、长按、Reverse 和固定流速；判定线后的已过音符不再继续滑出。

## 尚未实测

- BJMania 会话自然过期后的恢复、其他账号的私人数据权限，以及个人 Skill/IN TARGET 与网站逐项一致性。
- 连续运行 24 小时后的系统后台任务实际触发时间，以及不同厂商省电策略下的调度。
- 真实手机的长时间播放性能、所有源站谱面及真实手机上的安装升级。

源码内的测试数据和以上 UI fixture 不能替代这些验证。
