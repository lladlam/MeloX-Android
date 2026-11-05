# API Token Demo

仅用于验证宿主凭据代理，**只输入一次性虚构测试 Token**。示例会向公开测试服务
`https://httpbingo.org/bearer` 发送 Bearer 请求；它不是任何音乐平台的授权端点。

```sh
# 从 plugin-sdk 目录执行
node tools/cli.mjs pack examples/api-token/manifest.json examples/api-token/index.ts /tmp/opencode/api-token.melox-plugin
```

导入并启用后，在插件设置填写账号名及测试 Token，选择“导入并使用凭据”，再检查会话状态。
插件只提供 `credential: "active"` 引用；host.account 只含 ID/名称。响应可能回显测试
Token，示例不会返回或记录响应体。可创建两个账号并切换/删除，验证 UI 与网络行为。
普通搜索/播放/歌词复用 CC0 test-tone，不需要登录。

SDK 自动化验证覆盖请求引用与过期状态映射，不包含 Keystore/Binder/实际设备网络验证。
真实平台请用自己合法取得的公开 API 和凭据域名策略；OAuth 配置见开发文档。
