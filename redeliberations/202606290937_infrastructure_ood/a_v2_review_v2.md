# OOD 设计方案审查报告（v2）

## 审查结果

REJECTED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计中涉及的类型形态（JPA `@Entity`、`@Embeddable`、`@ElementCollection`、`@OneToOne` + `@MapsId`、Spring `interface` 仓储契约、`@Scope("singleton")` Bean、`@ConfigurationProperties`、`@Scheduled` 定时任务、`@EventListener` / `ApplicationEventPublisher` 事件机制）均在 Java Spring Boot 类型系统能力范围内。泛型仓储接口（`JpaRepository<T, String>`）的使用方式为 Spring Data JPA 的惯用模式。所有继承和实现关系符合 Java 单继承+多接口实现的约束。

### 2. 标准库与生态覆盖

**[通过]** 设计中假设的所有关键能力均在目标语言生态的覆盖范围内：Spring Data JPA / Hibernate（ORM 映射与乐观锁 `@Version`）、HikariCP（连接池，Spring Boot 默认）、ShardingSphere-JDBC（一致性哈希分片）、Eclipse Paho / Spring Integration MQTT（MQTT 客户端）、华为云各服务 Java SDK（IoTDA、DMS Kafka、SMN、SparkRTC、DEW、OBS、Push Kit）、Jedis/Lettuce（Redis 客户端）、`javax.crypto`（AES-256-GCM 加密）、SQLite JDBC 驱动（边缘侧持久化）。上述库均为成熟可用组件，设计假设合理。

### 3. 语言特性可行性

**[通过]** 错误处理策略（unchecked `PersistenceException` 子类型 + `Optional<T>` 表达不存在）与 Java 异常体系匹配。边缘侧单线程串行模型、云端多线程 + JPA `@Version` 乐观锁的并发设计在 Java 并发模型内可行。资源管理通过 Spring IoC 容器生命周期 + `try-with-resources` 实现，符合 Java 资源管理模式。模块打包结构（`infra.persistence`、`infra.repository`、`infra.eventbus`、`infra.messaging`、`infra.adapter`、`infra.cloud`、`infra.security`、`infra.edge`）遵循 Spring Boot 多模块项目组织惯例。outbox 事务性事件表 + `@Scheduled` 轮询投递的方案在 Spring 事务管理框架内可行（ShardingSphere-JDBC 场景下需 XA 分布式事务支持——见维度 4 相关讨论）。

### 4. 设计一致性

**[一般]** §3.1.3 策略 C 分类标题与内容不一致。策略 C 标题为"不持久化的值对象（瞬时/派生）"，但表中列出了实际会被持久化的条目——VO-01 RiskLevel（以 VARCHAR 列持久化于 `safety_alert_event` 表）、VO-02 AlertType（同上）、VO-06 SensorStatus（以 JSONB 列嵌入 `vehicle` 表）、VO-08 OTAVersion（以 VARCHAR 列嵌入 `vehicle` 表）。此外 VO-23 RescueAuthorizationToken 描述为"不入库（可持久化于审计表供追溯）"存在自相矛盾——"不入库"与"可持久化于审计表"互斥。此分类错误可能导致后续实现者对哪些值对象需要 JPA 映射策略产生误解——VO-01/02 是枚举，无需 `@Embeddable` 是合理的，但它们确实以列形式持久化，归入"不持久化"类别属于归类错误。

**[轻微]** §3.5.2 声明 outbox 表（固定于 shard-0）与分片聚合根表在同一数据库事务中原子提交，但 ShardingSphere-JDBC 下非分片表与分片表跨物理分片的事务需要 XA 分布式事务支持（如 ShardingSphere 的 XA 事务模块或 Seata 集成）。此方案在金仓/PostgreSQL 环境下技术上可行（通过 PostgreSQL 两阶段提交），但设计未提及对分布式事务类型的依赖，后续详细设计时需明确事务管理器选型。

**[轻微]** VehicleStateBuffer 适配器（§3.4.1）使用的方法契约描述 `getSnapshots(tripId, window)` 与原始需求 §四.1 建议的 `getSnapshotWindow(Instant from, Instant to)` 在命名上存在差异。架构级设计阶段方法签名差异不影响方案可行性，但建议统一以保持接口契约的跟踪一致性。

其他维度一致性检查通过：EdgeSessionContext（§3.4.2a）生命周期定义完整（VehicleIgnitionOnEvent 创建 → VehicleIgnitionOffEvent 销毁 + 崩溃重启恢复），outbox 轮询查询在三处引用（§3.3.2、§3.3.5、§4 场景 2）经 v4 修订已统一，DriverHealthProfile（§3.1.2）`@OneToOne` + `@MapsId` 与 §3.1.4 乐观锁归属一致，guardianship 的并发策略（SELECT FOR UPDATE 串行化）与 §6.3 描述一致，部署架构图（§3.7.2）与 §3.7.3 MQTT 信道拓扑互不矛盾。

### 5. 设计质量

**[通过]** 模块划分遵循单一职责原则（`infra.persistence`、`infra.repository`、`infra.eventbus` 等 8 个模块各司其职）。抽象层次恰当——在架构级设计层面给出了足够的设计决策理由（7 个 decisions），但未过度深入实现细节。仓储接口与实现分离、端口接口与适配器分离的设计使得各组件可独立 mock 测试。断网降级、乐观锁冲突重试、投影同步失败不阻塞主链路等设计体现了容错性考量。

**[轻微]** 边缘侧 JDBC 仓储实现（§3.2.2a）未涉及测试策略——JDBC 直连 SQLite 的实现需要运行中的 SQLite 实例才能测试，不同于云端 JPA 实现可通过 Spring Data JPA 测试切片 mock。建议在后续详细设计时考虑是否使用 SQLite 内存模式或 H2 兼容模式进行单元测试。

## 修改要求（REJECTED 时存在）

- **问题**：§3.1.3 策略 C 分类错误——VO-01 RiskLevel、VO-02 AlertType、VO-06 SensorStatus、VO-08 OTAVersion 实际会被持久化，但被归类于"不持久化的值对象（瞬时/派生）"策略下。VO-23 RescueAuthorizationToken 的"不入库"与"可持久化于审计表"自相矛盾。

- **原因**：分类标题"不持久化"与表中持久化描述矛盾，属于设计文档的结构性错误。后续实现者依据此分类可能错误判断某些值对象不需要 JPA 映射，导致持久化层实现遗漏或多余。

- **建议方向**：
  1. 将策略 C 拆分为两个子类别：(a) 单列映射无需 @Embeddable 的值对象/枚举（VO-01、VO-02、VO-06、VO-08——它们以单列 VARCHAR/JSONB 持久化，不构成独立的 @Embeddable 类，但仍在数据库中有对应列）；(b) 真正不持久化的瞬时/派生值对象（VO-09~VO-21）。或调整策略 C 标题为"单列映射 / 不持久化的值对象"，并在表格中明确标注每项的持久化状态（持久化列名 / 不入库）。
  2. 明确 VO-23 RescueAuthorizationToken 的持久化策略——选择"不入库"或"持久化于审计表"，二选一，消除矛盾描述。
