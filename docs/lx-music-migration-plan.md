# LX Music 音源接入规划

[English](../README.en.md)

对比对象：`lx-music-mobile`（`/项目/lx-music-mobile`）与 MeloX 现有 `core/provider/lxuser`。

## 现状对比

| 能力 | lx-music-mobile | MeloX 现状 |
| --- | --- | --- |
| 运行时 | 原生 JS 沙箱（`loadScript`），受限 `global.lx` API | QuickJS（`LxUserRuntime`），受限环境 |
| 脚本元数据 | 强校验 `userApi.name/description/version/author/homepage/sources` | `LxUserScriptMetadata` 基本解析，`sources` 未强校验 |
| 音源声明 | `sources` 显式声明每个源（kw/kg/tx/wy/mg/xm）支持的 `type` | 不读取声明，逐源盲试 |
| 质量协商 | 按脚本声明的 `type` 请求，`qualityList` 全局暴露 | `lxQualityFallbacks` 盲降级（flac24bit→flac→320k→128k） |
| 请求能力 | `lx.request` 支持 method/headers/body/form/timeout/二进制 | QuickJS 桥接的 `lx.request`，能力需核对 |
| 脚本过期 | `expirationTime` 提示 + 更新提醒开关 | 无 |
| 失败切换 | `allowToggleSource` 自动换源/换脚本重试 | 单脚本失败即失败 |
| 请求去重 | 同请求合并为单个 promise（`reqPromise`） | 无 |
| 日志 | `lx.log.*` 开关式日志流 | `Log.w` 分散、无开关 |
| 网易兜底 | 官方 musicSdk 各源独立 | `resolveViaNeteaseMatch` 模糊匹配（可能错版本） |

## 主要缺陷

1. **音源/质量未按脚本声明协商**：MeloX 对每个源盲试，脚本不支持时浪费请求、也可能拿到错误音质。
2. **无失败自动换源**：单个脚本失败后不再尝试其它脚本或其它源（官方会 toggle source）。
3. **网易兜底匹配过宽**：仅按标题+时长匹配，可能选中错误版本（live/翻唱）。
4. **脚本过期无提醒**：`expirationTime` 被忽略。
5. **无请求去重**：同一首歌并发请求会重复调用脚本。
6. **日志不可控**：无法在设置中开启/关闭脚本日志。

## 修复规划（按优先级）

1. **读取 `sources` 声明**（高）：导入/加载脚本时解析 `userApi.sources`，播放时只请求声明支持的源与 type；未声明则维持现状兜底。
2. **失败自动换源**（高）：`LxUserPlaybackResolver.resolve` 改为遍历「声明支持该源的脚本」×「质量降级表」，失败继续下一个组合，而不是单脚本单次。
3. **请求去重**（中）：同一 `(script, source, quality, id)` 合并为一次调用。
4. **网易兜底收紧**（中）：匹配时优先 artist 精确 + 时长 ±3s + 排除非 live/cover 标题。
5. **脚本过期提醒**（低）：读取 `expirationTime`，过期时在音乐服务页提示。
6. **日志开关**（低）：设置页增加 LX 脚本日志开关，统一经 `lx.log` 收集。
