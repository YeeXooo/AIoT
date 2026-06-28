# 再审议判定报告（v5）

## 判定结果

RETRY

## 判定理由

组件B诊断报告（b_v5_diag_v2）共发现 8 个问题，其中严重等级 0 个、一般等级 4 个、轻微等级 4 个。质询报告（b_v5_challenge_v2）结果为 LOCATED（审查结论被确认），实际轮次 2 < 最大轮次 12，质询提前终止且诊断报告结论被采纳。根据判定标准——审查报告包含一般等级的问题，判定为 RETRY。

## 需要解决的问题（仅 RETRY 时存在）

- **问题描述**：`startLifeDetection` 公开方法调用路径与事件驱动触发路径未明确界定——§3.1 将其列为公开方法契约，§8.2 时序图显示通过 EventBus 触发，两条路径并存造成实现歧义。
- **所在位置**：§3.1 接口方法契约表 `startLifeDetection` 行（L157） vs §8.2 时序图（L1203–1205） vs §3.1 协作关系事件订阅列表（L147）
- **严重程度**：一般
- **改进建议**：二选一明确——（1）若 `startLifeDetection` 仅作为事件处理器内部方法，应从公开接口契约中移除或标注"仅供内部事件驱动调用"；或（2）若保留为公开方法，则应在 §8.2 时序图中改为外部调用方直接调用，而非经 EventBus 路由。

- **问题描述**：`BatchStrategy` 类型在 §4.6 UpgradeOptions 中被引用但全文任何位置（含 §4 DTO、§5 跨层类型、§5.5 领域层引用表）均未给出定义，下游开发者无法确定分批策略的数据结构。
- **所在位置**：§4.6 UpgradeOptions（L763）
- **严重程度**：一般
- **改进建议**：在 §4.6 UpgradeOptions 之后补充 `BatchStrategy` 的枚举或结构类型定义（如 `BY_MODEL`、`BY_REGION`、`BY_BATCH_SIZE(size: UInt32)`），或列入 §5.5 并标注领域层定义位置。

- **问题描述**：`controlVehicleWindow` 异步指令执行-确认语义缺失——方法返回 `Result<Unit, AppError>`，将"指令下发成功"与"车窗操作完成"混同为同一个 `Unit` 返回值，前端无法区分指令已下发但未执行、正在运动中、已到位的不同状态，缺少状态确认回调或轮询查询机制。
- **所在位置**：§3.3 接口方法契约 `controlVehicleWindow` 行（L272）、§4.3 ControlVehicleWindowRequest（L600–604）
- **严重程度**：一般
- **改进建议**：（1）明确返回值语义——`Ok(Unit)` 表示"指令已成功下发至 IoTDA"而非"车窗已到位"；（2）新增 `queryWindowStatus` 查询方法或在 DriverStatusSnapshot 中增加车窗位置字段；（3）补充车窗控制超时未确认的异常处理策略。

- **问题描述**：运营管理界面 DTO 缺少人类可读标识字段——OfflineVehicleInfo、HighRiskDriverSummary、RescueRecordSummary 仅含 VehicleId/DriverId，不含车牌号和驾驶员姓名，导致前端大屏需额外查询，增加接口层调用次数和复杂度。
- **所在位置**：§4.4 OfflineVehicleInfo（L627–632）、HighRiskDriverSummary（L650–654）、§4.5 RescueRecordSummary（L744–748）
- **严重程度**：一般
- **改进建议**：在上述 DTO 中增加 `licensePlate: String` 和 `driverName: String` 字段；若出于数据最小化原则不愿包含，则至少在设计决策中说明理由。

- **问题描述**：CQRS 读模型访问机制在应用层未定义——`queryAlertHistory` 和 `queryVehicleTrajectory` 标注"数据来源为 CQRS 读模型投影"但未定义访问端口/仓储/查询服务，下游开发者无法确定通过哪个接口获取投影数据。
- **所在位置**：§3.1 `queryAlertHistory` 方法说明（L159）、§3.4 `queryVehicleTrajectory` 方法说明（L337）
- **严重程度**：轻微
- **改进建议**：在方法说明中补充委托目标（如 `AlertProjectionRepository` 或 `TrajectoryQueryService`），或在协作关系中增加读模型端口依赖声明。

- **问题描述**：`QueryTrajectoryRequest` 双参数交叉校验缺失——`vehicleId` 和 `driverId` 均为可选但约定"至少提供一个"，未覆盖二者同时提供且不匹配的场景，缺少处理策略。
- **所在位置**：§4.4 QueryTrajectoryRequest（L692–696）
- **严重程度**：轻微
- **改进建议**：补充说明：若 vehicleId 与 driverId 同时提供但不匹配，返回空轨迹序列或 `AppError.InvalidParameterCombination`。

- **问题描述**：离线消息队列 7 天告警保留策略未衔接隐私边界要求——告警内容可能含敏感信息，但与 BR-04 隐私边界的对齐（加密方式、过期清除、家属权限过滤）未明确。
- **所在位置**：§3.3 WebSocket 生命周期表"离线消息补推"行（L260）
- **严重程度**：轻微
- **改进建议**：补充隐私约束——（1）离线告警数据加密存储、到期自动清除；（2）补推时按当前有效授权范围过滤；（3）已撤销监护权限的家属不补推。

- **问题描述**：OTAManagementService 接口中 S6 依赖领域事件订阅声明不完整——S6 协作关系中仅列出 3 个 OTA 状态事件，OTAUpgradeStartedEvent 在链路 D 和 S6 协作关系中均未显式声明，S6 与 S2 的 OTA 事件消费分工未形成统一交叉引用说明。
- **所在位置**：§3.6 S6 协作关系事件订阅列表（L444） vs §7.2 链路 D（L1052–1067） vs §3.6 OTA 回滚策略（L428–438）
- **严重程度**：轻微
- **改进建议**：在 §3.6 S6 协作关系中明确 OTA 领域事件订阅范围，并与 §7.2 链路 D 中 S2 的订阅分工形成统一交叉引用说明。
