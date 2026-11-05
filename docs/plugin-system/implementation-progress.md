# 插件系统实施进度（2026-11-05）

保留前序审计与架构方案。当前进入核心接线及集中验证阶段，**整套插件系统尚未完成**。

## 已编写并接线

- `SourceId` 成为 `MusicResourceId` 的主身份，保留内置来源构造器；动态/缺失来源不回退网易云。
- `MeloXTrackJson`、MediaItem extras/ID、Provider 队列 URI、播放历史记录及下载索引接入动态 ID。
- 收藏合并保留不可解码记录、未知来源记录及已知记录的顶层扩展字段；显式删除的插件收藏可以移除。
- Registry 动态注册/查询/启停/注销/替换，UI 与播放 registry 共用目录及 StateFlow generation。
- `PluginPackageStore → PluginManager → MeloXPlugins → Registry`；设置页 SAF 导入/更新/启停/卸载，应用启动异步恢复。
- `PluginRuntime → PluginIsolatedService`，独立 UID、QuickJS 内存/栈限制、Promise、HTTP/存储代理、watchdog、资源回收。
- `PluginHttpBroker` 在实际 HTTP 与媒体请求处限制域名/DNS 地址、重定向、代理、请求/响应体和数量。
- `PluginMusicProvider` 将核心及全部目录/音乐库/收藏/下载标准结果映射到既有 Capability 和模型，不实现第三方平台转换。
- SearchScreen 插件选择/搜索 → 现有结果行 → ProviderPlaybackCommands → ProviderPlaybackResolver → 插件专用 Media3 DataSource。
- 插件歌词接入现有 `MeloXProviderLyricsLoader`；封面走带来源的受控 Coil Fetcher，动态媒体绕过共享字节缓存。
- SDK TypeScript 类型、Manifest/核心协议 Schema、验证/打包 CLI、CC0 测试音插件及开发文档。
- SearchScreen 动态目录浏览器支持歌曲、歌单、专辑、歌手、榜单、推荐和用户音乐库，上下页及 total。
- 原生收藏状态/写入入口、按能力隐藏下载、插件下载的实际专用 DNS/HTTP 网络出口与配额/撤销。
- Keystore 独立插件/账号 AES-GCM 保险箱、API Token 导入、多账号切换/删除/注销、账号级普通 storage。
- 公开客户端 OAuth2/S256 PKCE 浏览器登录、一次性 state 校验、宿主 token 兑换/自动临期与手动刷新。
- HTTP 与媒体的 opaque credential reference，由宿主按固定凭据策略注入；升级变更策略不得复用旧授权。
- 显式跨源搜索白名单扩展到 SourceId，保留每源分页/总数和缺失插件身份；取消向上传播。
- 迁移歌曲操作、详情、下载/推荐列表、分享、CLI/LAN 等通用来源读取；LX/补源跳过动态输入。
- 主来源使用 SourceId 持久化及变更流；Home/Explore/Library/Search 根路由识别插件，服务中心和设置账号区域复用插件组件。
- 导入权限预览、升级扩权的 Manager 前置批准、卸载确认、无脚本日志正文的内存诊断。
- Messenger 发送 UID/调用 token/宿主配额复查，账号会话戳防 A→B→A 后旧请求恢复。
- 动态分享深链接保留 SourceId；媒体 open 的 401/403/410 最多重新解析一次；下载菜单和下载页显示错误，
  下载页支持会话内任务取消、重试和移除失败记录。

## 自动化验证

详见 [verification.md](verification.md)，其中保留前序 478 项结果与后续扩展验证记录。
当前最终 Debug 构建成功；501 项 Kotlin 测试，498 项通过、3 项跳过。专用 broker 实际取得 Range 206/416。

SDK 类型检查/构建、8 项 Node 测试已通过。Manifest 共用 fixture 和能力必需方法表在双端验证。
实际访问公开测试服务已取得 HTTP 200、2044 字节 WAV；最终 SDK 包已被编译后的 Kotlin
PluginArchiveReader 接受。这些检查不是 Android/Media3 端到端验证。

## 未完成

参考 [remaining-work.md](remaining-work.md)。完整 SourceId 迁移、旧 Activity 全路径统一、
发布者信任、流媒体、持久诊断及数据清理等仍未完成。动态身份保留旧版 built-in-only getter，未完成全仓库
所有路径迁移。没有连接 Android 设备，也未通过插件运行时隔离、死循环、Binder、OAuth/Keystore、
实际 Media3/歌词显示和安装升级的设备测试。不宣称系统完整或安全验证完成。
