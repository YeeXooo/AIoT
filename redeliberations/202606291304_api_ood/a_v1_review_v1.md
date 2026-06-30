# 产出审查报告（v1）

## 审查结果

APPROVED

## 逐维度审查

### 1. 任务完备性

**[通过]** REST API 契约完整覆盖六个应用服务的全部对外方法，所有端点路径、HTTP 方法、请求/响应体、状态码、认证头均已定义。S1 边缘侧内部方法（startMonitoringSession、processSensorReading、startLifeDetection）明确标注不对外暴露，符合边缘—云协同架构约束。

**[通过]** MQTT 主题路由表覆盖全部设备-云通信方向（上报、下指令、响应、推送→APP），按数据分类定义 QoS 等级，核心 Payload 均提供 JSON Schema（draft-07）。

**[通过]** WebSocket/SparkRTC 集成完整定义了信令协议（双向消息类型表）、连接生命周期（心跳/重连/离线补推）、SparkRTC 房间管理流程（含时序图）和 Token 签发 API。

**[通过]** ArkTS 前端对接契约覆盖家属 APP REST 调用清单、TypeScript DTO 定义、WebSocket 连接管理示例代码、车队大屏数据订阅模型、HMI 本地查询接口。

**[通过]** 安全设计覆盖 JWT/OAuth2 认证（含角色→权限映射）、二次身份验证门控、令牌桶限流策略、MQTT X.509 设备鉴权（含备选设备密钥方案）、全链路加密策略（TLS/SRTP/AES-GCM）、密钥管理（KMS）、隐私边界校验点。

**[问题-轻微]** 任务描述要求 S3 覆盖"家属权限查询/管理"，产出中"管理"侧通过 `updateNotificationPreference` (PUT) 实现，但缺少独立的家属权限状态查询端点。权限状态通过 WebSocket `access_granted`/`access_revoked` 事件隐式传达，不阻塞后续实现。

**[问题-轻微]** 任务描述要求 S5 覆盖"家属手动救援触发"，产出将此端点置于 S3 (`POST /api/v1/guardianship/manual-rescue`) 而非 S5 的 REST 端点表中。这与应用层 OOD 的架构一致（`triggerManualRescue` 归属 `IRemoteGuardianshipService`），但与任务描述的端点分派表述存在轻微偏差。不阻塞后续工作。

### 2. 质量达标性

**[通过]** API 端点与响应体 DTO 均映射到应用层 OOD 中已定义的应用服务方法，映射关系可追溯。`ControlVehicleWindowRequest` 和 `RequestMediaSessionRequest` 在应用层 DTO 基础上合理补充了 `windowPosition` 和 `secondaryAuthToken` 字段，提升接口可用性。

**[通过]** REST API 的异步语义设计清晰：车窗控制返回 202 Accepted（指令下发确认 ≠ 物理执行完成），配合 `queryWindowStatus` 轮询确认结果。报告生成 SLA ≤15s，超时返回 504。这些设计约束可直接指导接口层实现。

**[通过]** MQTT 主题设计正确区分了数据分类与 QoS 等级——心跳采用 QoS 0（可丢失），安全攸关数据采用 QoS 1（至少一次），且说明了不采用 QoS 2 的原因（IoTDA 并非严格保证 + 额外延迟不适合实时告警）。Payload JSON Schema 与领域层的 SensorReading、SafetyAlertEvent、DriverStatusSnapshot、InterventionInstruction 等值对象对齐。

**[通过]** WebSocket 信令协议的心跳/重连/离线补推策略参数明确（30s PING、10s PONG 超时、指数退避 1s~16s、最多 5 次、离线消息保留 7 天补推最多 20 条），与应用层 OOD §3.3 的连接生命周期管理策略一致。

**[通过]** ArkTS DTO 定义完整覆盖了 REST API 中的请求/响应模型和 WebSocket 下行消息模型，TypeScript interface 命名清晰，枚举值以注释标注。

### 3. 正确性

**[通过]** JWT 采用 RS256（RSA-SHA256）非对称签名方案正确，JWT payload 结构（sub/role/iat/exp/scope）合理。

**[通过]** MQTT X.509 证书鉴权方案正确描述了 IoTDA 设备注册流程（预置密钥→首次连接→证书下发→证书认证），并提供了设备密钥备选方案。

**[通过]** SparkRTC 作为华为云实时音视频服务的引用正确，房间 ID 格式、Token 签发流程、SRTP 端到端加密描述均与实际服务能力一致。

**[通过]** 加密策略中 SRTP（DTLS-SRTP）、AES-256-GCM、TLS 1.2+、密码套件 `TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384` 等技术选型正确且业界认可。

**[通过]** JSON Schema 引用 `http://json-schema.org/draft-07/schema#` 为有效规范版本。

**[通过]** REST API 基础路径 `/api/v1` 统一，认证头 `Authorization: Bearer <JWT>` 一致，命名风格遵循 RESTful 惯例。

**[问题-轻微]** 产出 §1.1 `GetDriverRiskStatusResponse` 的 `derivedStatusColor` 取值描述为 `GREEN / YELLOW / RED`，而应用层 OOD 同名 DTO 定义为 `GREEN / YELLOW / ORANGE / RED`。领域层 VO-15 仅定义 `GREEN / YELLOW / RED`（对应三级风险体系），产出与领域层一致，与应用层不一致，下游实现者若以应用层 DTO 为参照需自行判断。

## 修改要求（存在严重或一般问题时）

无严重或一般问题。
