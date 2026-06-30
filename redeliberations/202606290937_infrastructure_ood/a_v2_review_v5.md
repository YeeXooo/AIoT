# OOD 设计方案审查报告（v5）

## 审查结果

REJECTED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计中全部类型形态（JPA `@Entity` / `@Embeddable` / `@Version`、Spring `@Repository` / `@Component` / `@EventListener` / `@Scheduled`、`JpaRepository<T, ID>` 泛型继承）均在 Java 17+/Spring Boot 类型系统能力范围内。聚合根与实体的 1:1 关系（DriverHealthProfile 的 `@OneToOne` + `@MapsId` 映射）、多对多关联表（guardianship 的复合主键）、值对象 `@ElementCollection` 集合映射等均符合 JPA/Hibernate 规范约束（单继承限制不涉及，因设计中无跨抽象继承需求）。泛型使用限于 `JpaRepository<T, String>`、`Optional<T>`、`List<T>` 等标准 JDK/Spring 泛型范式，无超出 Java 泛型擦除模型能力边界的用法。

### 2. 标准库与生态覆盖

**[通过]** 设计中依赖的所有库/服务均在目标技术栈覆盖范围内：Spring Data JPA + Hibernate（ORM）、HikariCP（连接池）、ShardingSphere-JDBC（分片，`CONSISTENT_HASH` 一致性哈希算法为 ShardingSphere-JDBC 内置支持的分片算法）、Eclipse Paho / Spring Integration MQTT（MQTT 客户端）、华为云 DMS Kafka（兼容 Apache Kafka 协议）、Redis（Jedis/Lettuce）、Kingbase JDBC 驱动（兼容 PostgreSQL 协议，方言 `PostgreSQLDialect`）、BouncyCastle（`bcprov-jdk18on`，HKDF 依赖已注明）。SQLite JDBC 用于边缘侧轻量持久化——该选型匹配边缘侧资源受限约束。设计中假设的所有库能力均合理。

### 3. 语言特性可行性

**[通过]** 错误处理策略与 Java unchecked exception 模型一致——`PersistenceException.OptimisticLockConflict` 等继承自 `RuntimeException`，`Optional<T>` 表达"不存在"语义。并发设计：边缘侧采用单线程串行（Java 单线程进程内顺序执行，无需同步原语），云端侧采用乐观锁版本号 + Kafka consumer group 分区消费 + Redis 缓存——全部在 Java/Spring 并发模型能力范围内。资源管理：ring buffer 基于 `ArrayDeque` 定长容量 + 手动丢弃旧槽实现，HikariCP 管理 JDBC 连接池，JPA EntityManager 由 Spring 托管——均为 Java 生态标准资源管理模式。模块划分（`infra.persistence`、`infra.repository`、`infra.eventbus` 等）遵循标准 Java package 按职责分层惯例，可行。

### 4. 设计一致性

**[严重]** PhysiologicalSnapshot 的 `@ElementCollection` 优化触发条件指定溢出的快照"改为仅写入 trajectory_projection 投影表"（§3.1.3 策略 B 注释段落）。但 `trajectory_projection`（P3，§3.2.3）的字段为 `trajectory_id`、`trip_id`、`vehicle_id`、`driver_id`、`timestamp`、`gps_latitude`、`gps_longitude`、`speed`——仅承载 GPS 轨迹点，缺少 PhysiologicalSnapshot 所需的核心字段（`heart_rate`、`blood_oxygen`、`emotion_index`）。按此描述实现将导致溢出生理快照数据丢失。正确的溢出目标应为 `trip_physiological_snapshot` 表（`@ElementCollection` 的实际底层存储表），与 §3.7.4 中边缘侧快照同步至该表的方案一致。

**[通过]** 除此问题外，设计各部分内部一致：十二项遗留问题（分片键缺失、取模与一致性哈希矛盾等）均已在 v3–v6 修订中逐一修复；表结构与值对象映射策略（策略 A/B/C/D）对应关系正确（VO-16 DrivingBehaviorCounters、VO-17 L3DurationTracker 已在 v3/v5 修正至策略 A，策略 C/D 已在 v5 拆分）；outbox 表与 DLQ 表的广播表策略（§3.5.2）解决了跨 shard 事务原子性问题；EdgeSessionContext 生命周期（§3.4.2a）与 SessionLifecycleManager 的 Spring 集成方式描述一致；所有五个聚合根、八个领域端口、三张 CQRS 投影表均已覆盖设计；模块间依赖为单向、无循环依赖。

### 5. 设计质量

**[通过]** 职责划分清晰：`infra.persistence` 仅负责 JPA 映射与表结构管理，`infra.repository` 负责接口实现与乐观锁冲突检测，`infra.eventbus` 负责事件总线双模式实现（边缘侧同步 + 云端 outbox/Kafka），`infra.adapter` 负责全部八个端口适配器，`infra.cloud` 负责华为云服务适配，`infra.security` 负责安全加密与认证，`infra.edge` 负责边缘侧专用基础设施。各模块仅依赖领域层接口契约，不向上依赖应用层。边缘侧与云端侧分别提供契合各自运行环境的实现（单线程同步 vs 多线程异步），抽象层次恰当。乐观锁冲突重试由领域服务自行决策、仓储层不封装业务重试的设计保持仓储可测试性。CQRS 投影表独立于聚合根事务边界，读模型与写模型解耦，便于测试。值对象映射策略（嵌入/集合表/JSONB/单列/不持久化）有明确的选择标准（Decision 1），过度设计或设计不足的风险较低。

## 修改要求

- **问题**：PhysiologicalSnapshot 的 `@ElementCollection` 优化溢出目标指定为 `trajectory_projection` 投影表，但该表字段仅包含 GPS 轨迹数据，缺少生理快照所需的心率、血氧、情绪指数等字段，按此描述实现将导致溢出生理数据丢失。
- **原因**：`trajectory_projection`（P3）是为按车辆/驾驶员/时间范围查询 GPS 轨迹点序列而设计的投影表，与 PhysiologicalSnapshot 的核心字段完全不匹配。两表语义不同——轨迹点（位置、速度）与生理快照（心率、血氧、情绪指数）不可互相替代。若溢出快照写入轨迹表，其生理字段将无法存储，读查询也无法从轨迹表中恢复完整的快照数据，构成数据完整性破坏。
- **建议方向**：将溢出目标从 `trajectory_projection` 改为 `trip_physiological_snapshot` 表（即 `@ElementCollection` 的实际底层存储表）。溢出快照以 JDBC batch INSERT 直接写入该表（绕过 JPA 级联路径），与 §3.7.4 中边缘侧 PhysiologicalSnapshot 批量同步至云端的同一张表的方案一致。此修正后，该表同时承载三路数据来源——JPA `@ElementCollection` 级联写入（前 500 条）、溢出优化直接写入（第 501 条起）、边缘侧同步批量写入（断网恢复后）——数据完整性完整覆盖，且后续 Trip 聚合加载时 JPA 从同一张表加载全部快照。
