# MeloX 插件开发文档

**状态：核心、目录、账号及下载接线的开发预览；仍有剩余工作，未完成真机端到端验证。**

## 开发者入口

1. [SDK 使用、真实代码、打包与安装](../../plugin-sdk/README.md)
2. [API v1、数据模型与能力](03-protocol-v1.md)
3. [运行时、生命周期、HTTP/存储/日志、安全边界](04-runtime-and-security.md)
4. [CC0 测试音完整示例](../../plugin-sdk/examples/test-tone/README.md)
5. [实际验证结果](verification.md)
6. [未完成与尚未验证事项](remaining-work.md)
7. [全部已接线 Capability、API Token 与 OAuth2/PKCE](05-capabilities-and-authentication.md)
8. [完整设备验收清单](device-acceptance.md)
9. [交付文件、核心链路及 12 项能力证据表](delivery.md)

## 工程背景

- [仓库审计](01-repository-audit.md)：前序调用链与风险记录。
- [目标架构](02-architecture.md)：完整系统的设计目标，不等同于已实现能力。
- [认证审计与设计](11-authentication.md)：前序审计；当前实现另见能力与认证接入文档。
- [实施进度](implementation-progress.md)：当前源码接线状态。

插件作者承担全部第三方平台适配。宿主只验证标准协议并映射到现有 Provider、UI、Media3
和歌词模型；不允许把平台原始响应交给宿主猜测转换。不支持的能力必须不声明。
