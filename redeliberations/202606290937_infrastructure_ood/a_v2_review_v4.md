# OOD 设计方案审查报告（v4）

## 审查结果

REJECTED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计方案中所有类型形态选择（JPA `@Entity`、`@Embeddable`、`@Version`、`@ElementCollection`、`@OneToOne` + `@MapsId`、Spring Data JPA `JpaRepository`、Spring `ApplicationEventPublisher` + `@EventListener`、`@Scheduled`、`@ConfigurationProperties`、`@TransactionalEventListener`、Java `ArrayDeque`、`ConcurrentHashMap`、JDBC `JdbcTemplate` 等）均在 Java/Spring Boot 目标语言类型系统能力范围内。泛型使用（`JpaRepository<TripEntity, String>`）和继承/实现关系（仓储实现类实现领域层接口契约）均符合 Java 单继承、多接口实现的约束。设计中的协作关系（如 OutboxPersister 在同一事务中写入 outbox 与聚合根、OutboxRelayer 独立线程轮询投递等）均可通过 Spring 事务管理和调度机制实现。

### 2. 标准库与生态覆盖

**[通过]** 设计中依赖的所有关键库与服务均已有成熟生态：
- Spring Boot / Spring Data JPA / Hibernate：Java 主流生态，完全覆盖
- ShardingSphere-JDBC：Apache 顶级项目，CONSISTENT_HASH 分片算法为内置支持
- HikariCP：Spring Boot 默认连接池，开箱即用
- Eclipse Paho / Spring Integration MQTT：Java MQTT 客户端主流方案
- BouncyCastle (bcprov-jdk18on)：HKDF 实现的正确依赖，已在 §3.6.2 注明
- SQLite JDBC Driver：成熟方案
- 华为云托管服务（IoTDA、DMS Kafka、SMN、SparkRTC、DEW、OBS、DCS Redis、Push Kit、Account Kit）：均为华为云实际产品
- 金仓数据库（PostgreSQL 协议兼容）：实际产品，JPA PostgreSQLDialect 已验证可行

### 3. 语言特性可行性

**[通过]** 错误处理策略与 Java unchecked exception 机制匹配，`PersistenceException` 层次体系、`Result<T, E>` 模式均可行。边缘侧单线程串行模型 + 云端多线程乐观锁并发模型均与 Java 并发能力匹配。资源管理方案（Spring Bean 托管 + EdgeSessionContext POJO 由 SessionLifecycleManager 手动管理）可行。模块结构（`infra.persistence`、`infra.repository`、`infra.eventbus` 等）遵循 DDD 分层，依赖方向合理。

### 4. 设计一致性

**[一般]** Trip 表结构（§3.1.1）缺少 VO-16 DrivingBehaviorCounters 和 VO-17 L3DurationTracker 的嵌入列字段。§3.1.3 策略 A 明确声明 VO-16 以 `hard_braking_count` + `hard_acceleration_count` 嵌入 Trip 表、VO-17 以 `l3_started_at` + `l3_accumulated_seconds` + `l3_active` 嵌入 Trip 表，但 §3.1.1 Trip 表列清单中未包含这 5 个列。同样，Vehicle 表（§3.1.1）缺少 VO-19 OTAUpgradeStatus 的 5 个嵌入列字段（`ota_stage`、`ota_target_version`、`ota_transferred_bytes`、`ota_total_bytes`、`ota_stage_entered_at`），这些列在 §3.1.2 OTA 小节中描述为嵌入 Vehicle 表但未出现在 Vehicle 表定义中。这种表定义与值对象映射策略之间的内部不一致会导致实现阶段的 schema 设计歧义。

**[轻微]** 边缘侧产生的领域事件到达云端异步消费者（如投影同步器订阅 Kafka topic）的实时路径缺少显式的桥接组件设计。§3.7.3 描述了 MQTT → IoTDA → 云端应用服务的数据通路，但未说明云端哪个组件负责将 MQTT 上行数据（如告警事件、行程状态）转换为领域事件并发布至 outbox/Kafka，以便云端异步消费者（投影同步器、通知推送等）能够订阅处理。建议在 `infra.messaging` 或 `infra.cloud` 模块中补充一个"上行消息消费适配器"的职责描述。

**[通过]** 其余协作关系形成闭环：outbox 事务性保证（广播表策略确保 outbox 写入与聚合根变更为同一本地事务）、CQRS 投影同步路径（AlertTriggeredEvent → outbox → Kafka → 投影同步器）、乐观锁冲突处理策略（仓储层检测 → 领域层重试）、EdgeSessionContext 生命周期管理（SessionLifecycleManager 订阅 VehicleIgnitionOnEvent/OffEvent 驱动创建/销毁）均描述清晰。

**[通过]** 模块间依赖方向合理：`infra.repository → infra.persistence`、`infra.eventbus → infra.messaging`、`infra.adapter → infra.cloud/infra.security`，无循环依赖。

### 5. 设计质量

**[通过]** 职责划分遵循单一职责原则——`infra.persistence` 仅负责 JPA 映射与表结构、`infra.repository` 仅负责仓储接口实现、`infra.eventbus` 仅负责事件总线、`infra.adapter` 仅负责端口适配、`infra.cloud` 仅负责云服务集成、`infra.security` 仅负责安全加密、`infra.edge` 仅负责边缘侧基础设施。

**[通过]** 抽象层次恰当——设计在多处做了务实的取舍：边缘侧用 SQLite + JDBC 而非强行统一 JPA、家属常态快照不纳入领域事件体系（独立轻量推送通道）、outbox 广播表策略确保事务原子性而非引入分布式事务。

**[通过]** 设计便于单元测试——仓储接口与实现分离、端口以 interface 定义依赖注入、边缘侧/云端侧两套事件总线实现共享同一接口契约、EdgeSessionContext 为 POJO 可在测试中独立构造。

## 修改要求

### 问题 1

- **问题**：Trip 表（§3.1.1）缺少 VO-16 DrivingBehaviorCounters 的 2 个嵌入列（`hard_braking_count`、`hard_acceleration_count`）和 VO-17 L3DurationTracker 的 3 个嵌入列（`l3_started_at`、`l3_accumulated_seconds`、`l3_active`）；Vehicle 表（§3.1.1）缺少 VO-19 OTAUpgradeStatus 的 5 个嵌入列（`ota_stage`、`ota_target_version`、`ota_transferred_bytes`、`ota_total_bytes`、`ota_stage_entered_at`）
- **原因**：表结构定义与 §3.1.3 值对象映射策略存在内部不一致，影响设计的完整性和实现阶段的可操作性
- **建议方向**：在 Trip 表定义中补入上述 5 个列，在 Vehicle 表定义中补入上述 5 个 OTA 列（这些列已在 §3.1.2 OTA 小节中详细说明，仅需在 §3.1.1 Vehicle 表列清单中列出）
