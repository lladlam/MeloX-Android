# 播放回退顺序

MeloX 的播放解析按固定三段执行，前一阶段成功就不会进入下一阶段。

## 1. 当前音源

先使用队列中歌曲所属的平台：

- 本地已下载文件优先于网络解析。
- 其他情况调用该平台自己的 Provider（网易云、QQ、酷狗概念版、汽水、酷我、Bilibili 等）。
- 解析结果必须是完整音频（`Playable`）；当前平台的试听片段仍按试听处理。

登录态缺失（`LoginRequired`）不会进入后续阶段，因为这是账号层面的问题，逐曲回退只会清空整个队列。

## 2. 内置跨平台匹配

仅在设置中开启「不可用资源从其他平台获取」时执行，并且要求：

- 远程兼容性配置没有关闭 `cross_provider_fallback`；
- 当前平台明确返回会员、版权、地区或没有可播放音源。

默认顺序：

```
Bilibili → YouTube Music → 网易云 → QQ音乐 → 酷狗概念版 → 汽水音乐 → 酷我 → Jellyfin
```

- Bilibili 与 YouTube Music 放在最前：两者都能用匿名接口取到完整音频，不要求用户登录任何国内平台。登录后会自动带上对应平台的 Cookie/凭证。
- 其余平台同样遵循「已登录走凭证、未登录走匿名」：酷我、酷狗、酷狗概念版、汽水的公开接口在未登录时仍会尝试；酷狗额外使用无需账号的公共 tracker（免费曲目返回完整音频，会员曲目 `status != 1`，不会绕过限制）。
- QQ 的匿名 vkey 会被服务端拒绝（`result=101404`），因此未登录时 QQ 会在尝试后跳过；登录后正常使用账号凭证。
- Jellyfin 只有配置了自建服务器才会命中，放在最后，不影响普通用户；它必须使用服务器凭证，没有匿名路径。
- 顺序由云控 `crossProviderFallback.order` 控制；旧配置只列出 QQ／酷狗／Bilibili 时无法通过完整校验，会回退到上面的默认顺序（因此新增平台不会被旧云控误伤）。
- `disabledProviders` 可临时停用某个来源。
- 当前刚失败的音源会被跳过，避免重复请求同一平台。
- 各平台按顺序逐个搜索，不使用并行竞速；匹配必须通过标题、歌手与时长校验。
- 只接受完整音频，试听片段不会被当作完整歌曲。
- Apple Music 因为 DRM 无法产出直链；Spotify 需要 Premium 且要整曲下载，超出本阶段的 3–10 秒超时，均不参与跨平台匹配。

## 3. 第三方音乐源

仅在「第三方音乐源」开关和协议确认都开启时执行：

1. 依次尝试已配置的 Meting / CHKSZ / UNM 解析器（Meting 使用用户填写的完整 API 路径；UNM 仅接受实现 `/match` 合约的服务）。
2. 再尝试已启用、声明 `resolver` 能力的插件（按插件来源 ID 排序）；插件收到的是当前内置源的 track 元数据，返回的 HTTP(S) 完整音频 URL 仍走同一播放管线。
3. 再尝试已导入的 LX Music 脚本；LX 保留自身按音源、音质和脚本顺序的选择逻辑。

- 未配置地址/Key、没有启用的解析器插件或没有导入脚本时，对应来源直接跳过。
- 开启「遇到会员歌曲时再调用」后，只有会员／版权／地区受限才会进入此阶段；普通的“暂时没有音源”不会请求第三方。
- 第三方返回的链接仍必须是 HTTP(S) 完整音频。
- 插件自带的音源（Provider）不会进入内置/LX 解析器；只有声明 `resolver` 能力的插件会在本阶段接收**内置源**的 track。解析器插件不提供目录，也不会被设为主音乐源。

## 调试日志

```bash
adb logcat -s MeloXPlayback MeloXThirdParty MeloXNeteaseResolver
```

关键字段：`Cross-provider fallback: search <source>`、`resolved … via <source>`、`Resolved via Meting/CHKSZ/UNM`、`Resolved via plugin:<pluginId>`、`Resolved via lx:<scriptId>`。
