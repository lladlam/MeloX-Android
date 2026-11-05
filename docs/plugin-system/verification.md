# 实际验证记录

## 2026-11-06 实体手机测试中止

用户说明 `.dev` 为其主要使用的软件之前，误将其视为独立测试安装：设备 Android 17、ARM64，
命令 `adb install -r` 已覆盖 `com.lladlam.melox.android.dev` 并安装对应 instrumentation APK。
未执行卸载或清除数据；instrumentation 报 `Process crashed`，没有通过结果。日志
`/tmp/opencode/melox-plugin-rc-arm64-phone-command.log`。收到说明后停止对手机的测试和修改；
不能据此断言该手机的数据或用户体验未经影响。后续故障、降级及旧数据试验需要明确的独立测试环境。

用户指定仅用现有模拟器继续：API24 使用独立 AVD 数据目录
`/mnt/ntfs_files_sda4/melox-rc-api24-isolated.avd` 成功启动，`ro.build.version.sdk=24`，
ABI 为 `x86_64,x86`；安装当前 ARM64 APK 实际返回 `INSTALL_FAILED_NO_MATCHING_ABIS`。
故仍没有 API24 应用验收结果；日志 `/tmp/opencode/melox-rc-api24-isolated.log`。

在 API35 x86_64 + ARM 转译模拟器 `emulator-5554` 的独立正式包名测试安装中，下载官方
0.7.0 APK（`/tmp/opencode/melox-android-0.7.0-release.apk`），SHA-256
`4c9163b6b7554d3e2c2e4d77b4de2e9217955ae92b287e9c862c47f4a2034aa0` 与官方
SHA256SUMS 一致，签名为项目正式证书。全新安装旧版并完成首次引导，在 General 将默认启动页
设置为 Library；`adb install -r` 升级本轮0.7.1正式签名 ARM64 APK后冷启动，General 仍显示
Library（`/tmp/opencode/melox-upgraded-setting.xml`）。再在模拟器执行 `adb install -r -d`
降回官方0.7.0，冷启动后同一设置仍显示 Library（`/tmp/opencode/melox-downgraded-setting.xml`）。
这只证明该模拟器上一个真实旧设置的升级/降级往返，不代表收藏、账号、插件、下载或所有
未知字段均无损，也不代表任何真实手机允许或完成降级。未使用用户手机执行该实验。

MeloX-Docs 已更新12能力、当前 SDK schema、账号/签名/安全范围及未验收限制；本地站点 typecheck
与生产 build 成功，分支提交 `4501864`，合并后 main `57615bf87d02bf21de93f586e23387d8ec2841cd`
已推送且 `git ls-remote` 核实。公开站点 `https://md.lladlam.top/docs/plugin-system/` 已实际
读取到更新后的 RC 指南及 12 项能力列表。

## 2026-11-06 播放器交互与系统上一首修复

设置 → 通用新增两个默认关闭的开关（`general_artwork_tap_lyrics`、
`general_artwork_swipe_tracks`）：点击封面在封面/歌词页之间切换；左右滑动封面切歌，
沿用原有弹簧回弹动画，iOS 与 Classic 播放器均接入。API35 模拟器（320×640）实测：
导入 rc-app 后点击歌词页头部封面切到封面页、再点大封面回到歌词页；左滑封面由
RC Silence 1 切到 RC Silence 2，右滑在已播 0:12 时仍切回 RC Silence 1（滑动固定切歌）。

系统上一首修复：原回调只处理 `COMMAND_SEEK_TO_PREVIOUS`、处理完仍返回
`RESULT_SUCCESS`，Media3 会再执行一次默认 `seekToPrevious`，导致"先回开头又跳到上一首"。
现在同时拦截 `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM` 并返回 `RESULT_INFO_SKIPPED`。
模拟器实测：开启 5 秒设置、播到 1:18 时 `cmd media_session dispatch previous` 只回到
RC Silence 2 的 0:00；关闭设置后同一命令切到 RC Silence 1。新增
`PluginRcPlaybackControlChecks` 用真实 MediaController 覆盖 5 秒阈值两侧与关闭设置，
尚未执行。

歌词跟随：定位超过约 200ms 的跳转（含系统上一首回到开头）会重置"浏览歌词"状态并
重新对齐焦点。模拟器从 0:00 拖到 1:24 显示"One minute"、拖到 0:30 显示
"Thirty seconds"，系统上一首回到 0:00 后歌词列表回到第一行。

以上为 API35 模拟器证据，不是实体手机验收；正式 `.dev` 尚未重新安装本轮构建。

`PluginRcPlaybackControlChecks`（真实 `MeloXPlaybackService` + MediaController）在 API35 模拟器
通过：设置开启且已播 12 秒时上一首只回到本曲开头；开启但仅 1 秒时切到上一首；关闭设置后
12 秒仍切到上一首。运行时输出 `PASS MediaSession previous obeys 5s restart preference`。
随后将同一 Debug APK 覆盖安装到用户手机 `.dev`（`adb install -r`，未卸载、未清数据）：
versionCode 26 / 0.7.1，安装后应用正常启动，音乐库、歌单和当前歌曲仍在；手机端实际
base.apk 的 SHA-256 `a7592b4480cbcfce93066ec807aada94c39f9e4871fbea3c565778c6a4d10af0`
与本机构建产物一致，并在包内确认存在新增的两个设置字符串。手机交互验收由用户自行测试。

最新状态见本节；文末“RC 工程验收”以及 478/490/501/510 项记录均有各自历史范围。

## 最新批次：功能收拢后进入集中验证

发布操作记录：用户明确要求后，RC 代码已提交 `305801a6ce2257d525ac97ecfbf5eeb6291ec667`
并推送 main，`git ls-remote origin refs/heads/main` 确认远端一致。提交包含86个文件，
排除了原有 README、SettingsScreen 背屏入口和 `android/rear-companion` 的独立改动。
推送是代码检查点，不是测试或发布验收通过。

推送后新增任务持久化、保守 Range 续传、受控错误文案与持久诊断；新增
`PluginRcDownloadChecks` 在受控 HTTPS 上检查截断保留、Range/If-Range、ETag 变化、
服务端忽略范围、错误范围、弱校验器、作用域变化和实际任务 AtomicFile 重建。
`PluginRcFaultChecks` 增加诊断容量/方法名脱敏/来源隔离/重建/清除检查。
Android 集中构建已通过；API35故障子项和全字段子集已取得结果，完整验收仍未通过。

继续实现批次的设备验收范围：
- `PluginRcMediaRevocationChecks`：服务端先发1字节、再延迟10秒；运行时关闭后读操作须在2秒内
  失败，而非等待15秒 socket 超时。该代码经现有受控 HTTPS runner 接入。
- `PluginRcOAuthChecks` 增加原账号/普通存储保留、无重复账号、凭据戳变化、过期状态、
  拒绝和切号后旧重新授权回调，以及旧版本凭据写入拒绝检查。
- `PluginRcDownloadChecks` 增加账号归属摘要的实际 AtomicFile 往返；OAuth runner 检查
  当前 Provider 拒绝创建另一个账号归属的下载 Broker。
- 账号/目录/收藏组件状态隔离、失败路径媒体撤销、401/403 单次刷新与下载重解析均仅为
  已接线实现，未作为 UI 或网络验收通过项。

功能收拢后已启动集中验证：
- SDK：`npm run typecheck`、`npm run build` 成功；`npm test` **12/12 通过**，无跳过。
  包含共享全字段 Schema 类型变异与 Capability 方法完整性检查，不代表 Android 解码或 UI 通过。
- 受控服务 `server.py`、`dns-rebinding.py` 的 Python AST 语法检查通过；未部署或运行服务。
- `git diff --check` 通过；14份插件文档本地链接目标检查通过；3个设备/强杀/ARM64脚本的 `bash -n` 通过。
- Android：`:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` 已启动，
  首轮日志 `/tmp/opencode/melox-plugin-implementation-check.log`：主代码编译失败，发现隔离服务
  缺少 CANCEL 消息和设置页缺少 PluginErrors 导入，已修正。CANCEL 仅接受宿主 UID 及当前
  invocation token，不匹配时忽略；匹配时终止隔离进程以中断 native-loop。
  第二轮日志 `/tmp/opencode/melox-plugin-implementation-check-2.log`：Debug APK 生成成功；
  520项 JVM 测试，1失败、3跳过。失败项为 `PluginCatalogProviderTest` 仍捕获旧解码异常，
  全字段校验现在提前以 IllegalArgumentException 拒绝字符串布尔值，已更新断言。
  第三轮日志 `/tmp/opencode/melox-plugin-implementation-check-3.log`：**BUILD SUCCESSFUL in 2m 34s**，
  Debug APK、AndroidTest APK 均生成；汇总 JUnit XML 为 **520项，517通过、0失败、0错误、3跳过**。
  包含后续下载取消清理修改；前两轮未执行到 AndroidTest 编译。
- 新增 Manager 回归代码覆盖凭据提交前撤销旧 Provider、部分写失败且恢复失败时保留主异常、
  禁用状态账号操作和旧 Manifest 拒绝；第二轮 `PluginManagerTest` **6/6 通过**（内存 repository，
  不代表 Android 文件系统写失败实测）。
- API35 `emulator-5554`（x86_64 + arm64-v8a 转译）：首轮日志
  `/tmp/opencode/melox-plugin-rc-latest-device-command.log` 和 `/tmp/opencode/melox-plugin-rc-latest-device.log`。
  实际通过 UID/Binder、包生命周期、队列存储、QuickJS/storage/watchdog/取消重绑、Keystore、
  错 token 取消防重放/当前 token 强杀、native循环/堆/栈故障后恢复、storage v1迁移/v2跨来源清理、
  持久诊断容量/方法名脱敏/隔离/重建/清除。随后全字段 fixture 因漏填认证 header/prefix 失败；
  首轮整体 rcStatus=FAIL，不能称整轮通过。
- 补齐 fixture 后仅重建 AndroidTest APK 成功，日志 `/tmp/opencode/melox-plugin-wire-fixture-build.log`；
  `/tmp/opencode/melox-plugin-rc-wire-latest-command.log` 显示复验 **rcStatus=PASS**，全部API-v1方法
  的共享全字段 fixture 经真实隔离 QuickJS 往返，12个 Capability Provider 映射执行通过，外部
  shell 启动服务被拒绝。此命令没有重复运行 faults，也没有开启 OAuth/Range/catalog/online。
- 正常应用服务复验 `/tmp/opencode/melox-plugin-rc-normal-service-latest.log` 返回 FAIL：当前安装
  缺少已启用 rc-app fixture，尚未进入播放断言。随后界面 XML `/sdcard/melox-rc-current.xml`
  显示 `System UI isn't responding`；仍不能记录正常服务/后台/熄屏通过。
- 15组包提交/回滚强杀恢复 **15/15通过**，输出目录 `/tmp/opencode/melox-rc-crash-latest`，主日志
  `/tmp/opencode/melox-rc-crash-latest-command.log`。覆盖 unsigned/rotation/downgrade 各自
  decision/archive/publisher 提交边界和 rollback-archive/rollback-publisher 回滚边界；每组均
  检查重启后的包版本、匹配的发布者、期望 enabled 状态和 journal 恢复。不等于物理断电或全局事务。

### 正常应用 UI 继续复验（进行中）

- 模拟器进程退出后重启 `melox-http-disk`，保留数据并重新覆盖安装本次 APK；启动期多个 Google
  系统应用 ANR及高IO等待，日志 `/tmp/opencode/melox-rc-ui-resume-logcat.txt`、
  `/tmp/opencode/melox-rc-ui-activity-errors.txt`，不能当作应用播放通过证据。
- 原生界面重新完成引导、设置搜索插件入口、真实 SAF Downloads选择签名rc-app、核对权限/
  指纹并确认安装、启用、设为主来源及歌曲搜索；点击歌曲显示 MiniPlayer，未再触发原来的
  `MeloXPlaybackUiState.refresh` 崩溃。截图/XML：`/tmp/opencode/melox-rc-ui-latest/`。
- 播放仍失败：MediaSession为ERROR，Media3报FLAC首帧sync错误。字节比对发现旧fixture本地237字节，
  httpbingo实际返回249字节，将引号变为HTML实体，即使audio MIME也如此。替代httpbin端点实测
  返回237字节且逐字节一致，已修改fixture生成器；这不是正常播放PASS。
- 同时日志发现Lyricon桥接 `lyriconPosition` 仍读动态身份的`.source`，会捕获异常并关闭桥接。
  已改为`sourceId.builtIn`；重新构建/单测正在执行，日志`/tmp/opencode/melox-rc-lyricon-fix-build.log`。
- rc-app更新到1.1.0并新增httpbin域名：真实SAF权限扩张确认可见；先取消后仍为1.0.0 Enabled，
  再次导入确认后为1.1.0 Enabled，公钥不变。此fixture更新为合成测试包，不是应用版本发布。
- 正常服务runner首项Binder探针可能与已启用包的启动校验抢用同一服务，返回FAIL；已在原始
  Binder探针前等待插件启动恢复，验收APK重编译通过，实际复跑待执行。前一次编译120秒超时，
  重跑日志`/tmp/opencode/melox-rc-runner-restore-gate-build-2.log`。
- 更新fixture后真实点击歌曲，系统MediaSession **PLAYING(3)**、position=29823、buffered=120000、
  error=null，元数据为RC Silence 1 / MeloX SDK Example / Test Signals。
  证据`/tmp/opencode/melox-rc-app-v2-media-session.txt`；这是正常服务实际播放证据，不是完整UI通过。
- 展开MiniPlayer进入完整播放器时主进程崩溃：`MeloXPlaybackTitleMenu.kt:117` 仍读动态`.source`。
  日志`/tmp/opencode/melox-rc-player-open-crash.log`；误采集的页面实际为崩溃后Launcher，已命名为
  `/tmp/opencode/melox-rc-ui-latest/04-player-open-crashed-launcher.{png,xml}`，不可标为播放截图。
  已补齐标题菜单、操作页、音质chip/弹窗及B站歌词偏移的SourceId安全判断；重新构建尚未完成。
- Lyricon修复一轮构建成功（8m13s），但不能覆盖随后完整播放器调用点修改；最新日志为
  `/tmp/opencode/melox-rc-now-playing-sourceid-build.log`。正常服务runner等待restore后仍在原始
  Binder探针失败，进一步为原始Binder探针和强杀探针接入宿主同一全局worker gate，避免与
  自动队列恢复/播放解析抢占；此后源码变更尚待构建和复跑，不将原因推断记为已证实PASS。
- 完整播放器调用点及worker gate修复后构建 **BUILD SUCCESSFUL in 6m**，
  `/tmp/opencode/melox-rc-now-playing-sourceid-build.log`；JUnit XML汇总520项、0失败/错误、3跳过。
  最新APK覆盖安装后，`/tmp/opencode/melox-rc-normal-service-worker-gated.log` **rcStatus=PASS**：
  正常MeloXPlaybackService/MediaController、动态UI state、双歌曲队列、pause/seek/next、歌词Provider、
  媒体通知、后台/熄屏持续播放及前台服务标志均通过。与单独测试ExoPlayer证据区分。
- 原生完整播放器成功展开（`05-full-player-sourceid-fixed`），歌词面板实际显示三条合成歌词
  （`06-lyrics-panel`，绘制文本不在UI XML中）；队列页面可见当前RC Silence 1及下一首RC Silence 2
  （`09-queue-first-track`），之前标题菜单崩溃未复现。截图均位于`/tmp/opencode/melox-rc-ui-latest/`。
- 冷启动观察到Search的插件目录可用、Settings显示Enabled，但LazyRow来源chip仍可能保留空列表；
  已改为按registry generation在LazyRow外获取不可变Provider列表。后续构建日志
  `/tmp/opencode/melox-rc-search-restore-build.log` **BUILD SUCCESSFUL in 2m27s**；520项单测中
  517通过、0失败/错误、3跳过。覆盖安装后冷启动来源chip正确显示并选中插件，截图/XML
  `11-cold-restored-source-chip`；同时MiniPlayer保留原插件歌曲身份。
- 原生设置页禁用插件后按钮变为“启用”（`10-disabled`）；重新定位到第一首并尝试播放，
  MediaSession为ERROR、position=0、buffered=0，歌曲元数据仍RC Silence 1。之前已完整缓冲的
  音频不等于禁用后立即清空解码缓存；不宣称已测量缓冲音频的最长持续时间。证据
  `/tmp/opencode/melox-rc-before-disable-media.txt`、`melox-rc-disabled-next-media.txt`、
  `melox-rc-disabled-reopen-media.txt`。随后重新启用并保留当前测试包。
- 最新构建SHA-256记录：`/tmp/opencode/melox-rc-latest-sha256.txt`。
  主APK `a1851637793c3891ff9418d635177eaf0e5004c08fa5a133e93c9ad898bcb5dd`；
  AndroidTest `c9e7e65080cc2433c538fc130b50b2921ca502e8f85246b1709f41cf191d26ef`；
  rc-app1.1.0 `adf461240202254fce98e7c14c065e1d0ab85184645f90e6d5f20f79b7f6f9b0`。

新增实现不能沿用上一批次的 PASS，整体仍为 **NO_GO**。

此前实际应用操作已观察到：应用引导、SAF 选择签名包、权限/指纹确认、安装、启用、
主来源设置和搜索结果。首次点击歌曲导致主进程崩溃，栈为 `MusicResourceId.getSource` →
`MeloXPlaybackUiState.refresh`；已修改为动态来源安全读取并曾单独构建成功，但正常播放复验未完成。
随后 API 35 模拟器出现 GPU pipe/HardwareRenderer ANR，改 skiavk 后 UI 曾恢复；不能据此
将播放器链路、后台、熄屏或进程恢复标为通过。日志保存于 `/tmp/opencode/melox-rc-app-anr.txt`、
`/tmp/opencode/melox-app-after-fix-logcat.txt`，截图/XML 位于 `/tmp/opencode/melox-rc-ui/`。

API 24 系统镜像已安装，AVD 启动受 userdata 空间限制，尚无 API 24 应用结果。实体 ARM64 未验收。

后续集中验证入口（仅列出，**本批次未执行**）：

- `MELOX_RC_FAULTS=1`：资源循环/内存/栈、真实 Binder 错 token 取消重放、有效取消强杀
  native-loop worker、后续 worker 恢复、存储迁移和跨来源清理子集。
- `MELOX_RC_APP_SERVICE=1`：需先实际导入/启用 rc-app，使用正常服务/controller 检查
  UI state、队列、暂停/seek/切歌、通知、后台/熄屏；不替代屏幕上的 MiniPlayer/歌词人工观察。
- `MELOX_RC_OAUTH_ORIGIN=https://CONTROLLED-DOMAIN/`：实际公网 HTTPS 兑换、刷新及
  多账号测试；runner 模拟浏览器同意，不替代系统浏览器和 OS 回调验收。
  服务部署及管理事件审计见 [合成 OAuth 服务说明](../../scripts/oauth-fixture/README.md)。
- `scripts/plugin-rc-crash-check.sh`：无签名更新、换密钥和签名降级分别在三个提交/两个回滚
  持久化边界强杀单独 instrumentation 进程并重启恢复；同时核对包和发布者记录。
- `scripts/plugin-rc-arm64-device.sh`：拒绝把模拟器作为实体证据，输出仍待人工填写的验收清单。
- 新增 `PluginWireFieldsTest`、`wire-fields.test.mjs`、`PluginDnsFaultTest`：全字段类型边界及
  合成解析器变化；DNS 单测不等于真实权威 DNS/socket 抓包验收。
- `scripts/oauth-fixture/dns-rebinding.py`：权威 DNS 的逐查询 A/AAAA 故障响应代码已编写；
  真实委派、有效 HTTPS 基线、缓存/连接复用及 socket 抓包仍未执行。

## Gradle

前序任务在 NTFS Gradle journal 写入处停滞，线程转储保存于
`/tmp/opencode/melox-gradle-threads.txt`。终止该任务后退出码 143；没有视为通过。

本轮使用单个构建进程，日志移到 tmpfs：

```sh
cd android
TMPDIR=/tmp/opencode JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp/opencode \
  ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest \
  --offline --max-workers=1 --console=plain
```

- 第一轮：Kotlin 编译成功，475 测试，4 失败、3 跳过。日志：
  `/tmp/opencode/melox-plugin-integration-gradle.log`。
- 修正动态来源测试契约、收藏保留逻辑和 IPv6 地址范围后，第二轮：
  **BUILD SUCCESSFUL，478 测试，0 失败、0 错误、3 跳过**。
  日志：`/tmp/opencode/melox-plugin-integration-gradle-2.log`。
- 最终轮（包含后续播放字段、媒体缓存路由及旧 UI 调用点修改）：
  **BUILD SUCCESSFUL in 30s，478 测试，0 失败、0 错误、3 跳过**。
  日志：`/tmp/opencode/melox-plugin-integration-gradle-final.log`。

Manager 测试验证共享 registry 的生命周期、版本冲突、候选准备失败不覆盖旧包；使用内存
repository，不等同于 Android 文件系统故障注入。全量测试也包括既有 Provider/LX/播放单测。

## SDK

```sh
cd plugin-sdk
npm ci
npm run typecheck
npm run build
npm test
node tools/cli.mjs pack examples/test-tone/manifest.json examples/test-tone/index.ts /tmp/opencode/melox-test-tone.melox-plugin
```

先执行 `npm install --ignore-scripts --no-audit --no-fund` 生成 lockfile，最终再实际执行
`npm ci --ignore-scripts --no-audit --no-fund`、类型检查、SDK 构建、4 项 Node 测试，全部通过。
测试覆盖共享 Manifest fixture、拒绝危险声明、标准 ZIP 记录/CRC32、打包后的示例搜索/
播放/WAV/歌词/无需登录状态和核心协议 Schema。

最终示例包：`/tmp/opencode/melox-test-tone-final.melox-plugin`。使用 JDK 17 JShell 加载
最终编译的 Kotlin 类、Kotlin stdlib 和 org.json，然后实际调用：

```java
var plugin = PluginArchiveReader.INSTANCE.read(
    Files.readAllBytes(Path.of("/tmp/opencode/melox-test-tone-final.melox-plugin")));
```

成功读取名称、版本 `1.0.0`、`[Search, Playback, Lyrics]` 及带 `meloxPlugin` 的入口脚本。
这是 SDK ZIP 与宿主 reader 的实际互通，不包含在 Android 上执行脚本。

## 音频服务

实际 HTTP 请求首次得到 400（URL-safe Base64 不符合服务要求）；修改为标准 Base64 并
URL encode 后，`https://httpbingo.org/base64/...` 返回：

```text
HTTP 200; Content-Type: audio/wav; 2044 bytes; RIFF/WAVE; URL length 2862
```

WAV 是示例插件原创生成的 250 ms 测试音，CC0，无第三方音乐授权问题。此检查确认远端
fixture 内容，不包含 Android 的解码/扬声器/歌词 UI。

## 设备

`adb devices -l`：没有连接设备。没有实际安装 APK、启用插件或完成 Media3 端到端演示。
所有设备、安全/认证未验证项见 [remaining-work.md](remaining-work.md)。

## 扩展能力与认证阶段

此阶段新增目录/收藏/下载/认证 Provider、动态来源浏览器、Keystore vault、Credential Broker、
公开客户端 OAuth2/PKCE、刷新及多账号管理。后续构建记录与前序结果分开保存：

- `/tmp/opencode/melox-plugin-expanded-validation.log`：核心扩展轮 BUILD SUCCESSFUL。
- `/tmp/opencode/melox-plugin-delivery-validation.log`：包含 OAuth/PKCE 与跨源 SourceId 的中间轮
  BUILD SUCCESSFUL in 1m 38s。其后又增加媒体凭据接线和回归测试，须以最终记录为准。
- SDK 当前 `npm run typecheck`、`npm run build` 和 **8 项 Node 测试全部通过**，包括目录/详情/
  home/library、收藏持久化、凭据引用与过期状态、OAuth Manifest 边界和标准返回 Schema。
- 新增 Kotlin 覆盖目录身份/total、能力拒绝/跨源输入、严格收藏状态、凭据子白名单/升级策略、
  RFC7636 S256 测试向量、OAuth Token 解析与 Manifest、动态聚合身份/分页/取消及能力方法表。

### 原来跳过的 3 项测试调查

| 测试 | 跳过原因 | 覆盖缺口 |
| --- | --- | --- |
| QQMusicRichLyricsIntegrationTest | 类上明确 `@Ignore`，需主动运行的 QQ 在线服务测试 | 当前 QQ 实际在线搜索/富歌词未验证 |
| JapaneseG2pGoldenTest | 缺少 `MELOX_JA_G2P_HOST_LIB` 和 `MELOX_JA_G2P_DIC` | 主机 native G2P golden 未执行 |
| BsPatchTest 的环境 fixture 用例 | 缺少 `MELOX_DELTA_OLD_APK`、`MELOX_DELTA_PATCH`、`MELOX_DELTA_NEW_SHA256` | 指定真实 APK 增量补丁 fixture 未执行 |

均为既有外部依赖/显式在线测试，不是插件新测试被跳过；也不能把它们记作通过。本轮没有
虚构 fixture 或移除 skip。G2P/补丁测试需要有效且匹配的输入，才能提供实际覆盖。

### 设备状态

本阶段再次执行 `adb devices -l`，仍无设备。新增 [device-acceptance.md](device-acceptance.md)
覆盖运行时、Media3、登录、网络及生命周期五组操作，**所有设备项仍为未执行**。

### 最终当前工作区验证

```sh
cd android
TMPDIR=/tmp/opencode JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp/opencode \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest \
  --offline --max-workers=1 --console=plain
```

- 最终日志 `/tmp/opencode/melox-plugin-final-confirmed.log`：**BUILD SUCCESSFUL in 39s**。
- JUnit XML 合计 **490 测试、0 失败、0 错误、3 跳过**；比前序 478 项新增 12 项。
- 生成当前 Debug APK：
  - `android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
  - `android/app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk`
- 本阶段中间轮有一次新增测试 fixture 错误（空 built-in Registry，不符合既有构造契约），
  已改为真实宿主形态的内置 + 动态 registry；另外修正新增 Compose import 与目录来源
  类型推断错误。失败轮没有计入最终通过结果。
- SDK `typecheck`、`build`、8 项 Node 测试通过，三个 CLI 包构建成功；使用 JDK 17 JShell
  加载编译后的 Kotlin classes.jar、stdlib 和 org.json，三个包均被实际 PluginArchiveReader 接受：

| 包 | 接受的能力数 | SHA-256 |
| --- | --- | --- |
| `/tmp/opencode/melox-catalog-delivery.melox-plugin` | 12 | `d22232bf1ab26c579b21a799136451a1918c3417c44e11b7639cdb88cc73b9df` |
| `/tmp/opencode/melox-api-token-delivery.melox-plugin` | 4 | `1f847455a63581dd44dabda3bc43eab06e4fb415f65567cc8ae1bbcef72245e6` |
| `/tmp/opencode/melox-test-tone-delivery.melox-plugin` | 3 | `e7fea111a88397944431b15463a3ffd453fe11b5a6c480eb4488fb23c8e34658` |

这是实际 SDK ZIP → Kotlin Manifest/归档读取互通，不是隔离进程脚本执行或设备安装验证。
本地开发文档链接检查为 0 断链；`git diff --check` 通过。

## 集成与安全加固最终轮

- 补齐下载页任务/失败列表、取消、重试和移除失败记录后再次集中构建：
  **BUILD SUCCESSFUL in 1m 43s**；日志 `/tmp/opencode/melox-plugin-delivery-confirmed.log`。
  本次最终 XML 仍为 **501 项、0 失败、0 错误、3 跳过**。下载列表交互仅编译通过，未做设备操作。
  该次中间编译暴露缺失 `TextButton` import，修正后重新完整执行通过。
- 集中执行上述 `assembleDebug testDebugUnitTest` 命令：**BUILD SUCCESSFUL in 1m 21s**。
  日志 `/tmp/opencode/melox-plugin-integration-hardening-final.log`。
- JUnit XML 实际汇总 **501 项、0 失败、0 错误、3 跳过**，即 498 项执行通过。
  3 项跳过原因同上，没有补造外部 fixture。
- 新增测试覆盖 IPC token/配额/方法白名单/错误脱敏、升级权限扩张先拒绝后批准、
  封面来源和 URL 边界、OAuth 回调重复/错误目标/互斥参数、音频权限与质量、严格歌词模型、
  分享链接缺失来源与不透明 ID 往返。测试类见 [交付清单](delivery.md)。
- 中间编译发现缺失 Compose import 与 suspend 回调错误，已修正；一次运行期间新文件加入
  导致测试编译读到旧主类产物。最终停止改动主代码后重新完整执行通过，失败轮不计入通过。
- SDK `npm run typecheck`、`npm run build`、`npm test`：全部通过，**8/8 Node 测试**。
  共享 Manifest fixture 追加作者/主页、全部能力、Token 子域权限与 OAuth 端点边界。
- CLI 重新打包，并在 JDK 17 JShell 中加载本轮编译的 Kotlin classes.jar 实际读取成功：
  `/tmp/opencode/melox-catalog-hardening.melox-plugin`（12 能力）、
  `/tmp/opencode/melox-api-token-hardening.melox-plugin`（4 能力）、
  `/tmp/opencode/melox-test-tone-hardening.melox-plugin`（3 能力）。
  这仍只是归档/Manifest 互通，未执行 Android QuickJS。

### 实际公开网络 Range 检查

用编译后的 `PluginHttpBroker.mediaClient(false)`（非普通 curl），以仅声明
`httpbingo.org` 的无账号 Manifest 请求 `https://httpbingo.org/range/1024`：

| 请求 | 实际状态 | Content-Range | 实际读取字节 |
| --- | --- | --- | --- |
| `Range: bytes=0-15` | 206 | `bytes 0-15/1024` | 16 |
| `Range: bytes=2048-4095` | 416 | `bytes */1024` | 33 |

证明专用 broker 的实际公开 HTTPS 出口能保留 Range/206/416；不是 Android Media3 seek、
凭据注入、DNS 重绑定或恶意重定向验收。测试服务不携带任何账号凭据。

### 本轮设备结果

再次执行 `adb devices -l`，列表为空。**安装、隔离 UID、Binder、QuickJS、Keystore、
Coil 解码、真实 OAuth 和 Media3 全链路均未执行设备验证。** 可执行准备命令已补入
[device-acceptance.md](device-acceptance.md)。当前仍为开发预览，不具备正式稳定发布证据。

## RC 工程验收（2026-11-05，最新）

### 构建与 SDK

```sh
cd android
TMPDIR=/tmp/opencode JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp/opencode \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest \
  -PmeloxTestInstrumentationRunner=com.lladlam.melox.core.plugin.PluginRcDeviceRunner \
  --offline --max-workers=1 --console=plain
```

- 最终日志 `/tmp/opencode/melox-plugin-rc-acceptance-final.log`：**BUILD SUCCESSFUL in 1m 50s**。
- 增加 SDK 签名 catalog 的全部能力设备调用测试后，再执行同一命令：
  `/tmp/opencode/melox-plugin-rc-catalog-final.log`，**BUILD SUCCESSFUL in 1m 8s**。
  生产源码和 Kotlin 单测未再改动，相关任务复用上轮结果；AndroidTest 已重新编译打包。
- XML 合计 **510 项，507 通过、0 失败、0 错误、3 跳过**。3 项原因见上表；没有计为通过。
- SDK `typecheck`、`build` 与 **10/10 Node 测试**通过。新增包完整性/签名与不支持媒体/封面 Schema 回归。
- 新增 Kotlin 覆盖签名篡改/文件增删、换密钥/去签名的精确包批准、JSON 深度与入口限制、HTTP
  类型强制转换拒绝、HLS/DASH/DRM、嵌套扩展保留和推荐候选未知来源。旧兼容测试原先断言
  “未来字段丢失”，现改为断言保留，未移除该回归。
- 中间构建一次暴露上述旧测试预期；一次在主编译完成后才加入新测试文件导致类产物过旧。
  最终源码冻结后完整构建通过，失败轮没有计为通过。
- CLI 生成 `/tmp/opencode/melox-catalog-rc-signed.melox-plugin`，编译后的 Kotlin reader
  实际接受全部 12 能力、integrity=true、RSA 签名验证成功。合成发布者公钥指纹为
  `fbefaffe720ce7b11a85527c6fd9a7aae5c5d3046fac8363fffd55042683218d`；不是项目正式发布者密钥。
  私钥只位于临时测试目录，不在源码、包或文档中。

### 可用设备环境

- `/dev/kvm` 可用。Emulator 37.2.12 位于 `/mnt/ntfs_files_sda4/android-sdk/emulator/emulator`。
- 可用系统镜像为 **API 35 Google APIs x86_64 revision 9**；已有 AVD `melox-http-disk`。
- 用 `-read-only -no-window -no-audio -no-snapshot -gpu swiftshader_indirect` 启动，
  ADB 为 `emulator-5554`，boot_completed=1。
- ABI 为 `x86_64,arm64-v8a`，native bridge 为 `libndk_translation.so`。当前 ARM Debug 包通过
  转译执行，不能替代 ARM 真机兼容性/性能结论。已安装 SDK platforms 34/37；未安装 API 24 系统镜像。
- 正式 applicationId 为 `com.lladlam.melox.android`，Debug 为 `.dev` 后缀，验收脚本使用 Debug ID。

### 实际设备自动化结果

```sh
ANDROID_SERIAL=emulator-5554 MELOX_RC_ONLINE=1 \
  MELOX_RC_CATALOG=/tmp/opencode/melox-catalog-rc-signed.melox-plugin \
  bash scripts/plugin-rc-device-check.sh
```

最终 `/tmp/opencode/melox-plugin-rc-device-result.log`：**rcStatus=PASS**，实际输出包括：

| 项目 | 实际结果 | 边界 |
| --- | --- | --- |
| Messenger 身份 | 伪造 sendingUid 被系统覆盖；最终 worker UID 99030、宿主 10209；worker INTERNET denied | 验证真实 Binder/Messenger，不是 Bundle 自报 UID |
| 外部服务访问 | shell startservice 被 `not exported` 拒绝 | 非导出检查；不是恶意应用获取泄漏 Binder 后的完整红队测试 |
| 生命周期 | 安装/启用/升级、候选执行失败回滚、重建 Manager 恢复、禁用、卸载通过 | 合成包和 sandbox 文件目录；不是 SAF UI 或真实断电 |
| AtomicFile/损坏包 | 实际 .bak 恢复、损坏包隔离、重导入默认禁用、卸载后保留 trust | 不是全部磁盘 I/O/断电故障注入 |
| 队列 | 真 SharedPreferences 读写保留缺失 SourceId、歌手/专辑 ID、扩展字段及位置 | 使用 fake Player 提供数据；不等于旧 APK 降级 |
| QuickJS | Promise、storage Broker、无 fetch/Java、watchdog、协程取消与后续重绑成功 | 尚未覆盖全部 native OOM/FD/长时间压测 |
| Keystore | 合成 Token 加解密、多插件/多账号隔离、A→B→A 会话失效 | 没有真实平台凭据；未测设备锁变化/密钥失效 |
| SDK 12 能力 | 签名后的实际 CLI catalog 包经 Android 完整性/RSA 校验；全部 Provider 方法通过隔离 QuickJS 调用，详情/分页/跨 Context 收藏持久化通过 | 不是全 UI、真实平台 OAuth 或完整下载落盘测试；使用 sandbox 普通存储 |
| Coil | 1×1 PNG 经专用出口解码；4097 像素宽度拒绝；来源移除后请求失败 | 合成公开 HTTPS fixture；非全部图片格式与解码器攻击测试 |
| Media3 | 真实 QuickJS resolve → 专用 Broker → WAV 解码播放、seek、两首连续切换到 ENDED | 测试播放器静音；未验证系统后台服务、熄屏、焦点、401刷新与长音频 |

在线 fixture 使用原创 CC0 音调与本地生成 PNG，经公开 httpbingo HTTPS 返回，不含账号凭据。
第一次脚本使用正式包名无法找到 Debug instrumentation，修正 `.dev` 后重新安装和执行通过。

### 未执行及发布判断

API 24、实体 ARM 设备、SAF/URI 授权生命周期、真实受控 OAuth 兑换/刷新、完整目录/账号 UI、
后台/熄屏/媒体会话、密钥失效、native 内存耗尽、DNS rebinding/socket 抓包、完整断电与旧 APK
降级仍未执行。当前具备 RC 工程与部分真实设备依据，**不是正式稳定版验收通过**。
逐项发布阻塞见 [RC 工程验收](rc-acceptance.md) 与 [remaining-work.md](remaining-work.md)。
