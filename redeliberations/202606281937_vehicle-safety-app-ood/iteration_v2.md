# 再审议判定报告（v2）

## 判定结果

RETRY

## 判定理由

组件B诊断报告（b_v2_diag_v1.md）识别出 10 个问题，含 3 个严重问题（问题1 S4缺失车辆轨迹查询、问题2 S5事件订阅声明与时序图矛盾、问题3 路径3偏离需求链路边）、4 个一般问题（问题4 S1未覆盖分心/异常驾驶行为、问题5 枚举类型定义缺失、问题6 结构类型字段级定义缺失、问题7 S6批量操作缺少幂等性机制）、3 个轻微问题（问题8批量约束缺失、问题9心跳超时处理不明、问题10事务策略段落位置不当）。组件B质询报告（b_v2_challenge_v1.md）质询结果为 LOCATED，10 个问题全部通过证据充分性、逻辑完整性、覆盖完备性三维度审查，诊断结论被确认。组件B内部循环实际轮次 1 小于最大轮次 12，属提前终止。根据判定标准，审查报告包含严重和一般等级问题，判定为 RETRY。

## 需要解决的问题

- **问题描述**：S4 FleetManagementService 缺失车辆轨迹查询功能，需求文档明确将"车辆轨迹"列为 S4 职责，但接口方法契约中无轨迹查询方法
- **所在位置**：设计方案 §3.4 IFleetManagementService 接口方法契约全文
- **严重程度**：严重
- **改进建议**：在 S4 接口契约中新增 `queryVehicleTrajectory` 方法，输入含 VehicleId 或 DriverId + TimeRange，输出轨迹点序列（GPS 坐标 + 时间戳 + 车速），并在 §4.4 补充对应 DTO 定义

- **问题描述**：S5 领域事件订阅声明与时序图存在逻辑矛盾——§3.5 声明 S5 订阅 EmergencyActivatedEvent，但 §8.3 时序图显示 S5 实际接收 DS-12 产出的 RescueReportReadyEvent
- **所在位置**：设计方案 §3.5 协作关系（L368）vs §8.3 时序图（L1144）
- **严重程度**：严重
- **改进建议**：统一事件订阅声明；若实际为 DS-12 消费 EmergencyActivatedEvent 后产出 RescueReportReadyEvent，则 §3.5 应声明 S5 订阅 RescueReportReadyEvent，并在协作说明中描述二级事件路由关系

- **问题描述**：路径 3 时序图未按需求指定链路包含 RiskMonitoringService——需求指定链路为 RiskMonitoringService → EmergencyRescueService → RemoteGuardianshipService → InterventionService，但 §8.3 时序图全程未出现 S1
- **所在位置**：设计方案 §8.3 路径 3 时序图全文
- **严重程度**：严重
- **改进建议**：在时序图中补充 S1 作为碰撞信号的编排入口，或在 §10 设计决策中说明碰撞失能判定不走 S1 编排而走独立领域通道的原因，并同步更新需求文档

- **问题描述**：S1 风险监测未显式覆盖需求中的"分心"与"异常驾驶行为"类别，仅涉及疲劳和活体遗留两类
- **所在位置**：设计方案 §3.1 接口契约与职责描述全文、§8.1 时序图
- **严重程度**：一般
- **改进建议**：在 §3.1 职责描述中补充分心和异常驾驶行为判定覆盖说明，确保需求项可追溯

- **问题描述**：多个 DTO 中使用的枚举类型（RescueRequestStatus、RescueTriggerType、RescueRecordStatus）缺少完整 enum 定义块
- **所在位置**：设计方案 §4.3 L575、§4.5 L663–664
- **严重程度**：一般
- **改进建议**：参照 §4.2 中 OverrideResult 的定义格式，为上述三个枚举类型补充完整变体定义

- **问题描述**：HeatmapPoint 和 UpgradeOptions 两个 DTO 结构类型缺少字段级定义
- **所在位置**：设计方案 §4.4 L585、§4.6 L672
- **严重程度**：一般
- **改进建议**：在相应章节中补充 HeatmapPoint（至少含 latitude、longitude、riskIntensity）和 UpgradeOptions（含 batchStrategy、scheduledWindow）的字段定义

- **问题描述**：S6 批量写操作 createUpgradeTask 缺少幂等性机制，重复请求可能产生重复升级任务
- **所在位置**：设计方案 §3.6 createUpgradeTask 方法（L434）、§7.4 事务边界表（L998）
- **严重程度**：一般
- **改进建议**：在 CreateUpgradeTaskRequest 中增加 idempotencyKey 字段，补充幂等性语义说明

- **问题描述**：S6 批量操作缺少车辆数量上限约束，无防御性约束说明
- **所在位置**：设计方案 §3.6 createUpgradeTask 方法（L434）、§4.6 CreateUpgradeTaskRequest（L670）
- **严重程度**：轻微
- **改进建议**：在方法或 DTO 说明中标注批量上限（如单次最多 100 辆），并增加超限错误变体

- **问题描述**：S3 WebSocket 心跳超时后服务端处理动作不明确
- **所在位置**：设计方案 §3.3 WebSocket 连接生命周期管理表（L241–242）
- **严重程度**：轻微
- **改进建议**：在生命周期管理表中明确服务端动作（主动发送 CLOSE 帧、释放推送流和订阅关系、清除连接映射）

- **问题描述**：S6 批量操作事务策略段落位置不当，位于 §3.5 节末尾而非 §3.6 或 §7.4
- **所在位置**：设计方案 §3.5 节末尾块引用（L383）
- **严重程度**：轻微
- **改进建议**：将该段落移至 §3.6 createUpgradeTask 方法说明之后，或合并到 §7.4 事务边界汇总表中
