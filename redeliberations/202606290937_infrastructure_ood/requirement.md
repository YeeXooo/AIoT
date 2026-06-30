# 需求：基础设施/适配层 OOD 设计

> 本项目为「智能物联——基于多传感器融合的车载安全监测系统」。领域层 OOD 产出见 `docs/ood_domain.md`，应用层 OOD 产出见 `docs/ood_application.md`，需求文档见 `docs/requirements.md`。
> 
> 技术栈：后端 Java Spring Boot，部署华为云。设备接入华为云 IoTDA（MQTT），数据库使用金仓（兼容 PostgreSQL，JPA 方言 PostgreSQLDialect），推送 SMN，音视频 SparkRTC，前端 ArkTS（HarmonyOS）。
>
> 本次设计要求产出**基础设施/适配器层**的架构级 OOD 设计文档，产出文件路径为 `docs/ood_infrastructure.md`。要求不实现具体代码，只做架构级设计。

## 一、数据持久化设计

基于领域层定义的五个聚合根（AR-01 Trip、AR-02 Driver、AR-03 Vehicle、AR-04 SystemAccount、AR-05 RoadRageVoiceRecord），设计完整的数据库持久化方案：

1. 各聚合根的数据库表结构：表名、字段名、字段类型（金仓/PostgreSQL 类型）、是否可空、默认值、主键策略、索引设计（含索引类型和索引字段）。
2. 值对象的 JPA @Embeddable 映射策略：PhysiologicalSnapshot、GeoLocation、TripScore、VehicleStateSnapshot、DrivingBehaviorCounters、L3DurationTracker、OTAUpgradeStatus、NotificationPreference、Permission、DriverComprehensiveScore、RescueAuthorizationToken 等值对象如何映射到数据库列。哪些嵌入聚合根表、哪些独立为关联表。
3. 实体 SafetyAlertEvent（E-01）和 DriverHealthProfile（E-03）的数据库表结构——它们不是聚合根但需要独立查询（尤其 SafetyAlertEvent 需跨行程历史告警查询），说明其与 Trip/Driver 表的外键关系和查询策略。
4. 所有需要并发控制的表统一使用乐观锁版本号字段（如 `version` INTEGER NOT NULL DEFAULT 0），说明版本号在 JPA 中的实现方式（`@Version`）和冲突处理策略。
5. 金仓数据库的 CREATE TABLE DDL 概要（概念级，不含完整 DDL，但需展示关键表的结构定义）。

## 二、仓储实现设计

基于领域层已声明的五个仓储接口（TripRepository、DriverRepository、VehicleRepository、SystemAccountRepository、RoadRageVoiceRecordRepository），给出 JPA 实现要点：

1. 各仓储的 JPA 实现策略：Spring Data JPA 的 `JpaRepository` 继承、自定义 `@Query` 方法、EntityManager 使用场景。
2. 复杂查询的 JPQL/原生 SQL 策略：
   - RoadRageVoiceRecordRepository.findByExpiryBefore：按到期时间批量查询待清除记录，需考虑边缘侧存储（可能非关系型数据库）的实现差异。
   - TripRepository 中按时间范围和 DriverId 查询行程列表（报告生成场景需要）。
   - SystemAccountRepository.findByDriver：按驾驶员 ID 查询关联家属账户列表。
3. CQRS 读模型投影表设计：
   - 告警历史查询投影表（alert_projection）：支持按 driver_id + time_range + alert_type + risk_level 多条件过滤和分页查询，字段包括告警摘要信息，与主表 SafetyAlertEvent 的同步策略（同步方式、延迟 SLO）。
   - 看板聚合查询投影表（fleet_dashboard_projection）：支持按 fleet_id 聚合 fatigue_distribution、heatmap 数据，每 5 分钟周期刷新。说明缓存/物化视图策略。
   - 轨迹点查询投影表（trajectory_projection）：支持按 vehicle_id / driver_id + time_range 查询轨迹点序列。
   - 三张投影表的数据同步机制：采用应用层事件驱动（订阅 AlertTriggeredEvent、TripScoredEvent 等）异步写入投影表，还是数据库层触发器/物化视图？给出选型理由。
4. 乐观锁冲突的重试策略：评分计算（可重试）、OTA 升级（在重试上限内重试）、传感器自检（立即重试一次）等不同场景的重试策略如何在仓储层支持。

## 三、领域事件基础设施

基于领域层已声明的 DomainEvent 接口、事件总线契约（§3.6）和 outbox 表结构约定，设计完整的事件基础设施：

1. **事件总线选型与实现**：
   - 边缘侧：进程内同步 EventBus。采用 Spring 的 ApplicationEventPublisher + @EventListener 实现同步回调。需保证 RiskDeterminedEvent → InterventionService 的判定→干预链路在进程内同步完成，≤500ms 时延。
   - 云端侧：outbox 事务性事件表 + 消息队列异步投递。outbox 表与聚合根状态更新在同一数据库事务中提交，事务提交后由独立的 outbox 投递器（如 Spring Scheduled Task 轮询 outbox 表）将事件投递至消息队列。
2. **事件持久化表结构**（outbox 表，表名 `domain_event_outbox`）：
   - 字段：event_id（UUID，主键）、event_type（VARCHAR，事件类型判别符）、aggregate_id（VARCHAR，关联聚合根标识）、aggregate_type（VARCHAR，聚合根类型枚举）、payload（JSONB/TEXT，事件完整载荷）、occurred_at（TIMESTAMP，事件发生时间）、created_at（TIMESTAMP，outbox 记录创建时间）、published（BOOLEAN，投递状态，默认 FALSE）、retry_count（INTEGER，重试次数，默认 0）、last_error（TEXT，最近一次投递错误信息）。
   - 索引：(published, created_at) 复合索引供投递器轮询；(aggregate_id, aggregate_type) 复合索引供审计追踪；(event_type, occurred_at) 复合索引供事件重放。
3. **消息队列选型**：推荐选型及理由（如华为云 DMS Kafka/RabbitMQ），topic 命名规范（如 `iot.safety.alert.triggered`、`iot.safety.risk.determined`）。
4. **重试与死信策略**：
   - outbox 投递器：固定间隔（如每 1s）轮询待投递事件，投递成功后标记 published=TRUE。投递失败时 increment retry_count，按指数退避（1s、2s、4s、8s、16s，最大 60s）延迟重试。
   - 最大重试次数（如 10 次），超限后移入死信表（`domain_event_dlq`），由运维人工处理或定时回放。
   - 消费方幂等：基于 event_id 去重，消费方维护已处理事件 ID 的本地缓存（如最近 N 条）。
5. **事件消费者注册机制**：
   - 边缘侧同步消费者：在 Spring Boot 启动时通过 @EventListener 注解自动注册，或通过 EventBus.registerSyncHandler 编程式注册。
   - 云端侧异步消费者：基于消息队列的 consumer group 机制，每个应用服务作为独立的 consumer group 订阅对应 topic。
   - 消费方注册配置表：列出所有领域事件类型及其消费方（应用服务/领域服务）、消费模式（同步/异步）、投递保证（at-least-once）。

## 四、外部端口适配器

基于领域层声明的多个端口（依赖接口），给出具体实现方案与技术选型：

1. **VehicleStateBuffer**（30 秒 ring buffer 实现）：
   - 数据结构：定长环形缓冲区，每个槽存储 VehicleStateSnapshot（含时间戳、车速、加速度、车门锁状态等）。
   - 实现方式：边缘侧内存内 ring buffer（如 Java ArrayDeque 或 Disruptor RingBuffer），固定容量覆盖 ≥30s 时间窗（按采样频率计算槽数）。
   - 接口实现：提供 `getSnapshotWindow(Instant from, Instant to): List<VehicleStateSnapshot>` 方法。
   - 线程安全：边缘侧单线程保证无竞争；若未来多线程，使用 ConcurrentLinkedDeque 或加锁。

2. **PhysiologicalDataBuffer**（≥10 秒滚动缓冲实现）：
   - 与 VehicleStateBuffer 类似但维度不同（生理读数时间窗）。
   - 数据结构：定长环形缓冲区，每个槽存储 PhysiologicalSnapshot（含时间戳、心率、血氧、情绪指数）。
   - 边缘侧内存内实现，覆盖 ≥10s 时间窗。提供 `getPhysiologicalWindow(Instant from, Instant to): List<PhysiologicalSnapshot>` 方法。

3. **DrivingBehaviorTrackingPort**（急刹/急加速阈值注入与增量上报）：
   - 端口方法契约：`onHardBrakingDetected(HardBrakingEvent)`、`onHardAccelerationDetected(HardAccelerationEvent)`。
   - 实现：边缘侧持续加速度监测组件（独立于 VehicleStateBuffer 的 ring buffer）按固定频率采样加速度信号，超过阈值时回调端口方法。
   - 阈值配置：急刹阈值（如减速度 > 3.5 m/s²）和急加速阈值（如加速度 > 3.0 m/s²）作为可配置参数，通过 Spring @ConfigurationProperties 注入。

4. **CameraOcclusionDetectionPort**（遮挡检测回调）：
   - 端口方法契约：`onOcclusionDetected(OcclusionDetectedSignal)`、`onOcclusionRemoved(OcclusionRemovedSignal)`。
   - 实现方案：边缘侧视觉处理模块定期比对连续帧画面差异——画面长时间无明显变化且非纯色（排除镜头盖完全覆盖），判定为物理遮挡。本期以模拟数据注入遮挡/移除信号验证回调链路。

5. **OTADeliveryPort**（升级包传输）：
   - 端口职责：通过 IoTDA MQTT 通道向车载终端下发 OTA 升级包，支持断点续传和传输进度回调。
   - 实现方案：基于 IoTDA 的文件传输能力或 MQTT 大文件分片传输。升级包分片大小（如 64KB），每片带序号和校验和。车载终端接收后按序号组装，传输中断后从已确认的最后分片续传。

6. **NotificationPort**（SMN 推送）：
   - 端口职责：向家属 APP 推送告警通知、状态快照，向管理员推送绩效预警，向救援中心推送 SOS 报告。
   - 实现方案：华为云 SMN（Simple Message Notification），创建多个 Topic（如 `iot-safety-alert`、`iot-safety-sos`、`iot-safety-performance`），按消息类型路由。家属 APP 通过华为云 Push Kit 接收（HarmonyOS）。

7. **RescueReportPort**（救援中心投递）：
   - 端口职责：向 120/救援中心投递 RescueReport。
   - 实现方案：通过 SMN 或 HTTP API（对接救援中心接口规范）投递，含指数退避重试（1s/2s/4s/8s/16s，最多 5 次）。

8. **MediaSessionPort**（音视频会话管理）：
   - 端口职责：建立/拆除家属与车载终端之间的音视频对讲会话。
   - 实现方案：基于华为云 SparkRTC，通过其服务端 SDK 创建房间（Room）、生成临时 Token、管理音视频流。端口方法契约：`createSession(driverId, familyAccountId, sessionType): MediaSessionHandle`、`endSession(sessionHandle): Unit`。

## 五、华为云服务集成

1. **IoTDA 设备接入**：
   - 设备注册：车辆作为 IoTDA 设备注册（设备 ID = VehicleId 或终端序列号），家属 APP 作为 IoTDA 设备或直连云端应用服务。
   - 设备影子（Device Shadow）：用于云端向车载终端下发期望状态（如远程车窗控制指令）和车载终端上报实际状态。车窗控制、车门锁控制通过设备影子 desired/reported 机制实现——云端更新 desired，车载终端同步后更新 reported。
   - Topic 路由设计：定义以下 MQTT Topic 层级：
     - `iot/{vehicleId}/sensor/data` — 车载终端上报感知数据（上行）
     - `iot/{vehicleId}/alert/event` — 车载终端上报告警事件（上行）
     - `iot/{vehicleId}/status/heartbeat` — 车载终端心跳（上行）
     - `iot/{vehicleId}/ota/progress` — OTA 升级进度上报（上行）
     - `iot/{vehicleId}/command/intervention` — 云端下发干预指令（下行）
     - `iot/{vehicleId}/command/ota` — 云端下发 OTA 升级指令（下行）
     - `iot/{vehicleId}/command/window` — 云端下发车窗控制指令（下行）
     - `iot/family/{accountId}/alert` — 家属 APP 告警推送（下行）
     - `iot/family/{accountId}/status` — 家属 APP 状态快照推送（下行）

2. **金仓数据库连接与分库分表策略**：
   - 连接配置：Spring Data JPA + 金仓 JDBC 驱动，dialect 设为 PostgreSQLDialect。连接池使用 HikariCP（Spring Boot 默认），配置最大连接数、最小空闲连接等。
   - 分库分表策略：按 VehicleId 哈希分片。分片键 = `hash(vehicleId) % shard_count`。Trip 表、PhysiologicalSnapshot（嵌入 Trip 表）、SafetyAlertEvent 表、轨迹点表按 VehicleId 分片（Trip 归属于 Vehicle，因此间接按 VehicleId 分片）。Driver 表、SystemAccount 表、Vehicle 表（元数据）不按 VehicleId 分片，作为全局表或按 DriverId/AccountId 分片。
   - 分片中间件：推荐 ShardingSphere-JDBC（嵌入应用进程），以 `sharding-db` 策略按 VehicleId 一致性哈希路由。不推荐独立分片代理（增加部署复杂度和网络跳数）。

3. **SMN 消息模板**：
   - 告警推送模板（`iot-safety-alert`）：包含告警类型、风险等级、时间、位置、驾驶员脱敏状态色。
   - SOS 救援模板（`iot-safety-sos`）：包含 GPS 坐标、生命体征摘要、车辆状态快照、健康档案摘要（可选）。
   - 绩效预警模板（`iot-safety-performance`）：包含驾驶员标识、评分值、所属周期、主要扣分项。
   - 家属端推送同时支持通过华为云 Push Kit 走 HarmonyOS 推送通道，以 SMN 模板 + Push Kit 组合实现。

4. **SparkRTC 集成**：
   - 房间管理：为每次家属-驾驶员音视频会话创建 SparkRTC 房间（Room ID = `{driverId}_{familyAccountId}_{timestamp}`）。
   - 音频流管理：家属 APP 和车载终端作为两个参与者加入同一房间。默认仅音频（低带宽），家属可请求升级为视频（低码率）。视频流需经 BR-04 隐私约束——云端不存储原始音视频流，仅 SparkRTC 实时转发。
   - 服务端 SDK：云端应用服务通过 SparkRTC 服务端 REST API 创建/销毁房间、生成临时鉴权 Token。Token 有效期与会话时长一致（默认最长 30 分钟，需续期）。

## 六、安全与隐私基础设施

1. **数据脱敏（人脸关键点提取）的边缘侧实现策略**：
   - 边缘侧 DMS 视觉处理模块在接收到原始摄像头帧后，立即提取人脸关键点（如 68 点或 106 点 landmark）和 PERCLOS/眨眼频率等视觉特征。
   - 原始图像帧在关键点提取完成后**立即丢弃**，不写入任何持久化存储。
   - 仅脱敏后的数值特征向量（关键点坐标数组、PERCLOS 值、眨眼频率等）经数据通道上传云端或送入本地判定引擎。
   - 本期以模拟数据验证——模拟数据源直接提供"已脱敏的特征向量"字段，验证原始图像不在数据通道中出现的控制逻辑。

2. **语音存证边缘加密存储方案**：
   - 加密算法：AES-256-GCM（提供认证加密，防篡改）。
   - 密钥管理：每个车载终端预置独立的设备密钥（Device Key），由云端 KMS（华为云 DEW）在设备注册时下发。路怒语音存证使用设备密钥加密后存储于边缘侧本地文件系统。
   - 存储目录：`/data/iot/road_rage_evidence/{record_id}.enc`，文件名包含存证 ID。
   - 保留与清除：RoadRageVoiceRecord 聚合根携带 `expiryTime` 字段，边缘侧定期扫描到期存证并物理删除加密文件。清除操作需经二次校验（验证文件确实过期，防止误删）。
   - 授权访问：仅在交通事故定责、路怒投诉受理、司法协查三类场景下，经二次授权后由 PrivacyProtectionService 调用解密并返回音频。解密密钥通过云端 KMS 临时下发（单次使用，用完即弃）。

3. **二次身份验证集成方案**：
   - 家属端高敏操作（远程对讲、视频监控、车窗控制）前，应用层 IRiskMonitoringService / IRemoteGuardianshipService 的安全门控链要求二次身份验证。
   - 方案一（推荐）：集成华为云人机验证服务 + HarmonyOS 生物特征认证 API。HarmonyOS 设备端调用系统生物特征 API（指纹/人脸），服务端通过华为 Account Kit 校验生物特征 Token。
   - 方案二（备选）：短信动态验证码。家属端请求发送短信验证码至注册手机号，输入验证码后完成二次验证。通过华为云 SMS 服务实现。
   - 高危失能场景的自动激活接入豁免二次验证——由 PermissionService 基于 EmergencyActivatedEvent 和场景有效性校验驱动，无需家属手动发起。

## 七、边缘-云部署拓扑

1. **组件部署图**：
   - **边缘侧（车载终端）**：部署以下组件：
     - 感知数据采集模块（模拟数据源）
     - 风险判定引擎（RiskDeterminationService + 子判定服务，进程内）
     - 本地 HMI 服务（渲染干预指令、语音播报、氛围灯控制）
     - VehicleStateBuffer（30s ring buffer）
     - PhysiologicalDataBuffer（≥10s 滚动缓冲）
     - 驾驶行为追踪组件（持续加速度监测 + DrivingBehaviorTrackingPort 回调）
     - 摄像头遮挡检测模块（CameraOcclusionDetectionPort 回调）
     - 路怒语音存证加密存储模块
     - 边缘侧数据本地持久化（SQLite 或嵌入式数据库，存储 Trip 和告警）
     - MQTT 客户端（连接华为云 IoTDA）
     - OTA 升级客户端（接收升级包、校验、刷写、回滚）
     - 断网兜底模块（断网时本地判定不受影响 + 本地缓存待上报数据）
   - **云端（华为云）**：部署以下组件：
     - Spring Boot 应用服务集群（无状态，可水平扩展）
     - IoTDA 设备接入服务（MQTT Broker，华为云托管）
     - 领域事件总线（outbox 投递器 + 消息队列消费者）
     - 金仓数据库集群（主从复制）
     - 消息队列（DMS Kafka/RabbitMQ）
     - SMN 消息推送服务
     - SparkRTC 音视频服务
     - OBS 对象存储（存储路怒语音存证加密文件、报告 PDF/Excel 导出文件）
     - Redis 缓存（看板缓存、家属 WebSocket 连接会话管理、幂等去重缓存）

2. **MQTT 信道拓扑**：
   - 所有车载终端通过 MQTT 协议连接华为云 IoTDA（TLS 加密）。
   - 车载终端 → IoTDA → 云端 Spring Boot 应用服务订阅对应 Topic 获取数据上行。
   - 云端 Spring Boot 应用服务 → IoTDA → 车载终端通过设备影子/下行 Topic 下发指令。
   - 家属 APP 通过 WebSocket 长连接直连云端应用服务（不经过 IoTDA MQTT），应用服务通过 Push Kit/SMN 推送告警和状态快照。
   - 家属 APP → 云端应用服务 → IoTDA → 车载终端：车窗控制等指令下发路径。
   - 救援中心通过 REST API 接收 SOS 报告（由应用服务的 RescueReportPort 投递）。

3. **断网状态下的边缘侧正常运行策略**：
   - 判定引擎：边缘侧本地运行，不依赖云端，断网时核心安全告警（疲劳/分心/路怒/活体遗留/碰撞失能）全部正常判定。
   - HMI 反馈：本地 HMI 服务在边缘侧运行，氛围灯变色、语音播报、座椅震动等直接通过本地渲染，无云端依赖。
   - 数据缓冲：Trip 状态变更和 SafetyAlertEvent 创建在边缘侧本地数据库（SQLite）中持久化，待网络恢复后通过 MQTT 批量上报云端。上报使用幂等键去重。
   - 断网兜底：感知数据采集、判定、干预链路在边缘侧形成完全闭环，端到端 ≤500ms 在断网状态下仍然成立。

4. **断网状态下云端功能的优雅降级策略**：
   - 家属 APP 状态快照：云端无法获取边缘侧实时数据，家属 APP 展示"连接中断（Disconnected）"状态，标注最后同步时间。
   - 车队看板：云端数据库中该车辆的最新数据显示"监测脱线"标记（基于心跳超时判定）。心跳超时阈值：90s（3 个心跳周期，每个 30s）。
   - 远程对讲/视频/车窗控制：云端无法与车载终端建立连接，返回 `AppError.IoTDAChannelFailure` 给家属 APP。家属 APP 展示"车辆离线，功能暂不可用"。
   - 云端判定/评分/报告：使用云端已持久化的历史数据正常服务（按已有数据算，缺失时段标注）。
   - OTA 升级：升级指令排队等待，待车辆重新上线后 IoTDA 自动下发（利用 IoTDA 的离线消息缓存能力）。
   - 网络恢复后：边缘侧批量上报缓存数据，云端按幂等键去重后完成数据同步。上报完成后家属 APP 恢复实时状态推送，车队看板清除脱线标记。

5. **部署架构图（概念级）**：以 Mermaid flowchart 或文本图描述。
