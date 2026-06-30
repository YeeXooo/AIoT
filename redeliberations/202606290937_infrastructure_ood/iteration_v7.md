# 再审议判定报告（v7）

## 判定结果

RETRY

## 判定理由

诊断报告识别出 6 项问题，其中严重等级 1 项、一般等级 3 项、轻微等级 2 项。质询报告确认审查结论为 LOCATED，证据充分、逻辑自洽，无需要驳回的缺陷。组件B内部循环实际轮次（1）远未达到最大轮次（12），属正常的质量审查完成终止。

根据判定标准：审查报告包含严重或一般等级的问题，判定为 RETRY。

## 需要解决的问题

- **问题描述**：trip_physiological_snapshot 的非 JPA 写入路径（溢出 JDBC batch INSERT、边缘同步 JDBC batch INSERT）在 ShardingSphere-JDBC 下的分片路由可行性未验证——INSERT 语句不包含分片键 vehicle_id，ShardingSphere-JDBC 绑定表机制无法仅凭 trip_id（UUID）推断分片目标，将导致溢出快照写入错误物理分片、数据永久分散不可恢复。
- **所在位置**：§3.1.3 策略 B（三路写入描述）+ §3.1.5（绑定表说明）
- **严重程度**：严重
- **改进建议**：方案A——为 trip_physiological_snapshot 表增加冗余 vehicle_id 列，非 JPA 写入时一并提交该列值，由 vehicle_id 直接驱动分片路由，弃用绑定表方案；方案B——若维持绑定表方案，需在 §3.1.5 明确 ShardingSphere-JDBC 绑定表对不含分片键的子表 INSERT 的实际路由行为并标注已验证的版本号，同时补充绑定表不可用时的回退方案。

- **问题描述**：WebSocket 会话管理缺少多实例部署下的消息路由机制——§6.2 以 ConcurrentHashMap 维护会话（内存级），但 §3.7.1 明确部署为集群二实例起步。实例 B 从 Kafka 消费告警事件后无法路由到实例 A 持有的 WebSocket 连接，导致家属端实时推送断裂。此外 §3.7.1 中 Redis 职责含"WebSocket 会话管理"，但 §6.2 的实现描述与之矛盾。
- **所在位置**：§6.2（共享状态管理/家属 WebSocket 会话段）+ §3.7.1（Redis 职责）+ §3.7.2（部署图家属通道）
- **严重程度**：一般
- **改进建议**：方案A（推荐）——改用 Redis PubSub 实现跨实例 WebSocket 消息路由，Kafka 消费者查询 Redis 获取目标账户 WebSocket 所在实例标识，通过 Redis PubSub 将消息路由至正确实例；方案B——所有实例均消费 Kafka 消息，各自检查是否持有目标会话；方案C——引入 STOMP broker 作为外部消息中继。需在 §6.2 明确所选方案。

- **问题描述**：IoTDA 数据转发规则到 DMS Kafka 的 topic 映射关系未定义——§3.5.1 IotdaUplinkConsumer 需从 DMS Kafka 消费 IoTDA 转发的上行消息，但 IoTDA 源 MQTT topic（如 iot/{vehicleId}/alert/event）映射到哪个 DMS Kafka topic 未定义，消费者无法确定订阅目标及区分消息类型。§3.3.4 定义的 Kafka topic 是 outbox 的出站 topic，与入站无关。
- **所在位置**：§3.5.1「IoTDA 上行消息消费适配器」
- **严重程度**：一般
- **改进建议**：新增 IoTDA 数据转发 topic 映射表，定义 IoTDA 源 topic 模式到 DMS Kafka topic 的映射关系（如 iot/{vehicleId}/alert/event → iotda.uplink.alert），说明消费者按 topic 区分消息类型的路由逻辑，同步补充 IoTDA 数据转发规则的 JSON body 结构概要。

- **问题描述**：CQRS 三张投影表的数据访问层实现机制未定义——§3.2.3 定义了三张投影表的字段、索引、同步策略和聚合口径，但投影同步器通过什么数据访问机制写入这些表未说明。投影表不在任何仓储接口职责范围内，fleet_dashboard_projection 的 INSERT ... ON CONFLICT DO UPDATE 原子覆盖写入与 JPA save() 语义不兼容，开发者无法确定使用 JPA Repository、JdbcTemplate 还是 MyBatis。
- **所在位置**：§3.2.3（CQRS 投影表设计全节）
- **严重程度**：一般
- **改进建议**：在 §3.2.3 末尾增加"投影表数据访问层"小节，说明各表写入机制（推荐 alert_projection 和 trajectory_projection 用 JdbcTemplate.batchUpdate()，fleet_dashboard_projection 用原生 SQL），明确投影同步器的 Spring Bean 类型和注入方式，读侧查询通过专用 @Repository 接口暴露。

- **问题描述**：边缘侧 trip_physiological_snapshot 表的索引策略缺失——边缘侧 SQLite 的 trip_physiological_snapshot 表仅沿用了云端侧的复合主键 (trip_id, timestamp)，对按行程查询和时间范围过滤的性能覆盖未显式确认。
- **所在位置**：§3.2.2a（TripRepository 边缘侧段）+ §3.1.3（云端表结构仅定义了 PK）
- **严重程度**：轻微
- **改进建议**：在 §3.2.2a 中补充边缘侧 trip_physiological_snapshot 表的索引策略——明确边缘侧 SQLite 使用复合主键 (trip_id, timestamp)（UNIQUE 约束同时作为索引），说明该主键已覆盖按行程查询和时间范围过滤的需求。

- **问题描述**：Redis 全局故障对多个关键子系统的级联影响未集中评估——Redis 承载看板缓存、幂等去重、WebSocket 会话管理、guardianship 分布式锁、离线消息队列、IotdaUplinkConsumer 重试队列、outbox 积压缓冲降级共 7 项职责，当前文档对各职责的降级策略分散描述，缺少系统级影响矩阵。
- **所在位置**：§3.1.2（guardianship 降级）、§3.3.5（outbox 降级）+ 分散于 §3.3.5、§3.4.6、§3.5.1、§3.7.1 的 Redis 依赖声明
- **严重程度**：轻微
- **改进建议**：在错误处理策略或并发设计中新增"Redis 全局故障影响矩阵"小节，以表格形式列出各 Redis 职责的降级行为、是否阻断核心业务、恢复后补偿措施，重点标注 IotdaUplinkConsumer 重试队列与幂等去重共享同一 Redis 时的相互影响。
