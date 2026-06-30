# OOD 设计方案审查报告（v3）

## 审查结果

REJECTED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计中的类型形态选择（JPA `@Entity`、`@Embeddable`、`@ElementCollection`、`@OneToOne` + `@MapsId`、`@Version`、Spring `@EventListener`、`@Scope`、`JpaRepository<T, String>` 等）均与 Java/Spring Boot 类型系统能力匹配。继承和实现关系（单继承 + 多接口实现）在 Java 约束范围内。泛型使用（如 `JpaRepository<T, String>`、`ConcurrentHashMap<K,V>`）均为 Java 泛型系统标准用法。

**[一般]** §3.1.3 中 VO-17 L3DurationTracker 被归入策略 B（`@ElementCollection` + `@CollectionTable`）表格，但其映射描述为"嵌入列字段（`l3_started_at` + `l3_accumulated_seconds` + `l3_active` BOOLEAN）"，且 Trip:Driver=1:1，至多一个活跃 Tracker——这是典型的策略 A（`@Embedded`）特征，而非集合持有场景。该归类错误会误导实现者为其选用错误的 JPA 映射策略。

### 2. 标准库与生态覆盖

**[通过]** 设计中依赖的所有技术组件在 Java 生态中均有成熟支持：ShardingSphere-JDBC 一致性哈希分片算法（`CONSISTENT_HASH`）、HikariCP 连接池、Spring Data JPA、DMS Kafka 客户端、华为云 IoTDA/DEW/SparkRTC/SMN SDK、javax.crypto（AES-256-GCM）。金仓 JDBC 驱动支持 PostgreSQLDialect。

**[轻微]** §3.6.2 语音存证加密方案中使用 HKDF-Expand(DeviceKey, recordId) 派生会话密钥。HKDF 不在 JDK 标准 `javax.crypto` 包中，需引入 BouncyCastle 等第三方库或手动实现。这不影响可行性（BouncyCastle 是 Java 生态中的标准密码库），但设计中未注明依赖。

### 3. 语言特性可行性

**[通过]** 错误处理策略（unchecked exception + `Optional<T>` + 领域层 `Result<T, E>`）与 Java 异常机制匹配。并发设计（边缘侧单线程串行、云端乐观锁 `@Version` + Kafka 分区）与 Java 并发模型兼容。资源管理（Spring Bean 生命周期、EdgeSessionContext 会话容器）在 Spring Boot 中可行。模块/包结构设计符合 Spring Boot 项目组织方式。

**[轻微]** §3.4.2a 对 EdgeSessionContext 的 Spring 集成方式描述存在内部不一致：开头定义为"Spring Boot 应用中的**会话作用域 Bean**"，后续推荐"进程级单例 + 手动生命周期管理"，并列出了 `EdgeSessionContext.create()` 工厂方法和 `destroy()` 方法。Spring 的 singleton scope 不支持运行时销毁再重建实例，手动生命周期管理的对象实际应为非 Spring 管理的 POJO（由 SessionLifecycleManager 持有引用）。实现上可行，但文档描述自相矛盾。

### 4. 设计一致性

**[通过]** 五份仓储接口的云端和边缘侧实现策略均已覆盖（§3.2.1、§3.2.2a）。八个领域端口的适配器设计均已给出（§3.4.1–§3.4.8）。事件总线两端实现（边缘同步 + 云端 outbox）完整。事件消费者注册表（§3.3.6）覆盖所有已声明领域事件。模块间依赖方向均为单向（领域层 ← 基础设施层）。关键行为契约（§4 四个场景）形成闭环，覆盖持久化→事件投递→投影同步→断网恢复全链路。

**[一般]** 边缘侧与云端侧 PhysiologicalSnapshot 同步机制缺失：§3.2.2a 描述边缘侧以 JDBC batch INSERT 写入 SQLite 本地 `trip_physiological_snapshot` 表，§3.1.3 描述云端侧以 JPA `@ElementCollection` 映射至金仓同名表。但 §3.7.4 断网恢复和网络恢复后的数据批量上报流程中，仅提及"Trip 状态变更和 SafetyAlertEvent 创建"的上报，未说明边缘侧 SQLite 中积累的 PhysiologicalSnapshot 集合如何同步至云端 JPA 管理的 Trip 聚合——两端持久化策略不同（JDBC 直写表 vs JPA 级联），数据同步需额外适配逻辑，设计中未给出方案。

**[一般]** §3.5.2 中 outbox 表"固定于 shard-0 所在的物理数据库"，与业务聚合根表（可能位于 shard-1/2/3）不在同一物理数据源。后续声称"ShardingSphere-JDBC 对**同一 datasource** 中的分片表和非分片表……提供完整的事务性保证"——但此处的 outbox 表与业务表不在同一 datasource，该保证不覆盖跨 shard 的 outbox 写入场景。outbox 模式的核心前提（"聚合根状态变更与事件持久化原子提交"）在跨物理数据库时需要分布式事务支持（ShardingSphere-JDBC 的 XA/Seata），设计中未说明此项依赖。

**[轻微]** §3.3.5 退避策略公式 `min(2^retryCount × 1s, 60s)` 与示例"第 1 次重试延迟 1s"存在数值偏差：若 retryCount 从 0 起始，首次失败后 retryCount 递增为 1，此时退避间隔为 2^1 × 1s = 2s，与示例中所述的 1s 不符。此为描述级不一致，实现时可按实际需求调整公式或修正示例。

### 5. 设计质量

**[通过]** 职责划分遵循单一职责原则——仓储层仅负责持久化与冲突检测，重试策略由领域服务自行决定（§3.2.4）；适配器层封装外部系统集成，对外暴露与领域端口契约一致的接口。抽象层次恰当——为架构级设计，不包含具体字段定义和方法签名。设计便于单元测试——仓储接口可 mock、端口接口可 mock、事件总线可注入测试实现、边缘侧 JDBC 实现可独立测试。

**[轻微]** §3.5.5 DEW 密钥轮转策略规定旧版本密钥在 90 天后自动删除。但路怒语音存证的加密文件可能需要长期保留（如交通事故定责、司法协查场景下远超 90 天）。若文件以旧版本密钥加密且该密钥已被删除，文件将无法解密。密钥删除策略应与数据保留策略对齐，而非采用固定 90 天期限。

## 修改要求（REJECTED 时存在）

### 问题 1：VO-17 L3DurationTracker 映射策略归类错误

- **问题**：§3.1.3 策略 B 表格中将 VO-17 L3DurationTracker 归类为 `@ElementCollection` + `@CollectionTable`，但其实际映射描述为嵌入列字段，且 Trip:Driver=1:1 基数下仅存在一个活跃 Tracker，不具备集合语义。
- **原因**：归类错误会误导实现者选用错误的 JPA 映射策略（@ElementCollection），导致不必要的关联表和级联操作开销，与设计的实际意图（嵌入列字段）不一致。
- **建议方向**：将 VO-17 从策略 B 表格移至策略 A 表格，归类为嵌入列字段（`@Embedded`）；或为策略 B 表格增加注记说明 VO-17 虽列于此但采用嵌入映射。

### 问题 2：边缘-云端 PhysiologicalSnapshot 同步机制缺失

- **问题**：边缘侧以 JDBC batch INSERT 写入 SQLite 本地表，云端以 JPA `@ElementCollection` 管理 Trip 聚合的 PhysiologicalSnapshot 集合。断网恢复后边缘侧批量上报数据时，PhysiologicalSnapshot 如何从 SQLite 同步至云端 JPA 聚合未给出方案。
- **原因**：两端持久化策略不同（JDBC 直写表 vs JPA 级联集合），缺乏同步适配设计会导致断网恢复后云端 Trip 聚合的 PhysiologicalSnapshot 数据不完整，影响报告生成和事故回取。
- **建议方向**：(a) 在断网恢复上报流程中补充 PhysiologicalSnapshot 的上报通道（如通过 MQTT 批量上报至云端，云端适配器写入投影表或直接 INSERT 至金仓 `trip_physiological_snapshot` 表）；或 (b) 明确云端不再依赖 JPA @ElementCollection 加载 PhysiologicalSnapshot，统一改为通过投影表（trajectory_projection）查询。

### 问题 3：跨 shard 的 outbox 事务性保证未说明

- **问题**：§3.5.2 将 outbox 表固定于 shard-0 的物理数据库，而业务聚合根表可能路由至 shard-1/2/3。outbox 模式要求"聚合根状态变更与事件持久化在同一数据库事务中提交"，但跨物理数据库时此前提不成立。设计声称的"同一 datasource"事务保证不覆盖此场景。
- **原因**：若实现时忽略此问题，outbox 与聚合根写入之间丧失原子性，可能出现"聚合根已持久化但事件丢失"或"事件已发布但聚合根未持久化"的不一致状态，破坏 outbox 模式的核心契约。
- **建议方向**：明确说明跨 shard 的 outbox 写入需要 ShardingSphere-JDBC 的分布式事务支持（如 XA 事务管理器或 Seata），并评估对性能的影响；或将 outbox 表改为广播表（每个分片冗余一份），使 outbox 写入与同 shard 的业务聚合变更在同一本地事务中完成。

### 问题 4：EdgeSessionContext 的 Spring 集成方式描述矛盾

- **问题**：§3.4.2a 同时将 EdgeSessionContext 描述为"Spring Bean"和需要"手动生命周期管理"（create/destroy），但 Spring 的 singleton scope 不支持运行时销毁再重建。
- **原因**：描述矛盾会误导实现者——若按 singleton Bean 实现则无法在行程间重建实例，若按手动管理则应为非 Spring POJO。
- **建议方向**：明确 EdgeSessionContext 为非 Spring 管理的 POJO，由 SessionLifecycleManager（`@Component`）创建并持有引用；移除与 Spring Bean scope 相关的矛盾表述。此问题可与问题 2 同步修正。

### 问题 5：DEW 密钥删除策略与数据保留策略不对齐

- **问题**：§3.5.5 规定旧版本密钥在轮转后 90 天自动删除，但路怒语音存证可能有法律场景下的长期保留需求（远超 90 天）。若加密文件以已删除的旧版本密钥加密，文件将永久不可解密。
- **原因**：密钥删除策略与数据保留需求脱节，可能导致合规风险——司法协查要求在事故发生数年后提供解密后的原始存证，而旧密钥已被删除。
- **建议方向**：将旧版本密钥的保留周期与数据保留策略挂钩（如保持 DECRYPT_ONLY 状态直至所有使用该密钥版本的加密文件均被删除），而非采用固定 90 天期限。或引入密钥归档机制——将旧密钥迁移至长期离线存储（如 DEW 的密钥归档功能）。

### 问题 6：HKDF-Expand 依赖未注明

- **问题**：§3.6.2 语音存证加密方案使用 HKDF-Expand 从设备主密钥派生会话密钥，但 HKDF 不在 JDK 标准库中。
- **原因**：实现者若不了解此依赖可能使用不安全的密钥派生方式替代，造成安全隐患。
- **建议方向**：在设计中注明 HKDF 实现需引入 BouncyCastle 或使用 Java 11+ 的 `javax.crypto` 手动实现 HKDF-Extract/Expand。

### 问题 7：退避策略公式与示例不一致

- **问题**：§3.3.5 退避公式 `min(2^retryCount × 1s, 60s)` 结合 retryCount 从 0 起始，首次失败后 retryCount=1 时退避间隔为 2s，与示例"第 1 次重试延迟 1s"矛盾。
- **原因**：公式与示例不一致会误导实现者，导致实际重试间隔与设计意图不符。
- **建议方向**：统一公式与示例——若意图为 1s→2s→4s…，则将公式改为 `min(2^(retryCount-1) × 1s, 60s)`（retryCount≥1）或改为 `min(2^retryCount × 1s, 60s)` 并将首次重试前 retryCount=0（首次失败后不递增，直接以 retryCount=0 计算退避）。
