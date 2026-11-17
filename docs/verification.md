# 验证记录

日期：2026-11-17。环境：JDK 17、Android SDK 36、Android API 36.1 模拟器。

## 本地构建与测试

```bash
./gradlew -p android :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

- 33 项单元测试通过，0 失败。
- Android Lint：0 error；仍有依赖新版本、WebView JavaScript 使用和 KTX 写法等建议。登录页需要 JavaScript；未增加原生 JavaScript bridge，也未将日志输出设为默认开启。
- Debug APK 构建成功，可安装。
- Release APK 构建成功，启用 R8/资源压缩；输出未签名，不表示已完成正式签名发布。

测试覆盖 JSON 提取与异常格式、SP/DP 槽位、参考定数缺失语义、单谱面 BPM 覆盖、长按匹配、停拍与延迟边界、时间轴双向换算、gRPC 帧/trailer/截断、未知 protobuf 字段、Skill 90 张分组、失败成绩排除、同分排序、重复成绩、未知曲目保留和曲名匹配。

曲绘专门验证：重复读取、仓库对象重建后读取、20 个同 ID 并发请求均只下载一次；失败可重试；无效图片不写入缓存；非法 ID 不能逃出缓存目录。

## 公开数据检查

使用 `tools/fetch_public_fixtures.py` 获取快照后运行 `:app:auditSources`：

- 3icecream：1,534 首曲目、11,549 张谱面（含删除曲）。
- BJMania MDX 公共数据库：1,461 首曲目，1,447 个 3icecream 曲名匹配到唯一 BJM 记录。
- SP / DP、Lv.1–19 共 38 个难度页均可解析，曲目等级与曲库一致。
- 4 首真实曲目的 34 张 SP/DP 谱面均可解析，共 15,835 个音符对象；包含恒定 BPM、密集变速、停拍、长按和 Shock 样本。

这些数量是当日源数据快照，不是永久覆盖率，也不表示未收录的谱面一定不存在。

## 模拟器检查

- 首次联网同步完成，默认稼动曲列表显示 SP 1,484 首、DP 1,483 首。
- 曲绘实际下载到应用私有目录，列表与播放器可显示。
- `#OurMemories` SP EXPERT 显示 400 个音符对象，DP EXPERT 显示 420 个音符对象；播放进度正常推进，四轨/八轨及长按正确呈现。
- 进度条定位后继续播放，能够显示对应位置的音符。
- 关闭模拟器 Wi-Fi 和移动数据、强制停止并重新启动，曲库、DP 选择和已有曲绘可恢复。
- 通过注入明确标为 `UI TEST FIXTURE` 的本地合成成绩检查成绩与 Skill 界面：Lv.12 / Flare IX 显示 Skill 785、WHITE 1/30、总对象数 1/90、等级 NONE +；该数据不来自真实账号。

## 尚未实测

- 真实 BJMania 账号登录、会话过期恢复、私人数据权限和个人 Skill/IN TARGET 与网站逐项一致性。
- 连续运行 24 小时后的系统后台任务实际触发时间，以及不同厂商省电策略下的调度。
- 真实手机的长时间播放性能、所有源站谱面及正式签名安装升级。

源码内的测试数据和以上 UI fixture 不能替代这些验证。
