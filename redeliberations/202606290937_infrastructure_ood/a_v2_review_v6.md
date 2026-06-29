# OOD 设计方案审查报告（v6）

## 审查结果

APPROVED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计方案中的类型形态选择（JPA `@Entity`、`@Embeddable`、`@ElementCollection`、`@OneToOne` + `@MapsId`、`@Version`）均为 Java/Spring Boot/JPA 标准能力，与目标语言类型系统完全匹配。聚合根表（`@Entity`）、值对象嵌入（`@Embeddable` + `@Embedded`）、集合映射（`@ElementCollection` + `@CollectionTable`）、1:1 共享主键（`@MapsId`）均在 JPA 规范范围内。Spring Data JPA 的 `JpaRepository<T, ID>` 泛型继承是标准模式。Spring `ApplicationEventPublisher` + `@EventListener`、`@Scheduled`、`ConcurrentHashMap` 等均为 JDK/Spring 原生能力。ArrayDeque 用于 ring buffer 实现可行。

**[轻微]** §3.1.2 中 OTA 升级状态小节的标题为"关联表：OTA 升级状态（`ota_upgrade_status`）"，但正文明确说明其为 Vehicle 表的嵌入字段组（非独立表）。标题与内容不一致，建议将标题改为"嵌入字段组：OTA 升级状态"或直接移除"关联表"措辞。

**[轻微]** §3.1.4 称 DriverHealthProfile "嵌入 Driver 聚合"，但实际以独立表 `driver_health_profile` 存储（`@OneToOne` + `@MapsId` 共享 Driver 主键）。"嵌入"一词在关系型数据库语境下可能被误解为同表存储，建议改为"从属于 Driver 聚合"或"通过共享主键关联"以准确描述其独立表存储方式。

### 2. 标准库与生态覆盖

**[通过]** 所依赖的库和框架均在 Java/Spring Boot 生态覆盖范围内：Spring Data JPA、HikariCP、Jackson、Eclipse Paho / Spring Integration MQTT、BouncyCastle、ShardingSphere-JDBC、SQLite JDBC。华为云服务（IoTDA、DMS Kafka、SMN、SparkRTC、DEW、Push Kit、OBS、DCS Redis）均为托管服务，集成方式（REST API、SDK、JDBC 驱动）在架构级设计层面合理。Kingbase JDBC 驱动 (`com.kingbase8.Driver`) 和 PostgreSQLDialect 的方案假设备注了兼容性前提。BouncyCastle 的 HKDF 依赖已显式注明。

**[轻微]** 设计方案依赖金仓数据库对 PostgreSQL JSONB 类型的兼容性。决策 2 中已注明物化视图 `REFRESH MATERIALIZED VIEW CONCURRENTLY` 的兼容性需确认。由于项目明确要求使用金仓且设计已标注该风险点，不构成阻塞。

### 3. 语言特性可行性

**[通过]** 错误处理策略与 Java 异常体系匹配——`Optional<T>` 表达"不存在"的正常业务情形，unchecked exception（`PersistenceException` 及其子类）表达持久化故障，适配器层使用 `Result<T, E>` 或 unchecked exception。并发设计合理：边缘侧单线程串行（无锁竞争），云端侧以乐观锁（`@Version`）+ ShardingSphere-JDBC + Kafka 分区消费应对多线程并发。资源管理方案可行：EdgeSessionContext 通过 `SessionLifecycleManager` 管理显式生命周期，ring buffer 为内存内数据结构，JDBC 连接通过 HikariCP 池化。模块/包结构（`infra.persistence`、`infra.repository`、`infra.eventbus` 等）符合 Spring Boot 项目组织惯例，依赖方向单向无循环。

**[轻微]** §3.3.1 边缘 EventBus 同时支持声明式注册（`@EventListener`）和编程式注册（`ConcurrentHashMap` handler 注册表）。两种注册机制并存可能导致同一消费方通过两种路径被重复注册。建议明确声明式注册为首选方式，编程式注册仅用于动态注册场景，并补充去重逻辑说明。

### 4. 设计一致性

**[通过]** 各抽象职责描述清晰无歧义。协作关系形成闭环：聚合根持久化 → outbox 事务写入 → DMS Kafka 异步投递 → 消费者幂等处理 → CQRS 投影写入 的完整链路在 §3.3、§3.3.5、§4 场景 2 和 §7 决策 3 中一致描述。模块间依赖方向合理（基础设施层仅依赖领域层接口契约，不向上依赖应用层），无循环依赖。行为契约（§4 四个场景）覆盖了核心链路：乐观锁冲突处理、outbox 全链路投递、CQRS 投影同步、断网缓冲恢复，完整到足以指导后续实现。§3.1.1 表结构定义与 §3.1.3 值对象映射策略之间已通过对齐修正（v6 修订补入了 VO-16/17/19 的全部嵌入列）实现一致。

**[轻微]** ShardingSphere-JDBC 广播表模式下 outbox 表的事务原子性保证（§3.5.2 称"任意聚合根的 outbox 写入均与同 shard 业务变更在同一本地事务中原子提交"）依赖于 ShardingSphere-JDBC 的事务管理器配置。若使用非 XA 的本地事务，广播表跨 shard 写入的原子性取决于 ShardingSphere-JDBC 的实现保证。建议在实现阶段明确要求使用 ShardingSphere-JDBC 的 XA 事务或验证其本地事务模式对广播表的原子性支持。

### 5. 设计质量

**[通过]** 职责划分遵循单一职责原则：`infra.persistence` 负责 JPA 映射、`infra.repository` 负责仓储实现、`infra.eventbus` 负责事件总线、`infra.adapter` 负责端口适配、`infra.cloud` 负责云服务集成、`infra.security` 负责安全加密、`infra.edge` 负责边缘侧专用基础设施。抽象层次恰当——架构级设计明确了模块边界、核心抽象、协作契约和关键决策，同时为详细设计留有空间。设计便于后续实现：每个模块仅实现领域层声明的接口契约，边缘/云端两套实现共享同一接口，切换方便。乐观锁冲突处理中，仓储层不封装业务重试逻辑的设计保持了仓储的可测试性。

**[轻微]** 设计方案未显式讨论基础设施层组件的单元测试策略。边缘侧 ring buffer、断网缓冲模块、SessionLifecycleManager 等组件具有独立的状态和行为逻辑，建议补充测试隔离策略说明（如 ring buffer 可通过接口抽象实现可注入 mock）。

## 修改要求

无。本方案无严重或一般问题，审查结果为 APPROVED。
