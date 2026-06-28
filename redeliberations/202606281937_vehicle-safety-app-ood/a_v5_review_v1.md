# OOD 设计方案审查报告（v1）

## 审查结果

APPROVED

## 逐维度审查

### 1. 类型系统可行性

**[通过]** 设计方案中的类型形态选择与仓颉类型系统能力高度匹配：
- 六个应用服务以 `interface` 声明契约、`class` 实现编排逻辑，符合仓颉面向接口编程的推荐模式，`interface` 支持方法签名声明（不含方法体），`class` 通过 `: InterfaceName` 实现。
- 类之间遵循单继承约束，多接口实现模式与仓颉能力一致。
- 构造器注入依赖（构造器参数声明为 `interface` 类型，运行时注入 `class` 实例）在仓颉中直接可行。
- `Result<T, AppError>` 模式用于方法返回值，与仓颉 `Result` 类型匹配。
- `Option<T>` 用于可空字段（如 `Option<GeoPoint>`、`Option<Float64>`），符合仓颉可选类型的惯用法。
- 泛型集合类型 `Map<K,V>`、`Array<T>` 均在仓颉标准库覆盖范围内。
- `enum` 定义（含携带数据的变体如 `AppError.SessionNotFound(sessionHandle: String)`）与仓颉枚举能力匹配。

**[轻微]** DTO 定义中使用了匿名元组语法如 `Array<(AlertType, RiskLevel)>`、`Array<(VehicleId, AppError)>`。仓颉中匿名元组支持取决于具体语言版本——若不支持，需为这些键值对定义命名结构类型（如 `AlertRiskEntry`、`SkippedVehicleEntry`）。此为语法适配层面的细节，不阻塞设计可行性。

### 2. 标准库与生态覆盖

**[通过]** 设计中需要的能力在仓颉标准库或合理扩展范围内：
- 集合类型（`Array`、`Map`）、可选类型（`Option`）、结果类型（`Result`）均为仓颉标准库能力。
- 领域事件总线（EventBus）、CQRS 读模型投影、outbox 事务性事件表等机制在 §7.3 中已明确标注为基础设施层关注点，应用层方案将其作为已存在的底层能力来引用——此假设合理，不阻塞应用层设计审查。
- 第三方集成（华为云 IoTDA、SparkRTC、SMN、GaussDB/RDS）通过端口（Port）抽象隔离，不直接依赖外部库的仓颉绑定。
- 模块路径命名（`application.risk`、`application.intervention` 等）符合 cjpm 项目组织方式。

### 3. 语言特性可行性

**[通过]** 设计中的语言特性使用策略与仓颉能力匹配：
- **错误处理**：`Result<T, AppError>` 模式与仓颉的 `Result` 类型完全匹配，错误枚举 `AppError` 携带结构化上下文信息（如 `SessionNotFound(sessionHandle: String)`），仓颉枚举支持此模式。
- **并发设计**：边缘侧单线程流式处理（§9.3）与云端无状态水平扩展（§9.1）的策略清晰分离。并发控制采用乐观锁（基于版本号）方案，仓颉可通过原子比较-交换操作实现。各方法的事务隔离级别（读已提交）和并发策略（§9.2）均有明确标注。
- **资源管理**：构造器注入 + 手动装配模式在仓颉中直接可行，不依赖特定 IoC 容器（§2.2 已说明可选容器引入路径）。
- **模块/包结构**：六个应用服务按功能域拆分为独立模块，模块间禁止直接调用（通过领域事件解耦），依赖方向单向（应用层→领域层），无循环依赖。

### 4. 设计一致性

**[通过]** 整体设计一致性好，上一轮审查（b_v4_diag_v1）的 8 个问题全部得到正确修复：

- **问题 1（BR-03 路怒链路）**：SensorType 已新增 MICROPHONE、ACOUSTIC；S2 已新增 AC_ADJUSTMENT + INFOTAINMENT_PLAYBACK 干预类型；路怒语音存证生命周期子节（§3.2）完整覆盖触发条件、录制范围、边缘加密存储、云端上传、90 天保留策略、BR-04 隐私边界衔接；§7.2 链路 E 描述了完整编排路径。✓
- **问题 2（§7.2 链路 B 矛盾）**：已统一为"RescueReportReadyEvent 路径，该事件由 DS-12 消费 EmergencyActivatedEvent 后产出"。✓
- **问题 3（远程车窗控制）**：S3 已新增 `controlVehicleWindow` 方法（含完整安全门控链）、`ControlVehicleWindowRequest` DTO；RescueOperation 已新增 `RemoteWindowControl` 变体。✓
- **问题 4（REAR_IR_CAMERA）**：SensorType 已新增该变体。✓
- **问题 5（BR-08 fail-safe）**：S1/S2 已订阅 SensorFaultEvent（标注 3 秒 SLA）；S4 已新增 `getOfflineVehicles` 方法及相关 DTO；S4 已订阅 SensorFaultEvent / VehicleMonitoringOfflineEvent。✓
- **问题 6（VehicleIgnitionOffLockedEvent）**：已补充至 §3.1 S1 事件订阅列表。✓
- **问题 7（PerformanceWarningEvent 生产链）**：已明确"DS-09 ScoringService 在每次评分计算完成后判断（low-score → 即时事件触发）并产出"。✓
- **问题 8（协作关系图 EventBus 节点）**：§7.1 图已重绘，新增领域事件总线 EventBus 节点作为路由中介。✓

跨文档一致性验证：
- §3.5 S5 订阅声明（RescueReportReadyEvent / FamilyManualRescueRequestedEvent / LifeDetectedEvent）与 §8.2（LifeDetectedEvent 驱动救援预判）和 §8.3（RescueReportReadyEvent 驱动 SOS 投递）时序图一致。✓
- §3.1 S1 订阅声明（VehicleIgnitionOffLockedEvent）与 §8.2 时序图触发活体检测一致。✓
- §3.2 S2 订阅声明（RiskDeterminedEvent / RiskResolvedEvent / LifeDetectedEvent / RageDeterminedEvent / SensorFaultEvent）与 §8.1、§8.2、§7.2 链路 E 一致。✓
- 六个应用服务的职责描述清晰，方法契约表中的输入/输出 DTO 均已在 §4 定义，无遗漏。✓

**[轻微]** §3.2 S2 协作关系的事件订阅列表未显式列出 OTA 升级状态事件（OTAUpgradeStartedEvent / OTAUpgradeCompletedEvent / OTAUpgradeRolledBackEvent），而 §3.6 和 §7.2 链路 D 明确说明 S2 订阅这些事件以管理 CAN 干预抑制。建议在 §3.2 订阅列表中补全以保持单一查阅点完整性。

### 5. 设计质量

**[通过]** 设计质量良好：
- **单一职责原则**：六个应用服务按功能域清晰划分（风险监测、干预执行、远程监护、车队管理、应急救援、OTA 管理），每个服务的职责描述明确无交叉。
- **抽象层次恰当**：应用层严格遵循"薄层"原则——不包含业务逻辑，仅负责编排、适配、事务管理和安全门控（§1 设计目标明确）。
- **契约先行**：`interface` 与 `class` 分离（决策 A1），支持测试 mock——验收测试可直接通过 entry 驱动全链路验证（§11 验收测试场景已覆盖三条核心路径）。
- **可测试性**：各服务对领域服务、仓储、端口的依赖均以 `interface` 声明（构造器注入），测试时可注入 mock 实现，无需依赖真实基础设施。
- **安全设计**：安全门控链（身份认证 → 角色校验 → 二次验证 → 权限校验）在应用层统一编排（§6.3），领域层保持纯粹。

**[轻微]** 以下为可优化但不阻塞的改进点：
1. §7.4 事务边界汇总表未包含 S3 新增的 `controlVehicleWindow` 方法（该方法不涉及事务写，但为保持表格完整性建议补充）。
2. `WindowControlOperation` 枚举（OPEN / CLOSE / PARTIAL_OPEN）与 `RescueOperation.RemoteWindowControl` 存在语义重叠——前者服务于家属远程车窗控制（S3），后者服务于应急救援授权操作（S5）。虽处于不同安全上下文，建议在注释或说明中区分以避免实现混淆。
3. DTO 中 `Array<(AlertType, RiskLevel)>` 等匿名元组语法在仓颉中可能需要显式定义为命名结构类型（如 `AlertRiskEntry`），属于实现阶段的语法适配，不影响设计审查结论。

## 修改要求（REJECTED 时存在）

无。本轮审查无严重或一般问题，设计方案 APPROVED。
