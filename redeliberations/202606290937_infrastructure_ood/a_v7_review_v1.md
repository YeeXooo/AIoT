# OOD 设计方案审查报告（v1）

## 审查结果

APPROVED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计中使用的类型形态选择均在 Java/Spring Boot 类型系统能力范围内：JPA `@Entity` / `@Embeddable` / `@ElementCollection` / `@Version` 为标准 JPA 机制；`@Repository`、`@Component`、`@Scheduled`、`@EventListener` 为 Spring 标准注解；`ArrayDeque` 实现 ring buffer、`ConcurrentHashMap` 管理共享状态均为 JDK 标准集合；guardianship 复合主键 `(driver_id, account_id, granted_at)` + 部分唯一索引是 PostgreSQL 扩展语法，设计已标注金仓兼容性待验证并给出替代方案。继承/实现关系均在 Java 单继承多接口约束内。泛型使用场景仅涉及标准集合和仓储接口的泛型参数，无超出 Java 泛型系统能力的使用方式。

### 2. 标准库与生态覆盖

**[通过]** 设计依赖的技术栈均在 Java 生态有效覆盖范围内：Spring Boot + Spring Data JPA + Hibernate 提供持久化基础设施；HikariCP 为连接池标准选型；ShardingSphere-JDBC 提供分片路由；Eclipse Paho / Spring Integration MQTT 提供 MQTT 客户端；BouncyCastle 补充 HKDF 实现（已标注为外部依赖）；华为云服务（IoTDA、DMS Kafka、SMN、SparkRTC、DEW、OBS、DCS Redis、Push Kit）均有 Java SDK 或 REST API 可集成。设计中未假设不存在或不合理的库能力。

**[轻微]** §3.6.2 HKDF 依赖标注为"需引入 BouncyCastle"或"手动实现 HKDF-Extract/Expand 步骤"。建议明确选择其一作为推荐方案，避免实现阶段的选型歧义。

### 3. 语言特性可行性

**[通过]** 错误处理策略与 Java 异常体系匹配——使用 unchecked exception（`PersistenceException` 及其子类型、`MediaSessionException` 等），与 Spring 事务回滚机制兼容；`Optional<T>` 表达"不存在"语义符合 Java 惯例。并发设计合理——边缘侧单线程串行模型消除锁竞争，云端侧乐观锁（JPA `@Version`）+ Redis 分布式锁 + `SELECT ... FOR UPDATE SKIP LOCKED` 的 outbox 排他投递，均在 Java 并发能力范围内。资源管理遵循 Spring 容器生命周期和 HikariCP 连接池管理。模块划分（`infra.persistence` 等 8 个模块）符合 Java 包组织惯例，依赖方向单向无环。

**[轻微]** §3.5.1 IoTDA 上行消费适配器将消息写入 outbox 广播表后手动提交 Kafka 偏移量。当 outbox 为广播表时，`IotdaUplinkConsumer` 的事务写入路由到哪个分片的 outbox 副本未说明。建议补充说明消费者的事务写入目标分片。

### 4. 设计一致性

**[通过]** 各抽象职责描述清晰：五份仓储接口的 JPA/JDBC 实现策略均有明确对应（§3.2.1、§3.2.2a）；八个端口适配器逐一实现（§3.4.1—§3.4.8）；事件总线边缘/云端双实现（§3.3.1）与领域层契约对齐。协作关系形成闭环——IoTDA 上行消息消费适配器（§3.5.1）桥接边缘事件到云端 outbox/Kafka，投影同步器消费 Kafka 写入投影表（§3.2.3），outbox 投递器扫描广播表投递（§3.3.5），全链路无缺失环节。行为契约（§四）覆盖持久化、outbox 投递、投影同步、断网恢复四个核心场景，步骤链完整。模块间依赖方向：`infra.repository` → `infra.persistence`，`infra.adapter` → `infra.cloud`/`infra.security`，`infra.eventbus` → `infra.messaging`，均单向无循环。

**[通过]** §3.1.3 策略 B 中 `trip_physiological_snapshot` 表的三路数据来源（JPA 级联、溢出 JDBC batch、边缘同步）写入路径和字段覆盖范围已完整定义。§3.2.3 P2 看板投影即时增量聚合的数据源统一为 `alert_projection` 表（同在 shard-0），消除了此前版本跨分片 JOIN 的不一致。§3.1.6 DDL 中外键约束的文档级逻辑声明与物理层移除的区分已说明。

### 5. 设计质量

**[通过]** 职责划分遵循单一职责原则——`infra.persistence` 专注 JPA 映射，`infra.repository` 专注仓储实现，`infra.adapter` 专注端口适配，`infra.cloud` 专注云服务集成，`infra.security` 专注加密与认证，`infra.edge` 专注边缘侧专用基础设施。抽象层次恰当——定义了数据结构、组件职责、接口契约、行为场景，未过度设计具体实现代码。设计便于后续实现——接口与实现分离、依赖注入、模块边界清晰。便于测试——仓储接口可 mock、端口适配器可替换、事件总线可注入测试实现。

**[通过]** 设计中多处标注了技术风险项并要求编码阶段验证（ShardingSphere-JDBC 广播表事务原子性、金仓部分唯一索引兼容性、Hibernate StatelessSession 行为等），并均提供了替代方案。这体现了负责任的设计态度，不将未经验证的假设当作既定事实。

## 修改要求

无。本轮审查未发现严重或一般问题，设计通过所有五个维度的审查。
