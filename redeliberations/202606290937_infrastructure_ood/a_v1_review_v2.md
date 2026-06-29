# OOD 设计方案审查报告（v2）

## 审查结果

**APPROVED**

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计方案全程使用的类型形态（Java `interface`、`class`、`enum`、JPA `@Entity`、`@Embeddable`、`@Embedded`、`@ElementCollection`、`@Version`）均在 Java 17+/Spring Boot 3.x 的类型系统能力范围内。领域层声明的仓储接口和端口接口（均为 Java `interface`）由基础设施层以 `class` 实现并通过 Spring IoC 注入，符合 Java 单继承/多接口实现的约束。设计中涉及的泛型使用（`List<T>`、`Consumer<E extends DomainEvent>`、`Result<T, E>`）均为 Java 泛型系统已支持的常规模式。协作关系中描述的类型交互模式（领域服务调用仓储接口、适配器实现端口接口、事件总线回调 handler）在 Java 中可直接映射。

**[轻微]** §5.3 提及适配器层以 `Result<T, E>` 作为返回类型。Java 标准库中没有内建的 `Result<T, E>` 类型，需自定义此类或引入 Vavr 等函数式库。领域层 OOD 已使用此模式，属于整体设计约定的一部分，实现时需确认自定义 `Result` 类型的包归属和异常桥接方式。不阻塞通过。

### 2. 标准库与生态覆盖

**[通过]** 设计中假设的所有关键库能力均在 Java/Spring Boot 生态系统或华为云服务覆盖范围内：
- **持久化**：Spring Data JPA + Hibernate + HikariCP + ShardingSphere-JDBC — 均为成熟开源库
- **事件**：Spring ApplicationEventPublisher + @EventListener — Spring Framework 内置
- **定时任务**：Spring @Scheduled — Spring Framework 内置
- **消息队列**：华为云 DMS Kafka — 兼容 Apache Kafka 协议，有标准 Java 客户端
- **设备接入**：华为云 IoTDA — 标准 MQTT 协议 + REST API
- **推送**：华为云 SMN + Push Kit — 均有 Java SDK
- **音视频**：华为云 SparkRTC — 有服务端 Java SDK
- **加密**：AES-256-GCM — Java Cryptography Architecture (JCA) 原生支持；HKDF — Java 11+ `javax.crypto` 或 BouncyCastle 支持
- **边缘存储**：SQLite — 有 JDBC 驱动，嵌入式部署无额外依赖
- **MQTT 客户端**：Eclipse Paho / Spring Integration MQTT — 均有 Maven 中央仓库支持
- **配置注入**：Spring @ConfigurationProperties — Spring Boot 内置

**[轻微]** 设计中多处使用 JSONB 列类型（PhysiologicalSnapshot 集合、Permission、NotificationPreference 等），JSONB 是 PostgreSQL 原生类型。金仓数据库对 JSONB 的支持以及 JPA 中如何映射 JSONB（需 `@Column(columnDefinition = "jsonb")` + 自定义 `AttributeConverter` 或 Hibernate Types 库）在设计中未明确说明。建议在详细设计阶段确认金仓对 JSONB 的兼容性及 JPA 映射方案。不阻塞通过。

### 3. 语言特性可行性

**[通过]**
- **错误处理**：采用 unchecked exception（`PersistenceException` 子类型、`NotificationException`、`MediaSessionException` 等），与 Java Spring Boot 标准的异常处理模式一致。`Result<T, E>` 模式已在领域层确立，基础设施层延续使用。
- **并发模型**：边缘侧单线程串行处理（无需同步）、云端多线程（乐观锁 `@Version` + Kafka 消费者组分片）均与 Java 并发机制兼容。ConcurrentHashMap 用于家属 WebSocket 会话管理，put/remove 原子操作语义正确。
- **资源管理**：Spring IoC 容器管理组件生命周期；HikariCP 管理数据库连接池；MQTT 客户端、Kafka 生产者/消费者由各自客户端库管理连接。
- **模块结构**：8 个模块（`infra.persistence`、`infra.repository`、`infra.eventbus` 等）对应 Java 包结构，依赖方向明确（仅依赖领域层接口契约，模块间单向），符合 Spring Boot 多模块项目的组织方式。
- **事务机制**：`@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)` 是 Spring 4.2+ 标准特性；`INSERT ... ON CONFLICT DO UPDATE/NOTHING` 是 PostgreSQL 标准语法，金仓兼容。

**[轻微]** §6.2 中 outbox 表投递器以 `WHERE published=FALSE` 条件原子更新防止多实例重复投递——此机制的可靠性取决于数据库的原子 UPDATE 语义，PostgreSQL/金仓的 MVCC 确实保证了 `UPDATE ... WHERE` 的原子性。但设计方案中投递器先 `SELECT ... LIMIT 100` 再逐条 `UPDATE`，存在 TOCTOU 窗口（SELECT 与 UPDATE 之间另一实例可能已投递同一条记录）。建议实现时合并为 `UPDATE ... SET published=TRUE WHERE event_id IN (SELECT event_id FROM ... WHERE published=FALSE ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED) RETURNING *` 的原子获取-标记模式。此为实现优化建议，不阻塞通过。

### 4. 设计一致性

**[通过]**
- **职责清晰**：8 个模块的职责划分明确，与领域层 5 个仓储接口 + 8 个端口接口 + 1 个事件总线契约一一对应。
- **协作闭环**：场景 1-4（聚合根持久化与乐观锁、Outbox 投递全链路、CQRS 投影同步、边缘断网缓冲恢复）均描述了完整的端到端流程，无缺失环节。
- **契约继承**：仓储实现完整覆盖需求中规定的 4 种复杂查询策略、3 张 CQRS 投影表及乐观锁重试策略。事件总线实现完整覆盖需求规定的边缘同步 + 云端 outbox 异步两套方案。8 个端口适配器与领域层端口一一对应。
- **依赖方向**：基础设施层不向上依赖应用层，模块间依赖单向（`infra.repository` → `infra.persistence`，`infra.eventbus` → `infra.messaging`，`infra.adapter` → `infra.cloud`/`infra.security`），无循环依赖。

**[轻微]** §3.5.3 Push Kit 集成中描述"云端应用服务存储 Token 映射（`AccountId → PushToken`）"，该映射存储归属于应用层还是基础设施层未明确界定。若归属应用层，则 `infra.adapter.NotificationPort` 适配器在推送时需反向调用应用层获取 Token，违反 §2.2"禁止向上依赖应用层"的约束。建议明确该映射存储于基础设施层的共享缓存（如 Redis）中，由 `infra.cloud` 模块维护，NotificationPort 直接查询。不阻塞通过。

### 5. 设计质量

**[通过]**
- **单一职责**：各模块职责聚焦明确——persistence 只管映射、repository 只管 JPA 实现、eventbus 只管事件路由等，无交叉。
- **抽象层次适当**：设计停留在架构级，不涉及具体方法体实现。对值对象映射策略提供了清晰的三类选择标准（@Embedded / @ElementCollection / JSONB），对 @ElementCollection 优化给出了具体阈值（500 条）和触发行为。
- **可测试性良好**：所有仓储实现通过领域层仓储接口可 mock；端口适配器通过领域层端口接口可 mock；事件总线通过 EventBus 接口可 mock；边缘侧与云端侧的实现可独立单元测试。
- **边云分离清晰**：边缘侧（Spring ApplicationEventPublisher + SQLite + 本地 ring buffer）与云端侧（Outbox + DMS Kafka + 金仓数据库集群）的技术选型和设计约束分别明确，无混淆。
- **设计决策有据**：8 个设计决策均有明确的理由阐述，引用了具体的技术约束（如 MQTT 消息体大小限制决定 64KB 分片、高频遥测排除出领域事件体系等）。

**[轻微]** §3.3.1 边缘侧事件总线实现同时提及 `@EventListener` 注解（声明式）和 `registerSyncHandler`（编程式）两种注册方式。`ConcurrentHashMap` 用于 handler 注册表，但 §6.1 明确边缘侧为单线程。ConcurrentHashMap 在单线程环境下虽正确但存在微小的内存开销，设计意图是"为未来多线程预留"。不阻塞通过。

## 修改要求

无严重或一般问题，无需修改。
