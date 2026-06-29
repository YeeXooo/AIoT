# AiOT 前端（HarmonyOS ArkTS）

> 家属 APP + 车队大屏，基于 ArkTS 开发。数据模型与接口契约见 `docs/ood_interface.md` §4。

- `pages/` — 页面（登录、首页、监控、救援、设置、车隊大屏）
- `model/` — DTO 类型定义（对照 `docs/ood_interface.md` §4.1 全部接口）
- `api/` — REST API 客户端（auth / guardianship / monitoring / fleet / rescue / ota）
- `websocket/` — WebSocket 信令管理（§3.1 家属 APP 协议）
- `rtc/` — SparkRTC 集成（课程作业 mock）
- `viewmodel/` — MVVM 状态管理
- `common/` — 常量、工具函数、Token 存储
