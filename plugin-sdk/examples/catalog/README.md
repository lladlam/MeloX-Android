# CC0 Catalog Demo

完整合成目录，包含 Search、Playback、Lyrics、Playlists、Albums、Artists、Rankings、
HomeFeed、UserLibrary、Favorites、Download 和无需登录 Authentication。
声音来自 test-tone 示例原创生成的 250 ms、440 Hz WAV；内容按 CC0 使用。

```sh
# 从 plugin-sdk 目录执行，输出文件不能已存在
node tools/cli.mjs pack examples/catalog/manifest.json examples/catalog/index.ts /tmp/opencode/catalog.melox-plugin
```

Android 设置 → 音乐源插件 → 导入 → 启用。现有搜索页选择本插件，查看各栏目，点击目录
进入歌曲；歌曲点击使用现有播放器，滑动或菜单下载，菜单“音源收藏状态”读写示例收藏。
歌曲搜索输入 `tone`；专辑/歌手/歌单可输入空关键词浏览。每页 30 条，此示例只有一首曲目。
普通收藏写入 host.storage；新 JS Context 仍能读取，使用账号时按账号隔离。

示例音乐库不需要平台账号，accountSummary 返回 null；这不代表实现了真实平台登录。
SDK 测试已执行，Android 设备安装与 Media3 行为尚未验证。
