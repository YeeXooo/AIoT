# 应用层 OOD 设计方案 质量审查报告（b_v3 / v1）

> 审查对象：`a_v3_copy_from_v2.md`（第 3 轮迭代产出，基于 b_v2 审查报告修订）
> 审查视角：需求响应充分度、事实错误 / 逻辑矛盾、深度与完整性、实际落地可行性（接口是否可直接指导编码、边界条件是否已考虑）
> 内部审议已覆盖维度（不再重复）：技术可行性、领域层引用正确性、仓颉语言语法约定

---

## 审查发现

### 问题 1：S2 协作关系中事件订阅声明与 §8.2 时序图存在逻辑矛盾

- **问题描述**：§3.2（L190）S2 InterventionServiceImpl 的协作关系声明订阅 `RiskDeterminedEvent / RiskResolvedEvent`，但 §8.2（L1133）路径 2 时序图中 S2 直接接收 `LifeDetectedEvent` 并生成干预指令。`LifeDetectedEvent` 既未出现在 S2 的订阅列表中，也未通过 AlertPersistenceService 转为 `AlertTriggeredEvent` 后再投递给 S2（时序图中 LifeDetectedEvent 是单独路由到 S2 的，与 Alarm 产出 AlertTriggeredEvent 走的是并行路径）。
- **所在位置**：§3.2 L190 vs §8.2 L1133
- **严重程度**：严重
- **改进建议**：二选一修正——（1）在 §3.2 事件订阅列表中补充 `LifeDetectedEvent`，并说明其触发干预指令的语义；或（2）修改 §8.2 时序图使 S2 通过 AlertTriggeredEvent（经 Alarm）而非 LifeDetectedEvent 获取干预触发信号。

### 问题 2：VerifyRescueTokenResponse 中 TokenVerifyResult::INVALID 与 AppError 错误体系矛盾

- **问题描述**：§3.5 `verifyRescueToken` 方法返回 `Result<VerifyRescueTokenResponse, AppError>`，其中 `VerifyRescueTokenResponse` 含 `result: TokenVerifyResult`（值为 `VALID` / `INVALID`）。但 §3.5 说明和 §6.1 AppError 枚举已将所有失败场景（过期、已消费、并发、角色不匹配、车辆不匹配）映射为 `AccessDenied(reason)` 错误变体。`TokenVerifyResult::INVALID` 无任何使用场景——所有"无效"情形均已通过 `Err(AppError)` 路径返回，导致该变体成为设计冗余/死代码。
- **所在位置**：§4.5 VerifyRescueTokenResponse（L673–674）、TokenVerifyResult 枚举定义（L676）
- **严重程度**：严重
- **改进建议**：两种修正方向——（1）移除 TokenVerifyResult::INVALID 变体，仅保留 `VALID`（成功路径始终返回 VALID），或（2）将 TokenVerifyResult 扩展为包含详细失败原因的结构，统一通过 Ok 路径返回所有验证结果，此时 `verifyRescueToken` 返回类型改为 `TokenVerifyResult` 而非 `Result<T, AppError>`。推荐方案（1），与现有 AppError 体系统一。

### 问题 3：ReportData 类型完全未定义

- **问题描述**：§4.4 GenerateReportResponse 含 `reportData: ReportData` 字段（L625），标注为"报告结构化数据"。但该类型在全文（§4 DTO 定义、§5 跨层类型定义、§5.5 领域层引用类型表）中均未出现定义。接口层开发人员无法确定报告数据的结构（包含哪些字段、嵌套结构、数据类型），这将直接阻塞下游消费者（车队大屏、报告下载 API）的开发。
- **所在位置**：§4.4 GenerateReportResponse（L623–627）
- **严重程度**：严重
- **改进建议**：在 §4.4 中补充 `ReportData` 结构类型定义，至少包含：驾驶行为评分 Summary、风险分布 Map、各维度扣分明细（疲劳/分心/异常驾驶）、对比上一周期的变化趋势等字段。如 ReportData 已在领域层定义，应列入 §5.5 领域层引用类型表并标注引用位置。

### 问题 4：决策 A4 修订理由中的内部交叉引用使用了已失效的旧版章节号

- **问题描述**：§10 决策 A4（L1256）引用 `§5.2 路径 2`、`§5.3 路径 3`、`§4.3 明确的"应用服务间零直接调用"原则`。在当前文档（a_v3）结构下，路径 2 位于 §8.2、路径 3 位于 §8.3，"应用服务间零直接调用"原则位于 §7.3。这三个交叉引用全部断裂，读者无法定位到正确位置。
- **所在位置**：§10 决策 A4 修订理由段落（L1256）
- **严重程度**：一般
- **改进建议**：将引用更新为当前文档实际章节号：`§5.2 → §8.2`、`§5.3 → §8.3`、`§4.3 → §7.3`。建议对全文进行一次交叉引用一致性检查（搜索所有 `§数字` 模式引用），避免 v2→v3 章节重组后的残留引用。

### 问题 5：应用服务编排中对领域服务方法的调用签名覆盖率不一致

- **问题描述**：设计文档描述应用服务编排逻辑时，有的领域服务方法给出了具体方法名（如 §3.2 `DS-07.handleOverride`、§3.4 `ReportGenerationService.generateReport`），多数场景仅以自然语言描述（如 §3.1 `processSensorReading` 仅说"委托 RiskDeterminationService 执行流式融合判定"，未给出方法名；§3.1 `startLifeDetection` 仅说"委托 LifeDetectionService 在 60s 窗口内执行判定"）。这种不一致使开发人员在实现时需自行推断应调用的领域层方法名，增加了接口理解成本。
- **所在位置**：§3.1 方法契约表第 2 行（processSensorReading，L156）、第 3 行（startLifeDetection，L157）；§3.4 方法契约表第 1 行（getFatigueDistribution，L319）；§3.5 方法契约表（triggerManualRescue，L256 调用 DS-12.triggerManualRescue 已明确 ✓，但 confirmSOSReport、issueRescueToken 等方法仅说"更新救援记录状态"、"生成 RescueAuthorizationToken" 未指明领域服务方法）
- **严重程度**：一般
- **改进建议**：为每个应用服务方法的说明补充其所调用的领域服务方法签名（至少方法名），格式统一为 `委托 {领域服务名}.{方法名}`。如 §3.1 `processSensorReading` 中补为 `委托 RiskDeterminationService.determineRisk(session, reading)`。

### 问题 6：S5 verifyRescueToken 对授权操作集合的校验语义未定义

- **问题描述**：§3.5 `issueRescueToken` 签发凭证时指定了 `authorizedOperations: Array<RescueOperation>`（如 `RemoteUnlock` / `HealthProfileAccess`），但 §3.5 `verifyRescueToken` 的校验说明仅列举"未过期、未消费、目标匹配、角色匹配"四项，未提及对 `requestedOperation` 是否在凭证的 `authorizedOperations` 集合内的校验。这一遗漏导致持有 `RemoteUnlock` 凭证的攻击者可尝试执行 `HealthProfileAccess` 操作——存在权限跨越风险。
- **所在位置**：§3.5 接口方法契约 `verifyRescueToken` 行（L381）及 §4.5 VerifyRescueTokenRequest（L668–671）、IssueRescueTokenRequest（L658–661）
- **严重程度**：一般
- **改进建议**：在 §3.5 `verifyRescueToken` 说明中增加第 5 项校验：**操作匹配**（requestedOperation 是否在凭证的 authorizedOperations 集合内）。不匹配时返回 `AppError.AccessDenied(OperationNotAuthorized)`，并在 §6.1 AccessDenialReason 枚举中新增 `OperationNotAuthorized` 变体。

### 问题 7：SubscribeDriverStatus 初始化快照在无活跃行程时的行为未定义

- **问题描述**：§3.3 `subscribeDriverStatus` 返回 `SubscribeDriverStatusResponse`（含 `initialSnapshot: DriverStatusSnapshot`），但未说明当驾驶员无活跃行程时（TripStatus 为 NotStarted 或 Completed）初始快照应包含什么内容。DriverStatusSnapshot 含 `gpsLocation`、`speed`、`activeAlertLevels`、`physiologicalSummary` 等字段——无行程时这些字段是返回空值、默认值还是上一个行程的残留数据？该边界条件直接影响到家属 APP 前端的状态展示逻辑。
- **所在位置**：§3.3 订阅方法说明（L251）、§4.3 SubscribeDriverStatusResponse（L548–549）、§5.2 DriverStatusSnapshot 定义（L770–780）
- **严重程度**：一般
- **改进建议**：在 §3.3 补充说明：无活跃行程时 `initialSnapshot` 中 `tripStatus` 设为 `NOT_STARTED`/`COMPLETED`，`activeAlertLevels` 为空 Map，`gpsLocation` 和 `speed` 设为上一次已知值或 `Option.None`，`physiologicalSummary` 设为 `Option.None`。同步更新 §5.2 DriverStatusSnapshot 的 `gpsLocation`、`speed` 字段类型为 `Option<T>`，使 API 语义更精确。

### 问题 8：§7.1 协作关系图中缺少领域事件总线（EventBus）中介节点

- **问题描述**：§7.1 协作关系总览图（L905–925）以 ASCII 图展示六个应用服务间的依赖关系，但图中箭头直接从 S1 指向 S2/S3/S4、S3 指向 S5、S2 指向 S6，未引入 EventBus 作为路由中介。这与 §7.3 重申的"应用服务间零直接调用"原则及 §8 时序图中显式引入 EventBus 的做法不一致。读者可能误解服务间存在直接编译期依赖或方法调用。
- **所在位置**：§7.1 协作关系总览图（L905–925）
- **严重程度**：轻微
- **改进建议**：在 §7.1 图中增加 `领域事件总线（EventBus）` 节点，所有服务间连线改为经由 EventBus 中转。或在图注中明确说明"箭头表示事件流向，非直接方法调用依赖"。

### 问题 9：S6 createUpgradeTask 批量逐条提交的事务隔离问题未说明

- **问题描述**：§3.6 及 §7.4 说明 `createUpgradeTask` 采用"逐条创建 + 逐条提交"策略，但未明确：逐条提交过程中，已提交成功的任务是否在整体操作完成前对 `queryUpgradeProgress` 可见？如果可见，调用方可能在批量操作未完成时查询到部分结果（可被解释为正常行为，但应明确说明以指导 API 消费方编程范式）。反之若不可见（需等整体操作完成），则需说明读取隔离机制。
- **所在位置**：§3.6 `createUpgradeTask` 方法说明及批量操作事务策略块引用（L435、L440）、§7.4 事务边界表（L1039）
- **严重程度**：轻微
- **改进建议**：在批量操作事务策略段中补充说明：逐条提交后，已成功创建的升级任务立即可被 `queryUpgradeProgress` 查询到；调用方可通过 `CreateUpgradeTaskResponse.createdTaskIds` 获知本次操作已成功创建的子集。

---

## 整体评价

经过第 2 轮修订（b_v2 → a_v3），产出已补全了轨迹查询、幂等性、批量约束、事件订阅矛盾修正、时序图 S1 引入、枚举定义等重大缺口，整体质量显著提升。DTO 覆盖率达到较高水平，事务边界和错误枚举也具备指导编码的基础。

当前 v3 版本存在的**3 个严重问题**集中在：逻辑矛盾（S2 订阅声明与时序图不一致、TokenVerifyResult 死代码）和类型缺失（ReportData），这三个问题若不在本迭代修复，将直接导致编码阶段出现理解歧义或接口数据格式阻塞。"一般"和"轻微"级别的 6 个问题主要涉及交叉引用失效、语义缺失和边界条件不足，建议在本迭代一并修复以提升设计的完整性。

整体而言，当前产出在 DTO 完备性、事务边界、安全门控、事件路由等方面已达到可进行细化设计的质量水平，但尚需对上述 3 个严重问题进行修复后方可进入编码阶段。

---

## 修订说明（v1）

首轮审查，无历史质询意见。
