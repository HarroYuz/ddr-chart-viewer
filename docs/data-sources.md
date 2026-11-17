# 数据源与实现边界

核对日期：2026-11-17。以下是当日公开页面及前端代码观察，不代表上游提供稳定 API 合约。

## 3icecream

- 页面：`https://3icecream.com/difficulty_list/{1..19}?spdp={0|1}`。
- 页面引用 `/js/songdata.js`，其中 `ALL_SONG_DATA` 包含曲目 ID、名称/别名、版本、删除状态、九项 `ratings` 和 `tiers`。
- 难度页的 `difficultyList` 键是 `song_id/difficulty`，`tier` 为显示的难度小数部分；`rating` 是另一项统计值，不用作定数。`tier = 1` 表示资料不足。
- `/ddr/song_details/{songId}` 公开引用 `/api/chart_info/{songId}`。返回 `notes[slot][measure][row]`、`BPMS`、`STOPS`、`OFFSET` 等字段，新格式还包含 `chartTiming[slot]`，单谱面时序优先于全曲时序。
- 四轨 / 八轨顺序是左、下、上、右，各四轨构成一侧。`1` 为普通音符，`2/3` 为长按起止，`4` 为 Roll 起点，`M` 作为 Shock 显示，`L/F` 为 Lift/Fake。
- 曲绘使用曲目页引用的 `/img/banners/f/{songId}.jpg`，按 `songId` 不过期缓存。列表、详情、成绩和播放器共享缓存。

解析器只提取和解析 JSON 容器，不执行下载的 JavaScript。缺少字段、模式错位、等级页重定向、音符轨道数不符、长按缺少尾部都会显式报错。

## BJMania

- 会话验证：`GET https://u.bjmania.com/api/auth/me`。
- 成绩：`POST /api/WebUI/GetDdrScores`，gRPC-Web protobuf，空消息请求有 5 字节帧头。
- 响应字段 1 为重复成绩记录，字段 2 为业务状态。成绩记录字段：1 music ID、2 note type、3 rank、4 clear kind、5 score、6 Unix 秒时间、7 flare。
- gRPC-Web 帧长、protobuf 边界与 trailer 状态均校验；业务非零状态不写入本地成绩快照。
- 公开版本表：`https://assets.bjmania.com/mdb/ver.json`，读取 `MDX.mdb` 中最大的版本号；数据库为 `/mdb/MDX_mdb_{version}.bin`。
- 数据库记录字段：1 mcode、2 basename、3 title、4 title_yomi、5 artist、6 series、7 diffLv。
- 核对前端构建：`main-OGPHD5HR.js`；DDR 成绩页面模块 `chunk-GSC5PKSO.js`；Flare 表及等级 `chunk-IKWVGF57.js`；MDB 结构 `chunk-TUP3DNQX.js`；API/成绩 protobuf `chunk-MJTKKJZ7.js`。这些文件名可能随网站发布变化。

`flare-skill.json` 保留当日 BJMania 对照表的数值，包括 Lv.2 中的非等差条目。应用复现该站展示规则，不擅自用公式修正，也不使用 3icecream 的参考定数替代。

## 限制

- 无音乐播放；JSON 的 `MUSIC` 文件名不被当作可下载的音频地址。
- 原站无谱面、暂不支持的 WARP 或负时序会显示提示和原站入口。
- 两个曲库可能更新时间不同或缺曲，唯一曲名匹配与等级检查不能代替谱面内容比对。
- 没有真实账户授权会话时，公开协议核对和合成测试无法证明个人账号登录、私人数据权限以及实际 Skill 总数与网站一致。
- 系统限制后台运行时，周期任务可能延迟；联网打开应用时会补查超过 24 小时的曲库。
