# 插件系统实施进度（2026-11-05）

保留前序审计与架构方案。当前进入核心接线及集中验证阶段，**整套插件系统尚未完成**。

## 最新实现批次：先完成代码，再集中测试

当前 RC 检查点 `305801a` 已推送 main。推送后继续增加下载任务跨重启恢复、受强 ETag/
Content-Range 与插件包/账号会话/音质共同约束的渐进式断点续传、按来源持久诊断和主要插件
入口中文错误文案。下载完成文件与 `.part` 分离，临时文件含并发预留总限额1 GiB；
诊断不写入脚本日志正文和敏感请求数据。对应受控网络/文件验收代码已补齐，均未运行。

继续收拢账号/生命周期实现：支持原账号槽位重新授权；密文变更直接改变会话戳，CAS 拒绝旧写入；
已知失效状态由宿主收口。凭据媒体401/403触发最多一次刷新/重新解析，运行时关闭主动取消媒体连接
并唤醒 IPC 等待。新任务保存账号归属摘要并在实际媒体 Broker 创建时复查。插件账号表单和目录/
收藏组件以来源、认证策略或 Provider 为 Compose 身份，避免列表移位/切号继承旧实例状态。
此批次功能收拢后已进入集中验证：SDK 类型检查/构建及12项 Node 测试通过；Android 首轮发现
CANCEL 接线及错误文案导入缺失，已修正；第三轮 Debug/AndroidTest 构建成功，520项单测中
517通过、3跳过。API35故障子项/全字段设备子集与15组包提交/回滚强杀已取得通过证据。
后续真实UI复验又修复完整播放器标题菜单/音质/操作页/B站偏移/Lyricon中的动态.source遗漏，
以及冷启动Search来源chip未刷新；最新构建及520项单测通过（3跳过）。正常应用服务/controller、
pause/seek/next、队列、歌词Provider、通知、后台/熄屏runner通过，完整播放器/歌词/队列/SAF安装
及扩权更新取消/确认、冷启动chip恢复已有UI证据；公网OAuth/Range/媒体撤销等仍未执行。

按用户要求暂停构建和设备测试。以下新增实现、测试代码和执行脚本均为 **NOT_RUN**，
前轮 510 项 Kotlin/SDK/设备子集结果不能作为本批次回归证据：

- 修复正常播放器 `MeloXPlaybackUiState.refresh` 对动态来源读取 built-in getter 的崩溃；
  歌词绑定、预加载和缓存键继续迁移 SourceId，保留旧内置来源字段读取。
- 凭据 Lease 显式绑定 SourceId；存储 v2 按来源分目录、v1 回退迁移、删除账号可选清存储。
- 包/TOFU 提交增加预写恢复日志，保留旧 trust 快照；冷启动枚举恢复未完成事务，恢复错误可见。
- 启动恢复门闩接入动态播放解析；损坏启用状态可显式重置，保留包和用户数据。
- 媒体调用加入撤销集合，读取期间复查会话；OAuth 取消/重新开始会撤销正在兑换的网络请求。
- 下载索引合并保留无法解码行及未知字段；Track 保留未来质量/可用性枚举和类型专属 metadata 扩展。
- 全 Provider 方法统一通过 `PluginWireContract`；共享全字段 fixture、Kotlin/Schema 类型变异测试。
- 新增合成 OAuth HTTPS 服务、插件示例、真实 HTTPS 兑换 runner、资源耗尽 runner、
  包持久化边界强杀/恢复脚本、正常 MeloXPlaybackService/controller 验收代码和实体 ARM64 准备脚本。

实现与验收边界、真实应用崩溃及模拟器环境记录见 [verification.md](verification.md)。

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
当前最终 Debug/AndroidTest 构建成功；510 项 Kotlin 测试，507 项通过、3 项跳过。专用 broker 实际取得 Range 206/416。

SDK 类型检查/构建、10 项 Node 测试已通过。Manifest 共用 fixture 和能力必需方法表在双端验证。
实际访问公开测试服务已取得 HTTP 200、2044 字节 WAV；最终 SDK 包已被编译后的 Kotlin
PluginArchiveReader 接受。这些检查不是 Android/Media3 端到端验证。

## 未完成

参考 [remaining-work.md](remaining-work.md)。完整 SourceId 迁移、旧 Activity 全路径统一、
信任撤销、流媒体、持久诊断及完整孤儿数据清理等仍未完成。动态身份保留旧版 built-in-only getter，未完成全仓库
所有路径的设备验收。API 35 转译模拟器现已执行 IPC、watchdog/取消、Keystore、包生命周期、
队列、Coil 与真实 Media3 播放子集；最新构建 510 项 Kotlin 测试中 507 通过、3 跳过，SDK 10 项通过。
签名/TOFU、隔离包恢复、JSON 深度和嵌套数据保留已补入 RC；详见 [RC 验收](rc-acceptance.md)。
API 24/真机、SAF、OAuth、后台服务及安全故障注入仍未完成，不宣称系统完整或安全验证完成。
