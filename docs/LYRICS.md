# 歌词来源、格式与实现参考

## 自动获取

开启自动选择后，当前音源、AMLL、QQ、网易云及酷狗候选并发查询；当前音源本身是其中一个平台时避免重复查询。酷狗和酷狗概念版共用 KRC 能力，不额外重复查酷狗。

- AMLL 的真实逐字 TTML 优先，其次是真实逐字歌词（QQ QRC、网易云 YRC、酷狗 KRC），最后是逐行歌词。同类按既有来源顺序选择。
- 所有候选并发查询，并在各来源完成或超时后统一选择；不会因当前音源先返回逐行歌词而取消较慢的逐字歌词请求。网络慢时首次显示歌词可能需要更久。
- “此歌曲为没有填词的纯音乐，请您欣赏”等已知占位文本不视作有效歌词，不据此认定歌曲一定没有人声，也不阻断其他来源。
- 网易云当前接口失败、为空或只返回占位时，补查不附带账号 Cookie 的公开歌词接口。
- QQ Desktop 搜索不可用或无结果时，补查 Mobile 协议；展开同曲分组，在第一条没有有效歌词时继续尝试通过版本、歌手和时长校验的条目。标题尾部的 `feat.` / `ft.` 仅在确实对应艺人署名时移除后参与匹配。
- 歌词客户端允许匿名调用，但个别条目可能返回空数据或占位；账号、地区及上游接口状态可能影响结果。不能保证每首歌的逐字歌词都能匿名获取。
- 接口的 `lyric` 字段也可能是不带任何时间戳的正文：保留文字，以可手动滚动的静态歌词显示并提示“暂无时间轴”，不伪造逐行或逐字同步。真正同步的歌词优先于无时间轴文本。

## 本地歌词格式

KRC 是酷狗歌词格式；Lyricify Lines 和 Lyricify Syllable 是文本格式，并非独立的在线歌词服务。

通过本地音乐的文件夹授权入口添加目录并重新扫描，将歌词与音频放在同一目录、使用相同文件名主干（例如 `Song.mp3` 和 `Song.lrc`）。扫描器会读取同名 `.lrc`、`.krc`、`.qrc`、`.ttml`、`.lys`、`.lyl`、`.txt` 文件并按内容识别格式；多个歌词文件并存时优先逐字覆盖较多的文件。仅使用系统媒体库扫描不会获得旁边文本文件的读取权限，需要文件夹授权。

| 格式 | 支持内容 |
| --- | --- |
| KRC | `krc1` 二进制解码和明文 KRC；相对逐字时间 |
| Lyricify Lines | `[type:LyricifyLines]`、`[起始毫秒,结束毫秒]文本`；第二个数字是结束时间，不是时长 |
| Lyricify Syllable | `[属性]文本(起始毫秒,时长)…`；真实逐字时间、左右对唱、显式伴唱属性 |
| LRC / QRC / TTML | 继续使用现有解析器 |

Lyricify 格式支持 `[offset:毫秒]`：从原始时间中减去该偏移。Lines 保留行结束时间，不伪装成逐字歌词。自定义音源返回的歌词文本也使用同一格式识别入口。

## Lyricify Lyrics Helper 对照记录

- 仓库：[WXRIW/Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper)
- 对照提交：`3fc579b029c24a1f54ebb2fd002ee3624c85c7e7`
- 许可证：Apache-2.0。
- 重点阅读：`Providers/Web/QQMusic/Api.cs`、`Searchers/QQMusicSearcher.cs`、`Helpers/InstrumentalHelper.cs`、`Decrypter/Qrc/`、`Parsers/LyricifyLinesParser.cs`、`Parsers/LyricifySyllableParser.cs`。
- QQ Mobile 搜索：`u6.y.qq.com`，`ct=11`、`v=20080508`，不同时带 `cv`；完整请求别名和 UTF-8 JSON 正文，使用 form Content-Type；默认匿名。
- QQ 歌词：普通 LRC 走 `fcg_query_lyric_new.fcg`；逐字下载走 `lyric_download.fcg`，使用数字歌曲 ID，解密解压 XML 中的原文、翻译等字段。MeloX 保留原有多种 PlayLyricInfo 请求协议。
- 这是协议、格式和行为对照后的 Kotlin 实现；没有把 .NET 库作为 Android 运行时依赖。

2026-10-31 实测《非常简单》：QQ `004F0iEx16jLMW`（500877917）匿名返回纯音乐占位；Mobile 搜索还发现 `003pITel4NqRP4`（500877051），带榛葉nami 署名、同为 202 秒，匿名普通接口返回 86 行原始文本，没有时间戳。网易云 `2607641032` 的公开接口返回 91 行原始文本，同样没有时间戳。旧解析器把这两份正文当作空歌词；新实现保留为静态歌词。行数包含原始文件元信息，不等同于最终渲染行数；接口数据可能变化。
