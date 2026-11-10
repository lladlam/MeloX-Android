# 本地 MCP 服务（测试功能）

在 **设置 → 测试功能尝鲜 → 本地 MCP 服务** 开启。服务只监听
`127.0.0.1:8765`（端口被占用时会顺延到 8766–8774，以设置页显示的端点为准），
不会暴露到局域网；默认关闭。

开启后，设置页会显示：

```text
Endpoint: http://127.0.0.1:8765/mcp?token=...
Token: ...
```

MCP 客户端需要支持 Streamable HTTP。把完整 Endpoint 填入客户端即可；也可以使用
`http://127.0.0.1:8765/mcp` 并在请求头中发送：

```text
Authorization: Bearer <Token>
```

## 从电脑连接

如果 AI 客户端运行在电脑上，先把手机端口转发到本机：

```bash
adb forward tcp:8765 tcp:8765
```

然后使用：

```text
http://127.0.0.1:8765/mcp?token=<Token>
```

## 工具

| 工具 | 作用 |
| --- | --- |
| `get_playback_status` | 当前播放状态、歌曲、进度、音量、随机/循环 |
| `get_queue` | 当前播放队列 |
| `play` / `pause` / `toggle_playback` | 播放控制 |
| `next_track` / `previous_track` / `stop_playback` | 切歌与停止 |
| `seek` | 跳转，参数 `positionMs` |
| `set_volume` | 设置播放器音量，参数 `volume`（0–1） |
| `set_shuffle` | 开关随机播放 |
| `set_repeat` | `off` / `one` / `all` |
| `search` | 搜索指定音源歌曲 |
| `search_and_play` | 搜索后用结果替换播放队列，参数 `query`、`source`、`index` |

音源 ID 与 CLI 相同：`netease`、`qq_music`、`kugou_lite`、`kuwo`、`qishui`、
`bilibili`、`spotify`、`youtube_music`、`jellyfin`、`local`。

## 安全边界

- 仅绑定回环地址，不接受局域网连接。
- 每次连接必须携带 Token；Token 保存在本机应用私有存储中。
- 服务可控制 Media3 播放队列，但不会返回账号 Cookie 或 Token。
- 关闭开关后端口立即停止监听。
- Apple Music 官方 SDK 的独立播放路径不受此接口控制。

## 协议范围

当前实现提供 MCP 的 `initialize`、`tools/list`、`tools/call`、`ping`，
以及空的 `resources/list` / `prompts/list`。未实现资源订阅、采样和服务器主动推送。
