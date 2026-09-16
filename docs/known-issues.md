# 已知问题记录

记录规划与排查中的问题，作为待办追踪。

## 2026-09-13

### YouTube Music 登录无法获取 Cookie（未解决）

- 现象：登录流程结束后未能取得有效 Cookie，登录不成功。
- 现状：已改为与 Square 参考实现一致的 WebView 登录流程（去掉域名白名单拦截、去掉强制 `dataSyncId` 门槛、启用第三方 Cookie 与 DOM storage），仍需真机验证；若仍失败，可能是 Google 在嵌入式 WebView 上返回 `disallowed_useragent`，可考虑设置桌面 User-Agent 后再试。
- 待办：真机实测后根据结果继续排查。

### Spotify Client ID 需要用户自行设置

- 现象：之前 `MusicProviderSelectionStore.visibleSources()` 用 `BuildConfig.SPOTIFY_CLIENT_ID.isNotBlank()` 过滤，导致未配置时的构建隐藏 Spotify 音乐源。
- 决策：改为让用户在应用内自行填写 Spotify Client ID；`visibleSources()` 已不再按 Client ID 隐藏 Spotify。

### 开发版更新（CI + 镜像源）

- 已恢复 GitHub Actions：`.github/workflows/build.yml`，push 到 `main` 时构建 debug APK，artifact 保留 14 天（`retention-days: 14`）后自动删除。
- 关键修正：CI 必须安装 `platforms;android-37.0`（不是 `platforms;android-37`）和 `build-tools;37.0.0`，否则 runner 上找不到包。
- App 侧：构建时写入 `BuildConfig.GIT_SHA`；「设置 → 关于 → 检查开发版更新」对比 `main` 最新 commit，显示 commit message，并提供下载入口。
- 下载地址走 `MeloXGitHubRouting` 的镜像源自动选择，与正式版更新一致。
- 限制：GitHub Actions artifact 需要登录才能下载，App 目前是打开已路由的 Actions 页面，不是直接下载 APK 文件；若要做成完全自动安装，需要 CI 改为发布到 GitHub Release 资产（不会 14 天自动删除）。
