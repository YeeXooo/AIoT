# 基础设施/适配器层 OOD 质量审查报告（v7 / 第 7 轮）

> 审查视角：需求响应充分度、事实错误/逻辑矛盾、深度与完整性（侧重内部审议未充分覆盖的维度，以及从实际落地视角评估可编码性、接口定义对下游消费者的支撑度、异常场景和边界条件覆盖）。

---

## 1. trip_physiological_snapshot 的非 JPA 写入路径在 ShardingSphere-JDBC 下的分片路由可行性未验证

- **问题描述**：§3.1.3 策略 B 定义了三路写入 `trip_physiological_snapshot` 表的路径：(a) JPA `@ElementCollection` 级联（前 500 条）、(b) 溢出 JDBC batch INSERT（第 501 条起）、(c) 边缘同步 JDBC batch INSERT。路径 (a) 通过 Trip 聚合的 `vehicle_id` 分片键路由，路由可行。但路径 (b) 和 (c) 提交的 `INSERT INTO trip_physiological_snapshot (trip_id, timestamp, ...) VALUES (...)` 语句不包含分片键 `vehicle_id`。§3.1.5 声称通过 ShardingSphere-JDBC 绑定表机制按 `trip_id → vehicle_id` 映射路由，但 ShardingSphere-JDBC 绑定表机制的前提是两表共享同一分片键——`trip_physiological_snapshot` 表中不存在 `vehicle_id` 列，INSERT 语句中亦无该值，ShardingSphere-JDBC 无法仅凭 `trip_id`（UUID）推断分片目标。此问题若在编码阶段未解决，将导致溢出快照写入错误的物理分片，数据永久分散不可恢复。

- **所在位置**：§3.1.3 策略 B（三路写入描述）+ §3.1.5（绑定表说明）

- **严重程度**：严重

- **改进建议**：方案A——为 `trip_physiological_snapshot` 表增加冗余 `vehicle_id` 列，非 JPA 写入时一并提交该列值（从 Trip 元数据获取），由 `vehicle_id` 直接驱动分片路由，弃用绑定表方案；方案B——若维持绑定表方案，需在 §3.1.5 明确 ShardingSphere-JDBC 的绑定表对不含分片键的子表 INSERT 的实际路由行为并标注已验证的版本号，同时补充说明若绑定表不可用时的回退方案（如固定于 shard-0 + 最终一致性）。

---

## 2. WebSocket 会话管理缺少多实例部署下的消息路由机制

- **问题描述**：§6.2 描述家属 WebSocket 会话映射"以 `ConcurrentHashMap` 维护"，即内存级映射。但 §3.7.1 明确云端为"Spring Boot 应用服务集群（无状态，可水平扩展，二实例起步）"。在多实例部署下，存在以下路由断裂：(a) 实例 A 持有账户 X 的 WebSocket 连接，实例 B 从 Kafka 消费到发往账户 X 的告警事件；(b) 实例 B 查询本地 ConcurrentHashMap 无法找到该连接，导致告警无法推送。当前设计中家属 APP 的实时通知依赖"告警事件 → Kafka → 各实例消费 → 推送至 WebSocket 会话"路径，但消息路由到持有会话的实例的机制未定义。§3.7.1 中 Redis 的职责含"家属 WebSocket 会话管理"，但 §6.2 的实现描述与之矛盾——Redis 角色未明确。

- **所在位置**：§6.2（共享状态管理/家属 WebSocket 会话段）+ §3.7.1（Redis 职责）+ §3.7.2（部署图家属通道）

- **严重程度**：一般

- **改进建议**：方案A（推荐）——改用 Redis PubSub 实现跨实例 WebSocket 消息路由：每实例订阅统一的 Redis channel（如 `websocket:push`），Kafka 消费者收到告警事件后查询 Redis 获取目标账户的 WebSocket 所在实例标识，再通过 Redis PubSub 将消息路由至正确实例；方案B——所有实例均消费 Kafka 消息，各自检查本实例是否持有目标会话，持有则推送、否则静默跳过（简单但浪费资源）；方案C——引入 STOMP broker（如 RabbitMQ）作为 WebSocket 的外部消息中继，彻底解耦会话归属与消息推送。无论选何种方案，需在 §6.2 明确跨实例路由机制。

---

## 3. IoTDA 数据转发规则到 DMS Kafka 的 topic 映射关系未定义

- **问题描述**：§3.5.1「IoTDA 上行消息消费适配器」描述 IotdaUplinkConsumer 从 DMS Kafka 消费 IoTDA 转发的上行消息，并反序列化为领域事件发布。但 IoTDA 数据转发规则中将上行 MQTT topic（如 `iot/{vehicleId}/alert/event`、`iot/{vehicleId}/sensor/data`）映射到哪个 DMS Kafka topic 未定义。没有此映射规格，IotdaUplinkConsumer 无法确定订阅哪个 Kafka topic，也无法区分来自 IoTDA 的不同消息类型（告警 vs 传感器数据 vs 心跳）以执行不同的反序列化和分发逻辑。§3.3.4 定义的 Kafka topic 规范（如 `iot.safety.alert.triggered`）是 outbox → Kafka 的出站 topic，与 IoTDA → Kafka 的入站 topic 无关。

- **所在位置**：§3.5.1「IoTDA 上行消息消费适配器」

- **严重程度**：一般

- **改进建议**：新增 IoTDA 数据转发 topic 映射表，定义 IoTDA 源 topic 模式到 DMS Kafka topic 的映射关系（如 `iot/{vehicleId}/alert/event` → `iotda.uplink.alert`），同时说明 IotdaUplinkConsumer 按 topic 区分消息类型的路由逻辑。同步补充 IoTDA 数据转发规则的 JSON body 结构概要（含 `device_id`、`topic`、`payload` 等字段），供 IotdaUplinkConsumer 实现时参照。

---

## 4. CQRS 三张投影表的数据访问层实现机制未定义

- **问题描述**：§3.2.3 详细定义了三张投影表的字段、索引、同步策略和聚合口径，但未说明投影同步器通过什么数据访问机制写入这些表。投影表不属于领域层的五个聚合根——它们不在任何仓储接口的职责范围内。开发者阅读文档后不清楚：是使用 Spring Data JPA 的独立 Repository 接口（如 `AlertProjectionRepository extends JpaRepository<AlertProjection, String>`）、JDBC `JdbcTemplate` 批量写入，还是 MyBatis mapper。对于 `fleet_dashboard_projection` 的 `INSERT ... ON CONFLICT DO UPDATE` 原子覆盖写入，JPA 的 `save()` 无法直接表达此语义（Hibernate 的 `save()` 不生成 ON CONFLICT 子句），实现者需额外知道是使用原生 SQL 还是特定扩展。

- **所在位置**：§3.2.3（CQRS 投影表设计全节）

- **严重程度**：一般

- **改进建议**：在 §3.2.3 末尾增加"投影表数据访问层"小节，说明：(a) 三张投影表各自使用的写入机制（推荐：alert_projection 和 trajectory_projection 通过 `JdbcTemplate.batchUpdate()` 批量写入，fleet_dashboard_projection 通过原生 SQL `INSERT ... ON CONFLICT DO UPDATE` 写入）；(b) 投影同步器的 Spring Bean 类型（`@Component`）和注入方式；(c) 读侧查询通过专用 `@Repository` 接口暴露（方法签名概要）。明确投影表不使用 JPA Entity 的原因（无乐观锁需求、无需级联、原生 SQL 更高效）。

---

## 5. 边缘侧 trip_physiological_snapshot 表的索引策略缺失

- **问题描述**：§3.2.2a TripRepository 边缘侧实现描述生理快照的 JDBC 写入和加载路径："加载 Trip 时通过 `SELECT * FROM trip_physiological_snapshot WHERE trip_id = ?` 单独查询快照列表"，并支持"按时间戳范围分页查询（仅查最近 N 条快照）"。但边缘侧 SQLite 的 `trip_physiological_snapshot` 表仅定义了云端侧的复合主键 `(trip_id, timestamp)`（见 §3.1.3），边缘侧是否沿用相同的索引策略未说明。SQLite 中仅有该复合主键作为索引，对于 `WHERE trip_id = ? ORDER BY timestamp DESC LIMIT N` 查询效率可接受。但对于 `WHERE trip_id = ? AND timestamp >= ? AND timestamp <= ?` 的时间范围查询，复合主键 `(trip_id, timestamp)` 亦可有效利用。当前描述已在功能上覆盖，但缺少显式的边缘侧索引确认。

- **所在位置**：§3.2.2a（TripRepository 边缘侧段）+ §3.1.3（云端表结构仅定义了 PK）

- **严重程度**：轻微

- **改进建议**：在 §3.2.2a 中补充边缘侧 `trip_physiological_snapshot` 表的索引策略——明确边缘侧 SQLite 同样使用复合主键 `(trip_id, timestamp)`（UNIQUE 约束同时作为索引），说明该主键已覆盖按行程查询和时间范围过滤的需求，无需额外索引。

---

## 6. Redis 全局故障对多个关键子系统的级联影响未集中评估

- **问题描述**：Redis 在当前设计中承载了至少 7 项关键职责：(a) 看板缓存（§3.2.3）、(b) 幂等去重缓存 event_id LRU（§3.3.5）、(c) WebSocket 会话管理（§3.7.1）、(d) guardianship 分布式锁（§3.1.2）、(e) 家属离线消息队列（§3.4.6）、(f) IotdaUplinkConsumer 本地重试队列（§3.5.1）、(g) outbox 积压缓冲降级（§3.3.5）。当前文档对 guardianship 锁的 Redis 不可用降级（§3.1.2）和 outbox 积压降级（§3.3.5）有独立处理，但缺少对 Redis 全局故障时的系统级影响评估——即当所有 Redis 功能同时不可用时，哪些功能降级、哪些阻断、恢复后如何协调。此外，(f) IotdaUplinkConsumer 的重试队列存储在 Redis 中，而 (c) 幂等去重也依赖 Redis——若 Redis 故障后 IotdaUplinkConsumer 已无法查重，可能导致重复消息被处理。

- **所在位置**：§3.1.2（guardianship 降级）、§3.3.5（outbox 降级）+ 分散于 §3.3.5、§3.4.6、§3.5.1、§3.7.1 的 Redis 依赖声明

- **严重程度**：轻微

- **改进建议**：在错误处理策略（§五）或并发设计（§六）中新增"Redis 全局故障影响矩阵"小节，以表格形式列出各 Redis 职责在 Redis 不可用时的降级行为、是否阻断核心业务、恢复后补偿措施。重点标注 IotdaUplinkConsumer 的重试队列与幂等去重共享同一 Redis 时的相互影响。不要求每个功能都有完美降级，但需让运维和实现者知晓全局故障时的系统行为边界。

---

## 整体评价

产出在结构上完整覆盖了需求文档的全部七大维度（数据持久化、仓储实现、事件基础设施、外部端口适配器、云服务集成、安全隐私、边缘-云拓扑），经过 6 轮迭代修订后，内部一致性和需求响应度已显著提升。当前版本的主要剩余风险集中在两个层面：(1) ShardingSphere-JDBC 绑定表机制对不含分片键的子表写入的实际行为（第 1 项，严重——可能阻塞溢出优化和边缘同步的实现）；(2) 水平扩展部署下的 WebSocket 消息路由缺口（第 2 项，一般——影响生产环境家属端实时推送的可靠性）。其余问题为编码阶段可由开发者自行决策的实现细节或边缘场景的补充说明。

建议在第 8 轮迭代中优先解决第 1 项和第 2 项，其余可降级为编码阶段的实现备注。
