# OOD 设计方案审查报告（v1）

## 审查结果

APPROVED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计中的类型形态选择均与 Java/Spring/JPA 类型系统能力匹配：(a) @Entity + @Embeddable + @Embedded + @ElementCollection 的 JPA 映射链在 Hibernate 能力范围内，@MapsId 用于 DriverHealthProfile 的 1:1 共享主键符合 JPA 规范；(b) 仓储以 Java interface 声明于领域层、以 @Repository 实现于基础设施层，Spring Data JPA 继承 `JpaRepository` + 自定义 @Query 完全可行；(c) 泛型使用限于 `Optional<T>`、`List<T>` 等标准集合，在 Java 泛型系统约束内；(d) ShardingSphere-JDBC 一致性哈希分片算法（`CONSISTENT_HASH`）和绑定表（Binding Table）机制均为 ShardingSphere-JDBC 正式功能；(e) 事件总线以接口契约抽象，边缘侧注入 SpringEventBus、云端侧注入 OutboxEventBus，依赖注入替换可行。

### 2. 标准库与生态覆盖

**[通过]** 设计中引用的所有库和云服务均为真实可用的生态组件：(a) Spring Boot + Spring Data JPA + Spring ApplicationEventPublisher + @Scheduled 均为 Spring 生态标准组件；(b) HikariCP 为 Spring Boot 默认连接池；(c) ShardingSphere-JDBC 提供一致性哈希分片和绑定表功能；(d) 华为云 IoTDA、DMS Kafka、SMN、SparkRTC、DEW、OBS、DCS Redis、Push Kit 均为华为云正式服务；(e) 金仓数据库兼容 PostgreSQL 协议，PostgreSQLDialect 可正常使用；(f) Eclipse Paho 为 Java MQTT 客户端标准库；(g) SQLite 通过 JDBC 直连可行；(h) BouncyCastle 为 HKDF 提供实现，虽为外部依赖但设计已显式标注。

**[轻微]** 部分库版本未指定（如 BouncyCastle 的 `bcprov-jdk18on` 最小版本），但此类实现细节对架构级设计不构成阻塞。

### 3. 语言特性可行性

**[通过]** (a) 错误处理策略对齐 Java 异常模型：仓储层以 `PersistenceException`（unchecked）表达持久化故障、以 `Optional<T>` 表达不存在；适配器层按场景重试或向上抛出；outbox 投递器和 IoTDA 重连客户端作为独立后台线程自管理失败，不向上传播——均可行；(b) 并发设计合理：边缘侧单线程串行无需锁；云端侧乐观锁 `@Version` 为 JPA 标准机制，边缘侧 JDBC 手动版本号条件 UPDATE 在 SQLite 环境下可行，guardianship 以 SELECT ... FOR UPDATE 行锁实现串行化与金仓/PostgreSQL 兼容；(c) 资源管理：EdgeSessionContext 通过 SessionLifecycleManager 的生命周期管理（创建/销毁/重启恢复）可行，ring buffer 基于 ArrayDeque 的定长实现可行；(d) 模块分包结构 `infra.persistence / repository / eventbus / messaging / adapter / cloud / security / edge` 的八模块划分符合 Java 包组织惯例，依赖方向单向合理。

### 4. 设计一致性

**[通过]** (a) 五个聚合根表（trip、driver、vehicle、system_account、road_rage_voice_record）和两个独立实体表（safety_alert_event、driver_health_profile）的结构定义与 §3.1.3 值对象映射策略相互一致；(b) 三张 CQRS 投影表的数据同步机制形成闭环——alert_projection 通过 AlertTriggeredEvent 驱动写入，fleet_dashboard_projection 以事件驱动即时聚合+定时全量刷新双路径保证数据新鲜度，trajectory_projection 以批量写入处理高频遥测；(c) 事件总线全链路可追踪：边缘侧 SpringEventBus 同步回调 → IoTDA 上行 → DMS Kafka → outbox 投递器 → 云端异步消费者，IotdaUplinkConsumer 桥接组件显式衔接边缘-云端事件流；(d) 部署拓扑中各组件网络路径标注清晰，含 Mermaid 部署图和关键网络路径汇总表；(e) 跨模块引用一致：outbox 轮询 SQL 在三处（§3.3.2 / §3.3.5 / §4 场景 2）现已统一为固定上界 60s 过滤 + Java 层逐事件精确退避的两层过滤方案。

**[轻微]** 文档标题仍标注为 `a_v1 / v2` 而实际包含至 v8 的修订内容，可能造成版本导航困惑，但不影响设计实质内容。

**[轻微]** "修订说明（v5）" 标题出现两次（§七第 1328 行和第 1334 行），为重复标记。

### 5. 设计质量

**[通过]** (a) 职责划分清晰：八模块各司其职（持久化映射、仓储实现、事件总线、消息队列、端口适配、云服务适配、安全加密、边缘专属），接口契约与实现分离，依赖方向单向；(b) 抽象层次恰当：仓储层保持纯粹（仅持久化操作和冲突检测，不封装业务重试逻辑），适配器层封装技术细节（SMN 模板变量填充、SparkRTC Token 续期、DEW 密钥轮转），领域层仅见接口；(c) 边缘-云端差异以两套实现承载同一接口契约的方式处理，不污染领域层抽象；(d) 可测试性：仓储接口为 interface、端口为 interface、事件总线为 interface，均可 mock；边缘侧 JDBC 实现可独立测试不依赖 JPA 框架；(e) 设计决策（§七）提供 8 项关键决策的理由说明，支撑后续实现的选型依据。

**[轻微]** §3.1.3 策略 B PhysiologicalSnapshot 段中，"不支持仅查询部分快照列表"后的读优化路径提及 `trajectory_projection` 投影表，但 trajectory_projection 字段（GPS 坐标、速度）不含生理数据字段（心率、血氧、情绪指数），此处引用可能引起后续实现时的困惑。建议改为明确说明"读优化通过 trip_physiological_snapshot 表的按时间戳分页查询满足"或扩展 trajectory_projection 以纳入生理字段。不影响核心设计，仅影响文档精确性。
