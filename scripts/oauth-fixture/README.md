# 合成 OAuth 服务与故障控制

此服务只生成内存中的 Alpha/Beta 合成账号和短期随机凭据。没有用户密码或第三方账号接入。
当前新增代码**尚未运行验证**。测试不修改宿主的 HTTPS/443、公共 DNS 或系统 TLS 校验策略。

## 部署

在受控域名的公网 HTTPS 443 后部署，提供该域名的有效证书。也可在同机器的 TLS 反代后运行。
反代不要记录带 query 的 `/authorize` 请求；不要缓存 `/token` 和 `/api/me`。默认监听 loopback。

```sh
MELOX_RC_OAUTH_ADMIN="$(openssl rand -hex 24)" python3 scripts/oauth-fixture/server.py \
  --cert /path/to/fullchain.pem --key /path/to/key.pem --port 8443 --ttl 15
```

管理 secret 由测试人员保存在进程环境，勿加入插件包、日志或 Git。不设置它则管理端点关闭。
服务进程重启会使所有合成授权失效。前台浏览器授权需显式 Allow/Deny，不自动批准。

Manifest 需声明 `authentication` capability、受控域名的 `networkDomains` 和：

```json
{"type":"oauth2Pkce","authorizationEndpoint":"https://YOUR-DOMAIN/authorize",
 "tokenEndpoint":"https://YOUR-DOMAIN/token","clientId":"melox-rc-public",
 "scopes":["music.read"],"domains":["YOUR-DOMAIN"]}
```

固定回调：`com.lladlam.melox.plugin:/oauth2redirect`。不支持 client secret、隐式流或任意回调。
`plugin-sdk/examples/oauth-fixture` 提供适配代码；配置域名后使用现有 CLI 打包，不需要真实账号。

## 验收矩阵

1. 实际 MeloX 开始登录 → 外部浏览器 → 选 Alpha → Allow → 回调；检查授权记录并调用 `/api/me`。
2. 同插件创建 Beta，切换 Alpha→Beta→Alpha；验证旧请求失效、状态与资料不串号。
3. 等待15秒或管理 `expire`，请求触发刷新；刷新令牌轮换且旧令牌重放返回 invalid_grant。
4. 管理 `fail-refresh` + `expire`，验证 expired 状态、原账号保留及重新授权；恢复 allow-refresh。
5. 浏览器 Deny、用户取消、返回后重复回调、修改 state、redirect_uri、code_verifier 与 code 重放。
6. 相同域名但不同 SourceId 的两个插件独立登录；凭据与普通 storage 不共享。
7. 浏览器收到的 rc_browser_only Cookie 不应出现在宿主 token/API 请求。`/admin/events` 仅记录
   Cookie 是否存在和事件结果，不返回任何 token、code、state 或 verifier。插件不能读取 token 响应。

管理：GET `/admin/events`，POST `/admin/control`（form: `action=expire|fail-refresh|allow-refresh&account=alpha|beta`），
带 `X-RC-Admin` 请求头。只从测试控制端使用管理 API，不把 secret 放进插件。

本地回环 TLS 的协议测试只证明服务行为；只有上述公网域名配置下的实际应用授权才能计为宿主
端到端 PASS。未部署公网证书/域名时记录 BLOCKED，不以 Mock 或放开宿主 SSRF 规则替代。

## 网络故障与真实 DNS 注入代码（未运行）

同一 HTTPS 服务还提供 `/fault/redirect-outside`、`redirect-loop`、`large`、`gzip-large` 和
`adaptive`。`MELOX_RC_OAUTH_ORIGIN` runner 先验证 health，再使用生产 Broker 检查重定向、
解压后响应体限额、自适应 MIME、媒体禁止跳转和已撤销会话；服务不可达不算策略测试通过。

`/fault/progressive` 返回固定512 KiB合成字节，只用于下载传输验证，不是可播放音频。
`cut=1` 使首次无 Range 请求在16 KiB处截断，续传请求返回剩余字节；`v=2` 改变字节和
强 ETag，`mode=ignore|bad-range|weak` 注入忽略范围、错误 Content-Range 和弱 ETag。
同一 runner 还运行 `PluginRcDownloadChecks`；所有新增场景当前未运行。

`/fault/stall` 先发送1字节，再等待10秒补齐，用于 `PluginRcMediaRevocationChecks` 检查
运行时关闭会主动取消媒体连接，而非等待 socket 超时。OAuth runner 新增重新授权原账号、
普通存储保留、旧凭据版本拒绝、切号/拒绝回调和宿主过期状态场景；目前均未运行。

`dns-rebinding.py` 用于真实权威 DNS 实验，默认仅监听回环，必须显式部署到受控 DNS 服务器
并在父域委派测试主机的子域后才会影响真实解析。它只回答指定主机，TTL=0、不递归，A/AAAA
分别按计划推进，最后一组答案保持。示意计划（把首组替换为测试 HTTPS 服务真实公网地址）：

```json
{"a":[["PUBLIC_SERVER_IPV4"],["PUBLIC_SERVER_IPV4","127.0.0.1"],["10.0.0.1"]],
 "aaaa":[[],["fd00::1"]]}
```

```sh
python3 scripts/oauth-fixture/dns-rebinding.py --bind CONTROLLED_DNS_IP --port 53 \
  --hostname rebind.YOUR-DOMAIN --plan /path/to/synthetic-plan.json
```

分别以纯 A、纯 AAAA、混合地址、public→private 计划运行；公网基线必须先成功。
同时在受控服务器和设备出口抓包，核对系统 DNS 查询次数、连接复用和最终 socket 目标；
系统 DNS 缓存或连接复用造成没有新查询时不能记为 rebinding PASS。此 DNS fixture 不处理
通用 DNS zone、TLS 证书签发或设备 DNS 设置；只提供故障响应，不修改应用任何网络策略。
