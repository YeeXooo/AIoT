# 应用层 OOD 设计方案 质量审查报告（b_v2 / v1）

> 审查对象：`a_v2_design_v1.md`（车载安全监测系统 应用层 OOD 设计方案）
> 审查轮次：第 2 轮（本产出已通过组件A内部审议，内部审议侧重技术可行性）
> 审查视角：需求响应充分度、事实错误/逻辑矛盾、深度完整性（含实际落地可行性）

---

## 一、审查总评

产出整体质量较高——接口契约、DTO 体系、错误枚举、时序图、事务边界均已建立，较第 1 轮有明显提升。但存在若干需求响应缺口、逻辑矛盾和类型定义遗漏，部分问题会阻塞下游接口层开发，需在进入下一阶段前修正。

---

## 二、问题清单

### 问题 1：S4 FleetManagementService 缺失车辆轨迹查询功能（需求响应缺口）

- **所在位置**：§3.4 IFleetManagementService 接口方法契约（全节无轨迹相关方法）
- **严重程度**：严重
- **问题描述**：需求文档明确将"车辆轨迹"列为 S4 FleetManagementService 的职责之一（`requirement.md` L40："大屏态势感知、车辆轨迹、统计报表"），但设计方案 §3.4 的四个接口方法（`getFatigueDistribution`、`drillDownHighRisk`、`generateReport`、`subscribePerformanceWarning`）无一涉及车辆轨迹查询。车队大屏需要按车辆/驾驶员查询历史轨迹点序列的能力，此功能在当前设计中完全缺失。
- **改进建议**：在 S4 接口契约中新增轨迹查询方法，如 `queryVehicleTrajectory(req: QueryTrajectoryRequest): Result<QueryTrajectoryResponse, AppError>`，输入含 VehicleId 或 DriverId + TimeRange，输出轨迹点序列（含 GPS 坐标 + 时间戳 + 车速）。在 §4.4 补充对应 DTO 定义。

---

### 问题 2：S5 领域事件订阅声明与时序图存在逻辑矛盾

- **所在位置**：§3.5 协作关系（L368）vs §8.3 路径 3 时序图（L1144）
- **严重程度**：严重
- **问题描述**：§3.5 明确声明 S5 EmergencyRescueServiceImpl 订阅的领域事件为"EmergencyActivatedEvent、FamilyManualRescueRequestedEvent"。但 §8.3 时序图显示 S5 实际接收的是 DS-12 产出的"RescueReportReadyEvent"，而非 EmergencyActivatedEvent 直接投递给 S5。两种说法相互矛盾——如果 S5 订阅 EmergencyActivatedEvent，时序图中不应由 DS-12 产出中间事件；如果 S5 实际订阅 RescueReportReadyEvent，§3.5 的声明则错误。实现阶段将无法确定正确的事件订阅列表。
- **改进建议**：统一事件订阅声明。若实际架构为 DS-12 消费 EmergencyActivatedEvent 后产出 RescueReportReadyEvent，则 §3.5 应声明 S5 订阅 RescueReportReadyEvent（而非 EmergencyActivatedEvent），并在协作说明中描述二级事件路由关系。若 S5 确实需要同时订阅 EmergencyActivatedEvent（例如用于救援预判计数），需在时序图和声明中明确两者的不同用途。

---

### 问题 3：路径 3 时序图未按需求指定链路包含 RiskMonitoringService

- **所在位置**：§8.3 路径 3 时序图全文
- **严重程度**：严重
- **问题描述**：需求文档明确指定路径 3 的链路为"RiskMonitoringService → EmergencyRescueService → RemoteGuardianshipService → InterventionService"（`requirement.md` L54）。但 §8.3 时序图中，碰撞冲击信号直接由加速度传感器送入领域层 DS-06（EmergencyResponseService），全程未出现 RiskMonitoringService（S1）的参与，也不符合需求指定的组件顺序。如果设计意图是碰撞检测不经 S1 而走独立领域通道，此决策需明确记录；如果是遗漏，则实现阶段将无法正确串联全链路。
- **改进建议**：二选一：(a) 在时序图中补充 S1 作为碰撞信号的编排入口（如 S1.processSensorReading 接收加速度传感器数据并委托 DS-06），使流程符合需求指定的链路；或 (b) 在 §10 设计决策中新增一项决策，说明碰撞失能判定为什么不经 S1 编排而是走独立领域通道，并同步更新需求文档中的路径描述。

---

### 问题 4：S1 风险监测未显式覆盖需求中的"分心"与"异常驾驶行为"类别

- **所在位置**：§3.1 接口契约与职责描述全文、§8.1 时序图
- **严重程度**：一般
- **问题描述**：需求明确将 S1 职责描述为"疲劳/分心/异常驾驶行为实时监测与风险评分"（`requirement.md` L37）。当前设计在 §3.1 职责描述、方法契约表、§8.1 时序图中仅明确涉及疲劳（Fatigue）和活体遗留（Life Detection）两类风险。虽然 RiskDeterminationService 作为领域层流式融合判定门面理论上可覆盖所有风险类型，但应用层设计中未有任何地方显式提及"分心判定"（Distraction）或"异常驾驶行为判定"（Abnormal Driving），也未出现对应的 AlertType 示例——这使得需求与设计的覆盖关系无法追溯验证。
- **改进建议**：在 §3.1 的职责描述或依赖协作说明中补充：RiskDeterminationService 下辖的子判定服务中应包含分心判定服务（如 DistractionDeterminationService）和异常驾驶行为判定服务（如 AbnormalDrivingDeterminationService），或在方法说明中明确 flow 覆盖三类风险，确保需求项可追溯。

---

### 问题 5：多个 DTO 中使用的枚举类型缺少显式定义

- **所在位置**：§4.3 TriggerManualRescueResponse（L575）、§4.5 RescueRecordSummary（L663–664）
- **严重程度**：一般
- **问题描述**：以下枚举类型在 DTO 字段说明中出现了变体值，但未像 `OverrideResult`、`MediaSessionType`、`DataFreshness` 等那样提供完整的 `enum` 类型定义块：

  | 类型 | 使用位置 | 已出现的变体值 |
  |------|---------|-------------|
  | `RescueRequestStatus` | §4.3 `TriggerManualRescueResponse.status` | `PENDING` / `CONFIRMED` |
  | `RescueTriggerType` | §4.5 `RescueRecordSummary.triggerType` | `COLLISION_DISABILITY` / `MANUAL` / `LIFE_DETECTION` |
  | `RescueRecordStatus` | §4.5 `RescueRecordSummary.status` | `SENT` / `CONFIRMED` / `PENDING_RETRY` / `MANUAL_ESCALATION` |

  接口层开发人员需知道这些类型的完整变体集合才能编写正确的 switch/match 逻辑，仅看字段注释无法获得完整定义。此问题在内部审议中已被标记（a_v2_review_v1.md §4.1 L71–80），但在提交审查的产出版本中仍未修正。
- **改进建议**：参照 §4.2 中 `OverrideResult` 的定义格式，为 `RescueRequestStatus`（在 §4.3 内）、`RescueTriggerType` 和 `RescueRecordStatus`（在 §4.5 内）补充完整 `enum` 定义块。

---

### 问题 6：多个 DTO 结构类型缺少字段级定义

- **所在位置**：§4.4 GetFatigueDistributionResponse（L585）、§4.6 CreateUpgradeTaskRequest（L672）
- **严重程度**：一般
- **问题描述**：以下结构类型在 DTO 中被引用但未给出字段级定义：

  | 类型 | 使用位置 | 问题 |
  |------|---------|------|
  | `HeatmapPoint` | §4.4 `GetFatigueDistributionResponse.heatmapData` | 完全未定义——未出现在 §4.4、§5 跨层类型、或 §5.5 领域层引用表中 |
  | `UpgradeOptions` | §4.6 `CreateUpgradeTaskRequest.upgradeOptions` | 仅有一行文字说明"可选升级参数（分批策略、时间窗口等）"，无字段结构定义 |

  作为 DTO 的组成字段，这些类型必须有明确的字段级定义，否则接口层无法构造/解析对应的请求或响应。其中 `UpgradeOptions` 在内部审议中已被标记，但仍未修正。

- **改进建议**：在 §4.4 中新增 `HeatmapPoint` 字段定义（至少包含 `latitude: Float64`、`longitude: Float64`、`riskIntensity: Float64` 等）；在 §4.6 中补充 `UpgradeOptions` 字段定义（如 `batchStrategy: Option<BatchStrategy>`、`scheduledWindow: Option<TimeRange>` 等）。

---

### 问题 7：S6 批量写操作缺少幂等性机制

- **所在位置**：§3.6 createUpgradeTask 方法（L434）、§7.4 事务边界表（L998）
- **严重程度**：一般
- **问题描述**：`createUpgradeTask` 是一个批量写操作（可能涉及多辆车），且当前设计采用"逐条创建+逐条提交"策略。如果调用方因网络抖动重试同一请求，将产生重复的升级任务。设计全文未提及任何幂等性保障机制（如请求级幂等键 idempotencyKey），也未说明重复请求的业务语义（应视为幂等返回已有任务，还是视为新任务创建？）。对于 OTA 升级这种高危批量操作，幂等性缺失可能导致多辆车被重复刷写固件。
- **改进建议**：在 `CreateUpgradeTaskRequest` 中增加 `idempotencyKey: String` 字段，在 S6 方法说明中补充幂等性语义：同一 `idempotencyKey` 的重复请求应返回已创建的任务列表而非重复创建。在 §6.2 错误策略表中增加对应的幂等冲突处理项。

---

### 问题 8：S6 批量操作缺少车辆数量上限约束

- **所在位置**：§3.6 createUpgradeTask 方法（L434）、§4.6 CreateUpgradeTaskRequest（L670）
- **严重程度**：轻微
- **问题描述**：`CreateUpgradeTaskRequest.targetVehicleIds` 类型为 `Array<VehicleId>`，无上限约束说明。在实际部署中，一次升级任务可能被错误地指定为包含数千辆车，导致数据库事务持有时间过长、超时风险升高。设计方案未对此做任何防御性约束说明。
- **改进建议**：在方法说明或 DTO 字段说明中标注批量上限（如"单次最多 100 辆车"），并在异常处理策略中增加超限错误变体（如 `AppError.BatchSizeExceeded(maxLimit)`）。

---

### 问题 9：S3 WebSocket 心跳超时后服务端处理不明确

- **所在位置**：§3.3 WebSocket 连接生命周期管理表（L241–242）
- **严重程度**：轻微
- **问题描述**：心跳策略描述"云端每 30s 发送 PING 帧，家属 APP 须在 10s 内回复 PONG。连续 3 次未收到 PONG 视为连接断开。"但未说明视为断开后服务端采取什么动作——是主动关闭 TCP 连接、仅标记内部状态为断开、还是等待客户端下次操作再响应错误？不同处理方式影响客户端重连时的状态恢复逻辑（如服务端是否还在监听旧端口、旧连接资源何时释放）。
- **改进建议**：在生命周期管理表中明确"连接断开判定后的服务端动作"：①服务端主动发送 CLOSE 帧关闭 WebSocket 连接；②释放该连接关联的推送流和订阅关系（等同于主动断开的清理逻辑）；③将该家属账户的连接映射清除，允许后续重连。

---

### 问题 10：S6 批量操作事务策略段落位置不当

- **所在位置**：§3.5 节末尾 > 块引用（L383）
- **严重程度**：轻微
- **问题描述**：S6 `createUpgradeTask` 的批量操作事务策略（逐条创建+逐条提交）描述位于 §3.5（S5 EmergencyRescueService）的 `verifyRescueToken` 事务边界说明块之后，而非 §3.6（S6 OTAManagementService）内。读者在 S5 章节末尾看到 S6 的事务策略会感到困惑，也容易在后续维护中被遗漏。内部审议已标记此问题（a_v2_review_v1.md §4.2 L82），但产出中尚未修正。
- **改进建议**：将该段落移至 §3.6 的 `createUpgradeTask` 方法说明之后，或合并到 §7.4 事务边界汇总表中 `createUpgradeTask` 对应的行。

---

## 三、整体评价

产出在接口契约形式化、DTO 体系搭建、错误枚举定义、事务边界标注方面达到了较好的完整度，相比第 1 轮有实质性提升。但上述问题中，**问题 1（缺失车辆轨迹）、问题 2（事件订阅矛盾）、问题 3（路径 3 偏离需求）** 属于需求响应层面的关键缺口或事实矛盾，需优先修正后方可进入后续详细设计或实现阶段。问题 5/6（类型定义遗漏）直接影响下游接口层开发的可操作性，也应在下一轮中一并解决。

---

## 四、修订说明（v1）

首轮审查，无前序质询反馈。
