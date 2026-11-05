# 插件认证：现有账号系统审计

状态：**前序审计与设计约束**。当前已编写的插件 API Token、Keystore 和公开客户端 OAuth2/PKCE
另见 [能力与认证接入](05-capabilities-and-authentication.md) 及 [实际验证](verification.md)，仅完成部分设备子集。本文件保留对
内置账号系统的审计，不意味着其平台专属登录方式均可用于插件。源码基线和总体风险见
[01-repository-audit.md](01-repository-audit.md)、[02-architecture.md](02-architecture.md)。以下路径相对于 `android/app/src/main/kotlin/com/lladlam/melox/`。

## 现有实现

| 组件 | 已核实行为及可复用性 |
| --- | --- |
| `core/account/NeteaseSessionStore.kt` | 验证网易云专用 Cookie 和账号信息，保存于会话 preferences，登出清理 WebView Cookie；平台字段和规则不能作为通用插件逻辑。 |
| `core/provider/qqmusic/QQMusicSessionStore.kt:15-76` | 普通和播放会话分开，当前通过 `MODE_PRIVATE` SharedPreferences 保存 Cookie；校验和 WebView 清理均为 QQ 特定逻辑。不要假设所有现有会话均通过 Keystore 加密。 |
| `core/provider/kugou/KugouSessionStore.kt:47-85` | 登录信息与设备身份分开，标准版、概念版和播放会话分开。保留设备身份的登出语义值得参考，但不可直接复用平台格式。 |
| `core/provider/bilibili/BilibiliSessionStore.kt:17-34,55-78` | 私有 SharedPreferences 存 Cookie，变化时递增 revision，清理指定域 Cookie；revision 对缓存失效有参考价值。 |
| `core/provider/spotify/SpotifySessionStore.kt:21-24,38-81,92-97` | 存储访问令牌、刷新令牌和 PKCE 事务；使用 `SecureSessionPreferences.open`。`SpotifyOAuth.kt:27-41,63-116` 使用随机 state、PKCE S256、系统浏览器与 loopback 回调。回调方式和端点是 Spotify 配置，不能直接当作所有插件的通用流程。 |
| `core/provider/youtubemusic/YouTubeSessionStore.kt:28-32,65-96` | 加密存储、会话 revision 与过期状态；`revalidate` 区分网络失败和登出。其全局 InnerTube 状态及 cookie 逻辑不适用于插件隔离。 |
| `core/provider/jellyfin/JellyfinSessionStore.kt:13-17,39-74` | 单账号以专属 Android Keystore AES-GCM 密钥加密；服务器地址和用户 Token 是 Jellyfin 专有模型。 |
| `core/music/provider/ProviderAccountManager.kt:16-34,86-110,130-134` | 已有设置页账号门面，但身份和遍历基于 `MusicSource` 枚举，账号切换当前等同登出；不能直接代表多插件、多账号。应扩展现有 UI 的通用状态，而不是另造播放系统。 |
| `ui/account/YouTubeLoginScreen.kt:162-176,184-192`、`NeteaseLoginScreen.kt`、`BilibiliLoginScreen.kt` | 现有 WebView 使用进程共享 `CookieManager`；登录和读取规则为内置平台专用。它们不是隔离的插件授权容器。 |

`core/account/SecureSessionPreferences.kt:10-23,48-90` 在加密存储故障时可能删除无法读取的文件、重置共享默认 master key，最终退化为内存存储。该恢复策略不能直接用于插件凭据的无损升级和多账号隔离：插件存储应有独立密钥、显式失败及恢复策略，禁止一次插件凭据故障重置其他账户密钥。Keystore 仅保护密钥材料；不会自动防止宿主错误代码或授权的恶意插件泄露凭据。

## 插件账号设计约束（待实现与验证）

1. 以稳定的 `(pluginId, accountId)` 表示账号，活动账号及版本独立于插件包版本；更新插件不清空会话。插件声明各能力是否需要账号，公开搜索和播放无需登录。未知、过期及已卸载状态须明确区分，不能用内置来源身份回退。
2. 不向插件暴露内置账号，也不向其他插件暴露该插件的会话。插件使用宿主授予的账号句柄发起请求，HTTP Broker 在核验插件身份、账号、域名、重定向和实际连接目标后附加授权；响应、日志和错误不应返回原始敏感凭据。插件若控制获准目标及请求内容，仍可外传其可见数据；域名授权不是完整防外传保证。
3. OAuth 由宿主维护不可预测 state、PKCE verifier、事务有效期和回调匹配，用户在平台允许的浏览器完成授权；插件负责平台授权参数与结果适配。验证码、二次验证、授权和平台限制由用户及平台完成，不绕过。
4. WebView CookieManager 默认不按插件或账号隔离。需要设计并验证独立数据目录/进程隔离、登录期间的访问与导出范围、清理和 API 24 兼容，才能开启此方式；不得使用共享 CookieManager 将所有 Cookie 返回插件。手工 Cookie、Token 或密码亦只能流入相应宿主凭据容器并经受控请求使用；具体可支持的协议须依实现确认。
5. 原子保存会话及活动账号；成功刷新或切换后递增账号代数，拒绝旧代 in-flight 结果并清理相关播放 URL、headers 与请求缓存。取消登录、网络故障和插件崩溃不能清空现有会话。登出、卸载和显式删除的清理范围须清楚告知用户；迁移失败不得静默删除可恢复的原数据。
6. 增加用户可见的账号切换、失效提示及重新授权，与现有账号设置整合。不能把 Cookie 值、OAuth code/verifier、Token 或密码写入日志、媒体 URI 和 IPC 错误信息。

## 待验收

需要真实宿主和设备验证各登录方式的成功、失败、取消、失效与刷新、账号切换及缓存失效、进程崩溃、升级保留、卸载清理、跨插件凭据访问、WebView 隔离及现有内置登录回归。当前均未实现或验证；尚无可执行 OAuth、WebView、Token 或无登录插件示例。
