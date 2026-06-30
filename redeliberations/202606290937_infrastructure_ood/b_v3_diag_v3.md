# 质量审查报告 — 基础设施/适配器层 OOD v3（第 3 轮）

> 审查对象：`a_v3_copy_from_v2.md`（第 3 轮迭代产出）
> 审查维度：需求响应充分度、事实错误/逻辑矛盾、深度与完整性

---

## 〇、前序迭代问题验证

### 第 1 轮问题（12 个）

| # | 问题摘要 | 状态 | 验证位置/说明 |
|---|---------|:---:|------------|
| 1 | road_rage_voice_record 缺 vehicle_id 列 | 已修复 | §3.1.1 已增加 `vehicle_id VARCHAR(64) NOT NULL` |
| 2 | 分片取模运算 vs 一致性哈希 | 已修复 | §3.1.5 已改为 `CONSISTENT_HASH`，含扩容迁移方案 |
| 3 | EmergencyRescueService 部署标注错误 | 已修复 | §3.3.6 已改为"云端" |
| 4 | SafetyAlertEvent 冗余索引 | 已修复 | §3.1.2 已移除两条读侧复合索引 |
| 5 | EdgeSessionContext 生命周期未定义 | 已修复 | §3.4.2a 新增完整小节 |
| 6 | 边缘侧仅描述 1/5 仓储 | 已修复 | §3.2.2a 新增五仓储边缘策略 |
| 7 | outbox 缺 last_attempt_at 字段 | 已修复 | §3.3.2 已增加该字段 |
| 8 | MQTT family Topic 用途不一致 | 已修复 | §3.5.1 明确为"云端内部路由" |
| 9 | 部署架构图缺失 | 已修复 | §3.7.2 Mermaid flowchart 已补充 |
| 10 | VO-16 映射归类错误 | 已修复 | 已移至策略 A 表格 |
| 11 | 主键生成策略未说明 | 已修复 | 文档头部新增"统一主键策略"段落 |
| 12 | 边缘侧乐观锁方案未说明 | 已修复 | §3.1.4 新增边缘侧 JDBC 层实现 |

**第 1 轮通过率：12/12 = 100%**

### 第 2 轮问题（12 个）

| # | 问题摘要 | 状态 | 验证位置/说明 |
|---|---------|:---:|------------|
| 1 | fleet_dashboard_projection 缺主键 | 已修复 | §3.2.3 P2 已增加复合主键 `(fleet_id, risk_level, alert_type)` |
| 2 | trajectory_projection 分片策略未明确 | 已修复 | PK 改为 UUID，已纳入 §3.1.5 分片范围 |
| 3 | Vehicle 缺 last_heartbeat_at | 已修复 | §3.1.1 Vehicle 表已增加此列，§3.7.5 已补充心跳监控逻辑 |
| 4 | Outbox 轮询 SQL 三处不一致 | 已修复 | §4 场景 2 步骤 3 已统一为两层过滤方案 |
| 5 | guardianship 权限变更策略矛盾 | 已修复 | PK 改为三列复合，统一为保留历史记录方案 |
| 6 | IoTDA 上行消息消费方案未选定 | 已修复 | §3.5.1 已选定 DMS Kafka 数据转发方案 |
| 7 | fleet_dashboard_projection 缓存策略断裂 | 已修复 | §3.2.3 P2 已重构为事件驱动即时聚合 + 定时全量刷新的双路径 |
| 8 | @ElementCollection 冻结技术未说明 | 已修复 | §3.1.3 已补充冻结技术实现描述 |
| 9 | NotificationPort 方法契约未呈现 | 已修复 | §3.4.6 已新增端口方法契约概要段落 |
| 10 | OTA 升级车载终端侧设计缺失 | 已修复 | §3.4.5 已新增边缘侧 OTA 客户端小节 |
| 11 | RescueReportPort HTTP API 缺接口规范 | 已修复 | §3.4.7 已新增 HTTP API 备选路径接口概要 |
| 12 | DEW 密钥轮转离线车辆场景未覆盖 | 已修复 | §3.5.5 已新增离线车辆密钥轮转处理段落 |

**第 2 轮通过率：12/12 = 100%**

**总通过率：24/24 = 100%**。经逐条对照验证，第 1、2 轮迭代历史中的所有 24 个问题在 v3 产出中均已完成修复。

---

## 需求响应充分度：逐子项系统性核查

以下按需求文档 §一至§七 逐子项核对设计文档的响应情况，与 §〇 的 24 项问题验证表格式一致。

### 需求 §一（数据持久化设计，5 个子项）

| 子项 | 设计对应位置 | 状态 |
|------|-------------|:---:|
| §一.1 各聚合根表结构（表名/字段/类型/约束/主键/索引） | §3.1.1 五个聚合根表（trip/driver/vehicle/system_account/road_rage_voice_record），含完整字段表、金仓类型、索引设计 | 已覆盖 |
| §一.2 值对象 @Embeddable 映射策略 | §3.1.3 四类策略（@Embedded / @ElementCollection / 单列映射 / 不持久化），22 个 VO 全部分类 | 已覆盖 |
| §一.3 实体 SafetyAlertEvent 和 DriverHealthProfile 独立表结构 | §3.1.2 两表独立定义，含外键关系、查询策略、索引设计 | 已覆盖 |
| §一.4 乐观锁版本号统一规范 | §3.1.4 版本号字段规范、JPA @Version 实现、冲突处理、边缘侧 JDBC 实现 | 已覆盖 |
| §一.5 金仓 CREATE TABLE DDL 概要 | §3.1.1 至 §3.1.3 以 Markdown 表格给出字段定义（字段名/列类型/约束/说明），信息量等价于概念级 DDL，符合需求"概念级，不含完整 DDL"的宽松要求 | 已覆盖 |

### 需求 §二（仓储实现设计，4 个子项）

| 子项 | 设计对应位置 | 状态 |
|------|-------------|:---:|
| §二.1 JPA 实现策略 | §3.2.1 六份仓储接口的 JPA 实现策略总览表（JpaRepository 继承、@Query、@Modifying），含 DriverRepository @Modifying 乐观锁异常转换链路说明 | 已覆盖 |
| §二.2 复杂查询策略 | §3.2.2 四个场景全覆盖（RoadRageVoiceRecord.findByExpiryBefore 边缘/云端差异、TripRepository 时间范围+分页含进行中行程、SystemAccountRepository.findByDriver） | 已覆盖 |
| §二.3 CQRS 读模型投影表设计 | §3.2.3 三张投影表字段定义/索引/同步机制/缓存策略，含数据同步选型理由（决策 2） | 已覆盖 |
| §二.4 乐观锁冲突重试策略 | §3.2.4 五种场景策略表，含评分计算指数退避参数（50/100/200ms，3次）、OTA 上限（5次 100ms固定间隔）、自检（10ms重试一次）、权限不重试、告警不重试 | 已覆盖 |

### 需求 §三（领域事件基础设施，5 个子项）

| 子项 | 设计对应位置 | 状态 |
|------|-------------|:---:|
| §三.1 事件总线选型与实现 | §3.3.1 边缘侧 Spring ApplicationEventPublisher + @EventListener（同步，≤500ms）；云端 outbox + DMS Kafka（异步，at-least-once） | 已覆盖 |
| §三.2 事件持久化表结构 | §3.3.2 domain_event_outbox 表 11 字段 + 3 类索引，含轮询/审计/重放 | 已覆盖 |
| §三.3 消息队列选型与 topic 设计 | §3.3.4 DMS Kafka 选型 + 四条理由（吞吐/持久化/消费者组/华为云托管），13 个 topic 命名 + 消费方映射 | 已覆盖 |
| §三.4 重试与死信策略 | §3.3.5 outbox 投递器指数退避（1s→60s，10次上限）、两层过滤实现、死信表 + SMN 告警、消费方幂等（Redis LRU 10000条/24h TTL）、MQTT 下行由 IoTDA 平台管理 | 已覆盖 |
| §三.5 事件消费者注册机制 | §3.3.6 19 个事件类型的消费方/消费模式/投递保证完整配置表，含 DriverStatusSnapshot ≥1Hz 不走 outbox 的脚注说明 | 已覆盖 |

### 需求 §四（外部端口适配器，8 个子项）

| 子项 | 设计对应位置 | 状态 |
|------|-------------|:---:|
| §四.1 VehicleStateBuffer 30s ring buffer | §3.4.1 ArrayDeque 定长环形缓冲，getSnapshots 回取接口，BufferException 语义，生命周期 | 已覆盖 |
| §四.2 PhysiologicalDataBuffer ≥10s 滚动缓冲 | §3.4.2 与 VehicleStateBuffer 对称设计，getReadings 接口，独立缓冲空间 | 已覆盖 |
| §四.3 DrivingBehaviorTrackingPort 加速度事件检测 | §3.4.3 50ms采样，急刹/急加速阈值通过 @ConfigurationProperties 注入（具体数值可配，验收时固化），同步回调 | 已覆盖 |
| §四.4 CameraOcclusionDetectionPort 遮挡检测回调 | §3.4.4 模拟数据驱动，与 BR-08 传感器故障路径区分说明 | 已覆盖 |
| §四.5 OTADeliveryPort 升级包传输 | §3.4.5 MQTT 64KB分片 + CRC32校验 + 断点续传 + 进度回调，边缘侧 OTA 客户端完整描述（分片接收/组装/数字签名/RSA-2048/回滚） | 已覆盖 |
| §四.6 NotificationPort SMN 推送 | §3.4.6 三路 SMN Topic + Push Kit 组合，四种端口方法契约概要（告警/SOS/绩效/快照），离线消息缓存（Redis/TTL 7天/AES-256-GCM） | 已覆盖 |
| §四.7 RescueReportPort 救援中心投递 | §3.4.7 SMN 主路径 + HTTP API 备选（endpoint/认证/可重试状态码），指数退避 5 次，超时 30s，转人工干预 | 已覆盖 |
| §四.8 MediaSessionPort 音视频会话管理 | §3.4.8 SparkRTC 房间管理 + Token 续期（到期前5分钟/1分钟两次尝试） + 音频优先/视频可选 + 隐私约束 | **已覆盖** |

> **关于 HMI 输出适配器**：需求 §四 8 个子项均不涉及 HMI 输出端口（HMI 在需求中出现于 §七 部署拓扑，而非 §四 端口适配器需求清单）。设计 §3.7.4 明确 InterventionService 生成的 InterventionInstruction 由边缘侧本地 HMI 适配器渲染——氛围灯变色、语音播报、座椅震动等通过本地 CAN/HMI 接口执行。HMI 输出作为边缘侧本地执行组件，其接口协议取决于车载 CAN 总线硬件规范，不属于本次 OOD 适配器设计范围（无需定义软件层的 HmiOutputPort 接口——干预指令到物理输出的映射由车载 ECU 固件层完成）。

### 需求 §五（华为云服务集成，4 个子项）

| 子项 | 设计对应位置 | 状态 |
|------|-------------|:---:|
| §五.1 IoTDA 设备接入 + 设备影子 + Topic 路由 | §3.5.1 X.509认证、设备影子 desired/reported/delta 机制、9 个 MQTT Topic（QoS 0/1）、IoTDA 上行消息消费适配器（DMS Kafka 数据转发 → IotdaUplinkConsumer） | 已覆盖 |
| §五.2 金仓数据库连接 + 分库分表策略 | §3.5.2 HikariCP 连接池参数、ShardingSphere-JDBC CONSISTENT_HASH 分片（4 片/512 虚拟节点）、分片表/广播表/全局只读表三类配置、扩容数据迁移方案 | 已覆盖 |
| §五.3 SMN 消息模板 | §3.5.3 三类模板变量清单（告警 6项/SOS 9项/绩效 5项）、Push Kit 集成（AccountId→PushToken 映射） | 已覆盖 |
| §五.4 SparkRTC 集成 | §3.5.4 服务端 SDK、临时 Token（appId+appKey）、端到端加密转发、隐私约束强制 | 已覆盖 |

### 需求 §六（安全与隐私基础设施，3 个子项）

| 子项 | 设计对应位置 | 状态 |
|------|-------------|:---:|
| §六.1 数据脱敏（人脸关键点提取）边缘侧实现 | §3.6.1 数据流管道（原始帧→关键点提取→立即丢弃）、脱敏校验门控（JSON Schema 验证禁止 raw_image 字段）、模拟验证方案 | 已覆盖 |
| §六.2 语音存证边缘加密存储 | §3.6.2 AES-256-GCM + HKDF-Expand 独立会话密钥 + DEW 密钥管理 + 授权解密三场景 + BouncyCastle 依赖标注 | 已覆盖 |
| §六.3 二次身份验证集成 | §3.6.3 两方案（生物特征+短信）通过 SecondaryAuthProvider 统一抽象，高危失能场景豁免 | 已覆盖 |

### 需求 §七（边缘-云部署拓扑，5 个子项）

| 子项 | 设计对应位置 | 状态 |
|------|-------------|:---:|
| §七.1 组件部署图（边缘+云端组件清单） | §3.7.1 边缘侧 11 组件 + 云端 12 组件表格，含技术栈和说明 | 已覆盖 |
| §七.2 MQTT 信道拓扑 | §3.7.3 四条通信路径（车载↔IoTDA、云端↔IoTDA、家属↔云端、救援↔云端），ASCII 图 + 文字说明 | 已覆盖 |
| §七.3 断网边缘侧正常运行策略 | §3.7.4 判定/干预链路完全闭环、HMI 本地渲染、数据缓冲/批量重传、PhysiologicalSnapshot 同步、断网兜底 ≤500ms、存储空间监控双路径降级 | 已覆盖 |
| §七.4 断网云端优雅降级策略 | §3.7.5 六项云端功能的降级行为表（状态快照/看板/远程控制/判定评分/OTA/恢复），含心跳监控 30s 定时任务和脱线标记 90s 超时逻辑 | 已覆盖 |
| §七.5 部署架构图（Mermaid） | §3.7.2 Mermaid flowchart 覆盖车载终端/华为云 IoTDA+应用+数据层+推送层/外部客户端，所有连线标注协议；关键网络路径汇总表（10 条路径） | 已覆盖 |

**需求响应总评**：产出逐子项响应了需求文档 §一至§七 的全部 27 个子项，无遗漏。各子项的覆盖深度均达到可指导编码实现的级别（含具体参数、接口契约概要、技术选型理由）。

---

## 一、事实错误与逻辑矛盾

### 问题 1：outbox 广播表策略下的跨分片事务原子性假设未验证

- **所在位置**：§3.5.2（金仓数据库连接与分片配置）
- **严重程度**：一般（标注为"待验证"）
- **问题描述**：§3.5.2 将 `domain_event_outbox` 和 `domain_event_dlq` 配置为广播表，断言"写操作时 ShardingSphere-JDBC 同步广播至所有分片……ShardingSphere-JDBC 对广播表的写入与同 shard 分片表的写入在同一个物理数据库上执行，由数据库本地事务保证原子性"。该断言的核心前提——"广播表的写入与分片表的写入在同一个本地事务中"——取决于 ShardingSphere-JDBC 在 LOCAL 事务模式下对广播表的具体实现行为。查阅 ShardingSphere 官方文档（5.5.3 版本），其 Broadcast Table 与 Distributed Transaction 相关页面为 JS 渲染的 SPA 页面，无法获取具体内容以确认该行为。在无官方文档或实验验证的情况下，直接断言广播表写入与分片表写入具备本地事务原子性存在技术风险。若 ShardingSphere-JDBC 在 LOCAL 模式下将广播表写操作拆分为针对各分片的独立连接执行，则"同一本地事务"的前提不成立，outbox 模式核心契约（"聚合根状态变更与事件持久化原子提交"）将断裂。
- **改进建议**：(a) 在设计文档中标注 ShardingSphere-JDBC 目标版本号（如 5.5.x），并说明已在该版本上验证广播表写入与分片表写入的本地事务原子性（含验证方式）；(b) 若未经验证，给出后备方案——明确声明 outbox 固定于 shard-0（接受最终一致性，补充重试补偿 + SLO 标注）；(c) 或引入 XA 分布式事务（Seata/Atomikos）作为事务原子性的备选保障路径。

### 问题 2：fleet_dashboard_projection 即时增量聚合的跨分片 JOIN 可行性存疑

- **所在位置**：§3.2.3 P2（缓存/物化视图策略 路径 A）vs §3.5.2（分片配置）
- **严重程度**：一般（标注为"待验证"）
- **问题描述**：§3.2.3 P2 路径 A 描述事件驱动的即时增量聚合——"实时 JOIN `vehicle`、`safety_alert_event`、`trip` 表计算该组合下的 driver_count 和 heatmap_data"。§3.5.2 中 `safety_alert_event` 和 `trip` 按 `vehicle_id` 一致性哈希分片（分布在不同物理数据库），`vehicle` 为广播表，`fleet_dashboard_projection` 固定于 shard-0。该即时聚合查询是否可在 ShardingSphere-JDBC LOCAL 模式下执行取决于：(a) ShardingSphere 是否支持跨分片 JOIN；(b) 若启用 SQL Federation 引擎，其性能是否满足 ≤3s 的 SLO。由于 ShardingSphere 文档为 JS 渲染且无法获取具体内容，此问题无法确认是"不可行"还是"可行但需评估"。v2 审查报告的"严重"级别基于"不支持跨分片 JOIN"的未验证假设，本轮调整为"一般"并标注待验证。
- **改进建议**：(a) 将路径 A 的即时增量聚合数据源从 `safety_alert_event` + `trip` 直接查询改为从 `alert_projection`（同在 shard-0）聚合——在 `alert_projection` 中补充 `fleet_id` 冗余字段，使即时聚合的所有数据源集中在 shard-0 避免跨分片；(b) 或明确标注此设计依赖 ShardingSphere-JDBC 的 SQL Federation 引擎并说明已评估性能；(c) 或在设计文档中标注 ShardingSphere-JDBC 版本号并说明已验证跨分片 JOIN 的实际行为。

### 问题 3：CQRS 投影表写路由机制缺失

- **所在位置**：§3.5.2（分片配置）vs §3.2.3（投影表同步）
- **严重程度**：中等
- **问题描述**：§3.5.2 将 `alert_projection` 和 `fleet_dashboard_projection` 配置为"全局只读表固定于 shard-0"。但投影同步器（消费 Kafka 事件后写入投影表）通过 ShardingSphere-JDBC 连接的 datasource 写数据——ShardingSphere-JDBC 的路由引擎需要确定将写操作路由至哪个物理分片。对于非分片表（无 `vehicle_id` 分片键），设计中未说明如何确保写操作精确路由至 shard-0（而非广播至所有分片或路由至错误分片）。不说明此路由机制，开发者在实现时可能面临投影表数据写入错误分片或重复写入的 Bug。
- **改进建议**：在 §3.5.2 或 §3.2.3 中说明投影表写入的路由方式，建议三个选项：(a) 使用 ShardingSphere HintManager 强制路由至 shard-0；(b) 为投影表配置独立 datasource（直连 shard-0 的 JDBC 连接，绕过 ShardingSphere）；(c) 或接受投影表作为广播表写入（与 outbox 同理）。

### 问题 4：文档标题版本号与文件名不一致

- **所在位置**：文档第 1 行标题
- **严重程度**：轻微
- **问题描述**：文件名表明是 v3（`a_v3_copy_from_v2.md`），但文档一级标题为 `# 车载安全监测系统 基础设施/适配器层 OOD 设计方案（a_v1 / v2）`，标注为 v2。标题版本号与文件实际版本号矛盾。
- **改进建议**：将标题修正为 `（v3）`。

---

## 二、关键遗漏与深度不足

### 问题 5：@ElementCollection 冻结技术实现中 Hibernate detach()+merge() 行为风险未充分评估

- **所在位置**：§3.1.3 策略 B「集合冻结技术实现」
- **严重程度**：中等
- **问题描述**：§3.1.3 提出超阈值后将 Trip 实体的 @ElementCollection 集合字段置为 `null`，并对 EntityManager 执行 `detach()` + `merge()`，"使 Hibernate 忽略该集合的脏检查"。此方案的可行性存在已知的技术风险：(a) `detach()` 将实体从持久化上下文中移除，`merge()` 将传入实体的状态**拷贝**到持久化上下文中的托管实体——若传入实体的集合字段为 `null`，Hibernate 通常会将托管实体的对应集合也置为 `null`，在 flush 时可能触发面向集合表的 `DELETE` 语句（取决于 `@ElementCollection` 的 cascade/orphanRemoval 配置），导致已写入 `trip_physiological_snapshot` 表的溢出行被意外删除；(b) 该风险在设计中未给予"待编码验证"的技术风险标注。
- **改进建议**：(a) 明确冻结后 Trip 实体的 save 改用 `StatelessSession`（绕过一级缓存和脏检查），仅执行 `UPDATE trip SET ... WHERE trip_id = ? AND version = ?`；(b) 标注此冻结方案为技术风险项，注明需在编码阶段验证 Hibernate 的实际行为（尤其关注 flush 时是否触发 DELETE 语句）；(c) 给出备选方案——在 TrackedTripEntity 中维护 `boolean snapshotCollectionFrozen` 标志，仓储层据此跳过 @ElementCollection 的级联保存而仅保存 Trip 表列字段。

### 问题 6：VehicleStateBuffer/PhysiologicalDataBuffer 的 ArrayDeque 固定容量实现方式不明确

- **所在位置**：§3.4.1、§3.4.2
- **严重程度**：轻微
- **问题描述**：两缓冲均描述为"定长环形缓冲区（ring buffer），基于 Java `ArrayDeque`（固定容量）实现"。JDK 标准库 `ArrayDeque` 是无界队列——构造参数仅指定初始容量，不限制最大容量，元素满后自动扩容。设计未说明如何实现"固定容量"语义（容量满时自动淘汰最旧元素）。若开发者直接使用 `ArrayDeque` 而不加容量检查，长时间运行时缓冲区将无限增长导致 OOM。
- **改进建议**：明确实现方案——(a) 使用 `CircularFifoQueue`（Apache Commons Collections 4）；(b) 或在每次 `add` 前检查 `size() >= capacity` 并手动 `removeFirst()`；(c) 补充窗口溢出时的行为（抛弃最旧数据 + 记录监控指标）。

### 问题 7：EdgeSessionContext destroy() 与碰撞检测的时序安全间隙未定义

- **所在位置**：§3.4.2a vs §3.7.4
- **严重程度**：轻微
- **问题描述**：§3.4.2a 描述 EdgeSessionContext 在 `VehicleIgnitionOffEvent` 触发时执行 `destroy()`——将缓冲数据写入 SQLite，清空内存缓冲，释放引用。但 §3.7.4 和需求 §七.3 描述碰撞失能判定（EmergencyResponseService 需要事故前 30s 车辆状态快照和 ≥10s 生理数据）在断网状态下完全闭环——碰撞失能可能在车辆熄火的同时或之后才被检测到（如撞击发生在熄火过程中），此时若 `destroy()` 已清空内存缓冲，则无法获取所需窗口数据。设计中未说明 `destroy()` 与判定引擎的碰撞检测之间是否存在时序保证。
- **改进建议**：明确 `destroy()` 的执行时机——在判定引擎确认"无待处理的安全判定"之后（如等待 N 秒安全间隙，或由判定引擎显式发送 `SessionTeardownSafe` 信号），而非直接在熄火事件到达时同步执行。或改为 `destroy()` 延迟执行——熄火事件触发后先等待一个安全观察窗口（如 5s），在此期间缓冲仍可用，若窗口内未触发碰撞事件则执行销毁。

---

## 三、整体评估

产出逐子项响应了需求文档 §一至§七 的 27 个子项，覆盖了数据持久化映射、仓储实现、事件基础设施、端口适配器、华为云集成、安全隐私和部署拓扑等全部核心领域。经过 3 轮迭代修订，前序 24 个历史问题已全部修复（12/12 + 12/12），表结构完整性、值对象映射策略、乐观锁机制、CQRS 投影同步等维度的质量已成熟。

当前版本存在 **3 个待验证/一般问题**（outbox 广播表事务原子性假设未验证、fleet_dashboard 跨分片 JOIN 可行性未确认、CQRS 投影表写路由缺失）、**1 个中等遗漏**（@ElementCollection 冻结技术风险）、**2 个轻微问题**（标题版本号、ring buffer 容量、EdgeSessionContext 销毁时序）。无"严重"级别问题。

本产出已达到可指导编码实现的架构级 OOD 设计标准。建议优先标注两个 ShardingSphere 相关的待验证项并明确验证方式，补充投影表写路由机制，标注 @ElementCollection 冻结方案为编码阶段技术风险项。

---

## 修订说明（v3）

| 质询意见 | 回应 |
|---------|------|
| **严重**：问题 1（outbox 广播表事务原子性）和问题 2（fleet_dashboard 跨分片 JOIN）的关键技术判定缺乏证据——未引用 ShardingSphere 官方文档、未说明版本号、未提供验证手段 | **采纳**。已对两项问题进行重大修订：(a) 尝试查阅 ShardingSphere 5.5.3 官方文档（Broadcast Table 和 Distributed Transaction 相关页面），但页面为 JS 渲染的 SPA，webfetch 工具仅能获取导航结构而无法获取页面正文内容，因此无法引用具体文档段落；(b) 两项问题的严重程度从"严重"分别降级为"一般（标注待验证）"，明确标注前提假设未通过实验或官方文档验证；(c) 改进建议中增加了"标注 ShardingSphere-JDBC 目标版本号并说明验证方式"的要求；(d) 问题 2 的改进建议增加了通过 `alert_projection`（同在 shard-0）聚合的折中方案以避免跨分片依赖。 |
| **严重**："整体评估"中"产出整体响应了需求文档 §一至§七 的全部要求"的结论缺乏系统性验证证据——未提供 §一至§七 全部子项的逐条响应检查记录 | **采纳**。已新增"需求响应充分度：逐子项系统性核查"章节，按需求文档 §一至§七 的 27 个子项逐一核对设计对应位置及覆盖状态，格式与 §〇 的问题验证表一致。核对结果：全部 27 个子项均已覆盖，无遗漏。 |
| **中等**：问题 1 的推演链条存在逻辑跳跃——"广播 = 多独立本地事务"这一步缺乏中间论证，未说明 ShardingSphere 事务管理器类型、物理连接数与事务边界的关系 | **采纳**。问题 1 的描述已重写——不再以断言形式声称"广播写是多独立本地事务"，而是从正反两方面陈述：(a) 设计方的断言（广播写与分片写在同一本地事务中）；(b) 批评方的疑点（若 ShardingSphere 在不同 shard 使用独立连接，则同一本地事务的前提不成立）；(c) 承认在缺乏官方文档或实验验证的情况下无法判定哪方成立。严重程度从"严重"降为"一般（标注待验证）"。 |
| **中等**：以下需求子项的覆盖检查存在缺失——(a) §二.4 乐观锁冲突重试策略的参数覆盖度；(b) §三.3 消息队列选型合理性；(c) §三.5 事件消费者配置表完整性；(d) §四.3 DrivingBehaviorTrackingPort @ConfigurationProperties 注入；(e) §七.5 部署架构图语义覆盖度 | **采纳**。已在本轮报告的"需求响应充分度：逐子项系统性核查"中覆盖全部反馈的子项：(a) §二.4——已验证 §3.2.4 策略表含评分计算指数退避参数（50/100/200ms，3次）和 OTA 上限（5次 100ms）等具体数值；(b) §三.3——已验证 §3.3.4 DMS Kafka 选型 + 四条理由，topic 命名规范 13 个主题的完整映射；(c) §三.5——已验证 §3.3.6 配置表含 19 个事件类型的消费方/模式/保证，无遗漏；(d) §四.3——已验证 §3.4.3 包含 @ConfigurationProperties 注入策略（`hard-braking.threshold`、`hard-acceleration.threshold`）；(e) §七.5——已验证 §3.7.2 Mermaid 图覆盖了 §3.7.1 所列全部边缘/云端组件（车载终端/云端应用/数据层/推送层/外部客户端），且含关键网络路径汇总表（10 条路径）。均未发现实质性质问题。 |
| **问题**：对需求 §四.8（HMI 输出端口）的覆盖检查缺失——v2 报告问题 4 将 HMI 适配器归为"关键遗漏（中等）" | **修订立场**。经重新审查需求 §四 的 8 个子项清单，HMI 输出端口不在需求 §四"外部端口适配器"的 8 个需求子项范围内——HMI 在需求中仅出现于 §七"边缘-云部署拓扑"（§七.1 边缘侧组件清单列有"本地 HMI 服务"）。设计 §3.7.4 明确"InterventionService 生成的 InterventionInstruction 由边缘侧本地 HMI 适配器渲染——氛围灯变色、语音播报、座椅震动等直接通过本地 CAN/HMI 接口执行"。HMI 输出作为边缘侧本地执行组件，其接口协议取决于车载 CAN 总线硬件规范，不属于本 OOD 适配器设计范围。v2 审查报告的"中等遗漏"评级缺乏需求基础——已将此项从上轮问题清单中移除。详见本报告需求 §四 核查表中"已覆盖"行的注释说明。 |
