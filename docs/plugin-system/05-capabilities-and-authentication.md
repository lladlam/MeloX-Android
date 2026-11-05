# 目录能力与认证接入（开发预览）

## 架构与能力入口

所有方法遵循 `method(...arguments, host): Promise<Result>`，每次新建 QuickJS Context。
`PluginMusicProvider` 只校验标准字段、绑定插件 SourceId 并映射 Kotlin 模型；不猜平台字段。
`MeloXPlugins` 在启用及升级预加载时检查 Manifest 每项能力的全部必需方法。

| Manifest 能力 | 必需方法（省略最后的 host） | 现有 UI 入口 |
| --- | --- | --- |
| search | search(query, page, pageSize) | 搜索 → 动态音源 → 歌曲；可显式加入跨源搜索 |
| playback | resolvePlayback(track, quality) | 歌曲点击 → 现有播放队列/Media3 |
| lyrics | lyrics(track) | 现有歌词加载器 |
| playlists | playlists(page, pageSize), searchPlaylists(query, page, pageSize), playlistDetail(id, page, pageSize) | 动态音源 → 歌单 → 详情/上下页 |
| albums | searchAlbums(query, page, pageSize), albumDetail(id, page, pageSize) | 动态音源 → 专辑 → 歌曲 |
| artists | searchArtists(query, page, pageSize), artistDetail(id, page, pageSize) | 动态音源 → 歌手 → 歌曲 |
| rankings | rankings(page, pageSize), rankingTracks(id, page, pageSize) | 动态音源 → 榜单 → 歌曲 |
| homeFeed | homeFeed(playlistLimit, newSongLimit, rankingLimit) | 动态音源 → 推荐 |
| userLibrary | accountSummary(), userPlaylists(page, pageSize), savedAlbums(page, pageSize), followedArtists(page, pageSize) | 动态音源 → 我的歌单/收藏专辑/关注歌手 |
| favorites | isFavorite(track), setFavorite(track, favorite) | 歌曲菜单 → 音源收藏状态（和本地统一收藏独立） |
| download | resolveDownload(track, quality) | 歌曲滑动/菜单下载、现有下载列表 |
| authentication | authenticationStatus() | 设置 → 音乐源插件 → 账号/检查会话 |

返回类型详见 `plugin-sdk/src/types.ts` 和 `schema/protocol-v1.schema.json`。目录列表返回
`MusicPage<T>`；详情返回 `{summary, tracks: MusicPage<MusicTrack>}`，summary.id 必须等于
请求 ID。所有分页均从 1 开始且保留 total；未声明能力的栏目不显示。不存在的 ID 应报错，
不要将不支持的方法写成返回空列表。HomeFeed 返回 recommendedPlaylists、newSongs、rankings。
Favorites 查询返回严格 boolean，写入成功 resolve void，失败 reject；不能以猜测状态代替查询。
Download 使用和播放相同的标准 resolution，宿主只下载 playable，仍检查域名、DNS、配额及启停状态。

`examples/catalog` 是覆盖目录、收藏和下载的 CC0 合成音乐库，没有第三方音乐内容。
构建：

```sh
cd plugin-sdk
npm ci
npm run typecheck && npm run build && npm test
node tools/cli.mjs pack examples/catalog/manifest.json examples/catalog/index.ts /tmp/opencode/catalog.melox-plugin
```

## API Token 与多账号

```json
{
  "capabilities": ["authentication"],
  "networkDomains": ["api.example.com", "cdn.example.com"],
  "authentication": {
    "type": "apiToken", "header": "Authorization", "prefix": "Bearer ",
    "domains": ["api.example.com"]
  }
}
```

此片段需和完整 Manifest 的 id/name/version/apiVersion/entry 合并。domains 是凭据的
**精确子白名单**，必须属于 networkDomains。Header 不得为 Host、Cookie、代理或连接控制头。
在插件设置输入账号名称及 Token，点击导入并使用；可保存多个账号、切换、注销当前或删除。
普通 storage 按插件和当前账号隔离，原来的匿名 storage 命名保持兼容。

```ts
const response = await host.http.request({
  url: "https://api.example.com/me",
  credential: "active",
});
```

host.account 只包含不透明账号 ID 和名称。Token 不通过 Binder 给 JS，不放普通 storage，
不使用共享 WebView CookieManager。HTTP 代理每一跳检查域名与凭据权限；跨域跳转不会
继承自定义请求头，未获凭据授权的目标直接失败。媒体/下载可在 playable 返回
`credential: "active"`，由实际媒体网络出口注入，不需要把 Token 交给脚本。
授权服务器可能在响应中回显凭据；插件不得记录或导出此内容（见 api-token 示例）。

每个插件/账号使用独立 Keystore AES-256-GCM 密钥和带身份 AAD 的 AtomicFile 密文，存放在
noBackupFilesDir。损坏/丢失密钥不会重置其他账号或静默覆盖原密文。升级/卸载保留账号数据；
账号绑定导入时的完整凭据策略，升级改变策略需要重新授权。RC 包可验证可选发布者签名与
本地公钥指纹，首次导入仍需信任并独立核对发布者。账号切换/删除后重建运行时、变更 registry
generation；会话戳使切走再切回的旧请求也失效。卸载可选清除本地账号，不代表服务器撤销。

## OAuth2 / PKCE

```json
{
  "capabilities": ["authentication"],
  "networkDomains": ["auth.example.com", "api.example.com"],
  "authentication": {
    "type": "oauth2Pkce",
    "authorizationEndpoint": "https://auth.example.com/authorize",
    "tokenEndpoint": "https://auth.example.com/token",
    "clientId": "YOUR_REGISTERED_PUBLIC_CLIENT_ID",
    "scopes": ["music.read"],
    "domains": ["api.example.com"]
  }
}
```

平台必须允许公开原生客户端的授权码 + S256 PKCE，并注册固定回调
`com.lladlam.melox.plugin:/oauth2redirect`。不支持需 client secret 的平台；不能在插件里
内嵌密钥或绕过平台授权、验证码、付费或 DRM。端点必须是声明域名的 HTTPS 443，不能带
userinfo、query 或 fragment。浏览器是外部系统用户代理，其导航不由宿主 HTTP 代理执行。

设置页输入账号名称，发起浏览器登录。宿主用 SecureRandom 生成 state 和 verifier，验证
回调目标、唯一 state/code、10 分钟期限、插件仍启用且 Manifest 未变、未重复兑换且未取消，
然后仅在宿主通过受控 DNS/HTTPS 网络出口 POST token 表单。access/refresh Token 直接
进入加密保险箱，不交给插件。回调 state 只存在内存，宿主进程死亡后应重新发起登录。
系统浏览器可使用自己的平台 SSO 会话；它不是 MeloX 为插件分配的独立 Cookie 容器。

expires_in 临期时宿主尝试刷新，refresh Token 轮换以原账号密文原子更新；比较账号身份和
旧 Token，避免晚到响应复活已删除/切换账号。刷新失败保留原账号，过期凭据请求以
AUTH_EXPIRED 拒绝。UI 提供手动刷新；不自动重放写请求，也不实现服务器端 Token 撤销。
API Token 的失效/平台会话状态由 authenticationStatus 转换为标准 signedOut/signedIn/expired。

## 验证边界

源码接线和自动化通过不等于全部设备验收。API 35 已实测 Keystore 合成账号、隔离 UID、
Binder 与 Media3 子集；真实 OAuth、SAF、API 24 与完整后台播放仍待验收，步骤见
[device-acceptance.md](device-acceptance.md)，实际证据见 [verification.md](verification.md)。
剩余功能与限制见 [remaining-work.md](remaining-work.md)。
