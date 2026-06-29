# 基础设施层 OOD 设计 质量审查报告（b_v1 / v1）

> 审查对象：`a_v1_design_v2.md`（基础设施/适配器层 OOD 设计方案）
> 审查维度：需求响应充分度、事实错误与逻辑矛盾、深度与完整性
> 内部审议已覆盖：类型系统可行性、标准库与生态覆盖、语言特性可行性、设计一致性、设计质量

---

## 一、问题清单

### 问题 1：`road_rage_voice_record` 表分片键与表结构不匹配

- **所在位置**：§3.1.5（分库分表策略） vs §3.1.1（AR-05 RoadRageVoiceRecord 表结构）
- **严重程度**：**严重（阻塞实现）**
- **问题描述**：§3.1.5 将 `road_rage_voice_record` 列在"按 VehicleId 分片"表中，称其"关联至 Driver，但 Driver 归属于 Vehicle 的行程"。但 §3.1.1 中 RoadRageVoiceRecord 表字段仅有 `record_id`、`alert_id`、`trip_id`、`driver_id` 等，**不包含 `vehicle_id` 列**。若 ShardingSphere-JDBC 按 `hash(vehicle_id)` 路由，该表缺少分片键列，无法实施分片路由。
- **改进建议**：为该表增加冗余 `vehicle_id` VARCHAR(64) NOT NULL 列，并在 §3.1.1 表结构中补充；或将该表改为按 `driver_id` 哈希分片并调整 §3.1.5 分片范围说明。

### 问题 2：分片策略使用取模运算而非一致性哈希，与扩容目标矛盾

- **所在位置**：§3.5.2（金仓数据库连接与分片配置） vs §3.1.5（分库分表策略）
- **严重程度**：**严重（阻塞实现）**
- **问题描述**：§3.5.2 明确写为 `hash(vehicle_id) % 4`，而需求文档 §五.2 明确要求"按 VehicleId 一致性哈希路由"。取模哈希（`% N`）与一致性哈希是两种不同的路由策略——前者在变更分片数量 N 时需全量数据重分布，后者通过虚拟节点和哈希环使扩容时仅影响少数分片。§3.1.5 同时声称"按数据增长动态扩容"，取模方案与动态扩容目标直接矛盾。
- **改进建议**：将路由算法改为 ShardingSphere-JDBC 的一致性哈希分片策略（如 `CONSISTENT_HASH`），或明确声明本期固定 4 片不扩容、扩容需接受全量重分布，并给出迁移方案。

### 问题 3：`EmergencyRescueService` 的部署位置标注错误

- **所在位置**：§3.3.6（事件消费者注册配置表）
- **严重程度**：**中等（逻辑矛盾）**
- **问题描述**：§3.3.6 中 `EmergencyActivatedEvent` 的消费方第一项为"EmergencyRescueService（边缘）"。但领域层 OOD（`docs/ood_domain.md` §3.4 DS-12）明确 EmergencyRescueService 的职责包含"向救援中心/120 发送精准定位"和"云端授权开启车门锁"——这些操作依赖云端网络连通性和外部 HTTP/SMN 接口调用，无法在边缘侧执行。该服务应是云端消费者。
- **改进建议**：将该项标注改为"EmergencyRescueService（云端）"，与领域层 DS-12 的实际运行环境保持一致。

### 问题 4：`SafetyAlertEvent` 主表存在与 CQRS 读模型投影冗余的复合索引

- **所在位置**：§3.1.2（E-01 SafetyAlertEvent 索引设计）
- **严重程度**：**中等（资源浪费）**
- **问题描述**：设计在 SafetyAlertEvent **主表**上建立了复合索引 `(driver_id, occurred_at DESC, alert_type, risk_level)`——"按驾驶员 + 时间范围 + 类型 + 等级过滤"。但同节"查询策略"明确声明"读侧：跨行程/跨驾驶员的告警历史查询…均通过 CQRS 投影表（`alert_projection`）完成，不穿透 Trip 聚合根加载"。也就是说，该索引服务的使用场景（多条件过滤读查询）完全由 `alert_projection` 投影表覆盖，主表仅需写侧索引（`trip_id` FK 索引用于通过 Trip 聚合加载告警列表）。该索引不仅冗余，而且会拖累每次告警 INSERT 的写性能（维护 4 字段复合 B-tree）。
- **改进建议**：主表仅保留 `(trip_id)` FK 索引和 `(vehicle_id, occurred_at DESC)` 车队维度索引。将 `(driver_id, occurred_at DESC, alert_type, risk_level)` 索引限定于 `alert_projection` 投影表（已在 §3.2.3 定义）。

### 问题 5：`EdgeSessionContext` 被多处引用但从未定义

- **所在位置**：§3.4.1、§3.4.2、§3.7.1
- **严重程度**：**中等（关键概念缺失）**
- **问题描述**：§3.4.1 称"一个 EdgeSessionContext 对应一个 VehicleStateBuffer 实例"；§3.7.1 边缘部署组件表中列出"Spring Boot 应用实例"运行判定引擎，但未定义 EdgeSessionContext 是什么。领域层 OOD（`docs/ood_domain.md` §3.4 DS-01）提及"边缘侧流式会话上下文"作为会话级状态容器持有 ActiveRiskSet 等，但基础设施设计自身未说明会话上下文的创建时机、生命周期、与其他组件的关联关系以及基础设施层如何管理该上下文。这阻碍了下游开发者理解边缘侧各组件的协同时序。
- **改进建议**：在 §3.4（外部端口适配器）或新增独立小节中，明确定义 EdgeSessionContext：创建时机（随 `startMonitoringSession` 创建）、持有引用（VehicleStateBuffer 实例、PhysiologicalDataBuffer 实例、当前活跃 Trip 引用）、生命周期（随行程结束销毁）、线程模型（边缘侧单线程持有）。

### 问题 6：边缘侧仓储实现仅描述了 1/5，其余 4 个仓储的边缘实现缺失

- **所在位置**：§3.2.2
- **严重程度**：**中等（完整性不足）**
- **问题描述**：需求 §七.1 明确边缘侧需部署"边缘侧数据本地持久化（SQLite…存储 Trip 和告警）"；Decision 4 明确"边缘侧 SQLite 的 persistence 实现与云端 JPA 实现共用同一仓储接口"。但设计中仅描述了 `RoadRageVoiceRecordRepository.findByExpiryBefore` 的边缘实现（§3.2.2）。**TripRepository**（边缘侧 AlertPersistenceService 需通过它创建 SafetyAlertEvent）、**DriverRepository**（边缘侧 DS-09/DS-18 需更新评分）、**VehicleRepository**（边缘侧 DS-14/DS-15 需更新传感器状态和 OTA 状态）、**SystemAccountRepository**（边缘侧家属权限查询）的边缘侧 SQLite 实现策略均未描述。
- **改进建议**：在 §3.2 中增加"边缘侧仓储实现"小节，明确各仓储在 SQLite 环境下的实现策略——是采用 JDBC 直接操作 SQLite（如 Decision 4 所述），还是采用 JPA + Hibernate SQLite 方言（如 `org.hibernate.dialect.SQLiteDialect`）。至少需说明边缘侧 TripRepository 的 CRUD 实现（SQLite 不支持 JPA `@ElementCollection` 和 `@Version` 的自动处理，需替代方案）。

### 问题 7：MQTT Topic `iot/family/...` 的用途与通信拓扑不一致

- **所在位置**：§3.5.1（MQTT Topic 路由设计表）
- **严重程度**：**轻微（歧义）**
- **问题描述**：Topic 路由表中定义了 `iot/family/{accountId}/alert` 和 `iot/family/{accountId}/status` 两条下行 Topic，方向标注为"下行"、QoS 分别为 1 和 0。但 §3.5.1 末尾的"家属 APP 连接方式"说明中明确"家属 APP 不直接连接 IoTDA MQTT——而是通过 WebSocket 长连接直连云端 Spring Boot 应用服务"。若家属 APP 不连接 MQTT，这两个 Topic 的消费方是谁？其存在似乎与通信拓扑矛盾。可能意图是 IoTDA 规则引擎将这些 Topic 的消息路由至云端应用服务（再由应用服务通过 WebSocket 推送给家属 APP），但设计中未说明此路由机制。
- **改进建议**：明确这两个 Topic 的用途——若为内部路由 Topic（IoTDA → 云端应用服务消费后转 WebSocket 推送），在 Topic 表中增加"消费方"列说明；若确实不需要，则删除这两个 Topic。

### 问题 8：部署架构图缺失

- **所在位置**：需求 §七.5 要求 vs 设计 §3.7
- **严重程度**：**轻微（格式缺失）**
- **问题描述**：需求 §七.5 明确要求提供"以 Mermaid flowchart 或文本图描述"的部署架构图。设计 §3.7.2 提供了 MQTT 信道拓扑图（ASCII 文本图），但该图仅覆盖 MQTT 通信层面，不是完整的部署架构图。组件部署仅以表格形式列出了边缘侧和云端侧的组件清单（§3.7.1），缺少描述各组件之间网络连线和协议关系的整体架构图。
- **改进建议**：增加一张 Mermaid flowchart 或文本图（类似 §3.7.2 的 MQTT 信道拓扑图风格），覆盖 §3.7.1 列出的所有组件及其之间的网络连接关系（含协议标注），如：车载终端内部组件 → 进程内调用、车载终端 → IoTDA MQTT/TLS、云端应用服务 → 金仓数据库/TCP、Spring Boot → Redis/TCP 等。

### 问题 9：VO-16 DrivingBehaviorCounters 映射归类不准确

- **所在位置**：§3.1.3（值对象嵌入映射策略）
- **严重程度**：**轻微（归类错误）**
- **问题描述**：VO-16 DrivingBehaviorCounters 被列在"策略 B：集合嵌入聚合根表（`@ElementCollection` + `@CollectionTable`）"分类下，但其映射方式实际描述为"嵌入列字段（`hard_braking_count` + `hard_acceleration_count` INTEGER）"——这是策略 A（`@Embedded`）的映射方式，而非策略 B 的 `@ElementCollection` 独立关联表方式。VO-16 不是集合类型值对象，是两个计数字段嵌入 Trip 表，应归于策略 A。
- **改进建议**：将 VO-16 移至策略 A 表格中，或将 VO-16 单独归类为"策略 A 的嵌入式字段"并说明其与 TripScore 同为 Trip 表的嵌入字段。

### 问题 10：各表缺少显式主键生成策略说明

- **所在位置**：§3.1.1（聚合根表结构）
- **严重程度**：**轻微（实现细节缺失）**
- **问题描述**：需求 §一.1 要求说明各表的**主键策略**。设计中各表均标注了 `PK, NOT NULL` 和类型，但未说明主键的生成策略——是应用层生成 UUID 后赋值、JPA `@GeneratedValue` 自增序列、还是使用金仓的 `UUID` 生成函数。特别是 `trip_physiological_snapshot` 集合表使用"trip_id + timestamp 复合主键"、`guardianship` 表使用"复合主键 `(driver_id, account_id)`"、`alert_projection` 使用 BIGSERIAL——这些策略分别适用于不同场景，但正则聚合根表的主键策略未统一说明，下游开发者难以确定编码方向。
- **改进建议**：在各表定义中增加"主键策略"行或在 §3.1.1 开头统一说明：聚合根表主键采用应用层生成 UUID（通过 `@Id` + 构造器赋值），无 `@GeneratedValue`，理由：UUID 支持分布式无冲突生成，适合云边两侧独立创建实体后同步的场景。

---

## 二、整体质量评价

设计文档在事件基础设施（§3.3）、外部端口适配器（§3.4）、乐观锁与并发（§3.1.4 & §3.2.4 & §6）等方面覆盖充分，v2 修订说明表明已修复了前序审查指出的关键矛盾（outbox 表跨库事务、Guardianship 并发、PhysiologicalSnapshot 优化阈值等）。

但存在 **2 个阻塞级问题**（分片键缺失、取模哈希与扩容矛盾）会导致分库分表方案无法直接指导实现；**3 个中等严重性**问题涉及逻辑矛盾和关键概念缺失；余下若干轻微问题影响实现阶段的细节决策。建议在进入编码前修复严重和中等问题。

---

## 修订说明（v1）

| 质询意见 | 回应 |
|---------|------|
| （首轮审查，无质询意见） | — |
