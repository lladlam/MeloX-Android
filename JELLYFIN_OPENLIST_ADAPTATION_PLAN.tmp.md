# Jellyfin / OpenList 音乐源适配临时计划

## 目标

为 MeloX 增加 Jellyfin 和 OpenList WebDAV 音乐源，并统一接入现有音乐源接口。

## 核心规则

- Jellyfin 和 OpenList 在业务架构上都按内置音乐源 Provider 接入。
- “第三方音乐源”只表示设置页面分组，不代表走 CHKSZ/LX 解析链路。
- 当前选择的音乐源是整个普通界面的唯一数据上下文。
- 选择某个音乐源后，搜索、音乐库、收藏、播放列表和详情只能读取该源。
- 不允许因为当前源是 Jellyfin/OpenList 而加载网易云、QQ、酷狗等其他源的数据。
- 跨平台聚合搜索只在用户明确开启后作为独立例外，不影响普通源页面。
- 每个页面和入口必须依据 Provider 的 `MusicCapability` 判断是否显示。
- 平台不支持的能力直接隐藏，不显示空页面或无效按钮。

## Jellyfin 范围

- 仅支持音乐，不支持电影和电视剧。
- 仅支持一个 Jellyfin 服务器。
- 使用 Jellyfin 用户登录获取 AccessToken、UserId 和 ServerId。
- Token 使用 Android Keystore 加密保存。
- Jellyfin 作为完整统一 Provider 接入：
  - 音乐库
  - 搜索
  - 艺术家
  - 专辑
  - 歌曲详情
  - 音频播放
  - 收藏同步
  - 播放列表同步
- Jellyfin 使用自己的 ItemId 作为资源标识，格式为：

  ```text
  jellyfin:<serverId>:<itemId>
  ```

- Jellyfin 播放直接使用 Jellyfin 音频流和认证 Token。
- Jellyfin 不受 `ThirdPartyMusicSourceConsentStore`、CHKSZ 或 LX 开关控制。

## OpenList WebDAV 范围

- OpenList 只适配音乐文件。
- 使用方案 A：收藏和播放列表只保存在 MeloX 本地。
- 不实现远程 `.m3u` 上传，除非后续明确提出需求。
- 不声明歌单、歌单写入或远程收藏能力。
- 通过 WebDAV `PROPFIND` 递归扫描音频文件。
- 建立本地音乐索引并解析音频元数据：
  - 标题
  - 艺术家
  - 专辑
  - 曲号
  - 年份
  - 时长
  - 内嵌封面
- ExoPlayer 通过 WebDAV URL、认证 Header 和 Range 请求直接播放。
- OpenList 资源标识使用服务器地址哈希和远程路径，避免与其他 Provider 冲突。

## 设置页面顺序

在“第三方音乐源”分组中按以下顺序显示：

```text
Jellyfin
OpenList WebDAV
CHKSZ解析源
LX 添加音乐源
```

Jellyfin 和 OpenList 的设置项不依赖第三方解析源协议开关。

## 能力隔离

### Jellyfin

- 显示 Jellyfin 支持的音乐库、搜索、专辑、艺术家、收藏和播放列表。
- 只读取和写入 Jellyfin 自己的收藏与播放列表。

### OpenList

- 显示 OpenList 音乐索引、搜索、专辑、艺术家和本地收藏。
- 隐藏歌单列表、歌单详情、歌单编辑、远程歌单同步入口。

### 其他音乐源

- 选择某个平台后只显示该平台自己的内容。
- 不再使用“非网易云默认显示歌单”这类粗粒度判断。
- 页面、搜索类型和操作按钮统一由 Provider capability 控制。

## 实现阶段

### 阶段一：源上下文和能力门控

- 当前音乐源贯穿搜索、发现、音乐库、详情和操作页面。
- 删除基于 `source != Netease` 的旧页面判断。
- 根据 Provider capability 隐藏不支持的 Tab、媒体库分类、搜索类型和操作。
- 确保选中 Jellyfin 时不会出现其他平台歌单或收藏。
- 确保选中 OpenList 时隐藏歌单功能。

### 阶段二：Jellyfin Provider

- 登录、会话存储、服务器验证。
- 音乐库、搜索、详情、播放。
- 收藏和 Jellyfin 播放列表同步。

### 阶段三：OpenList Provider

- WebDAV 连接配置。
- 递归扫描和本地索引。
- 元数据和封面解析。
- 搜索、专辑、艺术家和播放。
- 本地收藏，不提供远程歌单能力。

### 阶段四：回归验证

- 每个阶段结束执行 Debug 构建。
- 构建失败必须在当前阶段修复后才能继续。
- 运行单元测试和最终 Debug 构建。
- 安装最终 Debug APK 到设备，不自动启动。
- 未完成真实 Jellyfin/OpenList 服务器联调前，不发布 Release。

## 当前代码状态

- Jellyfin 已初步注册为统一 Provider。
- Jellyfin 已具备基础登录、搜索、目录、播放和部分收藏/播放列表 API 适配。
- 设置页已出现 Jellyfin 入口，且已放在 CHKSZ 之前。
- OpenList 尚未实现。
- 当前仍需完成严格的源上下文隔离和 capability 驱动页面隐藏。
- 本文件为临时计划，任务完成后可删除或改名为正式文档。
