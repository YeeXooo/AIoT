# [design] 接口/API 层 OOD 设计

## 关联

- **需求**：docs/requirements.md（v4）
- **领域层 OOD**：docs/ood_domain.md（a_v10/v1）
- **应用层 OOD**：docs/ood_application.md（a_v5/v1）

## 产出

docs/ood_interface.md（2256 行）

## 覆盖范围

1. **REST API 契约** — 六个应用服务 30+ 端点，OpenAPI 3.0 风格描述
2. **MQTT 主题设计** — IoTDA 设备-云通信完整 Topic 路由表 + JSON Schema
3. **WebSocket/SparkRTC 集成** — 家属 APP 信令协议、房间管理、音视频会话
4. **ArkTS 前端对接契约** — 家属 APP / 车队大屏 / HMI 全部后端接口与 DTO
5. **安全设计** — JWT/OAuth2 认证、令牌桶限流、MQTT X.509 鉴权、TLS 加密

## 审议历程

| 阶段 | 详情 |
|------|------|
| 审议框架 | 再审议式执行（A-B 迭代） |
| A-B 迭代 | 13 轮 |
| 组件A 内部审议 | 每轮 1-2 轮次 |
| 组件B 内部审议 | 每轮 1-3 轮次 |
| 最终判定 | RETRY（达迭代上限，残留问题均为轻微等级） |

## 与下游文档的对齐

- [x] 领域层 AR-01~AR-05 聚合根均有对应 CRUD 端点或事件触发
- [x] 领域层 DS-01~DS-18 领域服务均有应用层编排入口
- [x] 应用层六份 interface 契约与 REST 端点一一映射
- [x] 需求 BR-01~BR-08 判定规则均有对应接口或 MQTT 主题
- [x] 边缘-云协同架构约束在 MQTT 主题设计中体现（边缘本地 vs 云端路由分离）
- [x] 隐私边界 BR-04 在安全设计中以 TLS + X.509 + 最小权限原则落地
