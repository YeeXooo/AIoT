# 质量审查报告 — 基础设施/适配器层 OOD v3（第 2 轮）

> 审查对象：`a_v3_copy_from_v2.md`（第 3 轮迭代产出）
> 审查维度：需求响应充分度、事实错误/逻辑矛盾、深度与完整性（侧重内部审议未充分覆盖的维度）

---

## 〇、前序迭代问题验证

本节逐条验证第 1、2 轮迭代指出的 24 个问题在 v3 产出中的处理状态，为整体评估提供事实基础。

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

## 一、事实错误与逻辑矛盾

### 问题 1：ShardingSphere 广播表策略下 outbox 事务原子性不成立

- **所在位置**：§3.5.2（金仓数据库连接与分片配置）
- **严重程度**：严重
- **问题描述**：§3.5.2 将 `domain_event_outbox` 和 `domain_event_dlq` 配置为广播表，宣称"任意业务聚合根（无论路由至哪个分片）的 outbox 写入均与聚合根状态变更发生在同一本地数据库事务中——ShardingSphere-JDBC 对广播表的写入与同 shard 分片表的写入在同一个物理数据库上执行，由数据库本地事务保证原子性"。该论断在 ShardingSphere-JDBC 的 LOCAL 事务模式下对**聚合根所在分片**成立，但广播表写入是同步广播至**所有分片**的独立写操作——shard-0、shard-1、shard-3（均非聚合根所在分片）上的 outbox 写入各自处于独立的本地事务中，与聚合根 save 不在同一原子边界内。OutboxRelayer 仅扫描 shard-0 的 outbox 表投递，若聚合根所在分片的本地事务提交成功但 shard-0 的广播写入失败（如 shard-0 数据库连接断开），则出现"聚合根已持久化、outbox 无记录"的断链。要保证跨分片原子性，必须引入 XA 分布式事务或接受最终一致性。
- **改进建议**：(a) 明确声明使用 XA 事务模式并补充事务管理器的选型与配置影响评估；(b) 或放弃广播表方案，改为 outbox 固定于 shard-0 并接受最终一致性（补充重试补偿机制 + 标注 SLO）；(c) 无论选哪种方案，需评估对 §4 场景 2 outbox 全链路时序的影响。

### 问题 2：fleet_dashboard_projection 即时增量聚合跨分片 JOIN 不可行

- **所在位置**：§3.2.3 P2（缓存/物化视图策略 路径 A）vs §3.5.2（分片配置）
- **严重程度**：严重
- **问题描述**：§3.2.3 P2 路径 A 描述事件驱动的即时增量聚合——"实时 JOIN `vehicle`、`safety_alert_event`、`trip` 表计算该组合下的 `driver_count` 和 `heatmap_data`"。但按 §3.5.2 的配置，`safety_alert_event` 和 `trip` 按 `vehicle_id` 一致性哈希分片（分布在多物理库），`vehicle` 为广播表（每个分片冗余），`fleet_dashboard_projection` 固定于 shard-0。即时增量聚合的 JOIN 需要跨分片访问数据——ShardingSphere-JDBC 在 LOCAL 事务模式下不支持跨分片 JOIN，即便是简单的聚合查询也要求所有参与表位于同一分片。这意味着路径 A 的"即时增量聚合"在设计上不可行，除非：(a) 改为从 `alert_projection`（同在 shard-0）作为数据源进行聚合，而非直接查 `safety_alert_event`；(b) 或启用 ShardingSphere 的跨分片查询引擎并评估其性能开销。
- **改进建议**：(a) 将路径 A 的即时增量聚合数据源从 `safety_alert_event` + `trip` 改为 `alert_projection`（同在 shard-0），并在 `alert_projection` 中补充聚合所需的字段（如 `fleet_id`）；(b) 或明确标注路径 A 依赖 ShardingSphere-JDBC 的 federated query 能力并评估延迟；(c) 同步更新 §3.2.3 P2 的聚合计算口径描述，说明数据来源。

### 问题 3：文档标题版本号与文件名不一致

- **所在位置**：文档第 1 行标题
- **严重程度**：轻微
- **问题描述**：文件名表明是 v3（`a_v3_copy_from_v2.md`），但文档一级标题为 `# 车载安全监测系统 基础设施/适配器层 OOD 设计方案（a_v1 / v2）`，标注为 v2。标题版本号与文件实际版本号矛盾，可能在协作中造成混淆。
- **改进建议**：将标题修正为 `（v3）`。

---

## 二、关键遗漏

### 问题 4：HMI 输出端口适配器设计缺失

- **所在位置**：需求 §七.1 vs 设计 §3.4、§3.7.1
- **严重程度**：中等
- **问题描述**：需求 §七.1 明确列出边缘侧需部署"本地 HMI 服务（渲染干预指令、语音播报、氛围灯控制）"，设计 §3.7.1 边缘侧组件表中也列出"Spring Boot 应用实例"含判定引擎+干预服务。然而 §3.4「外部端口适配器」的 8 个适配器中均不包含 HMI 输出适配器——判定引擎产出的 `InterventionInstruction`（VO-12）如何经由基础设施层渲染为 CAN 总线信号（氛围灯变色）、语音播报指令（TTS 模块）、座椅震动信号等，在设计中没有任何接口契约或实现策略描述。干预指令的终端渲染是"判定→干预"链路的关键最后一环，缺失此适配器设计意味着下游开发者无法从设计文档获知如何将 VO-12 转化为实际物理输出。
- **改进建议**：在 §3.4 新增一个 HMI 输出适配器小节（如 `HmiOutputPort` 适配器），描述端口方法契约概要（`renderIntervention(InterventionInstruction)`、`playVoicePrompt(text)`、`setAmbientLight(color, pattern)` 等）、底层技术选型（CAN 总线通信库、TTS 引擎选择）和与边缘侧单线程模型的一致性保证。

### 问题 5：trajectory_projection 写入时 vehicle_id 映射路径缺失

- **所在位置**：§3.5.1 IoTDA 上行消息消费适配器 vs §3.2.3 P3、§3.1.5 分片策略
- **严重程度**：中等
- **问题描述**：`trajectory_projection` 表按 `vehicle_id` 一致性哈希分片（§3.1.5），写入必含 `vehicle_id`（§3.5.2）。然而该表的实际数据来源是边缘侧 MQTT 上报的 GPS 位置数据——MQTT 消息体中仅含设备标识 `terminal_sn`（IoTDA device_id），不含 `vehicle_id`。云端 `IotdaUplinkConsumer`（§3.5.1）消费 GPS 数据后需先解析 `terminal_sn` → `vehicle_id` 的映射才能写入 `trajectory_projection` 的分片路由，但设计中未描述这一查找步骤（是否查 Vehicle 表？是否缓存映射？映射缺失时如何处理？）。缺失此路径描述会导致：开发者无法确定写入分片的完整流程，且映射查询可能成为轨迹写入的性能瓶颈。
- **改进建议**：在 §3.5.1 IotdaUplinkConsumer 或 §3.2.3 P3 数据同步机制中，补充 `terminal_sn → vehicle_id` 映射的获取方式（建议通过 Redis 缓存 Vehicle 表的 terminal_sn→vehicle_id 映射，TTL 与 Vehicle 缓存一致），并说明映射缺失时的处理策略（日志告警 + 丢弃该条轨迹点）。

### 问题 6：CQRS 投影表写路由缺失——非 vehicle_id 分片表如何路由至 shard-0

- **所在位置**：§3.5.2（分片配置）vs §3.2.3（投影表同步）
- **严重程度**：中等
- **问题描述**：§3.5.2 将 `alert_projection` 和 `fleet_dashboard_projection` 配置为"全局只读表固定于 shard-0"。但投影同步器（消费 Kafka 事件后写入投影表）作为 `infra.cloud` 模块组件，通过 ShardingSphere-JDBC 连接的 datasource 写数据。ShardingSphere-JDBC 的路由引擎需要知道将写操作路由至哪个物理分片——对于非分片表（无 `vehicle_id` 分片键），默认行为是路由至所有分片或随机分片，而非精确路由至 shard-0。设计中未说明如何强制非分片表的写操作路由至 shard-0（如通过 ShardingSphere Hint 机制或独立 datasource 配置），也未说明投影同步器如何获取 shard-0 的独占写连接。
- **改进建议**：在 §3.5.2 或 §3.2.3 中说明投影表写入的路由机制——建议选项：(a) 使用 ShardingSphere HintManager 强制路由至 shard-0；(b) 为投影表配置独立的 datasource（直连 shard-0）；(c) 或接受投影表作为广播表写入（与 outbox 同理）。无论哪种方案，需说明其对 §4 场景 3（CQRS 投影同步）时序的影响。

---

## 三、深度与完整性不足

### 问题 7：@ElementCollection 优化中 JPA 脏检查规避方案的可行性存疑

- **所在位置**：§3.1.3 策略 B「集合冻结技术实现」
- **严重程度**：中等
- **问题描述**：§3.1.3 提出超阈值后冻结 Trip 实体的 `@ElementCollection` 集合——将字段置为 `null`，对 EntityManager 执行 `detach()` + `merge()`，"使 Hibernate 忽略该集合的脏检查"。但此方案的可行性存疑：`merge()` 将传入实体的状态拷贝至持久化上下文中的托管实体——若传入实体的集合字段为 `null`，Hibernate 通常会将托管实体的对应集合也置为 `null`，在 flush 时可能触发面向集合表的 `DELETE` 语句（级联 orphanRemoval），导致已写入 `trip_physiological_snapshot` 表的溢出行被意外删除。此风险在设计中未给予"待编码验证"的技术风险标注。
- **改进建议**：(a) 明确冻结后 Trip 实体的 save 改用 `StatelessSession`（绕过一级缓存和脏检查），仅执行 `UPDATE trip SET ... WHERE trip_id = ? AND version = ?`；(b) 标注此冻结方案为技术风险项，注明需在编码阶段验证 Hibernate 的实际行为；(c) 给出备选方案（如在 TripEntity 中为 @ElementCollection 集合维护一个 `boolean snapshotCollectionFrozen` 标志，仓储层据此跳过级联保存）。

### 问题 8：VehicleStateBuffer/PhysiologicalDataBuffer 与 EdgeSessionContext 的生命周期绑定存在时序风险

- **所在位置**：§3.4.2a vs §3.7.4
- **严重程度**：轻微
- **问题描述**：§3.4.2a 描述 EdgeSessionContext 在 `VehicleIgnitionOffEvent` 触发时执行 `destroy()`——将缓冲区数据序列化写入 SQLite，清空内存缓冲区，释放引用。但 §3.7.4 和需求 §七.3 均描述边缘侧的"判定→干预"链路在断网状态下完全闭环——碰撞失能判定（EmergencyResponseService 需要事故前 30s 车辆状态快照和 ≥10s 生理数据）可能在 `destroy()` 执行后才触发，导致无法获取所需窗口数据。设计中未说明 `destroy()` 与判定引擎的碰撞检测之间是否存在时序保证。
- **改进建议**：明确 `destroy()` 的执行时机在判定引擎确认"无待处理的安全判定"之后（如等待 N 秒安全间隙，或由判定引擎显式发送 `SessionTeardownSafe` 信号），而非直接在熄火事件到达时同步执行。

### 问题 9：Ring buffer 固定容量实现方式未明确

- **所在位置**：§3.4.1、§3.4.2
- **严重程度**：轻微
- **问题描述**：VehicleStateBuffer 和 PhysiologicalDataBuffer 均描述为"定长环形缓冲区（ring buffer），基于 Java `ArrayDeque`（固定容量）实现"。JDK 标准库 `ArrayDeque` 是无界队列——构造参数仅指定初始容量，不限制最大容量，元素满后自动扩容。设计未说明如何实现"固定容量"语义（超过容量时自动淘汰最旧元素）。若开发者在实践中直接使用 `ArrayDeque` 而无容量检查，长时间运行时缓冲区将无限增长导致 OOM。
- **改进建议**：明确实现方案——(a) 使用 `CircularFifoQueue`；(b) 或在每次 `add` 前检查 `size() >= capacity` 并手动 `removeFirst()`；(c) 补充窗口溢出时的行为（抛弃最旧数据 + 记录监控指标）。

---

## 四、整体评估

产出整体响应了需求文档 §一至§七 的全部要求，覆盖了数据持久化映射、仓储实现、事件基础设施、端口适配器、华为云集成、安全隐私和部署拓扑等核心领域。经过 3 轮迭代修订（v1→v2→v3），前序 24 个历史问题已全部修复（12/12 + 12/12），表结构完整性、值对象映射策略、乐观锁机制、CQRS 投影同步等维度的质量已有显著提升。

当前版本存在 **2 个严重问题**（outbox 广播表事务原子性、fleet_dashboard_projection 跨分片 JOIN 不可行）、**4 个中等遗漏**（HMI 适配器缺失、trajectory_projection 映射路径缺失、CQRS 投影表写路由未定义、JPA 脏检查规避方案存疑）、**3 个轻微问题**（标题版本号、ring buffer ArrayDeque 容量、EdgeSessionContext 销毁时序风险）。

建议优先解决两个严重问题——outbox 事务原子性问题直接影响事件投递的可靠性，fleet_dashboard_projection 跨分片 JOIN 问题直接影响看板功能的可实现性。其次补充三个中等遗漏，确保下游开发者可据此直接编码。

---

## 修订说明（v2）

| 质询意见 | 回应 |
|---------|------|
| **严重**：整体评估中"经过 3 轮迭代修订……质量已有显著提升"的结论缺乏证据支撑——审查报告未逐条验证第 1、2 轮迭代的 24 个历史问题是否已在 v3 产出中修复 | **采纳**。已新增「〇、前序迭代问题验证」小节，逐条对照 24 个历史问题在各产出章节中的修复状态。验证结果：第 1 轮 12 个问题全部已修复，第 2 轮 12 个问题全部已修复，总通过率 100%。"质量已有显著提升"的结论现已有事实基础支撑。 |
| **问题**：问题 3（DDL 格式）严重度评定偏高——需求原文含"概念级"和"不含完整 DDL"限定语，Markdown 表格信息量等价于概念级 DDL，评定为"中等"缺乏充分依据 | **采纳**。问题 3（DDL 格式）已从本版审查报告中移除。需求原文对 DDL 要求的严格程度有明确保留，Markdown 表格（字段名/列类型/约束/说明）信息量等价于概念级 CREATE TABLE 语句，下游开发者的单次机械转换工作量和出错风险可控，不足以构成"中等"级别的质量问题。 |
| **问题**：问题 7（JPA 脏检查规避方案）的结论依赖对 Hibernate `detach()+merge()` 行为的特定假设，但未提供引用来源或验证依据，降低了说服力 | **部分采纳**。问题描述已调整为更审慎的表述——将"极大概率不可行"改为"可行性存疑"，明确指出技术风险在于 `merge()` 可能触发面向集合表的 DELETE。同时降低了问题严重程度以反映诊断的审慎性。改进建议中增加了技术风险标注要求，建议在编码阶段验证 Hibernate 实际行为。 |
| **轻微**：对需求 §六（安全与隐私基础设施）的覆盖检查缺失——审查报告 8 个问题中无一条涉及安全隐私维度 | **接受观察**。经重新核查，设计 §3.6.1（数据脱敏）、§3.6.2（语音存证加密）、§3.6.3（二次身份验证集成）对需求 §六.1-§六.3 均有逐条响应，且覆盖深度达到编码指导级别（含 AES-256-GCM 算法参数、HKDF 派生逻辑、DEW 密钥轮转策略、SecondaryAuthProvider 接口抽象等）。本轮未发现安全隐私维度的新增实质性问题，确认该维度已被内部审议充分覆盖。 |
| **轻微**：对需求 §五（华为云服务集成）中 IoTDA 设备注册、SMN 消息模板、SparkRTC 集成等子项是否在设计中被逐条响应的检查不够系统 | **已改进**。本轮已完成需求 §五.1-§五.4 与设计 §3.5.1-§3.5.4 的系统性逐条核对：IoTDA 设备注册与设备影子（§3.5.1 ✓）、金仓数据库连接与分片配置（§3.5.2 ✓）、SMN 消息模板与 Push Kit（§3.5.3 ✓）、SparkRTC 房间管理与 Token 续期（§3.5.4 ✓）。所有子项均有对应设计覆盖。此验证结果已纳入本报告整体评估中。 |
| **新增审查发现**：fleet_dashboard_projection 即时增量聚合中的跨分片 JOIN 在设计上不可行——ShardingSphere-JDBC LOCAL 模式不支持跨分片 JOIN | **本轮新增**。此问题在 v1 审查中未被发现。§3.2.3 P2 路径 A 描述的即时增量聚合需要 JOIN 分布在多分片上的 `safety_alert_event` 和 `trip` 表，与 §3.5.2 中这些表按 vehicle_id 分片的配置矛盾。已列为问题 2（严重程度：严重）。 |
| **新增审查发现**：CQRS 投影表（alert_projection / fleet_dashboard_projection）固定于 shard-0 但缺少写操作路由机制 | **本轮新增**。§3.5.2 将两张 CQRS 投影表固定于 shard-0，但投影同步器通过 ShardingSphere-JDBC 写入时的分片路由方式未定义。已列为问题 6（严重程度：中等）。 |
