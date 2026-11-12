# B 站定向流量（实验性）

在 **设置 → Bilibili → B站定向流量（实验性）** 开启，默认关闭。

## 它解决什么问题

国内运营商的定向流量包通常只对 B 站自有域名（`*.bilivideo.com` 等）免流。
B 站播放接口下发的地址不一定走官方 CDN —— 有一部分是第三方 PCDN 或裸 IP 主机，
这些线路既通常不在免流白名单里，也可能限速或拒绝第三方客户端。

开启后，MeloX 在**移动数据**下会：

1. 收集该音质 DASH 音频的全部候选地址（`base_url` + 所有 `backup_url`）；
2. 优先选用 B 站官方 CDN 主机且不是 PCDN 的地址；
3. 全部候选都是 PCDN 时，把 host 换成官方镜像 `upos-sz-mirrorali.bilivideo.com` 兜底。

## 不做什么（重要边界）

- **不会**改写 WBI 请求的平台身份（不会把 `platform=android` 之类的参数塞进签名请求）。
  这条与上游 BiliPai 的结论一致：定向流量只影响媒体地址处理。
- **不会**改动请求身份：`Cookie`、`Referer`、`User-Agent` 与开关前完全一致。
- **不会**改写 host 专属路径。只有 `/upgcxcode/` 与 `/v1/resource` 这两类路径的
  路径与签名在不同 host 之间通用，其余路径原样保留。
- **不会**在 Wi-Fi 或未知网络下生效。

## 关于「免流」

Android 没有通用的零计费（zero-rating）接口，应用**无法查询也无法保证**
运营商是否对某条线路免流。是否免流完全由运营商的计费策略决定。

因此本功能只做「选哪条线路」的优化，不对计费结果做任何承诺。
如果运营商的白名单与本实现的官方域名判断不一致，实际计费仍以运营商为准。

## 与 BiliPai 的关系

参考 [BiliPai](https://github.com/jay3-yy/BiliPai) 的媒体地址处理：

| 行为 | BiliPai | MeloX |
| --- | --- | --- |
| PCDN 判定口径 | `isRiskyCdnHost` / `isPcdnUrl` | 同口径（`mcdn` / `pcdn` / `gotcha` / 已知第三方后缀 / 裸 IP） |
| 全 PCDN 时换 host | `rewriteBilivideoHost` | 同规则（仅 `/upgcxcode/`、`/v1/resource`） |
| 兜底线路 | `upos-sz-mirrorali.bilivideo.com` | 同 |
| 改动 WBI 请求平台身份 | 早期版本有，后被移除 | 不做 |

BiliPai 早期版本曾在定向流量开启时把 WBI 请求改写成 Android 平台身份
（`platform=android`、`mobi_app=android`、`device=android`、`build=8130300`），
随后上游把这部分移除（`buildDirectedTrafficWbiOverrides` 变为空实现）。
MeloX 只保留媒体地址处理。

## 代码位置

| 文件 | 作用 |
| --- | --- |
| `core/provider/bilibili/BilibiliDirectedTrafficPolicy.kt` | 开关状态、主机判定、选线与 host 换写 |
| `core/provider/bilibili/BilibiliProvider.kt` | 收集候选地址并应用策略 |
| `core/music/provider/MeloXMusicProviders.kt` | 把「是否生效」注入 provider |
| `ui/settings/SettingsScreen.kt` | 设置页开关 |

## 验证

- `BilibiliDirectedTrafficPolicyTest`：主机分类、选线顺序、host 换写边界。
- `BilibiliDirectedTrafficPlaybackTest`：端到端确认开关只影响地址，
  `Cookie` / `Referer` / `User-Agent` 不变。

真机验收尚未完成（需要移动数据环境与运营商定向流量包才能确认计费表现）。
