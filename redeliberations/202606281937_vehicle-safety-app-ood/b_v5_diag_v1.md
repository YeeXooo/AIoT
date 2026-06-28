# 应用层 OOD 设计方案质量审查报告（b_v5_diag_v1）

> 审查对象：`a_v5_copy_from_v4.md`（v5 迭代产出）
> 审查视角：需求响应充分度、事实错误与逻辑矛盾、深度与完整性、可落地性
> 审查范围：聚焦内部审议未充分覆盖的维度（内部审议已确认类型系统可行性、标准库覆盖、语言特性可行性、设计一致性）

---

## 审查发现

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

## 整体评价

本版本（a_v5）已在前四轮迭代中解决了大量结构性问题（方法签名、DTO 定义、错误枚举、事务边界、时序图矛盾等），整体设计充分响应了需求文档中六个应用服务的职责定义，服务间协作关系清晰，异常处理策略覆盖了主要错误场景。

本次审查发现的 7 个问题主要集中在**边界条件完善性**（问题 1/3/6）、**类型定义完整性**（问题 2）、**下游消费者友好度**（问题 4/5）和**隐私一致性**（问题 7）四个维度，均不阻塞设计主体框架的合理性。其中问题 1（调用路径歧义）和问题 3（异步操作语义）对实际编码有直接影响，建议优先处理。

---

## 修订说明（v1）

首轮审查，无质询文件。
