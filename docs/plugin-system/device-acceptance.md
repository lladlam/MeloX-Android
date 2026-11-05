# 插件设备验收清单

**当前状态：部分执行。** API 35 转译模拟器已完成 RC 自动化子集，详情见
[verification.md](verification.md)；API 24 和实体 ARM 设备仍未执行，不得用 JVM/Node 单测替代。
准备 API 24 与较新 Android 设备、授权的公开 HTTPS 测试 API/音频/OAuth 服务，记录 APK
版本、插件包哈希、设备/API、操作、预期、实际及可复现日志。不要保存 Token 或账号隐私。

## 可执行的 ADB 准备与取证

在仓库根目录执行，连接自己的测试设备并允许 USB 调试。安装命令保留应用数据，勿使用
`pm clear` 或卸载主应用来代替插件卸载测试。多设备时为每条命令增加 `-s SERIAL`。

```sh
adb devices -l
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abilist
# 按设备 ABI 选一个 APK，以下为 arm64 示例
adb install -r android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
adb push /tmp/opencode/melox-catalog-hardening.melox-plugin /sdcard/Download/
adb push /tmp/opencode/melox-test-tone-hardening.melox-plugin /sdcard/Download/
adb push /tmp/opencode/melox-api-token-hardening.melox-plugin /sdcard/Download/
adb shell monkey -p com.lladlam.melox.android.dev -c android.intent.category.LAUNCHER 1
```

在“设置 → 音乐源插件”通过系统文件选择器导入；核对确认页并安装、启用，设置为主来源。
逐项执行下方 A–E。运行示例时快速执行以下命令，以抓到短生命周期 worker：

```sh
adb shell ps -A -o UID,PID,NAME
adb shell dumpsys activity services com.lladlam.melox.android.dev
adb shell dumpsys package com.lladlam.melox.android.dev
# 缺失来源链接应显示来源不可用，不自动安装、启用或回退
adb shell am start -a android.intent.action.VIEW -d 'melox://plugin-track?source=plugin%3Aorg.example.missing&id=opaque&title=Missing'
# 未知 OAuth state 必须拒绝且不创建账号
adb shell am start -a android.intent.action.VIEW -d 'com.lladlam.melox.plugin:/oauth2redirect?state=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa&code=invalid'
```

核对主进程与 `:melox_plugin` UID 不同，隔离进程没有应用网络/文件权限；仅查看 Manifest
的 isolatedProcess 标记不算通过。服务非导出、回调拒绝、封面停止和密钥隔离均需记录实际行为。
在无真实 Token 的测试会话中可采集 `adb logcat -d -v threadtime`；真实账号会话只保留脱敏证据，
不要直接发布完整 logcat、网络抓包或 app 私有目录。Debug APK 仅用于测试，不代表 release 签名验收。

每项记录：设备/API、APK/插件 SHA-256、操作、预期、实际、通过/失败、脱敏证据路径。
补充切源回退栈、封面 2 MiB/800 万像素拒绝、权限扩张取消、切号 A→B→A 的旧请求拒绝、
媒体 401/403/410 最多重解析一次，以及下载失败后再触发。未执行的项目保持“未执行”。

## A. 隔离运行时

- 导入 CC0 test-tone 和 catalog 示例，启用，确认 worker UID 与主应用不同、service 不可从外部绑定。
- 验证同步、Promise、连续宿主 HTTP/storage、JS throw/reject、非法 JSON/超长结果。
- 无限循环、内存/栈耗尽、永不 resolve Promise：记录 8 秒 worker watchdog 与宿主恢复；应用可继续使用。
- 快速搜索/切源取消、禁用时取消、worker kill、Binder 死亡/重绑定、连续重复 100 次调用。
  核对线程、FD、绑定和 OkHttp 请求释放；旧响应不能进入后一次调用。

## B. Media3

- 首播、暂停/恢复、seek 到首尾、连续切歌、混合内置/插件队列、后台/熄屏、焦点丢失/恢复。
- 测试服务器分别支持/拒绝 Range，返回 200/206/416、分块流、长度未知、断流、超大流。
- 每次解析返回短时效 URL；seek/重开必须重新解析，不复用过期地址。确认请求头与可选
  `credential: active` 在实际媒体请求生效，未进入共享插件字节缓存。
- 播放中禁用、卸载或切换账号：后续读取失败、旧来源 ID 保留；记录已缓冲音频持续多久。
- 下载相同音频，验证质量、权限不可用状态、配额、取消、禁用/切号撤销与下载列表身份。

## C. 账号与凭据

- 两个插件各两个账号：分别授权、读库、收藏、切换，确认不得交叉使用 Token/storage。
- 检查 noBackupFilesDir 中只有 AES-GCM 密文；无明文 Token 出现在 Binder、普通 storage、
  logcat/插件日志、备份或媒体缓存 key。原始凭据只在账号所属授权网络请求头出现。
- API Token 错误/过期，OAuth 取消/拒绝、错误 state、缺失/重复参数、重复回调、过期 state、
  宿主进程重启、登录中升级/禁用/卸载；失败不保存新账号、不覆盖旧账号。
- 使用自己注册的公开 OAuth 客户端验证 S256、code_verifier、固定 redirect_uri、Token
  兑换/轮换、临期自动刷新、手动刷新、invalid_grant、无 refresh_token；失败不重放写请求。
- 刷新期间注销/删号/切号，确认晚到响应不能复活账号；服务端撤销由平台独立验证。
- 同配置升级保留账号；改变凭据域名/header/OAuth 配置后，旧账号不自动获新权限。
- 密钥失效、密文损坏、文件写失败：保留原数据，不重建全局密钥、不清空无关账号。

## D. 网络安全

- 精确域名、子域伪装、userinfo、IP literal、HTTP、非 443 端口、跨域/降级重定向逐项拒绝。
- 使用可控 DNS 测试私网/loopback/link-local/CGNAT/保留地址、IPv6 ULA/组播/映射 IPv4、
  全局/私网混合解析、DNS rebinding；抓包确认实际 socket 地址和校验结果一致。
- API 重定向 0–4 跳，凭据目标不在子白名单时不得注入；跨域自定义头清除，Set-Cookie 不暴露。
- 32 KiB 请求、64 KiB 解压后响应、请求数/宿主 API 次数、5 秒 API timeout、媒体 512 MiB
  边界及并发/取消；不能借下载或封面绕过同一策略。内网服务不默认授权。
- OAuth 外部浏览器与宿主代理分别记录；浏览器导航不是宿主网络沙箱安全证明。

## E. 完整生命周期与 UI

- SAF 导入/取消、重复 ID、非法 ZIP/路径/版本、未知能力、声明缺失方法、安装后默认禁用。
- 全能力 catalog 各栏目、详情、空结果、上下页、total；未声明能力不显示；收藏状态与写入
  在下一次新 JS Context 中仍一致；禁用源失败不回退网易云。
- 同 ID 高版本更新、候选失败、磁盘写失败、更新期间 kill/断电恢复、卸载重启及旧队列。
- 插件与内置源的同 ID 曲目/收藏/历史/下载不得冲突；未知来源 JSON 读取再写入保持身份。
- 开启显式跨源搜索，插件成功/失败/取消互不覆盖，停用或卸载选中的插件保留失败来源身份。
- 回归内置源登录、播放、歌词、LX 补源、收藏和下载；不把在线服务故障误记为插件回归。

以上每组填写“通过 / 失败 / 未执行”和证据，再更新 verification.md。已测子集不代表整组通过。
