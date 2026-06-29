# API/接口层 OOD 设计任务

为「智能物联——基于多传感器融合的车载安全监测系统」完成接口/API 层 OOD 设计。

## 背景

本系统已有以下设计产出：
- 需求文档：docs/requirements.md
- 领域层 OOD：docs/ood_domain.md（含实体、值对象、聚合根、领域服务、领域事件设计）
- 应用层 OOD：docs/ood_application.md（含六个应用服务的 interface 契约、DTO 定义、异常处理策略）

技术栈：前端 ArkTS（HarmonyOS），后端 Java Spring Boot。

## 设计产出要求

请产出完整的接口/API 层 OOD 设计文档，覆盖以下五个部分：

### 1. REST API 契约

六个应用服务的全部 REST 端点清单（路径、方法、请求体/查询参数、响应体、HTTP 状态码、认证头），按 OpenAPI 3.0 风格描述。需覆盖：

- **S1 RiskMonitoringService**：流式判定会话管理、历史风险查询
- **S2 InterventionService**：干预状态查询、驾驶员覆盖上报
- **S3 RemoteGuardianshipService**：家属权限查询/管理、状态订阅、音视频对讲请求、远程车窗控制
- **S4 FleetManagementService**：看板查询、钻取查询、报告生成/下载、绩效预警订阅
- **S5 EmergencyRescueService**：SOS 确认、救援授权管理、家属手动救援触发
- **S6 OTAManagementService**：升级任务创建/查询、回滚指令下发、升级进度查询

### 2. MQTT 主题设计

IoTDA 设备-云通信的完整 Topic 路由表（边缘→云的感知上报、云→边缘的指令下发、云端推送→家属 APP 的告警/状态），按数据分类定义 QoS 等级与 Payload 格式（JSON Schema）。

### 3. WebSocket/SparkRTC 集成

家属 APP 音视频对讲、远程视频监控的 WebSocket 信令协议与 SparkRTC 房间管理接口。

### 4. ArkTS 前端对接契约

家属 APP（HarmonyOS）调用的全部后端接口清单与数据模型（DTO）定义，车队大屏的看板数据订阅模型，HMI（车机端）的本地查询接口。

### 5. 安全设计

API 认证（JWT/OAuth2）、接口限流策略（令牌桶/漏桶）、MQTT 设备鉴权（X.509 证书或 Token 认证）、敏感数据传输加密策略。

## 约束

- 不实现具体代码，产出接口契约级设计文档
- 所有 API 端点应映射到应用层 OOD 中已定义的应用服务方法
- MQTT Topic 设计需与领域事件和感知上报通道对齐
- 安全设计需覆盖需求文档中定义的隐私边界（BR-04）和认证要求
- 需考虑边缘—云协同架构的特殊性（边缘侧本地接口 vs 云端 REST API）

## 参考文件

- 需求文档：/home/jasper/AIoT/docs/requirements.md
- 领域层 OOD：/home/jasper/AIoT/docs/ood_domain.md
- 应用层 OOD：/home/jasper/AIoT/docs/ood_application.md
