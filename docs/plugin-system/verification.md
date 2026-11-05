# 实际验证记录

最新结论见文末“集成与安全加固最终轮”；此前 478/490 项记录为历史阶段结果。

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
