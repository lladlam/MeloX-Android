# Plugin API v1：当前协议与接入关系

## 实现状态

此文描述当前源码，不代表真机已验证。Manifest 支持核心、目录、音乐库、收藏、下载及认证能力，
完整方法和 UI 接线见 [能力与认证接入](05-capabilities-and-authentication.md)。不能用空实现冒充支持。

```text
第三方 API / 授权测试服务
  → 插件默认导出对象（全部平台适配）
  → SDK 打包生成 globalThis.meloxPlugin
  → PluginIsolatedService（QuickJS + 独立 UID）
  ↔ PluginRuntime ↔ PluginHttpBroker / PluginStorage
  → PluginMusicProvider（标准字段校验与类型映射）
  → MusicProviderRegistry / 共享 DynamicMusicProviders
  → SearchScreen / ProviderPlaybackCommands / MeloXProviderLyricsLoader
  → ProviderPlaybackResolver → PluginMediaDataSource → Media3
```

宿主不读取第三方平台的 songmid、hash、privilege、VIP 字段；只接受 SDK 标准数据。
平台错误到标准错误/播放不可用状态的转换也由插件完成。

## 协议契约

- `plugin-sdk/schema/manifest.schema.json`：Manifest 结构。
- `plugin-sdk/schema/protocol-v1.schema.json`：搜索、歌曲、音质、播放、歌词结构。
- `plugin-sdk/src/`：TypeScript 接口与 `definePlugin`、`PluginError`。
- `PluginManifestParser` / `PluginResultValidator` / `PluginMusicProvider`：Android 校验与映射。
- `plugin-sdk/test/fixtures/manifest-cases.json`：SDK 与 Kotlin 共用的接受/拒绝用例。

当前没有从 Schema 自动生成全部 Kotlin/TypeScript 代码；完整模型的双端一致性验证仍待完成。
Manifest 用例一致性不能代表所有返回类型都已一致。

接口只传 JSON；不依赖 Android Bundle、MediaItem、CookieManager 或 Compose 类型，便于后续
Windows/Web 实现独立的 Host Context。当前仓库不提供这两个平台的运行时。

## Manifest

参考完整样例：`plugin-sdk/examples/test-tone/manifest.json`。

| 字段 | 约束 |
| --- | --- |
| `id` | 稳定的小写分段 ID，如 `org.example.music`；宿主添加 `plugin:` 命名空间 |
| `name` | 非空，最多 120 字符 |
| `version` | SemVer，最大 80 字符；当前不接受 `+build` 元数据 |
| `apiVersion` | 数字 `1`，不接受字符串或其他版本 |
| `entry` | ZIP 内相对 `.js` 路径，无绝对路径、反斜杠、`.`/`..` |
| `capabilities` | 去重数组，完整枚举和必需方法见 Manifest Schema 的 `x-capability-methods` |
| `networkDomains` | 精确的小写 ASCII 域名，不允许通配、IP literal、端口或协议 |

合法 ID 不等于已安装、可信或启用。来源身份不会因插件被卸载而重写为内置来源。

## 调用签名

```ts
search(query, page, pageSize, host): Promise<MusicPage<MusicTrack>>
resolvePlayback(track, quality, host): Promise<PlaybackResolution>
lyrics(track, host): Promise<LyricsResult>
```

`host` 总是最后一个参数。每次调用重新加载入口；插件不能依赖上次调用留下的全局变量。
额外未声明的方法不会自动成为宿主 UI 的能力。

## 数据规则

歌曲必须有 `id`、`title`、1–32 个带 `name` 的 `artists`。资源 ID 是不透明字符串，最大
4096 UTF-8 字节。宿主将歌曲、歌手及专辑 ID 绑定到声明插件的 `SourceId`，插件不能指定
另一个来源。SDK 不传 Android 的 `ProviderTrackMetadata`。

分页从 1 开始，每页 1–100 条；响应 `page/pageSize` 必须和请求一致，`items.length` 不能
超过请求页长，`hasMore` 必须是布尔值。插件浏览器支持上下页；跨源聚合保留每源分页元数据，
聚合搜索 UI 支持上下页，每源 total/hasMore 单独保留。

`PlaybackResolution` 的 `type` 支持 playable、preview、loginRequired、subscriptionRequired、
regionRestricted、copyrightRestricted、unavailable。playable 的 `headers` 映射到宿主
`requestHeaders`；`quality` 请求参数和 `actualQuality` 返回值意义不同。URL 使用绝对 HTTPS，
域名须在权限列表中；过期 URL 拒绝使用。不支持 DRM 或自定义 Media3 实现。

歌词的 `timeMs` 是从歌曲开头计算的毫秒值，必须非递减；最多 2000 行。`text`、翻译和
罗马音由插件转换。当前无逐字 transport，不能把行级歌词声称为精确逐字数据。

当前图片请求尚未经过插件网络代理，Provider 会抑制插件封面，避免未经权限校验的 Coil 请求。
标准 `total` 已保留到 MusicPage，目录详情的轨道页也保留总数。

## 错误

SDK 的 `PluginError` 提供稳定 code。JS 拒绝会返回错误码；未知异常转换为 PLUGIN_ERROR，
原始异常堆栈和可能包含 Token 的信息不会写入全局日志。宿主当前将这些码包装成调用失败，
尚未提供全部错误码对应的本地化 UI。

## 额外能力

目录/音乐库/收藏/下载/认证的方法、Manifest 及标准返回类型见
[能力与认证接入](05-capabilities-and-authentication.md)。宿主检查必需方法后才发布 Provider；
UI 按声明隐藏未提供的栏目和操作。全部平台字段转换依然由插件完成。
