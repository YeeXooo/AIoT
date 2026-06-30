# OOD 设计方案审查报告（v1）

## 审查结果

REJECTED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计方案基于 Java/JPA/Spring Boot 技术栈，所有类型形态选择均在 Java 类型系统能力范围内。Spring Data JPA 的 `JpaRepository` 继承、`@Embeddable` + `@Embedded` 值对象映射、`@ElementCollection` 集合映射、`@Version` 乐观锁、`@EventListener` 事件监听等均为 JPA/Spring 标准机制，与 Java 单继承 + 多接口实现体系完全匹配。泛型使用场景（`Consumer<DomainEvent>`、`Result<T, E>`）在 Java 泛型系统能力范围内。`@TransactionalEventListener(phase = BEFORE_COMMIT)` 是 Spring 4.2+ 的标准特性。

### 2. 标准库与生态覆盖

**[通过]** 设计中假设的库能力均真实可用：Spring Data JPA + Hibernate（持久化）、HikariCP（连接池）、ShardingSphere-JDBC（分片）、Eclipse Paho / Spring Integration MQTT（MQTT 客户端）、华为云 DMS Kafka / IoTDA / SMN / SparkRTC / DEW / Push Kit / OBS / DCS Redis 均为华为云正式商业服务，Java SDK 可用。AES-256-GCM 加密由标准库 `javax.crypto` 完整支持。SQLite 可通过 JDBC 驱动在 Java 中使用。`ArrayDeque`、`ConcurrentHashMap` 均为 Java 标准库。

**[轻微]** §7 决策 2 中提到"金仓是否完全支持并发刷新（物化视图）需确认"——设计已主动标注不确定性，且最终选型已排除物化视图方案改用应用层事件驱动异步写入，故此不确定性不影响通过。

### 3. 语言特性可行性

**[通过]** 错误处理策略与 Java unchecked exception + `Optional<T>` 模式一致。边缘侧单线程串行模型与 Spring Boot 进程内 `ApplicationEventPublisher` 同步回调匹配，≤500ms 时延约束在进程内可满足。云端侧乐观锁（`@Version` + `OptimisticLockException`）是 JPA 标准并发控制机制。outbox 投递器通过 Spring `@Scheduled` 定时任务运行，错误不向上传播、内部状态机管理的设计合理。模块/包结构（8 个模块单向依赖）符合 Spring Boot 项目组织惯例。MQTT 连接（Eclipse Paho + Spring Integration MQTT + TLS）是成熟的 Java MQTT 客户端方案。

### 4. 设计一致性

**[一般]** Outbox 表放置位置与事务性保证矛盾：§3.5.2 称 `domain_event_outbox` 和 `domain_event_dlq` 表"采用独立的事件数据库实例或不分片"。若选择"独立的事件数据库实例"，outbox 表与聚合根表不在同一数据库事务中，则 §3.3.1 和 §4 场景 2 所描述的"outbox 表与聚合根状态更新在同一数据库事务中提交"这一核心保证无法成立。两处论述矛盾，设计方案需明确选择"不分片、同库"方案，并移除"独立数据库实例"的备选表述。

**[轻微]** Guardianship（监护关系）关联表未设计乐观锁版本号字段（§3.1.4 明确排除该表使用乐观锁），采用复合主键 + 值对象替换模式。但 §6.3 的并发策略表中将该场景归于 SystemAccount 聚合乐观锁保护范围，而 guardianship 独立于 SystemAccount 表，不受该乐观锁保护。在实际并发较低（少量管理员）的场景下 last-write-wins 可接受，但撤销优先于授予的安全语义依赖完全串行化，建议在设计中明确说明 guardianship 的并发安全性如何保证。

**[轻微]** Vehicle 表（§3.1.1）的显式列清单中缺少 `sensor_status` JSONB 列。该列在表格下方的说明文字中被提及（"各传感器通道的健康状态以 JSONB 列存储于 Vehicle 表中"），但未出现在上方的列结构清单中。属于文档遗漏，不阻塞设计。

**[轻微]** PhysiologicalSnapshot 集合映射采用 `@ElementCollection` 级联加载策略（§3.1.3 策略 B），设计自身在 §7 决策 1 中已承认"若单次行程的 PhysiologicalSnapshot 数量高达数千，@ElementCollection 的级联加载会成为瓶颈"。设计标注了替代方案（通过 trajectory_projection 投影表查询），但未明确触发优化方案的条件阈值或迁移路径。建议给出明确的优化触发条件。

**[通过]** 其余模块间依赖关系清晰、单向无循环。CQRS 读写分离（SafetyAlertEvent 写经 Trip 聚合根、读经投影表）一致。事件→消费方映射表（§3.3.6）覆盖全面且闭合。MQTT Topic 定义（§3.5.1 和需求 §5.1）与信道拓扑（§3.7.2）一致。值对象映射策略（策略 A/B/C）分类清晰，覆盖全部 22 个值对象。

### 5. 设计质量

**[通过]** 八个模块职责划分清晰遵循单一职责原则，依赖方向合理禁止向上依赖应用层。核心设计决策（§7）均有充分理由说明。边缘/云端双套实现共享同一接口契约（EventBus、Repository 接口）的设计便于测试——可通过依赖注入替换实现。乐观锁冲突处理将重试策略上移至领域服务、仓储层保持纯粹（仅检测冲突并抛异常）的设计提升了仓储层的可测试性和可替换性。outbox 投递器作为独立后台线程隔离于业务请求处理的设计便于独立运维监控。CQRS 投影采用应用层事件驱动异步写入（而非数据库触发器）降低了数据库引擎耦合，便于监控和测试。

**[轻微]** 决策 1 中 `@ElementCollection` 的性能风险评估已标注但未给出优化触发阈值。建议在架构级设计中增加"快照数量超过 N 条时切换至投影表方案"的明确决策条件。

## 修改要求

**问题**：§3.5.2 中 outbox 表/DLQ 表的放置方案提供"独立的事件数据库实例或不分片"两个选项，其中"独立数据库实例"选项与 §3.3.1 及 §4 场景 2 中"outbox 写入与聚合根保存同在数据库事务中提交"的核心设计前提相矛盾。

**原因**：Outbox 模式的核心价值在于保证"聚合根状态变更"与"事件已发布"的原子性——若 outbox 表与聚合根表不在同一数据库实例中，单数据库事务无法同时覆盖两者，必须引入分布式事务（XA 或 SAGA），而设计中未对此做任何说明。当前设计文本同时呈现两个互相矛盾的选项而未做出选择，给后续实现层造成歧义。

**建议方向**：删除"独立的事件数据库实例"备选方案，明确 outbox 表和 DLQ 表与业务聚合根表位于同一数据库实例中、仅不参与 VehicleId 分片路由（即"不分片"方案）。该方案下 ShardingSphere-JDBC 可在同一 datasource 中对分片表和非分片表进行同一事务操作，outbox 模式的事务性保证完整成立。在设计的部署/配置小结中补充说明 outbox 表的具体物理位置（如固定于 shard-0 或作为广播表存在）。
