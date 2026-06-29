# 质量审查诊断报告（b_v2 / v1）

## 审查概要

- **审查对象**：`a_v2_design_v6.md`（基础设施/适配器层 OOD 设计，共 1321 行）
- **审查轮次**：第 2 轮（首轮内部审议已完成，本次侧重需求响应充分度、深度完整性、逻辑一致性）
- **审查视角**：从实际落地视角评估——设计是否可直接指导编码实现、接口定义是否足以支持下游消费者、异常场景和边界条件是否已考虑

---

## 问题清单

### 问题 1 | 严重

- **问题描述**：`fleet_dashboard_projection`（P2 看板聚合投影表）缺少主键或唯一约束定义，导致数据同步策略中依赖的 `INSERT ... ON CONFLICT DO UPDATE` 操作无法执行。

- **所在位置**：§3.2.3 投影表 P2 字段定义

- **严重程度**：严重

- **改进建议**：
  1. 明确 P2 表的主键或唯一约束，建议以 `(fleet_id, risk_level, alert_type)` 作为复合唯一约束——语义上同一车队的同一风险等级/告警类型的聚合数据在同一刷新周期内仅保留一条。
  2. 在字段表中显式标注主键/唯一约束列，与 P1、P3 的标注风格保持一致。

---

### 问题 2 | 一般

- **问题描述**：`trajectory_projection`（P3 轨迹点投影表）的分片策略未明确。该表含 `vehicle_id` 列（可用于分片路由），但 §3.1.5 分片表清单中未包含此表。若不分片，所有车辆的轨迹点写入同一张表将成为串行写入瓶颈；若分片，PK 采用 `BIGSERIAL`（数据库自增序列）在跨分片环境下会产生主键冲突。

- **所在位置**：§3.2.3 投影表 P3 字段定义 vs §3.1.5 分片范围

- **严重程度**：一般

- **改进建议**：
  1. 明确 `trajectory_projection` 的分片归属：建议按 `vehicle_id` 分片（与 Trip 一致），并将 PK 改为应用层生成 UUID（与统一主键策略对齐），或改为 `(vehicle_id, timestamp, trajectory_id)` 复合分区键。
  2. 将 `trajectory_projection` 补充到 §3.1.5 分片范围清单中。

---

### 问题 3 | 一般

- **问题描述**：心跳监控机制缺失实现细节。§3.7.5 描述了心跳超时判定逻辑（90s = 3×30s）、心跳检测定时任务（每 30s），但 Vehicle 表结构中仅有 `offline_since TIMESTAMP NULL`（NULL 表示在线），**不存在**记录最后心跳时间的字段（如 `last_heartbeat_at`）。心跳监控定时任务如何判断"超时"——即如何知道车辆上次心跳在 90s 之前——缺乏数据支撑。

- **所在位置**：§3.1.1 Vehicle 表结构 vs §3.7.5 降级策略表

- **严重程度**：一般

- **改进建议**：
  1. 在 Vehicle 表中增加 `last_heartbeat_at TIMESTAMP NULL` 列，记录最近一次心跳时间戳。
  2. 说明心跳监控定时任务的执行逻辑：`UPDATE vehicle SET offline_since = NOW() WHERE last_heartbeat_at < NOW() - INTERVAL '90s' AND offline_since IS NULL`（标记脱线）和 `UPDATE vehicle SET offline_since = NULL WHERE last_heartbeat_at >= NOW() - INTERVAL '90s' AND offline_since IS NOT NULL`（恢复在线）。
  3. 说明 IoTDA 规则引擎将 `iot/{vehicleId}/status/heartbeat` 上行消息转发至应用服务后，由哪个组件更新 `last_heartbeat_at`。

---

### 问题 4 | 一般

- **问题描述**：Outbox 投递器轮询 SQL 查询条件存在逻辑缺陷。§3.3.2 和 §3.3.5 定义的轮询查询为 `WHERE published = FALSE AND (last_attempt_at IS NULL OR last_attempt_at < NOW() - 退避间隔)`，其中"退避间隔"为 **per-event** 变量（`min(2^retryCount × 1s, 60s)`），取决于各事件的 `retry_count` 列值。此变量无法在单条 SQL 中计算——SQL 无法为每行执行 `min(2^retryCount × 1s, 60s)` 的动态运算后再比较时间差。按当前 SQL 描述实现将导致在退避期内的重试事件被错误地纳入轮询（若使用固定标量），或 SQL 无法执行（若期望动态计算）。

- **所在位置**：§3.3.2 索引设计 + §3.3.5 退避策略 + §4 场景 2 步骤 3

- **严重程度**：一般

- **改进建议**：
  1. 明确实际实现方式——两种可行方案：
     - **方案 A（推荐）**：轮询 SQL 仅过滤 `published = FALSE` + `last_attempt_at IS NULL OR last_attempt_at < NOW() - INTERVAL '60s'`（以最大退避间隔 60s 为固定上界），将行拉入内存后由 Java 代码按各事件的 `retry_count` 逐一计算退避间隔并判定是否满足重试条件。
     - **方案 B**：在 outbox 表中增加预计算列 `next_retry_at TIMESTAMP`（写入/更新时由应用层计算），SQL 简化为 `WHERE published = FALSE AND (next_retry_at IS NULL OR next_retry_at <= NOW())`，将计算下沉到写入侧。
  2. 同步更新 §4 场景 2 步骤 3 的查询描述，消除三处描述（§3.3.2 / §3.3.5 / §4）之间的差异。当前 §4 已修正查询语句，但 §3.3.2 和 §3.3.5 仍存在模糊之处。

---

### 问题 5 | 一般

- **问题描述**：`guardianship` 监护关系表的权限变更策略存在内部表述矛盾，且历史记录无法保留。

  - §3.1.4 称"权限变更以整行 DELETE + INSERT 实现"和"REVOKED 为状态标记而非物理删除"——两句话矛盾：DELETE 即物理删除，不可能是状态标记。
  - 复合主键 `(driver_id, account_id)` 下，若撤销后重授权、或修改权限范围，旧记录被 DELETE 后永久丢失，无法追溯历史监护关系，也无法满足审计需求。
  - 当前设计的实际行为取决于对矛盾描述的解读方向——是真正的 DELETE（丢失历史）还是 UPDATE `revoked_at`（保留历史但对 PK 唯一约束产生多行冲突）。

- **所在位置**：§3.1.2 guardianship 表结构 + §3.1.4 不适用场景说明 + §6.3 并发策略

- **严重程度**：一般

- **改进建议**：
  1. **统一表述，明确实际策略**：推荐保留历史记录——将主键改为 `(driver_id, account_id, granted_at)` 或增加自增 `guardianship_id` 主键；权限撤销仅 UPDATE `revoked_at`；权限变更（范围修改）INSERT 新行并 UPDATE 旧行 `revoked_at`。
  2. 若确实不需要历史记录（依赖领域事件审计），则删除"REVOKED 为状态标记而非物理删除"的矛盾表述，明确说明历史记录不保留、审计通过 `FamilyAccessGrantedEvent` / `FamilyAccessRevokedEvent` 追溯。
  3. 无论选哪种方案，需同步修正 §6.3 并发策略表中对 guardianship 不可变替换模式的描述。

---

### 问题 6 | 一般

- **问题描述**：IoTDA 上行消息消费机制不够具体，无法指导编码实现。§3.5.1 中 `IotdaUplinkConsumer` 的消费方式描述为"通过 IoTDA REST API 订阅或规则引擎 HTTP 转发"，使用了"或"字——未选定具体方案。不同方案对部署架构和可靠性有关键影响：

  - **HTTP 转发**（规则引擎 webhook）：要求云端应用服务暴露公网 HTTP 端点，需处理 TLS 终接、DDoS 防护、HTTP 超时和重试。
  - **AMQP 订阅**：应用服务作为 AMQP 客户端连接 IoTDA，需维护长连接、处理断连重连、管理消费位点。
  - **数据转发至 DMS Kafka**：IoTDA 直接转发到 Kafka topic，应用服务从 Kafka 消费——减少一跳，但要求 IoTDA 和 Kafka 均已开通。

- **所在位置**：§3.5.1「IoTDA 上行消息消费适配器」小节

- **严重程度**：一般

- **改进建议**：
  1. **选定一种具体方案并说明理由**。推荐 IoTDA 数据转发至 DMS Kafka（应用服务从现有 Kafka topic 消费），理由：复用已有的 DMS Kafka 基础设施，减少额外 HTTP 端点暴露面，消费位点和重试由 Kafka consumer group 管理——与 §3.3.4 的消费模型一致。
  2. 说明 `IotdaUplinkConsumer` 在此方案下的具体消费方式：Kafka consumer（非 REST/HTTP），订阅 topic（如 `iot.device.uplink`），消息体格式（IoTDA 标准数据转发 JSON）。
  3. 若选 HTTP 转发方案，需补充云端应用服务暴露公网端点的安全措施（TLS、签名校验、限流）。

---

### 问题 7 | 一般

- **问题描述**：`fleet_dashboard_projection` 的缓存策略存在路径断裂。§3.2.3 P2 描述了两层数据流：

  1. **投影表层**：定时任务每 5 分钟全量刷新 `fleet_dashboard_projection` 表（从源表重新聚合）。
  2. **缓存层**：Redis 缓存 TTL 5 分钟，事件驱动的高危告警触发 Redis 缓存失效，"下次查询即触发即时重算"。

  问题在于：事件驱动的缓存失效仅删 Redis key，**不**更新投影表。若告警发生在定时任务刚执行完 4 分钟后（投影表数据已有 4 分钟延迟），Redis 缓存失效后的"即时重算"将回退到**已延迟 4 分钟**的投影表数据，而非从源表重新实时聚合。语义上的"即时重算"与实际的数据陈旧度不匹配。

- **所在位置**：§3.2.3 投影表 P2 缓存/物化视图策略

- **严重程度**：一般

- **改进建议**：
  1. 明确"即时重算"的实际含义，两种可行路径：
     - **路径 A**：缓存失效后，`IFleetManagementService` 执行针对该 `fleet_id` 的即时增量聚合（FROM source tables WHERE fleet_id = X），将结果写入 Redis 并同步更新 `fleet_dashboard_projection` 表中该车队的行。
     - **路径 B**：仅失效缓存，查询时从投影表读取，但接受 ≤5 分钟的固有延迟约束（在文档中明确此 SLO）。
  2. 若选路径 A，需补充描述即时聚合查询的 SQL 概要。

---

### 问题 8 | 一般

- **问题描述**：`@ElementCollection` 优化后，Trip 聚合的 JPA 集合被标记为"已截断并冻结不再追加"（§3.1.3），但未说明冻结后 JPA 对该集合的修改操作（如 `remove`、`clear`、或通过 setter 赋值新集合）会触发何种行为。JPA 的脏检查机制在集合"冻结"后若仍检测到集合变更（如应用层误操作），可能引发级联 DELETE + INSERT 刷写到底层 `trip_physiological_snapshot` 表，**覆盖** JDBC batch INSERT 写入的溢出数据。该交互风险直接影响数据完整性——实际编码时必须有明确的防御机制。

- **所在位置**：§3.1.3 策略 B 优化触发条件

- **严重程度**：一般

- **改进建议**：
  1. 说明"冻结"的技术实现方式：
     - 将集合字段替换为不可变包装（`Collections.unmodifiableList`）或清空 JPA 管理的集合引用后置为 null（并标注 `@ElementCollection` 的 `fetch = LAZY` 以抑制自动加载）。
     - 或使用防御性标记（`boolean elementCollectionFrozen`），在 Trip 聚合的 setter 中检查此标记，拒绝任何追加操作。
  2. 明确冻结后集合的只读语义：任何修改尝试应抛出 `IllegalStateException`（如"ElementCollection is frozen after exceeding max-collection-size threshold"）。
  3. 在 §4 关键行为契约中增加一个场景：**超阈值优化触发与集合冻结**。

---

### 问题 9 | 轻微

- **问题描述**：`trip_physiological_snapshot` 表按 `vehicle_id` 分片（§3.1.5），但表结构中未定义 `vehicle_id` 列（§3.1.3 仅定义 `trip_id + timestamp` 复合主键）。分片路由依赖 ShardingSphere-JDBC 的"绑定表"（binding table）特性——将子表绑定到 Trip 父表以共享分片键。该机制在设计中未被说明，实施者可能遗漏配置导致子表写入路由错误分片。

- **所在位置**：§3.1.5 分片范围 vs §3.1.3 trip_physiological_snapshot 表结构

- **严重程度**：轻微

- **改进建议**：
  1. 在 §3.1.5 分片配置中显式说明 `trip_physiological_snapshot` 通过 ShardingSphere-JDBC 绑定表机制绑定至 `trip` 表，共享 `vehicle_id` 分片键路由。
  2. 可选：在 `trip_physiological_snapshot` 表中增加冗余 `vehicle_id VARCHAR(64) NOT NULL` 列（与 `road_rage_voice_record` 表处理方式一致），使子表可独立于父表路由，简化 ShardingSphere 配置。

---

### 问题 10 | 轻微

- **问题描述**：`TripRepository` 的自定义 JPQL 查询（§3.2.2）未处理行程进行中场景。查询条件 `t.endedAt <= :to` 会将 `endedAt IS NULL`（行程未结束）的记录排除在外。对于报告生成场景（如"查询本月所有行程"），进行中的行程应被纳入结果。

- **所在位置**：§3.2.2「TripRepository 中按时间范围和 DriverId 查询行程列表」

- **严重程度**：轻微

- **改进建议**：
  1. 将查询条件改为 `(t.endedAt IS NULL OR t.endedAt <= :to)`，或提供一个单独的查询方法（如 `findOngoingTripsByDriver`）供调用方按需选择。
  2. 明确调用方（报告生成服务）的语言：是否包含进行中行程？若否，在文档中注明。

---

### 问题 11 | 轻微

- **问题描述**：`DriverRepository.updateScore` 使用 `@Modifying @Query`（§3.2.1），其 WHERE 子句含 `d.version = :version` 用于乐观锁判断。但 `@Modifying @Query` 不会抛出 JPA 的 `OptimisticLockException`——当 WHERE 条件不匹配时，仅返回受影响行数 0。调用方领域服务（DS-09）需检查返回行数并手动抛出 `OptimisticLockConflict`，但这一异常转换链路在设计中未说明。

- **所在位置**：§3.2.1 DriverRepository 实现策略

- **严重程度**：轻微

- **改进建议**：
  1. 说明 `updateScore` 方法返回 `int`（受影响行数），仓储实现（或包裹层）在返回 0 时手动抛出 `PersistenceException.OptimisticLockConflict`。
  2. 将此异常转换逻辑补充到 §3.2.4 的 Driver 场景行中，与 Trip 场景的 JPA `OptimisticLockException` 捕获路径形成对照说明。

---

### 问题 12 | 轻微

- **问题描述**：传感器自检状态更新的乐观锁冲突重试间隔设为"0ms"（立即重试一次，§3.2.4）。在多实例并发、短事务冲突场景下，立即重试有较高概率再次冲突（对方事务尚未提交），"0ms"的退避不提供任何隔离效果。

- **所在位置**：§3.2.4 乐观锁冲突重试策略表（传感器自检场景行）

- **严重程度**：轻微

- **改进建议**：
  1. 将重试间隔改为一个较小但不为零的值（如 10ms），在保持低延迟的同时提供基本的冲突隔离效果。
  2. 或保留 0ms 但说明仅在边缘侧单线程环境使用（§6.1 边缘侧为单线程串行，0ms 重试无竞争场景下可接受），云端侧另作配置。

---

## 整体评价

该产出已系统性地覆盖了需求文档七大部分的全部设计要点，在表结构定义、JPA 映射策略、事件总线双模实现、端口适配器设计、部署拓扑等方面达到了可观的深度。多轮修订（已标注至 v7）说明内部审议已纠正了若干结构性和逻辑性错误。

从实际落地视角看，当前产出存在 1 个严重问题（P2 投影表缺少主键）和 7 个一般问题，主要集中在：部分机制描述不够具体（IoTDA 消费方式、心跳监控实现、缓存即时重算路径）、个别 SQL/策略存在逻辑歧义（Outbox 轮询查询、guardianship 历史策略）、以及组件间契约细节可进一步闭合。上述问题建议在进入编码实现前修正，其中严重问题（P2 主键）为阻塞项。

**未发现以下类型问题**：需求响应遗漏（七大部分均有对应设计章节）、系统性事实错误、结构性逻辑矛盾（除上述标注项外）、整体深度严重不足。

---

## 修订说明（v1）

首轮审查，无前置质询意见，本章节为空。
