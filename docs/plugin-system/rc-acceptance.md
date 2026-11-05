# RC 工程与安全验收

## 版本与检查点

最新推送检查点为 `305801a`（用户明确授权直推 main，远端哈希已确认）。该检查点含未验证
实现；推送后新增的下载续传/任务恢复/持久诊断仍在本地。本阶段不修改正式签名或创建 Release。

开发预览检查点 `4af356f` 已推送至 Android 仓库。后续 RC 修改保留本地，不修改正式签名、
版本号或创建 Release。建议下一发布标识为插件子系统 `0.1.0-rc.1`（建议，未发布）；
API 协议保持 v1，包签名 envelope 单独标识 v1。App 的正式版本策略由维护者决定。

## 实际安全审查与修复

| 边界 | 检查与处理 | 证据/限制 |
| --- | --- | --- |
| Messenger 调用方 | `Message.sendingUid` 由 Android Messenger Binder 实现填写，不读取 Bundle 的 UID；worker 对比应用 UID，宿主钉住本次 worker UID | 设备 runner 主动设置伪造 sendingUid，检查系统覆盖和隔离 UID；结果另见 verification |
| 绑定/方法 | service 非导出且 isolatedProcess；Host 方法根据 Manifest 白名单；没有通用反射路由 | Manifest、IpcPolicy 单测；API 35 实际 shell startservice 被 not exported 拒绝 |
| 调用 token | 每次调用随机 UUID，仅内存存在；回复必须匹配，terminal 后停止接受；全局串行 worker | IPC 策略回归；进程重启不恢复调用 token |
| JS 出口 | 不注入 Java/Context/fetch；仅 HTTP/storage/log 回调，宿主再次验证 | runner 验证 fetch/Java 未定义及真实 storage IPC；不是 native 引擎漏洞不存在的证明 |
| DNS/SSRF | 精确域名、HTTPS/443、无 userinfo/代理；返回的同一 DNS 地址列表用于连接，任一非公网地址拒绝 | IPv4/IPv6 分类单测；受控 DNS rebinding/socket 抓包仍待验收 |
| 凭据/重定向 | 按插件 vault、账号会话戳、导入时授权策略和子域白名单注入；逐跳重新授权，跨域清自定义头；媒体不跟随重定向 | CredentialPolicy 回归；不能阻止授权服务故意在响应中回显凭据，插件/授权域必须可信 |
| 升级授权 | 扩权批准准确 Manifest；发布者变化另需批准准确候选包 SHA-256，执行候选前检查 | Manager 与签名回归；首次信任须独立核对指纹 |
| 资源 | 包 4 MiB/展开 8 MiB/64 文件；新增安装前脚本 128 KiB、Manifest 32 KiB限制及 JSON 深度32 | RcSecurity 回归；QuickJS native OOM/取消资源仍需设备覆盖 |
| 图片/歌词 | 图片专用无凭据网络、2 MiB/800万像素；歌词行数/时序/每字段长度限制 | 自动化边界测试；Coil 解码与 UI 内存需设备验证 |
| 媒体 | 明确拒绝 HLS/DASH URL/format、DRM/license 字段及自适应 MIME | RcSecurity 回归；伪造为音频 MIME 的内容仍可能只在解码时报错，不支持 DRM 探测/解密 |

## 包完整性与发布者信任

SDK 新包包含 `integrity.json`，列出除 integrity/publisher 外**全部文件**的 SHA-256。
可选 `publisher.json` 包含 `RSA-SHA256`、SPKI DER 公钥 Base64 和签名 Base64。
签名输入是 ASCII `MeloX-package-v1\n` 拼接 **integrity.json 原始字节**；使用 RSA PKCS#1 v1.5
SHA-256，公钥 2048–4096 位。哈希文件集合必须与载荷完全相等。校验先于脚本执行。

- 旧包无 integrity 时仍可导入，UI 明确显示“未验证发布者”。无签名完整性清单也不证明发布者。
- 已安装包是当前批准公钥的记录；本地 trust 文件保存 SourceId、公钥及指纹，卸载保留，
  重装不能无提示切换发布者。这里是本地首次信任（TOFU），没有全局 CA、证书撤销或官方背书。
- 同 ID 更新换密钥、签名降为无签名、无签名升为签名，都要求独立勾选并批准候选哈希。
- 最新实现改为预写事务决策（新/旧 archive 哈希和旧 trust 快照），然后原子写包和 trust。
  重启按磁盘实际 archive 完成提交或恢复旧 trust；回滚再次中断也保留恢复依据。
  未完成首次安装的日志也参与启动恢复。启用前检查包与已记录发布者一致。
  这些修改及 `scripts/plugin-rc-crash-check.sh` **尚未运行验证**；不宣称真实断电或
  package/trust/enabled 多文件全局事务已经通过。
- 损坏包从执行目录移至私有 quarantine，显示独立错误和删除隔离副本操作；重新导入可信原包
  恢复安装，初始禁用。隔离副本不会自动执行。

签名示例（私钥仅由插件作者本地保管，不加入仓库或插件包）：

```sh
node plugin-sdk/tools/cli.mjs pack plugin-sdk/examples/catalog/manifest.json \
  plugin-sdk/examples/catalog/index.ts /tmp/opencode/catalog-signed.melox-plugin \
  --sign-key /path/to/publisher-private.pem
```

## 卸载与数据策略

卸载确认默认保留账号、普通存储、收藏、历史、队列身份、下载和信任记录。可勾选删除
该插件的 Keystore 凭据及当前已知账号/匿名普通存储。共享媒体/图片缓存没有插件字节缓存。
历史与下载保留原 SourceId，恢复安装后可重新解析，不回退网易云。
已删除账号遗留的旧格式普通存储可能没有可反查归属信息，不声称该选项能清除全部历史孤儿文件；
这是待迁移/清理缺口。删除是本地动作，不是平台服务器 Token 撤销。

## 数据升级

Track JSON 新增不参与网络授权的 opaque 扩展保留：歌曲顶层、歌手、专辑及未来 metadata
在身份不变的读改写中保留未知字段；下载索引增加完整 track，同时保留旧扁平字段供旧版读取，
并改为 AtomicFile 写入和校验文件名。`PluginDataUpgradeTest` 使用旧内置数据及缺失插件的
嵌套数据验证读取/修改/再次读取。它是兼容 codec 测试，不是安装真实旧 APK 的降级测试。

## 设备自动化

```sh
cd android
TMPDIR=/tmp/opencode JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp/opencode \
  ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest \
  -PmeloxTestInstrumentationRunner=com.lladlam.melox.core.plugin.PluginRcDeviceRunner \
  --offline --max-workers=1 --console=plain
cd ..
ANDROID_SERIAL=emulator-5554 MELOX_RC_ONLINE=1 \
  MELOX_RC_CATALOG=/tmp/opencode/melox-catalog-rc-signed.melox-plugin \
  bash scripts/plugin-rc-device-check.sh
```

runner 使用合成账号，覆盖真实 Messenger UID、非导出标记、隔离 UID 无 INTERNET、QuickJS
Promise/storage IPC、无 fetch/Java、watchdog 后重绑、Keystore 多账号/多插件和会话戳。
每步完成后输出 PASS，失败返回 rcStatus=FAIL；脚本检查最终状态，不仅检查 adb 退出码。
实际运行结果和环境记录在 verification.md。SAF、Coil、Media3、OAuth 和完整 UI 生命周期另按
[device-acceptance.md](device-acceptance.md) 执行，未运行的部分不得视为通过。

`MELOX_RC_CATALOG` 指向上面 CLI 打出的签名 catalog 包；路径可自行设置，私钥应为一次性测试
密钥。脚本仅在 Debug app 的 cache 中暂存公开插件包；runner 使用 sandbox 存储执行实际包的
全部 12 能力调用，结束删除测试副本，不改用户收藏或账号。缺少该变量时输出 NOT_RUN。
`MELOX_RC_ONLINE=1` 执行公开 PNG/WAV 的 Coil 和 Media3 测试；默认不请求网络。
历史一批的 catalog/online 开关已在 API35执行通过，包括协程取消后的立即重绑定。
后续新增 faults/wireFields/appService/oauthOrigin 开关须分别看最新验证记录，不能沿用历史 PASS。

## RC 判定与阻塞项

最新 Debug/AndroidTest 构建成功，520项 Kotlin 中517通过、3跳过，SDK 12/12通过；
API35新增故障/全字段子集、15组包提交/回滚进程强杀、正常服务/controller与后台/熄屏均有
本次通过证据；真实SAF安装/扩权更新取消与确认、完整播放器/歌词/队列及冷启动来源恢复已复验。
这些仍是转译模拟器子集，适合继续 RC 内部验收。
不建议宣布稳定版或已完成全部安全审计验证。API24/ARM真机、真实受控OAuth/下载Range/媒体撤销、
DNS/socket红队、焦点/长音频、物理断电/磁盘写失败、旧APK降级及遗留孤儿数据清理仍阻塞正式验收。
更新扩权和换密钥不是无提示操作，但本地 TOFU 和多文件持久化也不是全局信任/事务保证。
