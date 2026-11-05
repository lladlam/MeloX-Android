# 插件运行时、生命周期和安全边界

## 生命周期接线

设置页 `PluginSettings` 使用 `MeloXPlugins.get(context)`。应用在主进程初始化网络后，异步
调用同一个 Manager 的 `restore()`。Manager 和播放 registry 都使用
`MeloXMusicProviders` 中同一个 `DynamicMusicProviders`。

1. 导入：SAF 输入流限制 4 MiB，`PluginArchiveReader` 在内存解 ZIP 并验证 Manifest/入口。
2. 安装：先展示 Manifest 与权限并确认；私有目录按 SourceId 的 SHA-256 命名，使用 `AtomicFile` 保存，初始禁用。
3. 启用：运行时预加载入口，检查声明的方法，再持久化启用意图和发布 Provider。
4. 禁用：保存禁用状态、从 Registry 移除、关闭运行时/网络请求。
5. 更新：同 ID、严格更高版本；增加网络/能力或改变认证配置须批准准确的候选 Manifest，
   Manager 在执行候选前再次检查。先准备候选，失败时不写原包；成功后原子写包并替换 Provider。
6. 卸载：先禁用，再删除包。收藏、历史和普通插件存储保留。
7. 恢复：重新验证包并恢复已启用来源。单个损坏包作为错误条目返回，不阻止其他包枚举。

包文件更新失败有 AtomicFile 回滚；尚未进行断电/进程死亡注入测试。候选脚本执行带来的
外部服务或存储副作用不在包文件事务内。RC 已加入完整性/可选签名、TOFU 公钥记录及密钥变化
单独批准，损坏包隔离与可选卸载清理；协议与保守回滚边界见 [RC 验收](rc-acceptance.md)。

## 独立执行环境

`PluginIsolatedService` 设置 `exported=false`、`isolatedProcess=true`，在独立 Android UID
中执行 QuickJS。`MeloXApplication` 对非应用 UID 跳过账号、网络、诊断及 UI 初始化。
不向脚本提供 Context、Java 类反射、文件句柄、宿主 Cookie 或内置账号凭据。

每次调用新建 QuickJS Context。通过 Messenger 传递有界 JSON；HTTP/存储请求回到宿主，
执行完成后 Promise 结果回到脚本线程。调用 token 防止上次调用的异步响应落入新调用。
整个 JS Context 的创建、执行和关闭都位于同一工作线程。

| 配额 | 当前值 |
| --- | --- |
| ZIP 压缩输入 / 展开 / 文件数 | 4 MiB / 8 MiB / 64 |
| 入口脚本 / 单次参数 / 返回结果 | 各 128 KiB |
| QuickJS 堆 / 栈 | 16 MiB / 256 KiB |
| Worker watchdog / 宿主等待 | 8 秒 / 12 秒 |
| Host API 调用 / HTTP 请求 | 32 / 8，每次调用 |
| API 请求体 / 响应体 | 32 KiB / 64 KiB |
| HTTP 重定向 | 最多 3 次；逐跳重新授权 |
| 渐进式媒体 | 512 MiB，上游不自动重定向 |

QuickJS 包装器没有暴露可用的强制中断接口；watchdog 由独立线程终止隔离 worker 进程，
不会终止宿主进程。API 24 使用共享隔离服务，因此宿主将插件调用串行化。取消会中断等待、
取消代理 HTTP、释放绑定；原生死循环最迟仍需等待 watchdog。反复取消/绑定、服务死亡恢复
和隔离 UID 行为尚未真机验证。进程边界不能替代 QuickJS/native 的安全审计。

## 实际网络权限

`PluginHttpBroker` 使用全新 OkHttpClient，无宿主拦截器、代理、Cookie jar 或账号认证器：

- 仅公开 HTTPS、443 端口，域名与 Manifest 精确匹配，无 userinfo。
- 自定义 DNS 在给 OkHttp 连接候选地址前检查所有结果；任一私网/保留地址使请求失败。
- 拒绝本机、链路本地、内网、共享地址空间、文档/基准测试地址和部分 IPv6 过渡范围。
- 关闭自动重定向，API 重定向逐跳检查；跨域时移除原请求头。
- 关闭系统代理及自动重试，限制超时、请求次数和实际读取的响应字节数。
- 不向插件暴露响应 Set-Cookie；Host、Proxy-Authorization、连接控制等请求头被拒绝。

DNS 校验返回的同一地址列表交给 OkHttp 建立连接，避免“校验后另做一次未校验解析”。
媒体使用相同 DNS 和 URL 策略，并关闭自动重定向。它不使用宿主媒体 HTTPClient。

`ProviderPlaybackResolver` 通过 DataSpec.customData 给 `PluginMediaDataSource` 传递
不可由 JS 构造的策略；每次读取检查 Registry generation。插件字节缓存绕过共享缓存，
避免已禁用插件通过缓存命中继续打开音频。已缓冲音频可能继续播放一段时间，未实现即刻
清空解码器。当前仅支持渐进式媒体，未验证 HLS/DASH/DRM，不应宣称支持这些格式。

普通 storage 是按插件/当前账号划分的私有非秘密数据，不是账号保险箱。日志按 Runtime
实例隔离、内存有界，不直接进入 logcat。账号保险箱使用按插件/账号独立的 Android Keystore
AES-256-GCM key，密文位于 noBackupFilesDir，AAD 绑定 SourceId 和账号 ID。
API Token 与 OAuth2/PKCE 凭据只在宿主代理中使用，具体约束见
[能力与认证接入](05-capabilities-and-authentication.md)。WebView/Cookie 登录不受支持。
API 35 已完成隔离身份、取消/watchdog、Keystore 合成账号与受控图片/媒体测试子集；尚未
完成 API 24、密钥失效、native 耗尽及全部恶意网络故障测试。详情见 verification.md。

封面使用带 SourceId 的 `melox-plugin-image` URI，经 Coil 自定义 Fetcher 进入独立 broker，
不接收账号凭据，不重定向，不使用图片内存/磁盘缓存。仅 PNG/JPEG/WebP，最大 2 MiB、
4096×4096 且总像素不超过 800 万，最多 4 路。获取前后检查 Registry，实际解码需设备验证。
诊断不保留脚本日志正文，只保存有界等级事件；脚本错误仅向 UI 传递白名单错误码。
Messenger 使用发送 UID 与调用 token 检查，宿主再次限制 32 次 Host API；账号选择文件加入
会话戳，切走再切回同一账号也不能使旧请求恢复授权。UID/IPC 仍待设备实证。

## 验证范围

目前自动化验证覆盖地址分类、Manifest 边界、包路径/版本、Manager 注册与失败准备回滚、
来源身份往返。DNS 重绑定、真实重定向、隔离进程越权、死循环/内存耗尽和 Binder 取消仍需
Android 集成/设备测试，不能把地址分类单元测试等同于安全验证完成。
