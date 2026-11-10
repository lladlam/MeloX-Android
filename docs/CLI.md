# CLI 模式（测试功能）

在 **设置 → 测试功能尝鲜 → CLI 模式** 开启。电脑需要 Python 3.8+、ADB，以及已授权的 USB / 无线调试连接。
开关默认关闭，重启后保留；关闭后所有命令会返回错误。入口使用系统 `DUMP` 权限，仅供 ADB shell / 具备对应系统权限的调用方使用。

## 使用

在仓库根目录执行；正式版默认包名为 `com.lladlam.melox.android`，Debug 版请加 `--package com.lladlam.melox.android.dev`。
多设备时加 `--serial 设备序列号`，全局选项放在子命令前。

```bash
python3 tools/melox_cli.py status
python3 tools/melox_cli.py play
python3 tools/melox_cli.py pause
python3 tools/melox_cli.py toggle
python3 tools/melox_cli.py next
python3 tools/melox_cli.py previous
python3 tools/melox_cli.py stop
python3 tools/melox_cli.py queue
python3 tools/melox_cli.py seek 60000
python3 tools/melox_cli.py volume 0.5
python3 tools/melox_cli.py shuffle on
python3 tools/melox_cli.py repeat all
python3 tools/melox_cli.py search '晴天 周杰伦' --source netease
python3 tools/melox_cli.py search-play '晴天 周杰伦' --source netease --index 0
python3 tools/melox_cli.py --package com.lladlam.melox.android.dev status
```

- `seek` 单位为毫秒，`volume` 为播放器音量（0–1），`repeat` 接受 `off / one / all`。
- `search` 返回指定音源前 10 条结果；`search-play` 重新搜索并以结果建立播放队列，`--index` 从 0 开始。
- 搜索与播放复用应用的音源账号、解析和回退链；使用对应音源存储 ID，如 `netease`、`qq_music`、`qishui`、`kugou_lite`、`kuwo`、`bilibili`、`spotify`、`youtube_music`、`jellyfin`、`local`。
- 命令输出 JSON，成功退出码 0、失败 1；单次处理最多 8 秒，网络超时可重试。播放命令成功表示操作已发送，音频解析与缓冲仍可异步进行，可随后用 `status` 查询。
- `status` 包括媒体 ID、标题、歌手、进度、时长、播放状态、音量和循环设置；不返回账号 Cookie 或 Token。
- CLI 操作 Media3 播放队列；Apple Music 官方 SDK 独立播放路径不在此入口控制范围内。

## 本地 MCP

同一套命令也可通过实验性本地 MCP 服务调用，详见 [MCP.md](MCP.md)（仅回环监听，需要 Token）。

## 直接使用 ADB

```bash
adb shell am broadcast --include-stopped-packages \
  -n com.lladlam.melox.android/com.lladlam.melox.platform.cli.MeloXCliReceiver \
  --es command seek --es value 60000
```

广播 `data="melox:…"` 是 Base64 编码的 UTF-8 JSON，脚本负责解码与参数转义。
