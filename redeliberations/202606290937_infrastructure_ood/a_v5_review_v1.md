# OOD 设计方案审查报告（v5）

## 审查结果

**APPROVED**

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计中的类型形态选择与 Java/Spring Boot 生态完全匹配：JPA `@Entity`/`@Embeddable`/`@ElementCollection`/`@Version`/`@OneToOne`/`@MapsId` 用于持久化映射；Spring `@Repository`/`@Component`/`@Scheduled`/`@EventListener`/`@TransactionalEventListener`/`@ConfigurationProperties` 用于组件管理和依赖注入；`ConcurrentHashMap`/`ArrayDeque` 用于内存状态管理。应用层生成 UUID 作为主键策略在 Java `java.util.UUID` 标准库中直接可行。泛型使用限于 Spring Data `JpaRepository<T, ID>` 标准模式，在 Java 类型擦除约束范围内。

**[通过]** 继承/实现关系均在 Java 单继承+多接口实现约束内：所有仓储实现类实现领域层仓储接口、适配器实现领域层端口接口、事件总线实现 EventBus 契约接口。

**[通过]** ShardingSphere-JDBC 嵌入模式以 jar 依赖形式集成，ConsistentHash 分片算法、广播表策略、绑定表机制均为 ShardingSphere-JDBC 5.x 标准能力，在 Java 类型系统中可行。广播表本地事务原子性已标注为待验证项（见 §3.5.2），此标注本身是审慎的设计实践，不构成类型系统问题。

**[通过]** `@ElementCollection` 集合冻结方案（不可变包装 + Hibernate `StatelessSession`）利用了 Hibernate 的一级缓存绕过和脏检查跳过机制，在 Hibernate 类型系统内技术可行。设计已将此标注为技术风险并要求编码阶段集成测试验证，处理恰当。

### 2. 标准库与生态覆盖

**[通过]** Spring Boot 生态全面覆盖所需能力：Spring Data JPA（持久化）、Spring Integration MQTT（IoTDA 连接）、Spring ApplicationEventPublisher（边缘侧同步事件）、`@Scheduled`（outbox 轮询/心跳监控/缓冲清理）、`@ConfigurationProperties`（阈值配置）。

**[通过]** 华为云服务 SDK 假设合理：IoTDA REST API、DMS Kafka SDK、SMN SDK、Push Kit REST API、SparkRTC 服务端 SDK、DEW SDK、OBS SDK 均为华为云标准 Java SDK 提供的服务。

**[通过]** 第三方依赖选择合理：Eclipse Paho（MQTT 客户端）、HikariCP（连接池，Spring Boot 默认）、ShardingSphere-JDBC 5.x（分片）、BouncyCastle（HKDF 的 `bcprov-jdk18on`，已标注依赖）。Jackson JSON 序列化为 Spring Boot 内置。

**[通过]** 金仓数据库以 PostgreSQL 兼容协议工作，`PostgreSQLDialect` 在 Hibernate 中为内置方言。`FOR UPDATE SKIP LOCKED`、`INSERT ... ON CONFLICT DO UPDATE/NOTHING` 等 SQL 特性在 PostgreSQL 9.5+ 中为标准能力，金仓作为兼容数据库理应支持，设计中已对部分唯一索引等扩展语法做了金仓兼容性标注。

**[轻微]** 设计中多处使用 `FOR UPDATE SKIP LOCKED`（outbox 轮询 §4 场景 2 步骤 3、guardianship 备选方案 §3.1.2），此语法在 PostgreSQL 9.5+ 为标准特性，但未像部分唯一索引那样单独标注金仓兼容性验证要求。建议在 guardianship 的兼容性验证条目中一并覆盖。

### 3. 语言特性可行性

**[通过]** 错误处理策略与 Java unchecked exception 机制匹配：`PersistenceException`（含子类型 `OptimisticLockConflict`、`ConnectionFailedException`）继承 `RuntimeException`，与 JPA `OptimisticLockException`（unchecked）对接。仓储层通过 `Optional<T>` 表达"不存在"语义，符合 Java 8+ 惯用模式。适配器层使用 `Result<T, E>` 或 unchecked exception，与领域端口契约一致。

**[通过]** 并发设计在 Java 并发模型中可行：边缘侧单线程串行执行无锁化（判定链路在进程内同步完成）；云端侧 JPA `@Version` 乐观锁 + `FOR UPDATE SKIP LOCKED` 行锁 + `ConcurrentHashMap` + Redis 缓存组合。Spring `@Scheduled` 定时任务默认单线程执行（可配置线程池），outbox 投递轮询的串行行为符合设计预期。

**[通过]** 资源管理方案可行：`EdgeSessionContext` 通过工厂方法创建、`SessionLifecycleManager` 管理生命周期、安全观察窗口（5s）延迟销毁、`destroy()` 执行缓冲序列化+资源释放，均为标准 Java 对象生命周期管理模式。HikariCP 连接池由 Spring Boot 自动管理。

**[通过]** 模块/包结构（`infra.persistence`、`infra.repository`、`infra.eventbus`、`infra.messaging`、`infra.adapter`、`infra.cloud`、`infra.security`、`infra.edge`）符合 Spring Boot 包组织惯例，模块间依赖方向单向且仅限领域层接口契约，Spring DI 在运行时完成具体实现注入。

**[轻微]** 边缘侧 JDBC 层乐观锁 UPDATE 语句（`WHERE id = ? AND version = ?`，受影响行数 0 则抛异常）需要依赖 JDBC `executeUpdate()` 返回值的原子性语义。JDBC 规范保证此返回值反映实际更新行数，但 SQLite 在特定配置下的行为（如 WAL 模式下并发写）需编码阶段验证。此风险可类比于设计中已有的"金仓兼容性验证"标注方式。

### 4. 设计一致性

**[通过]** 各抽象的职责描述清晰无歧义：八个模块的职责边界在 §2.1 中有明确划分，各核心抽象（§3.1-§3.7）的职责描述具体到组件和方法级别。

**[通过]** 协作关系形成闭环：(a) IoTDA 上行消息 → DMS Kafka → `IotdaUplinkConsumer` → EventBus → outbox → DMS Kafka → 投影同步器/通知推送 → 投影表/SMN/Push Kit，全链路闭合；(b) 边缘侧判定 → Spring Event → 消费方（InterventionService 等）→ HMI 渲染，进程内同步闭环；(c) 边缘断网 → 缓冲 → 批量重传 → 云端幂等去重 → 同步确认 → 本地清理，端到端闭环。

**[通过]** 行为契约（§4 场景 1-4）覆盖了核心数据流：聚合根持久化与乐观锁冲突处理、outbox 全链路投递、CQRS 投影同步、边缘断网缓冲恢复，每个场景步骤完整且与正文描述一致。

**[通过]** 模块间依赖方向合理：所有模块仅依赖领域层接口契约，无循环依赖。`infra.repository` → `infra.persistence`、`infra.eventbus` → `infra.messaging`、`infra.adapter` → `infra.cloud`/`infra.security` 均为单向。

**[轻微]** 文档标题标注为"v5"而文档内容实际已纳入 v5-v10 共六轮修订的成果。修订说明共 11 处标题（v2-v10），其中 v5 标题出现两次（第 1385 行和第 1391 行）。标题版本号与实际内容成熟度不一致，建议将标题更新为最终版本号。两次重复的"修订说明（v5）"标题应合并。

**[轻微]** `infra.cloud` 模块的依赖列在 §2.1 中标注为"无（纯基础设施）"，但该模块内的 `IotdaUplinkConsumer` 需调用领域层 EventBus 接口发布事件。此依赖合法（所有模块均可依赖领域层接口），但表格标注可能使读者误以为该模块对领域层无任何依赖。建议将表格标注改为"领域层 EventBus 接口"或统一定义为"领域层契约（通用）"。

### 5. 设计质量

**[通过]** 单一职责原则贯彻良好：`infra.persistence` 仅负责 JPA 映射和表结构管理，`infra.repository` 仅负责仓储接口实现，`infra.eventbus` 仅负责事件总线，`infra.adapter` 仅负责端口适配，`infra.cloud`/`infra.security`/`infra.edge` 各有明确职责边界。

**[通过]** 抽象层次恰当：设计保持了架构级设计的粒度，未陷入方法签名、字段注解等实现细节。同时关键的技术决策点（如 `@ElementCollection` 冻结策略、outbox 广播表事务原子性、金仓兼容性）均给出了具体方案而非仅停留于意图层面。

**[通过]** 设计便于后续详细设计和实现：(a) 所有值对象的映射策略按三类明确划分，每类有清晰的适用标准（§7 决策 1）；(b) CQRS 投影表有完整的字段定义、索引设计和同步机制描述；(c) 关键 SQL 片段（轮询查询、乐观锁条件、冲突处理 `ON CONFLICT` 等）给出了具体语法，可直接指导编码。

**[通过]** 设计便于单元测试：仓储接口和端口接口均为 Java interface，便于 mock；`EdgeSessionContext` 为 POJO 可独立测试；`SecondaryAuthProvider` 以接口抽象，便于两种方案切换测试；`OfflineBufferingDecorator` 以 Decorator 模式包装 `MessageHandler`，可独立于真实 MQTT 连接测试。

**[轻微]** `IotdaUplinkConsumer` 的职责范围较宽——同时负责事件反序列化、EventBus 发布、轨迹数据批量写入路由。未来若消费逻辑进一步复杂化，可考虑将"事件反序列化→EventBus 发布"与"轨迹数据→TrajectorySyncAdapter"拆分为两个独立消费者，提升单个消费者的可测试性。当前规模下合并可接受。

**[轻微]** 设计中多次出现的"技术风险标注"和"待验证项"（如 §3.1.3 @ElementCollection 冻结、§3.5.2 广播表事务原子性）应在后续生成编码任务时显式转化为集成测试用例清单，避免在实现阶段被遗漏。此为流程衔接建议，不构成设计缺陷。

## 修改要求

无严重或一般问题，无需修改要求。
