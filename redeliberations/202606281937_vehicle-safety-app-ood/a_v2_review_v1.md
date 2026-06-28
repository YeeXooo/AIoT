# OOD 设计方案审查报告（v1）

## 审查结果

APPROVED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 六个应用服务统一采用 `interface`（契约）+ `class`（编排实现）分离建模。仓颉语言原生支持 `interface` 声明方法签名、`class` 通过 `: InterfaceName` 语法实现，此模式在仓颉类型系统能力范围内完全可行。

**[通过]** 统一错误处理采用 `Result<T, AppError>` 泛型返回模式，仓颉标准库原生支持 `Result` 泛型类型。`AppError` 定义使用仓颉 `enum` 带关联数据的代数数据类型语法（如 `SessionNotFound(sessionHandle: String)`），与领域层 OOD 中既有的错误枚举风格一致，在仓颉类型系统中可行。

**[通过]** 设计中涉及的泛型使用方式（`Result<T, AppError>`、`Option<T>`、`Array<T>`、`Map<K, V>`）均在仓颉泛型系统能力范围内。

**[通过]** 跨层类型（SensorReading、DriverStatusSnapshot、OverrideSignal、SessionHandle）均以字段级结构定义，使用的基础类型（`Timestamp`、`String`、`Float64`、`Bool`、`UInt32`、`UInt64`、`GeoPoint`、`Duration` 等）均为仓颉标准库或领域层已定义的类型。

**[通过]** 抽象之间的继承和实现关系符合仓颉约束——`class` 实现 `interface`（单实现或多实现均可），无多继承场景。设计不涉及密封类（sealed class）。

**[轻微]** `Result<Unit, AppError>` 中使用 `Unit` 类型表示无返回值。仓颉中表达"无有意义返回值"的惯用方式需在实现阶段确认——可能为 `Unit`、`Void` 或 `()`。建议实现阶段统一使用仓颉标准库中的对应类型。

**[轻微]** DTO 中使用 `Array<(AlertType, RiskLevel)>` 和 `Array<(VehicleId, AppError)>` 的元组语法。仓颉中元组的表达方式需在实现阶段确认——若仓颉不支持括号元组字面量语法，需替换为具名结构体或内联记录类型。建议实现阶段为跨字段组合定义轻量值对象（如 `RiskEntry(alertType, riskLevel)` 和 `SkippedVehicle(vehicleId, error)`），语义更清晰且不依赖元组语法。

### 2. 标准库与生态覆盖

**[通过]** 应用层所需的基础类型能力——`Array<T>`、`Map<K, V>`、`String`、`Bool`、`Float64`、`UInt32`、`UInt64`、`Option<T>`、`Result<T, E>`——均在仓颉标准库覆盖范围内。设计中正确使用了这些基础类型作为方法契约的参数和返回值表达。

**[通过]** 设计将 WebSocket 长连接管理、MQTT（IoTDA）通道、事件总线（EventBus）、outbox 事务表、CQRS 读模型投影、缓存基础设施等能力归入基础设施层关注点（§7.3 基础设施实现假设块），应用层仅作为消费者引用。此划分合理——这些能力的具体实现由基础设施层设计阶段确定，应用层设计方案不绑定具体选型。

**[通过]** 依赖注入采用构造器注入 + 手动装配模式，不依赖特定 IoC 容器（§2.2 明确说明 `cangje-ioc` 为后续可选方案）。此策略在仓颉中可直接实现——`main` 入口模块中创建领域服务/仓储/端口的具体实例，作为参数传入应用服务 `class` 的构造器。

**[通过]** 设计中 S3 的音视频对讲能力假设了 SparkRTC 信令通道，S5 的救援报告投递假设了 120 救援中心接口。这些外部系统集成通过领域端口（MediaSessionPort、RescueReportPort、NotificationPort 等）抽象，应用层不直接依赖外部 SDK，符合端口-适配器模式。

**[轻微]** 设计 §5.1 SensorReading 的 `values` 字段使用 `Map<String, Float64>` 承载传感器读数键值对（如 `"PERCLOS": 0.85`）。此设计的键为字符串松散约定，缺乏编译期类型安全——拼写错误（如 `"PRECLOS"`）在编译期无法检测。建议在实现阶段将 SensorReading 的键约束为特定枚举或结构化字段（如 `perclos: Option<Float64>`、`yawnFreq: Option<Float64>`），以获得编译期校验。

### 3. 语言特性可行性

**[通过]** 错误处理策略采用 `Result<T, AppError>` 显式返回业务错误，不依赖异常抛出的控制流，与仓颉鼓励的显式错误处理风格一致。§6.1 定义的 `AppError` 枚举按 A/B/C 三类组织 15 个变体，每个变体携带结构化上下文信息，三级错误分类与领域层既有的分类体系对齐。

**[通过]** 并发设计正确区分了云端无状态水平扩展（六个应用服务自身无状态，无共享可变状态）与边缘侧单线程同步执行（感知数据按时间序列送达，判定→干预链路 ≤500ms 同步执行，无并发竞争）。此模型与仓颉的并发能力兼容——云端可安全地以多线程/多协程处理并发请求，边缘侧单线程执行无需同步原语。聚合的乐观锁策略（Vehicle、SystemAccount 基于版本号）在仓颉中可通过 CAS 语义或版本号字段实现。

**[通过]** 资源管理方面：音视频会话（MediaSessionPort）有明确的建立（`requestMediaSession`）与释放（`endMediaSession` + `FamilyAccessRevokedEvent` 驱动）生命周期；WebSocket 连接有完整的生命周期管理策略（§3.3，含心跳、重连、离线消息补推、连接数限制）。此模式在仓颉中可通过显式资源句柄管理或 RAII 风格实现。

**[通过]** 模块结构使用点分命名空间（`application.risk`、`application.intervention` 等），与 cjpm 的包目录层级组织方式兼容。应用层对领域层的单向依赖（不反向）符合 cjpm 的模块依赖约束。六个应用服务模块间无编译期依赖（协作通过领域事件总线运行期路由），不存在循环依赖。

**[轻微]** S1 跨边缘侧与云端两侧部署（§3.1 部署边界说明），两侧共享同一应用服务标识。边缘侧运行于单线程车载终端，云端运行于可水平扩展的华为云环境。实现阶段需注意：两侧编译产物可能需要拆分为不同的 cjpm 包以隔离运行环境依赖（边缘侧可能不需要 WebSocket、HTTP 服务器等云端依赖）。建议在设计文档中注明编译单元拆分策略。

**[轻微]** §3.1 `startMonitoringSession` 和 `startLifeDetection` 的事务列标注为"—"（无事务），因两者均为"边缘侧内存操作"。此说明在应用层方法的角度是正确的，但会话的创建和活体检测的启动本质上需要将 SessionHandle 写入内存中的会话注册表——若该注册表由边缘侧多个组件共享（如 S1 和 HMI 查询组件），则存在极低概率的竞态。鉴于边缘侧为单线程环境，此风险实际不存在，但建议在事务边界表中增加"边缘侧单线程，无需并发控制"的明确注释以消除疑虑。

### 4. 设计一致性

**[通过]** 六个应用服务完整覆盖了需求中指定的六个功能域（S1~S6）。每个应用服务的职责描述清晰——明确区分了"编排能力"（应用层）与"判定/执行业务逻辑"（领域层），与 §一 的职责边界表一致。

**[通过]** 三条关键路径的时序图（§8.1 疲劳判定→告警→干预、§8.2 活体遗留→报警、§8.3 碰撞失能→SOS+家属激活）覆盖了边缘端→云端→APP 全链路消息交互。所有跨层通信均经领域事件总线（EventBus）作为路由中介，HMI 交互统一经 InterventionServiceImpl（S2）的查询接口——修复了前轮审查中"跨层直接通信"的问题。每条路径均标识了关键时间约束（≤500ms 边缘同步、≤10s 报警推送、60s 活体检测窗口等）。

**[通过]** 服务间协作关系形成闭环：
- 链路 A：S1 产出 RiskDeterminedEvent → DS-07 生成干预指令 → S2 更新干预状态 → HMI 查询
- 链路 B：领域层产出告警事件 → S3（家属推送）/ S4（看板缓存失效）/ S5（SOS 投递）
- 链路 C：S3 家属手动救援 → DS-12 领域服务 → S5 救援上报
- 链路 D：DS-15 OTA 状态事件 → S2 抑制/恢复 CAN 干预

所有跨功能域协作均通过领域事件完成，应用服务之间无直接方法调用。

**[通过]** 模块间依赖方向合理：应用层 → 领域层（单向），领域层不反向依赖应用层。六个应用服务模块间无编译期依赖。

**[通过]** §4 为六个应用服务定义了完整的 DTO 体系（输入/输出），§5 定义了四个跨层类型，§6.1 定义了完整的 AppError 枚举（含附属枚举 PermissionDenialReason 和 AccessDenialReason）。§7.4 事务边界汇总表覆盖了全部 22 个方法的写/只读/无事务标记以及事务隔离级别和并发控制策略。

**[通过]** §8 中的时序图拼写错误已修正（`FatigueDeterminationService` 替代了前版的 `Fatgue-`）。

**[一般]** 以下 4 个类型在 DTO 定义中被使用但未给出完整枚举定义：

| 类型名 | 使用位置 | 已出现的变体值 | 问题 |
|--------|---------|-------------|------|
| `RescueTriggerType` | §4.5 `RescueRecordSummary.triggerType` | `COLLISION_DISABILITY` / `MANUAL` / `LIFE_DETECTION` | 未在 §4.5 或 §5 中定义为此类型的枚举 |
| `RescueRecordStatus` | §4.5 `RescueRecordSummary.status` | `SENT` / `CONFIRMED` / `PENDING_RETRY` / `MANUAL_ESCALATION` | 未在 §4.5 或 §5 中定义为此类型的枚举 |
| `RescueRequestStatus` | §4.3 `TriggerManualRescueResponse.status` | `PENDING` / `CONFIRMED` | 未在 §4.3 或 §5 中定义为此类型的枚举 |
| `UpgradeOptions` | §4.6 `CreateUpgradeTaskRequest.upgradeOptions` | 仅说明"可选升级参数（分批策略、时间窗口等）" | 结构字段未定义 |

上述类型中 `RescueTriggerType`、`RescueRecordStatus`、`RescueRequestStatus` 均为枚举类型，其变体值已在 DTO 字段说明中隐式给出，但缺少显式的枚举类型定义（其他 DTO 中如 `OverrideResult`、`MediaSessionType`、`DataFreshness`、`ReportType` 均给出了完整枚举定义，此不一致性影响接口层开发人员对类型的完整理解）。`UpgradeOptions` 则缺少字段级结构定义。建议为前三个类型补充枚举定义，为 `UpgradeOptions` 补充字段定义（如 `batchStrategy: BatchStrategy`、`timeWindow: TimeRange`），与文档中其他 DTO 附带枚举的格式一致。

**[轻微]** §3.6 `createUpgradeTask` 方法的"事务"列标注为"写（逐条提交）"，但事务边界说明块（§3.5 的 > 块引用末尾）中 S6 的批量操作事务策略描述被放置在 §3.5（S5 章节）的末尾。此内容在文档结构上属于 S6 的职责范围，放置在 S5 章节内可能导致读者遗漏。建议将批量操作事务策略段落移至 §3.6 内或 §7.4 中。

**[轻微]** §7.1 协作关系总览图中 S2→S6 的箭头标注为"订阅 OTA 升级状态事件"，S3→S5 标注为"家属手动救援触发"，注解已澄清了依赖本质（事件消费而非直接调用）。但图示仍将箭头画为应用服务间直连，与 §7.3 声明的"应用服务间零直接调用"原则在视觉上存在张力。建议将图示调整为各应用服务围绕共享事件总线的星形拓扑，或在图例中增加事件依赖箭头（虚线）与编译期依赖箭头（实线）的区分。

### 5. 设计质量

**[通过]** 六个应用服务各对应一个功能域，职责划分遵循单一职责原则。每个服务作为其功能域的用例编排入口，不包含领域业务逻辑，不跨越其他功能域的职责边界。

**[通过]** 抽象层次恰当——应用层定位于"薄层"编排（§一设计目标），不涉及具体算法实现，不沉入基础设施细节。与领域层的职责边界表（§一）清晰划分了事务管理、安全门控、事件发布、DTO 转换等职责的归属。

**[通过]** 设计充分考虑了可测试性：
- 六份 `interface` 与六份 `class` 分离，验收测试可针对 `interface` 编写 mock 实现（决策 A1）
- 应用服务的无状态性（§9.1）使其可在测试中独立实例化，通过构造器注入 mock 的领域服务和仓储
- §11 提供了 7 个验收测试场景（TC-1-1 到 TC-3-3），覆盖三条核心路径的正常路径和关键异常路径（无效会话、误报抑制、救援投递重试、投递全部失败转人工）

**[通过]** 安全设计内聚——§6.3 定义了四步安全门控链（身份认证→角色校验→二次身份验证→权限校验），所有高敏操作在进入领域层之前必须通过门控。门控逻辑集中在应用层，不泄露到领域层，符合横切关注点分离原则。

**[通过]** 设计产出完整：§4 DTO 定义、§5 跨层类型定义、§6 错误处理策略、§7 协作关系与事务边界汇总、§8 核心时序图、§9 并发设计、§10 设计决策、§11 验收测试场景概要——为后续详细设计和实现提供了充足的规格输入。

**[轻微]** S1 RiskMonitoringService 的接口契约（§3.1）中 `processSensorReading` 方法的说明为"委托 RiskDeterminationService 执行流式融合判定"，但未说明该方法的调用频率预期（设计上下文暗示"边缘侧高频调用"）。从接口层调用方角度，理解调用频率约束有助于设计客户端的批处理或限流策略。建议在方法说明中补充预期的调用频率量级（如"典型 10~30 Hz，按感知采集层采样率决定"）。

**[轻微]** §5.2 DriverStatusSnapshot 的 `activeAlertLevels` 字段类型为 `Map<AlertType, RiskLevel>`。此类型语义上等价于 §4.1 `GetDriverRiskStatusResponse.activeAlerts` 的 `Array<(AlertType, RiskLevel)>`，但使用了不同的容器类型（Map vs Array）。两个类型表达相同的数据语义却在不同的 DTO 中采用不同容器形态，可能让接口层开发人员产生困惑。建议统一为同一容器类型（优先 `Map<AlertType, RiskLevel>`，因为 AlertType 在一个快照内不应重复）。

## 修改要求（REJECTED 时存在）

（无 — 本轮审查结果为 APPROVED）
