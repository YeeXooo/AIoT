# OOD 设计方案审查报告（v2）

## 审查结果

**REJECTED**

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计中所有类型形态选择均在 Java + Spring Boot 生态的能力范围内。JPA `@Entity` 用于聚合根和独立实体、`@Embeddable` + `@Embedded` 用于值对象嵌入、`@ElementCollection` 用于集合值对象、Spring Data JPA `JpaRepository` 继承用于仓储实现——这些均是 Java 类型系统原生支持的范式。泛型使用限定于标准库模式（如 `JpaRepository<T, ID>`），无超出 Java 泛型擦除限制的设计。协作关系中的依赖注入（Spring IoC）、事件监听（`@EventListener`/Spring ApplicationEventPublisher）、JDBC `JdbcTemplate` 等交互模式均可直接实现。

**[轻微]** §3.3.2 的 `domain_event_outbox` 表中 `event_id` 类型为 `UUID`，而其余所有聚合根表主键类型为 `VARCHAR(64)`。虽然 JPA 支持 `UUID` 到 `VARCHAR` 的自动映射，但类型体系不一致可能导致跨表关联时的类型对齐问题。建议统一为 `VARCHAR(64)` 以保持全域主键类型一致。

### 2. 标准库与生态覆盖

**[通过]** 设计中依赖的全部能力均在目标技术栈标准库或常用生态库的覆盖范围内：JPA/Hibernate 用于 ORM 映射、HikariCP 用于连接池、ShardingSphere-JDBC 用于分片路由、Spring ApplicationEventPublisher 用于进程内事件、DMS Kafka 用于异步消息、Eclipse Paho 用于 MQTT 客户端、华为云 SMN/Push Kit/SparkRTC/DEW/OBS/IoTDA 各 SDK 用于云服务集成。设计中假设的库能力（如 ShardingSphere-JDBC 的 CONSISTENT_HASH 算法和 scaling 在线迁移）均有实际 API 支撑。边缘侧 JDBC 直连 SQLite 的方案合理，避免了在资源受限环境中引入 Hibernate。

**[轻微]** §3.1.5 将 `guardianship` 表列入广播表策略——ShardingSphere-JDBC 的广播表对写操作执行全分片同步写入（写放大倍率为分片数），而 guardianship 表承载权限授予/撤销的写操作。在当前千辆级规模下可接受，但建议决策说明中增加对此写放大代价的评估，并明确扩展性的触发条件（如车辆数超 1 万时考虑将 guardianship 移出广播表）。

### 3. 语言特性可行性

**[通过]** 错误处理策略与 Java 的 unchecked exception 体系匹配：`PersistenceException` 层次结构、`Optional<T>` 表达不存在语义、适配器层按故障类型选择重试或向上传播。并发设计在边缘侧采用单线程串行（无锁）、云端侧采用乐观锁 `@Version` + 显式行锁 `SELECT ... FOR UPDATE` 的混合策略，均符合 Java 并发模型。资源管理方面，EdgeSessionContext 通过 Spring Bean 生命周期 + `VehicleIgnitionOnEvent`/`VehicleIgnitionOffEvent` 驱动创建/销毁，与 Spring IoC 容器的资源管理模式一致。模块划分（`infra.persistence`、`infra.repository`、`infra.eventbus` 等）符合 Java 包组织惯例。

**[通过]** DEW 密钥轮转策略（§3.5.5）的设计完整，涵盖轮转周期（90 天定时 + 强制触发）、五阶段流程、密钥版本标识嵌入密文、临时密钥 HKDF 派生、跨 AZ 灾备——所有步骤在 DEW API 能力范围内可实现。

### 4. 设计一致性

**[一般]** Outbox 投递器轮询查询的多处描述不一致。§3.3.2 索引设计为 `(published, last_attempt_at)` 并标注 `ORDER BY last_attempt_at ASC NULLS FIRST`；§3.3.5 退避策略描述轮询条件为 `WHERE published = FALSE AND (last_attempt_at IS NULL OR last_attempt_at < NOW() - 退避间隔)`，可配合该索引高效执行。但 §4 场景 2 步骤 3 的 SQL 为 `ORDER BY created_at ASC`，与索引键 `last_attempt_at` 不匹配——若查询按 `created_at` 排序，`(published, last_attempt_at)` 索引将无法覆盖排序需求，导致额外排序开销；若按 `last_attempt_at` 排序，则 `(published, created_at)` 索引（原需求 §3.3 要求的轮询索引）失去作用。此外 §4 场景 2 步骤 3 缺少退避间隔条件（`last_attempt_at IS NULL OR last_attempt_at < NOW() - 退避间隔`），与 §3.3.5 中指数退避的核心查询逻辑矛盾。

- **问题**：OutboxRelayer 的轮询查询在索引设计（§3.3.2）、退避策略（§3.3.5）和行为契约（§4 场景 2）三处描述不一致。
- **原因**：若按当前描述实现，要么索引失效（查询排序键与索引键不一致），要么退避逻辑缺失（场景 2 未包含退避间隔条件），二者均导致运行时行为偏离设计意图。
- **建议方向**：统一三处描述——将轮询查询规范化为 `SELECT * FROM domain_event_outbox WHERE published = FALSE AND (last_attempt_at IS NULL OR last_attempt_at < NOW() - 退避间隔) ORDER BY last_attempt_at ASC NULLS FIRST LIMIT N`；更新 §4 场景 2 步骤 3 以匹配此规范；确认索引 `(published, last_attempt_at)` 对该查询模式的支持。

**[一般]** DriverHealthProfile 的持久化形式与其乐观锁归属描述矛盾。§3.1.2 将其设计为独立表（有独立 PK `profile_id`），而 §3.1.4 称"DriverHealthProfile（E-03）嵌入 Driver 聚合，其乐观锁由 Driver 表统一管理"。独立表拥有自己的行和 `version` 字段（即使设计省略了显式 version 列），其乐观锁应由自身管理而非由 Driver 表代理。若意图是通过 JPA `@OneToOne` + `@MapsId` 使 profile 共享 driver 的主键和版本号，应明确说明映射策略；若确实作为独立表，则需补充其自身的乐观锁方案或明确不需要乐观锁的理由。

- **问题**：DriverHealthProfile 的持久化模式（独立表）与乐观锁归属（嵌入 Driver 聚合）描述矛盾。
- **原因**：实现时对两种设计取舍（共享主键/独立表）的不同选择将导致不同的并发行为和 JPA 映射策略，当前描述无法指导实现。
- **建议方向**：明确 DriverHealthProfile 的 JPA 映射策略——若与 Driver 共享主键（`@MapsId`），则版本号自然由 Driver 表统一管理，需在 §3.1.2 中说明 `profile_id` = `driver_id`；若保留独立主键，则为其补充独立的乐观锁版本号列。

**[一般]** 看板投影表 `fleet_dashboard_projection`（§3.2.3 P2）以 `fleet_id` 为聚合维度，但数据模型中 `trip`、`vehicle`、`safety_alert_event` 等核心表均无 `fleet_id` 列，设计中未定义车队（Fleet）实体或 Vehicle→Fleet 的归属关系。缺少此映射关系将导致"按 fleet_id 聚合 fatigue_distribution、heatmap"（需求 §二.3）的聚合查询和数据刷新逻辑无法实现。

- **问题**：看板投影的 `fleet_id` 聚合维度在数据模型中缺少来源定义。
- **原因**：`fleet_dashboard_projection` 的定时刷新（每 5 分钟聚合 SQL）需要从 Vehicle 或关联表获取 `fleet_id`，若该映射不存在则聚合查询无法正确按车队维度分组。
- **建议方向**：在 Vehicle 表或新增的 Fleet 关联表中增加 `fleet_id` 列，并说明其与看板投影的刷新查询关系。

**[通过]** 其余协作关系形成闭环：仓储 → 聚合根持久化 → 乐观锁冲突 → 领域服务重试；领域服务 → 事件发布 → outbox 表 / Spring Event → 消费者处理；边缘断网 → 缓冲 → 重连 → 批量重传 → 幂等去重。各链路无缺失环节。

**[通过]** 模块间依赖方向单向合理：`infra.repository` → `infra.persistence`，`infra.eventbus` → `infra.messaging`，`infra.adapter` → `infra.cloud`/`infra.security`。无循环依赖。基础设施层不依赖应用层，遵守分层架构约束。

### 5. 设计质量

**[通过]** 职责划分遵循单一职责原则：持久化映射（`infra.persistence`）与仓储实现（`infra.repository`）分离，事件持久化（outbox 表在 persistence 范畴）与事件投递（`infra.eventbus`）分离，云服务适配（`infra.cloud`）与安全加密（`infra.security`）分离。抽象层次恰当——EdgeSessionContext 作为基础设施层内部的编排组件而不暴露给领域层，符合分层隔离原则。

**[通过]** 乐观锁冲突重试策略（§3.2.4）明确将重试逻辑归入领域服务而非仓储层，仓储层仅忠实返回冲突信号。这一设计使仓储保持纯粹（可测试、可替换），重试策略按业务场景差异化定制，符合关注点分离。

**[通过]** 设计便于单元测试：仓储接口以领域层 `interface` 声明，可 mock；端口适配器以领域层端口接口为契约，可 mock；事件总线通过 `EventBus` 接口抽象，边缘侧和云端侧实现可分别测试。CQRS 投影同步器与主业务链路解耦，可独立测试投影写入逻辑。

**[轻微]** §3.2.2a 中 `SystemAccountRepository` 的边缘侧实现被声明为"云端驱动的只读缓存"，但其 `save` 方法描述为"仅本地缓存更新 (upsert)"。将缓存更新操作命名为 `save` 可能与领域层仓储接口的 `save` 语义（持久化聚合根）混淆，建议将边缘侧缓存更新操作命名为 `refreshCache` 或 `upsertCache` 以区分。

**[轻微]** §3.3.6 中 `DriverDeactivatedEvent` 的消费方标注位置为"边缘/云端"，是唯一使用斜杠而非"边缘同步 / 云端异步"格式的条目，格式不一致。虽然此事件可能确实两侧均需处理，但标注方式与表中其他条目不一致。

## 修改要求（REJECTED 时存在）

### 问题 1：Outbox 轮询查询三处描述不一致

- **问题**：§3.3.2 索引设计、§3.3.5 退避策略、§4 场景 2 行为契约中对 OutboxRelayer 轮询查询的 ORDER BY 键和退避条件描述不一致。
- **原因**：若实现时按 §4 场景 2 的 `ORDER BY created_at ASC` 且无退避间隔条件，则索引 `(published, last_attempt_at)` 无法被高效利用，且指数退避重试策略无法生效——所有事件将被每 1s 统一重试而非按各自退避间隔分时重试。
- **建议方向**：将轮询查询统一为 `WHERE published = FALSE AND (last_attempt_at IS NULL OR last_attempt_at < NOW() - 退避间隔) ORDER BY last_attempt_at ASC NULLS FIRST LIMIT N`，同步更新 §3.3.2 索引说明、§3.3.5 退避策略中的查询描述、§4 场景 2 步骤 3，确保三处完全一致。步骤 4 中的事件遍历逻辑也需与新查询匹配（当某条事件的 `last_attempt_at` 尚未满足退避间隔时，由于 ORDER BY 是按 `last_attempt_at` 升序，后续事件也不会满足条件，可提前终止本轮轮询）。

### 问题 2：DriverHealthProfile 持久化模式与乐观锁归属矛盾

- **问题**：§3.1.2 设计为独立表（独立 PK `profile_id`），§3.1.4 描述为"嵌入 Driver 聚合，其乐观锁由 Driver 表统一管理"，两者互斥。
- **原因**：若实现者按 §3.1.2 建表而按 §3.1.4 设计乐观锁，DriverHealthProfile 表将缺少独立的并发控制机制；若出现 DriverHealthProfile 的独立写操作（如更新健康档案），缺少乐观锁可能导致静默覆盖。
- **建议方向**：明确选择一种策略：(a) 若 DriverHealthProfile 确实作为 Driver 聚合的内部实体（共享 Driver 主键和版本号），在 §3.1.2 中注明 `profile_id` = `driver_id`，使用 JPA `@MapsId` 映射；(b) 若保留独立表设计，则为其增加 `version` INTEGER 列，并从 §3.1.4 中移除"嵌入 Driver 聚合"的表述。

### 问题 3：看板投影的 fleet_id 聚合维度缺少数据来源

- **问题**：`fleet_dashboard_projection` 按 `fleet_id` 聚合，但数据模型中没有 `fleet_id` 字段或 Fleet 实体定义。
- **原因**：定时刷新任务（每 5 分钟 SQL 聚合）若无法获取 vehicle→fleet 的归属映射，将无法按车队维度生成聚合数据，`fleet_dashboard_projection` 表在实际运行时无法被正确填充。
- **建议方向**：在 Vehicle 表（或新增 `vehicle_fleet` 关联表）中增加 `fleet_id VARCHAR(64)` 字段，定义其与 Fleet 实体的关系，并在 §3.2.3 P2 的刷新 SQL 中说明如何 JOIN Vehicle 表获取 `fleet_id`。
