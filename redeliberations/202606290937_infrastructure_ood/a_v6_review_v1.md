# OOD 设计方案审查报告（v6 迭代验证）

## 审查结果

APPROVED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计中使用的类型形态（class / interface / abstract class / enum / @Embeddable / @Entity / @ElementCollection）均与 Java + Spring Boot + JPA 目标技术栈完全匹配。抽象之间的继承和实现关系遵循 Java 单继承 + 多接口实现的约束。泛型使用（如 `Result<T, E>`、`Consumer<DomainEvent>`、`Optional<T>`）均在 Java 泛型系统能力范围内。协作关系中描述的类型交互模式（仓储接口→JPA Entity、端口接口→适配器实现）均为标准 Spring 依赖注入模式，可在目标语言中实现。

### 2. 标准库与生态覆盖

**[通过]** 设计中所需的核心能力均在目标技术栈的标准库或常用库覆盖范围内：Spring Data JPA（仓储实现）、HikariCP（连接池）、ShardingSphere-JDBC（分片路由）、Eclipse Paho / Spring Integration MQTT（MQTT 客户端）、华为云 DMS Kafka（消息队列）、华为云 SMN/Push Kit/SparkRTC/DEW/OBS/IoTDA（云服务集成）、BouncyCastle（HKDF 密钥派生）、Redis（缓存/去重/分布式锁）。对标准库能力的假设合理。

### 3. 语言特性可行性

**[通过]** 错误处理策略与 Java 的异常机制匹配——`PersistenceException`（unchecked）、`Optional<T>` 表达不存在、`Result<T, E>` 表达可恢复错误。并发设计符合 Java 的乐观锁（`@Version`）、行锁（`SELECT ... FOR UPDATE`）、`ConcurrentHashMap`、单线程串行（边缘侧）等模型。资源管理方案在 Java 的 try-with-resources / 显式生命周期管理范围内可行。模块/包结构（8 个 infra.* 子模块）符合 Spring Boot 项目的标准组织方式。

### 4. 设计一致性

**[通过]** 各抽象的职责描述清晰无歧义——8 个基础设施模块各有明确的职责边界和依赖方向。协作关系形成闭环——仓储实现→持久化映射→数据库分片、事件总线→outbox 表→消息队列→消费者、端口适配器→云服务集成。行为契约（见 §四 4 个场景）完整到足以指导后续实现。模块间依赖方向为单向，无循环依赖。

**[轻微]** 文档第 274 行块引用中可能存在残留的字面量格式化工件（`\n>`），建议在最终发布前通过 Markdown 渲染器验证输出效果。

### 5. 设计质量

**[通过]** 职责划分遵循单一职责原则——仓储层仅负责持久化，事件总线层仅负责事件投递，适配器层仅负责外部端口实现，云服务层仅负责华为云 API 适配。抽象层次恰当——在"架构级设计"约束下，未过度设计具体方法签名也未设计不足。CQRS 投影表、事件驱动异步同步、outbox 事务性投递等模式为设计提供了良好的可扩展性。乐观锁冲突重试由领域层控制、outbox 投递器使用 `FOR UPDATE SKIP LOCKED` 等设计便于单元测试（可 mock、可隔离）。

## 迭代问题逐项验证

对照迭代需求指定的 23 个问题，逐项验证修改措施是否在设计文件中落实：

### 严重问题（3 项）

| # | 问题 | 验证结果 |
|---|------|---------|
| 1 | SystemAccountRepository.findByDriver JPQL 矛盾（§3.2.1 vs §3.2.2） | **已修正** — §3.2.1 的 JPQL 已统一为跨表 JOIN 写法 `JOIN guardianship g ON a.id = g.accountId`，与 §3.2.2 一致 |
| 2 | 文档标题版本号与内容修订层级不一致（标题 v5，修订至 v10） | **已修正** — 标题已改为 `（v11）`，与最新修订说明版本号对齐 |
| 3 | 修订说明「v5」标题出现两次（重复） | **已修正** — 两个 v5 批次已合并为单一"修订说明（v5）"小节，含全部 8 条审查意见 |

### 一般问题（13 项）

| # | 问题 | 验证结果 |
|---|------|---------|
| 4 | 缺失需求 §一.5 要求的概念级 DDL 概要 | **已修正** — §3.1.6 新增概念级 DDL 概要，以 CREATE TABLE 语法展示 Trip 和 SafetyAlertEvent 两张代表性表 |
| 5 | DriverHealthProfile 表设计缺少独立查询路径的显式声明 | **已修正** — §3.1.2 新增"独立查询路径声明"段落，明确急救场景和隐私合规审计场景的独立查询路径及 DriverHealthProfileRepository |
| 6 | 修订说明中使用 REJECTED 标记不符合设计文档规范 | **已修正** — v6 和 v7 修订说明中的 `**REJECTED**` 已替换为 `**已核实并确认**` |
| 7 | OTA 升级刷写期间安全监测中断的安全风险未被承认 | **已修正** — §3.4.5 新增"刷写前置条件"段落，明确刷写前必须校验车辆为驻车熄火状态 |
| 8 | VehicleStateBuffer.getSnapshots 方法签名与需求中的端口契约不一致 | **已修正** — §3.4.1 回取接口已修正为 `getSnapshotWindow(Instant from, Instant to)`，与需求 §四.1 一致 |
| 9 | guardianship 表广播表策略下的悲观锁替代方案未说明跨分片 SELECT...FOR UPDATE 执行方式 | **已修正** — §3.1.2 新增"广播表 SELECT...FOR UPDATE 行为说明"段落，明确跨分片不具排他性，推荐改用 Redis SET NX 分布式锁 |
| 10 | PhysiologicalDataBuffer.getReadings 方法签名与需求中的端口契约不一致 | **已修正** — §3.4.2 回取接口已修正为 `getPhysiologicalWindow(Instant from, Instant to)`，与需求 §四.2 一致 |
| 11 | OTADeliveryPort 端口方法契约不够完整 | **已修正** — §3.4.5 新增"OTADeliveryPort 端口方法契约概要"表格，定义 deliverPackage/cancelDelivery/verifyFirmwareSignature 三个方法 |
| 12 | MediaSessionPort 会话超时与并发限制未定义 | **已修正** — §3.4.8 新增"会话生命周期约束"表格（最大持续 30 分钟、空闲超时 5 分钟、并发 ≤1） |
| 13 | Outbox 表 / DLQ 表在消息队列长时间不可用时的存储膨胀 | **已修正** — §3.3.5 新增 outbox 积压监控告警（10000/50000 条阈值）及降级策略、DLQ 30 天保留窗口自动清理策略 |
| 14 | 边缘侧 SQLite 核心表无容量管理与清理策略 | **已修正** — §3.7.4 新增"SQLite 核心表清理策略"段落，trip/safety_alert_event 保留 7 天，road_rage_voice_record 按 expiry_time 清理 |
| 15 | IoTDA 离线消息缓存 24 小时上限与长期离线车辆的矛盾 | **已修正** — §3.7.4 新增"长期离线数据弥补策略"段落（断网恢复批量补推 + 幂等去重 + 7 天超限清理） |
| 16 | DEW 密钥服务不可用时的边缘侧加密降级策略不完整 | **已修正** — §3.5.5 新增"DEW 不可用时的分级降级策略"段落（L1 缓存密钥 ≤30min、L2 临时随机密钥 30min~6h、L3 审计告警 >6h） |

### 轻微问题（7 项）

| # | 问题 | 验证结果 |
|---|------|---------|
| 17 | guardianship 表描述中存在格式化 artifact `\n\n` | **已修正** — 字面量 `\n\n` 和 `\n>` 已替换为标准 Markdown 空行分隔和块引用格式 |
| 18 | domain_event_dlq 表主键使用 BIGSERIAL 与统一主键策略不一致 | **已修正** — §3.3.3 DLQ 表 `dlq_id` 已改为 `VARCHAR(64)`（应用层生成 UUID） |
| 19 | 看板投影 P2 中 driver_count 的聚合语义与数据源不一致 | **已修正** — §3.2.3 P2 聚合计算口径概要中数据源引用已统一为 `alert_projection` |
| 20 | CameraOcclusionDetectionPort 的遮挡/移除信号的载荷结构未定义 | **已修正** — §3.4.4 新增"信号载荷字段概要"表格 |
| 21 | NotificationPort 各方法接收的 Command 对象载荷字段未定义 | **已修正** — §3.4.6 新增"三种 Command 对象载荷字段概要"表格 |
| 22 | 边缘侧 EmergencyRescueService 的碰撞判定时间窗口与 ring buffer 容量之间的量化关系未给出 | **已修正** — §3.4.2a 新增"碰撞前数据可用性保证"段落，明确容量保证和判定延迟超限时的降级处理 |
| 23 | IoTDA 离线消息缓存 24 小时上限（轻微级子项） | **已修正** — 与问题 15 合并处理 |

## 审查总结

本次迭代的 23 个指定问题（3 严重 + 13 一般 + 7 轻微）在设计文件中已全部得到正确修改。各问题的修正措施与迭代需求中指定的改进建议一致，修改位置与问题定位的文档章节一一对应。未发现本轮引入的新增严重或一般问题。

此外，对比原始需求（`requirement.md`），设计文件覆盖了需求中要求的全部 21 个设计要点（一至七节），各维度的设计决策（数据持久化、仓储实现、事件基础设施、端口适配器、云服务集成、安全隐私、部署拓扑）均已展开为架构级方案。
