# 质量审查报告 — 基础设施/适配器层 OOD v3

> 审查对象：`a_v3_copy_from_v2.md`（第 3 轮迭代产出）
> 审查维度：需求响应充分度、事实错误/逻辑矛盾、深度与完整性（侧重内部审议未充分覆盖的维度）

---

## 一、事实错误与逻辑矛盾

### 问题 1：ShardingSphere-JDBC 广播表策略下 outbox 事务原子性不成立

- **所在位置**：§3.5.2（金仓数据库连接与分片配置）
- **严重程度**：严重
- **问题描述**：§3.5.2 将 `domain_event_outbox` 和 `domain_event_dlq` 配置为 ShardingSphere-JDBC 广播表，宣称"此策略确保任意业务聚合根（无论路由至哪个分片）的 outbox 写入均与聚合根状态变更发生在**同一本地数据库事务**中——ShardingSphere-JDBC 对广播表的写入与同 shard 分片表的写入在同一个物理数据库上执行，由数据库本地事务保证原子性"。该论断仅在 **LOCAL 事务模式**下对聚合根所在分片成立——例如 Trip 落在 shard-2 时，shard-2 上的 Trip save 与 outbox 广播表写入处于同一本地事务。但广播表写入是同步广播至**所有分片**的独立写操作——shard-0、shard-1、shard-3 上的 outbox 写入各自处于独立的本地事务中（与 shard-2 的 Trip save 不在同一事务边界内）。OutboxRelayer 仅扫描 shard-0 的 outbox 表投递（§3.5.2），若 shard-2 事务提交但 shard-0 的广播写入失败，则出现"聚合根已持久化、outbox 无记录"的断链——outbox 模式核心前提被破坏。要保证跨分片原子性，必须引入 XA 分布式事务（2PC），但设计中未提及 XA 事务配置、也未评估其对写性能的影响。
- **改进建议**：(a) 明确声明 ShardingSphere-JDBC 使用 XA 事务模式，补充 XA 事务管理器的选型（如 Atomikos/Narayana）及对连接池的配置影响；(b) 或放弃广播表方案，改为将 outbox 表固定于 shard-0 且 outbox 写入与聚合根保存**解耦**为最终一致性模式（引入重试补偿机制并标注 SLO）；(c) 无论选哪种方案，均需评估对 §4 场景 2 outbox 全链路时序的影响。

### 问题 2：文档标题与文件名版本号不一致

- **所在位置**：文档第 1 行标题
- **严重程度**：轻微
- **问题描述**：文件名表明是 v3（`a_v3_copy_from_v2.md`），但文档一级标题为 `# 车载安全监测系统 基础设施/适配器层 OOD 设计方案（a_v1 / v2）`，标注为 v2。标题版本号与文件实际版本号矛盾，可能在协作中造成混淆。
- **改进建议**：将标题修正为 `（v3）`。

---

## 二、关键遗漏

### 问题 3：需求要求 DDL 概要但产出使用 Markdown 表格替代

- **所在位置**：需求 §一.5 vs 设计 §3.1.1
- **严重程度**：中等
- **问题描述**：需求 §一.5 明确要求"金仓数据库的 CREATE TABLE DDL 概要（概念级，不含完整 DDL，但需展示关键表的结构定义）"。产出 §3.1.1 以 Markdown 表格（字段名/列类型/约束/说明四列）描述表结构，未以 DDL 语法形式呈现。虽然信息量等价，但下游数据库开发者期望看到 `CREATE TABLE trip (trip_id VARCHAR(64) PRIMARY KEY, version INTEGER NOT NULL DEFAULT 0, ...)` 格式的概念级 DDL，以便直接对照编写实际 DDL 脚本。当前 Markdown 表格格式增加了开发者自行转换为 DDL 的工作量和出错风险。
- **改进建议**：在 §3.1.1 每张聚合根表定义后追加概念级 `CREATE TABLE` 语句概要（关键字使用金仓语法），或将表结构描述统一改为 DDL + 注释的形式。

### 问题 4：HMI（人机界面）端口适配器设计缺失

- **所在位置**：需求 §七.1 vs 设计 §3.4、§3.7.1
- **严重程度**：中等
- **问题描述**：需求 §七.1 明确列出边缘侧需部署"本地 HMI 服务（渲染干预指令、语音播报、氛围灯控制）"，设计 §3.7.1 边缘侧组件表中也列出"Spring Boot 应用实例"含判定引擎+干预服务。然而 §3.4「外部端口适配器」8 个适配器中均不包含 HMI 输出适配器——判定引擎产出的 `InterventionInstruction`（VO-12）如何经由基础设施层渲染为 CAN 总线信号（氛围灯变色）、语音播报指令（TTS 模块）、座椅震动信号等，在设计中没有任何接口契约或实现策略描述。干预指令的终端渲染是"判定→干预"链路的关键最后一环，缺失此适配器设计意味着下游开发者无法从设计文档获知如何将 VO-12 转化为实际物理输出。
- **改进建议**：在 §3.4 新增一个 HMI 输出适配器小节（如 `HmiOutputPort` 适配器），描述端口方法契约概要（`renderIntervention(InterventionInstruction)`、`playVoicePrompt(text)`、`setAmbientLight(color, pattern)` 等）、底层技术选型（CAN 总线通信库、TTS 引擎选择）和与边缘侧单线程模型的一致性保证。

### 问题 5：trajectory_projection 写入时 vehicle_id 映射路径缺失

- **所在位置**：§3.5.1 IoTDA 上行消息消费适配器 vs §3.2.3 P3、§3.1.5 分片策略
- **严重程度**：中等
- **问题描述**：`trajectory_projection` 表按 `vehicle_id` 一致哈希分片（§3.1.5），写入必含 `vehicle_id`（§3.5.2）。然而该表的实际数据来源是边缘侧 MQTT 上报的 GPS 位置数据——MQTT 消息体中仅含设备标识 `terminal_sn`（IoTDA device_id），不含 `vehicle_id`。云端 `IotdaUplinkConsumer`（§3.5.1）消费 GPS 数据后需先解析 `terminal_sn` → `vehicle_id` 的映射才能写入 `trajectory_projection` 的分片路由，但设计中未描述这一查找步骤（是否查 Vehicle 表？是否缓存映射？映射缺失时如何处理？）。缺失此路径描述会导致：开发者无法确定写入分片的完整流程，且映射查询可能成为轨迹写入的性能瓶颈。
- **改进建议**：在 §3.5.1 IotdaUplinkConsumer 或 §3.2.3 P3 数据同步机制中，补充 `terminal_sn → vehicle_id` 映射的获取方式（建议通过 Redis 缓存 Vehicle 表的 terminal_sn→vehicle_id 映射，TTL 与 Vehicle 缓存一致），并说明映射缺失时的处理策略（日志告警 + 丢弃该条轨迹点）。

### 问题 6：Ring buffer 固定容量实现方式未明确

- **所在位置**：§3.4.1、§3.4.2
- **严重程度**：轻微
- **问题描述**：VehicleStateBuffer 和 PhysiologicalDataBuffer 均描述为"定长环形缓冲区（ring buffer），基于 Java `ArrayDeque`（固定容量）实现"。但 JDK 标准库 `ArrayDeque` 是无界队列——构造参数 `ArrayDeque(int numElements)` 仅指定初始容量，不限制最大容量，元素满后自动扩容。设计中未说明如何实现"固定容量"语义（即超过容量时自动淘汰最旧元素）。若开发者在实践中直接用 `ArrayDeque` 而不加容量检查，在长时间运行时缓冲区将无限增长导致 OOM。
- **改进建议**：明确实现方案——(a) 使用 Apache Commons Collections `CircularFifoQueue`，(b) 或在每次 `add` 前检查 `size() >= capacity` 并手动 `removeFirst()`，(c) 或采用 Disruptor 等专业 ring buffer 库。补充说明窗口溢出时的行为（抛弃最旧数据 + 记录监控指标）。

---

## 三、深度与完整性不足

### 问题 7：@ElementCollection 优化中 JPA 脏检查规避方案的可行性存疑

- **所在位置**：§3.1.3 策略 B「集合冻结技术实现」
- **严重程度**：中等
- **问题描述**：§3.1.3 提出超阈值后冻结 Trip 实体的 `@ElementCollection` 集合——将字段置为 `null`，对 EntityManager 执行 `detach()` + `merge()`，"使 Hibernate 忽略该集合的脏检查"。但 JPA 规范下，`merge()` 会将传入实体的状态拷贝至持久化上下文中的托管实体——若传入实体的集合字段为 `null`，Hibernate 通常会将托管实体的对应集合也置为 `null`，并在 flush 时触发面向集合表的 `DELETE` 语句（级联 orphanRemoval），导致已写入 `trip_physiological_snapshot` 表的溢出行被删除。`detach()` + `merge()` 组合无助于规避此行为——问题根源在于 Hibernate 的集合快照比较机制，而非实体托管状态。此方案在实践中极大概率不可行，需更具体的实现技术支撑（如自定义 Hibernate `@Immutable` 集合映射、或使用 `StatelessSession` 独立更新 Trip 列而完全绕过 @ElementCollection 映射）。
- **改进建议**：(a) 明确冻结后 Trip 实体的 save 改用 `StatelessSession`（绕过一级缓存和脏检查），仅执行 `UPDATE trip SET ... WHERE trip_id = ? AND version = ?`，不动 collection 表；(b) 或给出已验证可工作的 Hibernate 配置方案（如 `@Immutable` 或自定义 `CollectionPersister`）；(c) 标注此部分为"待编码验证"的技术风险项。

### 问题 8：VehicleStateBuffer/PhysiologicalDataBuffer 与 EdgeSessionContext 的生命周期绑定描述存在时序风险

- **所在位置**：§3.4.2a vs §3.7.4
- **严重程度**：轻微
- **问题描述**：§3.4.2a 描述 EdgeSessionContext 在 `VehicleIgnitionOffEvent` 触发时执行 `destroy()`——将缓冲区数据序列化写入 SQLite，清空内存缓冲区，释放引用。但 §3.7.4 和需求 §七.3 均描述边缘侧的"判定→干预"链路在断网状态下完全闭环——`VehicleIgnitionOffEvent`（熄火）本身是一个需要在本地判定和持久化的事件。如果熄火事件发生时缓冲区先被销毁，则碰撞失能判定（EmergencyResponseService 需要事故前 30s 车辆状态快照和 ≥10s 生理数据）可能在 `destroy()` 执行后才触发，导致无法获取所需窗口数据。关键在于 `destroy()` 与判定引擎的碰撞检测之间是否存在竞态条件——设计中未说明二者的时序保证。
- **改进建议**：明确 `destroy()` 的执行时机在判定引擎确认"无待处理的安全判定"之后（如等待 N 秒安全间隙，或由判定引擎显式发送 `SessionTeardownSafe` 信号），而非直接在熄火事件到达时同步执行。

---

## 四、整体评估

产出整体响应了需求文档 §一至§七 的大部分要求，覆盖了数据持久化映射、仓储实现、事件基础设施、端口适配器、华为云集成、安全隐私和部署拓扑等核心领域。经过 3 轮迭代修订，表结构完整性、值对象映射策略、乐观锁机制、CQRS 投影同步等维度的质量已有显著提升。

当前版本存在 **1 个严重问题**（outbox 广播表事务原子性）、**4 个中等遗漏**（DDL 格式、HMI 适配器、trajectory_projection 映射路径、JPA 脏检查规避方案）、**3 个轻微问题**（标题版本号、ring buffer 实现、生命周期时序风险）。建议优先解决 outbox 事务原子性问题——它直接影响 outbox 模式在分片环境下的正确性；其次补充 HMI 适配器和 trajectory 映射路径，确保下游开发者可据此直接编码。

---

## 修订说明（v1）

_首轮审查，无前序修订。_
