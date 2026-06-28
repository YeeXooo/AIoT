# 应用层 OOD 设计方案质量审查报告（b_v5_diag_v2）

> 审查对象：`a_v5_copy_from_v4.md`（v5 迭代产出）
> 审查视角：需求响应充分度、事实错误与逻辑矛盾、深度与完整性、可落地性
> 审查范围：聚焦内部审议未充分覆盖的维度（内部审议已确认类型系统可行性、标准库覆盖、语言特性可行性、设计一致性）

---

## 一、需求产出完整性核验

以下逐一核验需求文档（`requirement.md`）「输出格式要求」中指定的三大产出项：

| 序号 | 产出要求 | 存在性 | 格式符合性 | 核验依据 |
|:--:|---------|:--:|:--:|---------|
| 1 | 每个应用服务的接口方法签名 + 参数/返回值定义 + 异常处理策略 | ✓ | ✓ | §3.1–§3.6 各服务均已提供含方法名、输入/输出 DTO 类型、返回类型（`Result<T, AppError>`）、事务标注的完整契约表；§4 提供完整 DTO 定义；§6 提供统一 AppError 枚举与逐服务错误处理策略表 |
| 2 | 服务协作图（组件图 + 说明文字） | ✓ | ✓ | §7.1 提供 ASCII 组件图（含 EventBus 节点）与说明文字；§7.2 提供五条协作链路分解 |
| 3 | 三条核心路径的时序图（Mermaid sequenceDiagram） | ✓ | ✓ | §8.1（路径1）、§8.2（路径2）、§8.3（路径3）均以完整 Mermaid 语法提供 |

---

## 二、逐服务职责-方法覆盖核验

以下按需求文档中各应用服务职责描述，逐项核验设计产出中的接口方法覆盖情况。

### S1 — RiskMonitoringService

| 需求职责 | 覆盖状态 | 对应方法 / 机制 |
|---------|:--:|---------|
| AI 风险判定引擎 | ✓ | `processSensorReading` → RiskDeterminationService |
| 疲劳实时监测 | ✓ | 协作关系中声明 FatigueDeterminationService（§3.1 L145） |
| 分心实时监测 | ✓ | 协作关系中声明 DistractionDeterminationService（§3.1 L145） |
| 异常驾驶行为实时监测 | ✓ | 协作关系中声明 AbnormalDrivingDeterminationService（§3.1 L145） |
| 路怒实时监测 | ✓ | 协作关系中声明 RageDeterminationService（§3.2 L191）、SensorType 含 MICROPHONE/ACOUSTIC |
| 风险评分 | ✓ | RiskDeterminedEvent 携带 RiskLevel；RiskResolvedEvent 携带解除信息 |
| 活体遗留检测 | ✓ | `startLifeDetection` → LifeDetectionService |
| 碰撞失能判定 | ✓ | §8.3 时序图中 S1 接收碰撞冲击信号并委托 DS-06 |

### S2 — InterventionService

| 需求职责 | 覆盖状态 | 对应方法 / 机制 |
|---------|:--:|---------|
| 闭环干预与反馈 | ✓ | `reportOverride`（驾驶员覆盖信号处理） |
| 分级告警 — 车内声光 | ✓ | §8.1 中 AMBIENT_LIGHT_COLOR、HAZARD 干预指令 |
| 分级告警 — 语音 | ✓ | §8.1 中 VOICE 干预指令 |
| 分级告警 — 安全带预紧 | 域层依赖 | InterventionType 由领域层定义（§5.5），应用层编排领域层产出的干预指令。应用层设计中未显式提及此类型，但其存在性取决于领域层 InterventionType 枚举——非应用层设计缺陷 |
| 分级告警 — 紧急制动辅助 | 域层依赖 | 同上。CAN_DECELERATION_REQUEST（§3.6、§8.1）为减速相关指令，是否等同于"紧急制动辅助"取决于领域层语义 |
| 路怒判定后干预编排 | ✓ | AC_ADJUSTMENT + INFOTAINMENT_PLAYBACK（§3.2 L185）；路怒语音存证生命周期（§3.2 L209–220） |
| 查询干预状态 | ✓ | `queryInterventionStatus` |
| 查询干预指令历史 | ✓ | `queryInterventionHistory` |

> **说明**：S2 的干预类型由领域层 InterventionService（DS-07）产出，应用层负责编排执行。应用层设计中提及的干预指令类型（AMBIENT_LIGHT_COLOR、VOICE、VIBRATION、HAZARD、CAN_DECELERATION_REQUEST、AC_ADJUSTMENT、INFOTAINMENT_PLAYBACK）已覆盖需求示例中的"车内声光"和"语音"；"安全带预紧"和"紧急制动辅助"的具体类型是否被领域层 InterventionType 枚举包含，需在领域层 OOD 审查中确认。应用层设计在此处不存在方法遗漏——其 `queryInterventionStatus` 和 `queryInterventionHistory` 方法可返回领域层定义的任意干预类型。

### S3 — RemoteGuardianshipService

| 需求职责 | 覆盖状态 | 对应方法 / 机制 |
|---------|:--:|---------|
| 实时位置 | ✓ | `subscribeDriverStatus` → DriverStatusSnapshot.gpsLocation（§5.2） |
| 车内视频 | ✓ | `requestMediaSession`（MediaSessionType::VIDEO） |
| 告警推送 | ✓ | WebSocket 订阅机制 + AlertTriggeredEvent 推送（§3.3） |
| 音视频对讲 | ✓ | `requestMediaSession`（MediaSessionType::AUDIO） |
| 远程车窗控制 | ✓ | `controlVehicleWindow`（§3.3 L272） |
| 家属手动救援触发 | ✓ | `triggerManualRescue`（§3.3 L271） |
| 告警推送偏好配置 | ✓ | `updateNotificationPreference`（§3.3 L270） |
| WebSocket 连接管理 | ✓ | 生命周期表（§3.3 L249–260）覆盖连接建立、心跳、意外断开、主动断开、连接限制、离线补推 |

### S4 — FleetManagementService

| 需求职责 | 覆盖状态 | 对应方法 / 机制 |
|---------|:--:|---------|
| 大屏态势感知 — 疲劳指数分布 | ✓ | `getFatigueDistribution` |
| 大屏态势感知 — 风险热力图 | ✓ | heatmapData（§4.4 GetFatigueDistributionResponse） |
| 大屏态势感知 — 脱线车辆 | ✓ | `getOfflineVehicles`（§3.4 L336） |
| 大屏态势感知 — 钻取查询 | ✓ | `drillDownHighRisk`（§3.4 L338） |
| 车辆轨迹查询 | ✓ | `queryVehicleTrajectory`（§3.4 L337） |
| 统计报表生成 | ✓ | `generateReport`（§3.4 L339） |
| 绩效预警订阅 | ✓ | `subscribePerformanceWarning`（§3.4 L340） |

### S5 — EmergencyRescueService

| 需求职责 | 覆盖状态 | 对应方法 / 机制 |
|---------|:--:|---------|
| 碰撞失能自动 SOS | ✓ | EmergencyActivatedEvent → RescueReportReadyEvent → RescueReportPort（§8.3） |
| 位置上报 | ✓ | RescueReport 含 GPS + VehicleStateSnapshot（§8.3 L1274） |
| SOS 报告确认 | ✓ | `confirmSOSReport` |
| 救援授权凭证管理 | ✓ | `issueRescueToken` + `verifyRescueToken` |
| 救援历史查询 | ✓ | `queryRescueHistory` |
| 家属手动救援联动 | ✓ | triggerManualRescue 委托 DS-12（§3.3 L271） |
| SOS 重试策略 | ✓ | 指数退避 1s/2s/4s/8s/16s、最大 5 次、转人工干预（§3.5 L371–382） |

### S6 — OTAManagementService

| 需求职责 | 覆盖状态 | 对应方法 / 机制 |
|---------|:--:|---------|
| 升级包分发 | ✓ | `createUpgradeTask` |
| 升级策略 | ✓ | UpgradeOptions（BatchStrategy、scheduledWindow、forceUpgrade） |
| 回滚机制 | ✓ | `triggerRollback` + OTA 回滚期间 CAN 干预恢复策略（§3.6 L428–438） |
| 升级进度查询 | ✓ | `queryUpgradeProgress` |
| 升级历史查询 | ✓ | `queryUpgradeHistory` |

---

## 三、时序图路径参与者完整性核验

逐条对照需求文档指定的时序路径参与者顺序：

| 路径 | 需求指定顺序 | 设计时序图参与者（应用服务层） | 顺序一致性 |
|------|------------|---------------------------|:--:|
| 路径1 | S1→S2→S3 | §8.1：S1→DS07(领域层 InterventionService)→S2→S3 | ✓（S1 先行判定，S2 编排干预，S3 推送家属 APP） |
| 路径2 | S1→S2→S3→S5 | §8.2：S1→DS05→S2 + S3 + S5（三者并行触发） | ✓（S1 触发检测，S2 执行 HMI 干预，S3 推送 APP 报警，S5 救援预判） |
| 路径3 | S1→S5→S3→S2 | §8.3：S1→DS06→DS12→S5→S3→S2 | ✓（S1 接收碰撞信号，S5 投递 SOS，S3 自动激活家属端，S2 通知 HMI） |

所有三条路径的指定参与者均已出现在时序图中，且触发顺序与需求指定一致。

---

## 四、第 4 轮迭代严重问题修复状态逐条核验

| 轮次/编号 | 问题描述 | 修复状态 | 核验依据 |
|:--:|---------|:--:|---------|
| 第4轮#1（严重） | BR-03 路怒判定与调节链路覆盖率不足 | ✓ 已修复 | SensorType 含 MICROPHONE/ACOUSTIC/REAR_IR_CAMERA（§5.1 L820）；S2 含 AC_ADJUSTMENT + INFOTAINMENT_PLAYBACK 干预编排（§3.2 L185）；新增路怒语音存证生命周期子节（§3.2 L209–220）；§7.2 新增链路 E（L1069–1089）；§3.2 事件订阅列表含 RageDeterminedEvent（L191） |
| 第4轮#2（严重） | §7.2 链路 B 描述与 S5 协作声明及 §8.3 时序图矛盾 | ✓ 已修复 | §7.2 链路 B 中 S5 分支统一为"通过 RescueReportReadyEvent 路径，该事件由 DS-12 消费 EmergencyActivatedEvent 后产出"（L1029） |
| 第4轮#3（一般） | 远程车窗控制未在接口中体现 | ✓ 已修复 | S3 新增 `controlVehicleWindow`（§3.3 L272）及对应 DTO（§4.3 L600–606）；RescueOperation 含 RemoteWindowControl（§4.5 L719） |
| 第4轮#4（一般） | SensorType 缺少后排红外摄像头 | ✓ 已修复 | §5.1 SensorType 含 REAR_IR_CAMERA（L820，与#1 合并修改） |
| 第4轮#5（一般） | BR-08 失效保护编排链路缺失 | ✓ 已修复 | S1/S2 事件订阅含 SensorFaultEvent（§3.1 L147、§3.2 L191），标注 3 秒 SLA；S4 新增 `getOfflineVehicles`（§3.4 L336），事件订阅含 SensorFaultEvent/VehicleMonitoringOfflineEvent |
| 第4轮#6（一般） | VehicleIgnitionOffLockedEvent 未出现在 S1 订阅列表 | ✓ 已修复 | §3.1 S1 协作关系事件列表含 VehicleIgnitionOffLockedEvent（L147） |
| 第4轮#7（一般） | PerformanceWarningEvent 生产侧未说明 | ✓ 已修复 | §3.4 协作关系中补充"由 DS-09 ScoringService 在每次评分完成后判断并产出"（L327） |
| 第4轮#8（轻微） | §7.1 协作图缺 EventBus 节点 | ✓ 已修复 | §7.1 图中已新增 EventBus 节点（L973），图注说明箭头为事件路由方向 |

结论：第 4 轮迭代的 **8 个问题已全部修复**，其中 2 个严重级别问题经逐条核验确认修复到位。

---

## 五、审查发现

### 问题 1：`startLifeDetection` 公开方法调用路径与事件驱动触发路径未明确界定

- **问题描述**：§3.1 将 `startLifeDetection` 列为 IRiskMonitoringService 的公开方法契约（"车辆熄火落锁后，启动活体检测会话"），但 §8.2 时序图显示活体检测的触发路径为 VehicleIgnitionOffLockedEvent → EventBus → S1 → LifeDetectionService。设计未明确车辆 ECU 在检测到熄火落锁后应直接调用该方法，还是应发布 VehicleIgnitionOffLockedEvent 由事件总线触发。两条路径并存会造成实现歧义——若边缘侧开发人员选择直接调用而绕过事件发布，则其他订阅 VehicleIgnitionOffLockedEvent 的消费者（如果有）会丢失该事件。
- **所在位置**：§3.1 接口方法契约表 `startLifeDetection` 行（L157） vs §8.2 时序图（L1203–1205） vs §3.1 协作关系事件订阅列表（L147）
- **严重程度**：一般
- **改进建议**：二选一明确——（1）若 `startLifeDetection` 仅作为事件处理器内部方法，应从公开接口契约中移除或在说明中标注"仅供内部事件驱动调用，外部不应直接调用"；或（2）若保留为公开方法，则应在 §8.2 时序图中改为外部调用方直接调用 `startLifeDetection`，而非经 EventBus 路由。

---

### 问题 2：`BatchStrategy` 类型完全未定义

- **问题描述**：§4.6 UpgradeOptions 结构类型中 `batchStrategy: Option<BatchStrategy>` 引用了 `BatchStrategy` 类型，但该类型在全文任何位置（含 §4 DTO 定义、§5 跨层类型定义、§5.5 领域层引用类型表）均未给出定义。下游开发者在实现 `createUpgradeTask` 时无法确定分批策略的数据结构。
- **所在位置**：§4.6 UpgradeOptions（L763）
- **严重程度**：一般
- **改进建议**：在 §4.6 UpgradeOptions 之后补充 `BatchStrategy` 的枚举或结构类型定义（如按车型分批 `BY_MODEL`、按地域分批 `BY_REGION`、按批次大小分批 `BY_BATCH_SIZE(size: UInt32)`），或将其列入 §5.5 领域层引用类型表并标注领域层定义位置。

---

### 问题 3：`controlVehicleWindow` 异步指令执行-确认语义缺失

- **问题描述**：S3 `controlVehicleWindow` 方法返回 `Result<Unit, AppError>`（§3.3 L272，§4.3 ControlVehicleWindowRequest L600–604），说明中描述为"通过 IoTDA 下发 CAN 指令至车辆"。车窗物理动作为异步过程——IoTDA 下发指令成功不代表车窗已执行到位。当前设计将"指令下发成功"与"车窗操作完成"混同为同一个 `Unit` 返回值，前端无法区分以下场景：（a）指令已下发但车辆离线未执行；（b）指令已下发且车窗正在运动中；（c）车窗已到达目标位置。缺少状态确认回调或轮询查询机制。
- **所在位置**：§3.3 接口方法契约 `controlVehicleWindow` 行（L272）、§4.3 ControlVehicleWindowRequest（L600–604）
- **严重程度**：一般
- **改进建议**：（1）在方法说明中明确返回值语义——`Ok(Unit)` 表示"指令已成功下发至 IoTDA"而非"车窗已到位"；（2）在 S3 方法契约表中新增 `queryWindowStatus` 查询方法，或在 DriverStatusSnapshot 中增加车窗位置字段以支持前端轮询确认；（3）补充车窗控制超时未确认的异常处理策略（如 30s 内未收到 CAN Ack 返回 `AppError.WindowControlTimeout`）。

---

### 问题 4：运营管理界面 DTO 缺少人类可读标识字段

- **问题描述**：面向车队大屏和救援中心的多个 DTO 仅包含机器标识（VehicleId、DriverId），不含车牌号（licensePlate）和驾驶员姓名（driverName）等人类可读字段。这导致前端大屏在展示脱线车辆列表（OfflineVehicleInfo）、高危驾驶员摘要（HighRiskDriverSummary）、救援记录摘要（RescueRecordSummary）等内容时，必须额外调用其他服务查询车牌号和姓名，增加了接口层的调用次数和复杂度。从实际落地视角看，运营大屏的核心场景（态势感知、快速定位）高度依赖车牌号和驾驶员姓名直接可见。
- **所在位置**：§4.4 OfflineVehicleInfo（L627–632）、HighRiskDriverSummary（L650–654）、§4.5 RescueRecordSummary（L744–748）
- **严重程度**：一般
- **改进建议**：在上述 DTO 中分别增加 `licensePlate: String` 和 `driverName: String` 字段（若已在领域层定义则为引用），使前端可直接渲染而无需二次查询。若出于数据最小化原则不愿在应用层 DTO 中包含，则至少应在设计决策中说明理由。

---

### 问题 5：CQRS 读模型访问机制在应用层未定义

- **问题描述**：S1 `queryAlertHistory`（§3.1 L159）和 S4 `queryVehicleTrajectory`（§3.4 L337）的方法说明中数据来源标注为"CQRS 读模型投影"，但应用层设计中未定义任何用于访问读模型的端口（Port）、仓储（Repository）或查询服务（QueryService）。相比之下，其他方法明确标注了委托的领域服务或仓储（如 `getFatigueDistribution` 委托 `FleetAnalyticsService.getFatigueDistribution`）。`queryAlertHistory` 和 `queryVehicleTrajectory` 两处仅写"数据来源为 CQRS 读模型投影"，下游开发者无法确定应通过哪个接口获取投影数据。
- **所在位置**：§3.1 `queryAlertHistory` 方法说明（L159）、§3.4 `queryVehicleTrajectory` 方法说明（L337）
- **严重程度**：轻微
- **改进建议**：在相应方法说明中补充委托目标（如通过 `AlertProjectionRepository` 或 `TrajectoryQueryService`），或在 §3.1 和 §3.4 的协作关系中分别增加读模型端口依赖声明。

---

### 问题 6：`QueryTrajectoryRequest` 双参数交叉校验缺失

- **问题描述**：§4.4 QueryTrajectoryRequest 中 `vehicleId: Option<VehicleId>` 和 `driverId: Option<DriverId>` 二者均为可选，约定"至少提供一个"。但未覆盖二者同时提供且不匹配的场景（如 vehicleId 对应车辆 A、driverId 对应驾驶员 B，而 B 从未驾驶过 A）。此种情况下应返回空结果还是参数无效错误，设计未给出处理策略。
- **所在位置**：§4.4 QueryTrajectoryRequest（L692–696）
- **严重程度**：轻微
- **改进建议**：在 §3.4 异常处理策略或 §4.4 DTO 说明中补充：若 vehicleId 与 driverId 同时提供但不匹配（该驾驶员无此车辆的行程记录），返回空轨迹序列（非错误）或返回 `AppError.InvalidParameterCombination("vehicleId与driverId无匹配行程记录")`。

---

### 问题 7：离线消息队列 7 天告警保留策略未衔接隐私边界要求

- **问题描述**：§3.3 WebSocket 生命周期管理中离线消息补推策略约定"每条告警保留 7 天"（L260），但告警内容可能包含与路怒判定关联的敏感场景信息（如告警触发时间、行程 ID、风险等级），而 §3.2 路怒语音存证生命周期中音频正文有明确的加密和 90 天过期清除策略。离线消息队列中的告警摘要数据同样需要与 BR-04 隐私边界对齐——应明确该类数据的存储加密方式、过期清除策略，以及是否对家属可见范围做权限过滤。
- **所在位置**：§3.3 WebSocket 生命周期表"离线消息补推"行（L260）
- **严重程度**：轻微
- **改进建议**：在离线消息补推策略中补充隐私约束说明——（1）离线消息队列中的告警数据应加密存储（至少传输层加密），到期自动清除；（2）补推时应按家属当前有效授权范围过滤（不推送已撤销授权的驾驶员告警）；（3）若家属账户在离线期间被撤销监护权限，重连后不应补推该驾驶员的任何告警。

---

### 问题 8（本次新增）：OTAManagementService 接口中 S6 依赖领域事件订阅声明不完整

- **问题描述**：§3.6 S6 协作关系中领域事件订阅仅列出 OTAUpgradeCompletedEvent、OTAUpgradeFailedEvent、OTAUpgradeRolledBackEvent 三个事件（L444）。但 S2 在 OTA 回滚策略（§3.6 L428–438）中订阅 OTAUpgradeRolledBackEvent 以恢复 CAN 干预，S2 也订阅 OTAUpgradeCompletedEvent（§3.6 L438）。S6 自身作为 OTA 升级管理服务，其编排逻辑中未声明订阅 OTAUpgradeStartedEvent——该事件在 §7.2 链路 D 中用于触发 CAN 干预抑制的判定逻辑，但链路 D 的实际消费方是 S2 而非 S6。S6 自身对 OTA 状态事件的订阅范围是否充分，以及与 S2 之间的 OTA 事件消费分工未在设计各处统一描述。
- **所在位置**：§3.6 S6 协作关系事件订阅列表（L444） vs §7.2 链路 D（L1052–1067） vs §3.6 OTA 回滚策略（L428–438）
- **严重程度**：轻微
- **改进建议**：在 §3.6 S6 协作关系中明确其自身需要订阅的 OTA 领域事件范围（如查询进度时可能依赖的状态事件），并与 §7.2 链路 D 中 S2 的订阅分工形成统一的交叉引用说明。

---

## 六、整体评价

本版本（a_v5）在以下维度经系统性核验后，评价如下：

### 需求响应充分度

- **产出完整性**：需求文档指定的三大产出项（接口方法签名+参数/返回值+异常处理、服务协作图、三条核心时序图）**全部存在且格式符合要求**（见第一节核验）。
- **职责-方法覆盖**：经逐服务逐职责核对，S1 的 8 项职责、S3 的 8 项职责、S4 的 7 项职责、S5 的 6 项职责、S6 的 5 项职责**全部有对应的接口方法或编排机制覆盖**（见第二节）。S2 的干预类型中"安全带预紧"和"紧急制动辅助"的具体类型由领域层 InterventionType 枚举定义——应用层设计通过 `queryInterventionStatus` 和 `queryInterventionHistory` 方法可返回领域层定义的任意干预类型，不存在应用层方法遗漏（见第二节 S2 核验说明）。
- **时序路径参与者完整性**：三条路径的所有指定应用服务参与者均已出现在对应时序图中，且触发顺序与需求一致（见第三节）。
- **历史迭代修复**：第 4 轮迭代的 8 个问题（含 2 个严重级别）已全部修复并逐条核验确认（见第四节）。

### 事实错误与逻辑矛盾

- 本次审查发现的 8 个具体问题（第一～第五节）集中在**调用路径歧义**（问题1）、**类型定义缺失**（问题2）、**异步语义不完整**（问题3）、**下游消费者友好度不足**（问题4、5）、**参数校验缺失**（问题6）、**隐私一致性**（问题7）、**事件订阅声明不完整**（问题8）七个方面，均为局部性缺陷，不涉及设计主体的逻辑矛盾或事实错误。
- 经全文交叉核对，未发现影响设计主体框架的逻辑矛盾（服务间调用方向、事件路由路径、事务边界、分层职责等均自洽）。

### 深度与完整性（可落地性）

- **接口定义充分性**：六个应用服务的所有方法均定义了具体签名、输入/输出 DTO、返回类型和异常处理策略。DTO 已在 §4 中按服务分节定义，跨层类型在 §5 中统一定义，AppError 枚举在 §6.1 中完整定义。**下游接口层开发人员可直接据此编写调用代码**。
- **事务边界**：§7.4 完整覆盖了所有 23 个方法的写/只读/无事务类型、隔离级别和并发控制策略。
- **边界条件覆盖**：WebSocket 生命周期（连接建立/心跳/断开/重连/离线补推/连接限制）、看板缓存失效（TTL + 事件驱动）、SOS 重试策略（指数退避 + 人工兜底）、OTA 回滚干预抑制等关键边界条件均已定义。
- **验收测试对接**：§11 为三条核心路径提供了 7 个验收测试场景（含正常路径和关键异常路径），可通过应用服务入口直接驱动验证。

### 改进优先级建议

- **优先处理**：问题 1（调用路径歧义）、问题 3（异步操作语义）——直接影响编码实现决策。
- **建议处理**：问题 2（BatchStrategy 类型缺失）、问题 4（人类可读字段缺失）、问题 5（CQRS 读模型访问声明）、问题 6（参数交叉校验）——影响下游开发效率和运行健壮性。
- **可选处理**：问题 7（隐私一致性）、问题 8（事件订阅声明完整性）——补充后提升设计严谨性。

---

## 七、修订说明（v1）

首轮审查，无质询文件。

---

## 八、修订说明（v2）

本版（v2）基于质询报告 b_v5_challenge_v1（共 3 个质询要点）进行审查报告修订。

| 质询意见 | 回应 |
|---------|------|
| **质询要点 1：整体评价缺少系统性证据支撑**——审查报告在"整体评价"段中给出了三个未经验证的正面结论，但审查行为仅覆盖了 7 个点状缺陷的发现，未执行面向需求的系统性核对。 | **采纳**。已将原"整体评价"替换为基于四维度系统核验的结构化评价（见第六节）：(a) 产出完整性核验（第一节：三大产出项逐一确认存在性与格式符合性）；(b) 逐服务职责-方法覆盖矩阵（第二节：S1~S6 每条需求职责逐一标注覆盖状态与对应方法/机制）；(c) 时序路径参与者核验（第三节：三条路径逐一对照需求指定顺序确认）；(d) 历史迭代严重问题修复验证（第四节：第 4 轮 8 个问题逐条核验并提供核验依据）。整体评价中各项结论均不再以概括性断言呈现，而是关联到对应核验节的具体证据。 |
| **质询要点 2：需求覆盖核对完全缺失**——审查报告未执行任何形式的逐需求覆盖核对，三大产出项完整性、逐服务职责-方法对应、时序路径参与者完整性均未经核实。 | **采纳**。已在审查报告中新增三个系统核验节（第一节/第二节/第三节），分别对三大产出项的存在性与格式符合性、S1~S6 每条职责的接口方法覆盖状态、三条时序路径的应用服务参与者顺序进行逐项核验，所有核对结果以表格化形式呈现并标注核验依据（章节号/行号）。对 S2 中"安全带预紧"和"紧急制动辅助"两类干预类型，核验结论为"域层依赖"——应用层通过 `queryInterventionStatus` 可返回领域层定义的任意干预类型，不存在应用层方法遗漏，但应在核验表中明确标注此依赖关系。 |
| **质询要点 3：历史迭代严重问题修复状态未追溯**——第 4 轮迭代中两个"严重"级别问题的修复状态未被逐条验证，审查报告仅以笼统表述一笔带过。 | **采纳**。已在审查报告中新增第四节（第 4 轮迭代严重问题修复状态逐条核验），对第 4 轮全部 8 个问题（含 #1 和 #2 两个严重级别）逐一提供核验依据（章节号对照、新增内容检查）和核验结论（已修复/部分修复/未修复）。核验确认：8 个问题全部已修复，2 个严重问题经交叉查阅 §5.1（L820）、§3.2（L185–220）、§7.2（L1029、L1069–1089）等关键位置确认修复内容按建议实施。 |
| **补充发现** | 在系统性核验过程中新增发现问题 8：§3.6 S6 协作关系中的 OTA 领域事件订阅声明（仅 OTAUpgradeCompletedEvent/FailedEvent/RolledBackEvent 三个）与 §7.2 链路 D（S2 订阅 OTA 状态事件用于 CAN 干预抑制）之间的消费分工未形成统一交叉引用说明，S6 自身对 OTAUpgradeStartedEvent 的依赖在链路 D 和 S6 协作关系中均未显式声明。已作为问题 8 加入审查发现（严重程度：轻微）。 |
