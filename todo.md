# 实现任务清单

> 基于四份 OOD 设计文档（领域层 / 应用层 / 基础设施层 / 接口层）的架构级设计，拆解为可逐项推进的实现任务。
> 
> **课程作业定位**：全部采用本地免费替代方案，不依赖华为云付费服务（IoTDA 除外，已有）。
>
> **任务进度**：0.1~0.4 ✅，1.3~1.6 ✅，3.1~3.6 ✅，2.x 部分 ✅，4.1 部分 ✅，前端 DTO ✅，下一项 1.1 / 1.2 / 1.7

---

## 依赖关系与分工建议

```
                    ┌─────────────────────────────┐
                    │  0.1 包结构 ✅              │
                    └─────────────┬───────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
     ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
     │ 0.2 Maven 依赖✅│  │ 0.3 基础类型 ✅ │  │ 0.4 入口+配置 ✅│
     │ (一人)         │  │ (一人，关键!)   │  │ (一人)         │
     └───────┬────────┘  └───────┬────────┘  └───────┬────────┘
             │                   │                    │
             │    ┌──────────────┼──────────────┐     │
             │    │              │              │     │
             │    ▼              ▼              ▼     │
             │ ┌──────┐  ┌────────────┐ ┌──────────┐ │
             │ │ 1.1  │  │ 1.5 事件   │ │ 0.5 迁移 │ │
             │ │值对象│  │+1.6 总线 ✅│ │ 基线 ✅  │ │
             │ └──┬───┘  └─────┬──────┘ └────┬─────┘ │
             │    │             │              │       │
             │    ▼             ▼              ▼       │
             │ ┌──────┐  ┌──────────┐ ┌────────────┐ │
             │ │ 1.2  │  │3.3 事件   │ │ 3.1 JPA    │ │
             │ │聚合根│  │总线实现 ✅│ │ 映射 ✅    │ │
             │ └──┬───┘  └──────────┘ └─────┬──────┘ │
             │    │                          │        │
             │    ├──────────────────────────┤        │
             │    ▼                          ▼        │
             │ ┌──────┐              ┌────────────┐   │
             │ │ 1.3  │              │3.2 仓储    │   │
             │ │仓储  │              │实现 ✅     │   │
             │ │接口 ✅│              └────────────┘   │
             │ └──┬───┘                               │
             │    │                                   │
             │    ▼         可以并行                  │
             │ ┌──────────┐                           │
             │ │ 1.7 领域  │                           │
             │ │ 服务(19) │                           │
             │ └────┬─────┘                           │
             │      │                                 │
             │      ▼                                 │
             │ ┌──────────┐                           │
             │ │ 2 应用层 │                           │
             │ │ (6服务)🟡│                           │
             │ └────┬─────┘                           │
             │      │                                 │
             │      ▼                                 │
             │ ┌──────────┐                           │
             │ │ 4.1 REST │                           │
             │ │ 控制器🟡 │                           │
             │ └──────────┘                           │
             │                                        │
             ▼                                        │
    ┌─────────────────────────────────────────────┐   │
    │  以下不依赖任何东西，随时可做：              │   │
    │  3.5 端口适配器 ✅ 3.6 安全 ✅ 3.7 存储  3.4 缓存 ✅│   │
    │  1.4 端口接口 ✅                             │   │
    ├─────────────────────────────────────────────┤   │
    │  前端 DTO + API 客户端 ✅（角色 D 已完成）  │◄──┘
    │  4.2 MQTT  4.3 WebSocket  4.4 安全配置      │
    │  5 测试  6 CI/CD                            │
    └─────────────────────────────────────────────┘
```

### 关键路径（红线，不可跳过）

```
0.3 → 1.1 → 1.2 → 1.3 → 1.7 → 2.x → 4.1
```

### 当前分工（4 人，0.1~0.4 已完成）

| 人 | 任务 | 依据 | 产出位置 |
|---|------|------|---------|
| **A** | 1.1 值对象（23 个 VO） | `docs/ood_domain.md` §3.3 | `domain/model/` 枚举 + record |
| **B** | 0.5 数据库迁移基线（15 张表） | `docs/ood_infrastructure.md` §3 | `resources/db/migration/V1__init.sql` |
| **C** | 1.5 领域事件 + 1.6 事件总线契约 + 3.3 事件总线实现 ✅ | `docs/ood_domain.md` §3.6 | `domain/event/` + `infra/eventbus/` |
| **D** | 前端 DTO 模型 + API 客户端 ✅ | `docs/ood_interface.md` §4.1 | `frontend/model/` 类型 + `frontend/api/` |

> 四块中 C/D 已全部完成。A 完成后接 1.2（聚合根）→ 1.7（领域服务）；B 完成后接 3.1（JPA 映射）→ 3.2（仓储实现）。D 已完成，等后端接口上线后联调。

---

## 0. 项目骨架（前置）

- [x] **0.1 确定 Java 包结构**
  - 根包 `com.aiot`
  - 领域层：`com.aiot.domain.model` / `domain.risk` / `domain.intervention` / `domain.guardianship` / `domain.fleet` / `domain.emergency` / `domain.ota` / `domain.shared` / `domain.event` / `domain.port` / `domain.repository`
  - 应用层：`com.aiot.application.risk` / `application.intervention` / `application.guardianship` / `application.fleet` / `application.emergency` / `application.ota` / `application.shared`
  - 基础设施层：`com.aiot.infra.persistence` / `infra.repository` / `infra.eventbus` / `infra.adapter` / `infra.security` / `infra.edge`
  - 接口层：`com.aiot.interfaces.rest` / `interfaces.mqtt` / `interfaces.websocket` / `interfaces.dto`

- [x] **0.2 补齐 Maven 依赖**（`code/server/pom.xml`）
  - Spring WebSocket ✅
  - Jackson jsr310 ✅
  - Flyway ✅
  - Lombok ✅
  - ~~华为云 SDK~~ 本阶段不引入

- [x] **0.3 定义基础类型**（`com.aiot.domain.shared`）
  - `Result<T, E>` — 密封接口，Ok/Err 记录，含 map/ifOk/ifErr ✅
  - `AggregateId` — UUID 包装记录 ✅
  - 各标识类型：`DriverId`、`TripId`、`VehicleId`、`AccountId`、`RescueReportId`、`UpgradeTaskId`、`AlertId`、`GuardianshipId` ✅
  - `AppError` — 应用层错误记录，含 8 个工厂方法 ✅

- [x] **0.4 Spring Boot 入口类与基础配置**
  - `AiotApplication.java` ✅
  - `application.yml`：H2 + Flyway + JPA validate ✅
  - H2 改为 runtime scope ✅

- [x] **0.5 数据库迁移基线**（基于 ood_infrastructure.md §3 表结构）
  - 10 张业务表 ✅：V1+V2(outbox/dlq)+V3(核心表)+V4(剩余表)
  - 3 张投影表 ✅：`alert_projection` / `fleet_dashboard_projection` / `trajectory_projection`
  - 事件表 ✅：`domain_event_outbox` / `domain_event_dlq`
  - 所有表含 `version` 乐观锁列、`created_at` / `updated_at`

---

## 1. 领域层实现

> 依据：`docs/ood_domain.md`

- [ ] **1.1 值对象（23 个 VO）** 🟡 5/23 完成
  - 已完成：`PhysiologicalSnapshot` / `GeoLocation` / `VehicleStateSnapshot` / `TimeRange` / `RescueReport`（#29）
  - 剩余 18 个在 #43（待 rebase）
  - 全部实现为不可变 `record` 或 `@Embeddable`

- [ ] **1.2 聚合根与实体（5 AR + 2 Entity）**
  - `Trip`（AR-01）、`Driver`（AR-02）、`Vehicle`（AR-03）、`SystemAccount`（AR-04）、`RoadRageVoiceRecord`（AR-05）
  - `SafetyAlertEvent`（实体）、`DriverHealthProfile`（实体）
  - 每个聚合根含完整属性、协作关系、不变式校验方法

- [x] **1.3 仓储接口（5 个）**
  - `TripRepository` / `DriverRepository` / `VehicleRepository` / `SystemAccountRepository` + RoadRageVoiceRecordRepository 等 10 个 ✅
  - 方法签名严格按 ood_domain.md §3.5 定义

- [x] **1.4 领域端口接口（8 个）**
  - `VehicleStateBuffer` / `PhysiologicalDataBuffer` / `DrivingBehaviorTrackingPort` / `CameraOcclusionDetectionPort` / `OTADeliveryPort` / `NotificationPort` / `RescueReportPort` / `MediaSessionPort`
  - 方法签名严格按 ood_domain.md §3.7 定义

- [x] **1.5 领域事件（~20 个）**
  - 事件密封接口 `DomainEvent`（含 `eventId` / `occurredAt` / `aggregateId`）
  - 全部事件 record 类，按 ood_domain.md §3.6 定义
  - 18 个事件：RiskDeterminedEvent / RiskResolvedEvent / AlertTriggeredEvent / VehicleIgnitionOffLockedEvent / LifeDetectedEvent / EmergencyActivatedEvent / SensorFailureEvent / CameraOcclusionDetectedEvent / CameraOcclusionRemovedEvent / FamilyAccessGrantedEvent / FamilyAccessRevokedEvent / TripScoredEvent / DriverScoreUpdatedEvent / PerformanceWarningEvent / OTAUpgradeCompletedEvent / OTAUpgradeFailedEvent / DriverDeactivatedEvent / FamilyManualRescueRequestedEvent

- [x] **1.6 事件总线契约**
  - `DomainEventPublisher`（`publish` / `registerSyncHandler` / `registerAsyncHandler`）
  - 接口定义在 `com.aiot.domain.event`

- [ ] **1.7 领域服务（19 个）**
  - 按 ood_domain.md §3.4 逐一实现，每个含完整方法签名与协作对象注入
  - DS-01 `RiskDeterminationService`、DS-02 `FatigueDeterminationService`、DS-03 `DistractionDetectionService`、DS-04 `RoadRageDeterminationService`、DS-05 `LifeDetectionService`、DS-06 `EmergencyResponseService`、DS-07 `InterventionService`、DS-08 `PermissionService`、DS-09 `ScoringService`、DS-10 `FleetAnalyticsService`、DS-11 `ReportGenerationService`、DS-12 `EmergencyRescueService`、DS-13 `PrivacyProtectionService`、DS-14 `SensorSelfCheckService`、DS-15 `OTAUpdateService`、DS-16 `DriverStatusBroadcastService`、DS-17 `DrivingBehaviorTrackingService`、DS-18 `DriverScoreUpdateService`、DS-19 `AlertPersistenceService`

---

## 2. 应用层实现

> 依据：`docs/ood_application.md`

- [ ] **2.1 DTO 定义**（50+ 个 Request/Response DTO）🟡 部分完成
  - Alert/Driver/Trip/Health/Projection/Guardianship DTO 已随 Controller 雏形
  - S1~S6 完整 DTO 待规范化

- [ ] **2.2 应用服务接口（6 个）** 🟡 部分完成
  - Alert/Driver/Trip/Guardianship/Health/Projection 应用服务已实现（无接口分离）

- [ ] **2.3 应用服务实现（6 个）** 🟡 部分完成
  - AlertApplicationService / DriverApplicationService / TripApplicationService / GuardianshipApplicationService / HealthApplicationService / ProjectionApplicationService ✅

---

## 3. 基础设施层实现

> 依据：`docs/ood_infrastructure.md`。**全部采用本地免费替代方案**。

- [x] **3.1 JPA 实体映射**（`infra.persistence`）
  - 10 张表对应的 `@Entity` 类，含 `@Version` 乐观锁 ✅
  - AlertEvent / Driver / SystemAccount / Trip / Vehicle + AlertProjection / DriverHealthProfile / FleetDashboardProjection / Guardianship / PhysiologicalSnapshot / RoadRageVoiceRecord / TrajectoryProjection ✅

- [x] **3.2 仓储实现**（`infra.repository`）
  - 10 个仓储接口的 Spring Data JPA 实现（云端）✅
  - JDBC/SQLite 实现（边缘侧 `infra.edge.repository`）⬜ 待后续
  - 自定义 JPQL/原生 SQL 查询 ✅

- [x] **3.3 事件总线实现**（`infra.eventbus`）
  - CloudEventBus（云端 outbox 模式）+ EdgeEventBus（边缘同步模式）✅
  - Outbox 持久化 + 定时投递 + 指数退避重试 + 死信队列 ✅
  - 5 个单元测试覆盖全部组件 ✅
  - ~~DMS Kafka / Outbox 投递~~ → **课程作业无需，Spring 内置事件完全够用**

- [x] **3.4 缓存**（`infra.cache`）
  - Cache 接口 + CacheManager + ConcurrentHashMapCache（含 TTL）✅
  - 2 个单元测试覆盖 ✅

- [x] **3.5 端口适配器实现**（`infra.adapter`）
  - 8 个适配器全部 @Component 实现 ✅
  - Ring Buffer（VehicleStateBuffer/PhysiologicalDataBuffer）+ Mock（Notification/RescueReport/MediaSession）✅

- [x] **3.6 安全与隐私**（`infra.security`）
  - AES-256-GCM + KeyStore + 脱敏校验 + Mock 二次认证 ✅
  - 3 个测试覆盖 ✅

- [ ] **3.7 文件存储**（`infra.storage`）
  - 语音证据文件 → 本地文件系统存储（`/data/aiot/voice/`）
  - OTA 升级包 → 本地文件系统（`/data/aiot/ota/`）
  - 报表导出 → 本地文件系统（`/data/aiot/reports/`）
  - ~~OBS~~ → **本地文件系统替代**

- [ ] **3.8 边缘侧基础设施**（`infra.edge`）
  - SQLite 本地持久化
  - 断网数据缓冲 + 批量重传
  - MQTT 客户端管理（连接 IoTDA）
  - 边缘-云端数据同步 + 幂等去重

---

## 4. 接口层实现

> 依据：`docs/ood_interface.md`

- [x] **4.0 前端 DTO 模型 + API 客户端**（角色 D）
  - `frontend/model/` — auth / driver / fleet / guardianship / types / websocket 类型定义 ✅
  - `frontend/api/` — ApiClient + AuthApi / DriverApi / FleetApi / GuardianshipApi ✅
  - `frontend/api/` — BaseWebSocket + FleetWebSocket + GuardianshipWebSocket ✅
  - `frontend/common/JsonParser.ts` — ArkTS 兼容 JSON 解析工具 ✅
  - ArkTS 严格模式适配，`fromJson` 构造器接入类型化 DTO

- [ ] **4.1 REST 控制器**（`interfaces.rest`）🟡 部分完成
  - Account / Driver / Safety / Guardianship / Health / Projection 共 6 个 Controller ✅
  - `@Valid` / `@RestControllerAdvice` 统一异常处理 ⬜

- [ ] **4.2 MQTT 设备通信**（`interfaces.mqtt`）
  - 上行 Topic 消费者：接收车载终端上报的传感器数据、生理数据、设备状态、OTA 进度
  - 下行 Topic 生产者：下发 OTA 升级包、控制指令、配置更新
  - 主题路由与 QoS 严格按 ood_interface.md §2 定义

- [ ] **4.3 WebSocket 信令**（`interfaces.websocket`）
  - 家属 APP 与车机端的 WebSocket 连接管理
  - SparkRTC 信令交换 — **打桩**：mock 房间创建/加入/离开、ICE Candidate 转发 ~~（不连真实 SparkRTC）~~
  - 消息类型与 payload 按 ood_interface.md §3 定义

- [ ] **4.4 安全配置**
  - Spring Security + JWT 认证过滤器 ~~（不依赖 APIG）~~
  - API 限流 — `Bucket4j` 或 Guava `RateLimiter` 本地令牌桶
  - MQTT 设备鉴权（Token 认证，通过 IoTDA）
  - CORS 配置

---

## 5. 测试

- [ ] **5.1 领域层单元测试**
  - 聚合根不变式、领域服务业务逻辑、值对象相等性
- [ ] **5.2 应用层集成测试**
  - 应用服务编排逻辑、事务边界、异常映射
- [ ] **5.3 基础设施层集成测试**
  - 仓储 CRUD、乐观锁冲突重试、事件总线投递/消费
- [ ] **5.4 接口层契约测试**
  - REST API（MockMvc）、MQTT 消息编解码、WebSocket 协议

---

## 6. CI/CD

- [ ] **6.1 完善 GitHub Actions 流水线**
  - 多 profile 构建（dev/ci）
  - 自动运行单元测试 + 集成测试
- [ ] **6.2 Docker 镜像构建**
  - 云端服务 Dockerfile
  - 边缘侧精简 Dockerfile（arm64 兼容）

---

## 附录：华为云服务 → 本地替代对照

| 华为云服务 | 用途 | 课程作业替代方案 |
|-----------|------|-----------------|
| DMS Kafka | 服务间异步事件 | Spring `ApplicationEventPublisher` |
| SMN | 消息推送 | `NotificationPort` 打桩日志 |
| SparkRTC | 实时音视频 | `MediaSessionPort` mock 房间 |
| DEW | 密钥管理 | Java `KeyStore` |
| OBS | 对象存储 | 本地文件系统 |
| Push Kit | APP 后台推送 | 打桩（只用 WebSocket 前台推送） |
| APIG | 网关 JWT 校验 | Spring Security Filter |
| Redis (DCS) | 缓存 | `ConcurrentHashMap` |
| Account Kit / SMS | 二次认证 | mock 验证码 `123456` |
| IoTDA | 设备 MQTT 通信 | ✅ 已有 SDK，直接使用 |

---

> **实现顺序建议**：0（骨架）→ 1.1~1.5（领域类型）→ 3.1~3.2（持久化）→ 1.6~1.7（领域服务）→ 2（应用层）→ 3.3~3.8（基础设施适配器）→ 4（接口层）→ 5（测试）→ 6（CI/CD）
>
> 关联设计文档：`docs/ood_domain.md` / `docs/ood_application.md` / `docs/ood_infrastructure.md` / `docs/ood_interface.md`
