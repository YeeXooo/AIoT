# 质量审查报告（b_v6_diag_v1）

> **审查对象**：`a_v6_copy_from_v5.md`（车载安全监测系统 应用层 OOD 设计方案 a_v6）
> **审查轮次**：第 6 次迭代首轮审查
> **审查视角**：需求响应充分度、事实错误/逻辑矛盾、深度与完整性、落地可指导性

---

## 一、总体评价

文档在第 5 轮反馈基础上进行了系统修订，六个应用服务的接口契约、DTO 定义、异常体系、时序图和验收场景已较完整。审查发现 7 个问题，其中 2 个严重（事件订阅声明不一致、未定义事件类型引用）、3 个一般（职责冲突、依赖缺失、协作图不完整）、2 个轻微（字段语义偏差、批量回滚机制缺失）。

---

## 二、问题详情

### 问题 1（严重）：S2 协作关系事件订阅列表中缺少 OTA 相关事件

- **问题描述**：§3.6 S6 协作关系（L448）和 §7.2 链路 D（L1093）明确声明 S2 InterventionServiceImpl 订阅 `OTAUpgradeCompletedEvent` 和 `OTAUpgradeRolledBackEvent`（用于在固件升级/回滚完成后恢复 CAN 级干预能力），但 §3.2 S2 的正式协作关系事件订阅列表（L191）中仅列出 `RiskDeterminedEvent / RiskResolvedEvent`、`LifeDetectedEvent`、`RageDeterminedEvent`、`SensorFaultEvent`，两个 OTA 事件均未出现。S2 的完整订阅集合在不同章节间不一致，下游开发者仅阅读 §3.2 会遗漏 OTA 干预抑制恢复逻辑。
- **所在位置**：§3.2 S2 协作关系事件订阅列表（L191） vs §3.6 S6 协作关系（L448） & §7.2 链路 D（L1093）
- **严重程度**：严重
- **改进建议**：在 §3.2 S2 协作关系事件订阅列表中补充 `OTAUpgradeCompletedEvent` 和 `OTAUpgradeRolledBackEvent`，并标注语义："在固件升级/回滚完成后立即恢复 CAN 级干预能力（与 S6 分工详见 §7.2 链路 D）"。

---

### 问题 2（严重）：`RescueConfirmedEvent` 事件类型未在任何章节正式定义或声明

- **问题描述**：§8.3 路径 3 时序图（L1325–1326）中，S5 EmergencyRescueServiceImpl 向 EventBus 产出 `RescueConfirmedEvent`，EventBus 将其路由至 S2 InterventionServiceImpl 以更新干预状态并通知 HMI"救援已触发"。然而该事件类型：(1) 未在 §3.5 S5 协作关系中列为产出事件；(2) 未在 §3.2 S2 协作关系中列为订阅事件；(3) 未出现在 §5.5 领域层引用类型表中。时序图引用了一个无出处、无定义的事件类型，接口层和领域层无法据此实现。
- **所在位置**：§8.3 时序图（L1325–1326）、§3.5 S5 协作关系、§3.2 S2 协作关系、§5.5 领域层引用类型表
- **严重程度**：严重
- **改进建议**：三处统一修复：(1) 在 §3.5 S5 协作关系中补充"产出 RescueConfirmedEvent（投递 120 成功后产出）"；(2) 在 §3.2 S2 协作关系事件订阅列表中补充 `RescueConfirmedEvent`（标注语义：更新干预状态为"救援已触发"，供 HMI 查询渲染）；(3) 将该事件类型列入 §5.5 领域层引用类型表并标注定义位置（若属应用层事件则单独说明）。

---

### 问题 3（一般）：S1 对 SensorFaultEvent 的订阅描述与 HMI 交互职责分工存在冲突

- **问题描述**：§3.1 S1 协作关系（L147）声明 S1 订阅 `SensorFaultEvent` 并执行"3 秒内编排 HMI 语音告警——'传感器故障，请谨慎驾驶'"。但：(1) §7.3 协作设计原则（L1139–1140）明确规定"边缘侧 HMI 不直接接收领域服务消息，而是通过查询 S2 InterventionServiceImpl 的当前干预状态来获取渲染数据"，HMI 交互通道统一经 S2；(2) §3.2 S2 协作关系（L191）已声明对 `SensorFaultEvent` 的订阅及相同的"3 秒内编排 HMI 语音告警"逻辑。S1 的角色定义为风险判定编排（§3.1），承担 HMI 干预告警的编排职责属于越界，且与 S2 形成重复声明，造成实现时职责分歧。
- **所在位置**：§3.1 S1 协作关系事件订阅列表（L147）、§3.2 S2 协作关系（L191）、§7.3 协作设计原则（L1139–1140）
- **严重程度**：一般
- **改进建议**：从 §3.1 S1 的 `SensorFaultEvent` 订阅描述中移除"HMI 语音告警"编排职责，改为适合 S1 角色的处理语义（如"收到传感器自检故障信号后中止当前判定会话、记录传感数据缺失日志"）；HMI 语音告警编排统一由 S2 负责（§3.2 已覆盖）。

---

### 问题 4（一般）：S1 `queryAlertHistory` 依赖的 `AlertProjectionRepository` 未在模块依赖表中体现

- **问题描述**：§3.1 `queryAlertHistory` 方法说明（L159）标注"委托 AlertProjectionRepository 查询 CQRS 读模型投影"，但 §2.1 模块依赖表中 S1 的依赖领域层模块仅列出 `domain.risk`、`domain.life`、`domain.emergency`、`domain.event`、`domain.model`，未包含 `domain.alert` 或等效的告警投影模块。下游开发者无法确定 `AlertProjectionRepository` 属于哪个领域模块、仓储接口定义在何处。
- **所在位置**：§3.1 L159、§2.1 模块依赖表（L66）
- **严重程度**：一般
- **改进建议**：在 §2.1 S1 的依赖领域层模块中补充 `domain.alert`（或对应模块名），或在 §3.1 协作关系"依赖的仓储"行中明确 `AlertProjectionRepository` 所属的领域层模块。

---

### 问题 5（一般）：§7.1 协作关系总览图缺少 S5 和 S6 的事件订阅路径

- **问题描述**：§7.1 协作关系总览图（L1002–1027）中，S5（EmergencyRescueService）仅通过 S3 的家属手动救援触发箭头连接，S6（OTAManagementService）仅通过 S2 的 OTA 事件订阅箭头连接，二者均缺少从 EventBus 直接到 S5/S6 的箭头来表示各自的事件订阅路径（S5 订阅 RescueReportReadyEvent 和 LifeDetectedEvent，S6 订阅四个 OTA 状态事件）。图中 S3 和 S4 有从 EventBus 到它们的箭头，S5 和 S6 却没有，造成图面不一致。
- **所在位置**：§7.1 协作关系总览图（L1002–1027）
- **严重程度**：一般
- **改进建议**：在 §7.1 图中补充从 EventBus 到 S5 和 S6 的箭头，标注关键事件：S5 ← `RescueReportReadyEvent` / `LifeDetectedEvent`；S6 ← `OTAUpgrade*Event`（或标注"OTA 状态事件"）。

---

### 问题 6（轻微）：`OfflineVehicleInfo` 中 `driverId` 和 `driverName` 为必填字段，不符合脱线车辆语义

- **问题描述**：§4.4 `OfflineVehicleInfo` 结构类型（L649–656）中 `driverId: DriverId` 和 `driverName: String` 均为必填字段。但车辆脱线（传感器故障或通信中断）的场景下，车辆可能处于停车场熄火状态，无当前驾驶员。将这两个字段设为必填会在无驾驶员场景下造成数据填充困难或引入无效的哨兵值。同类 DTO（§5.2 DriverStatusSnapshot）中对行程相关字段使用了 `Option<T>` 包裹，此处应按一致模式处理。
- **所在位置**：§4.4 OfflineVehicleInfo（L649–656）
- **严重程度**：轻微
- **改进建议**：将 `driverId` 和 `driverName` 的类型分别改为 `Option<DriverId>` 和 `Option<String>`，并补充语义说明："无当前驾驶员时（如停车场熄火脱线），两字段为 None"。

---

### 问题 7（轻微）：S6 `createUpgradeTask` 逐条提交后调用方取消已成功任务的机制缺失

- **问题描述**：§3.6 `createUpgradeTask` 采用逐条创建 + 逐条提交策略（L460），一次批量请求中部分车辆成功、部分失败。若调用方（运维人员）对整体结果不满意（如成功率低于期望阈值），文档未提供取消已成功子任务的方法，运维人员需逐车手动调用回滚。在批量操作场景下缺少一种统一的撤销机制，影响运维效率。
- **所在位置**：§3.6 createUpgradeTask 事务策略（L460）、S6 接口方法契约表（L453–459）
- **严重程度**：轻微
- **改进建议**：选项一：新增 `cancelUpgradeTask(UpgradeTaskId)` 方法供批量撤销使用；选项二：在批量操作事务策略中补充说明"调用方如需撤销已成功的子任务，可通过 `triggerRollback` 逐车执行回滚"。

---

## 三、审查总结

| 维度 | 评估 |
|------|------|
| 需求响应充分度 | 较高。六个应用服务完整覆盖需求指定的功能域，时序图覆盖三条关键路径。BR-03 路怒和 BR-08 失效保护已在 v5 补全。 |
| 事实错误与逻辑矛盾 | **存在**。S2 OTA 事件订阅声明缺失（P1）、RescueConfirmedEvent 未定义（P2）、S1 SensorFaultEvent 职责冲突（P3）。详见问题 1–3。 |
| 深度与完整性 | 整体较好。接口契约、DTO、错误枚举、事务边界、验收场景均完整。细节遗漏见问题 4–7。 |
| 落地可指导性 | 六份 interface 的方法签名 + DTO + 错误类型可直接转化为仓颉代码骨架。下游消费者（家属 APP、车队大屏、救援中心）所需的数据契约已基本就位，修复上述问题后可直接用于编码。 |
