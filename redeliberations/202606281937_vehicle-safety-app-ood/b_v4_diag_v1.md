# 质量审查报告（b_v4_diag_v1）

> 审查对象：`a_v4_copy_from_v3.md`（车载安全监测系统 应用层 OOD 设计方案 a_v4）
> 审查轮次：第 4 次迭代，首轮审查
> 审查视角：需求响应充分度、事实错误与逻辑矛盾、深度与完整性；实际落地可编码性

---

## 一、问题清单

### 问题 1：BR-03 路怒判定与调节链路覆盖率不足

- **问题描述**：需求 BR-03（预警级 L2）定义了完整的路怒判定与调节链路——语音情绪检测（谩骂关键词+声压级 >85dB）结合心率上升 >20% → 判定路怒 → 自动空调降温 2°C + 播放白噪音/舒缓音乐 + 触发路怒语音存证。该设计对路怒链路的覆盖存在三方面缺口：
  1. **传感器类型缺漏**：§5.1 SensorType 枚举仅包含 DMS_CAMERA / MILLIMETER_WAVE_RADAR / ACCELEROMETER / PHYSIOLOGICAL_MONITOR，缺少语音/声学传感器类型（如 MICROPHONE / ACOUSTIC），导致语音情绪判定失去数据来源；
  2. **干预类型缺漏**：S2 InterventionService 的干预指令类型未涵盖空调温度调节和音视频娱乐系统的控制指令（如 INFOTAINMENT_CONTROL、AC_ADJUSTMENT），路怒判定后的调节动作无法通过应用层编排执行；
  3. **语音存证缺漏**：路怒语音存证（需求 §3.3、BR-03 定义的"仅录制路怒判定成立时段、边缘侧脱敏/加密留存、按周期清除"）在应用层设计中完全未体现，缺少对应的存证触发逻辑、存储生命周期管理和清理策略。
- **所在位置**：§3.1 S1 职责描述（仅提及"疲劳、分心、异常驾驶行为"三类，未显式覆盖路怒）、§5.1 SensorType 枚举（L780）、§3.2 S2 干预类型体系（全文未提及空调/娱乐系统控制指令）、全文缺失路怒语音存证相关设计
- **严重程度**：严重
- **改进建议**：
  1. 在 §5.1 SensorType 中补充 MICROPHONE / ACOUSTIC 变体；
  2. 在 S2 InterventionService 的干预类型体系中补充 AC_ADJUSTMENT、INFOTAINMENT_PLAYBACK 等变体，并在 §3.2 中说明路怒场景下 S2 通过这些指令协调空调和音响系统；
  3. 为路怒语音存证补充应用层编排策略（S1 处理语音传感器数据 → 领域层判定路怒 → S2 编排触发存证录制与边缘侧存储），明确存证生命周期管理、加密与清除策略与 BR-04 隐私边界的衔接。

---

### 问题 2：§7.2 链路 B 描述与 §3.5 订阅声明及 §8.3 时序图存在逻辑矛盾

- **问题描述**：§7.2 链路 B（告警→推送）描述中显示 EmergencyActivatedEvent 经 AlertPersistenceService 产出 AlertTriggeredEvent 后，由事件总线分发至 S5："S5 EmergencyRescueServiceImpl（仅 EmergencyActivatedEvent 路径）→ RescueReportPort 投递 120"（L982）。但：
  - §3.5 S5 协作关系声明 S5 订阅的是 RescueReportReadyEvent（"DS-12 消费 EmergencyActivatedEvent 后产出"，L371），而非 EmergencyActivatedEvent 或 AlertTriggeredEvent；
  - §8.3 时序图（L1205–1206）明确显示 S5 通过 RescueReportReadyEvent 接收触发信号，再由 RescueReportPort 投递 SOS 报告到 120。

  三处描述不一致——§7.2 暗示 S5 直接从 EmergencyActivatedEvent→AlertTriggeredEvent 路径获取触发，而 §3.5 和 §8.3 均说明中间经过 DS-12 产出 RescueReportReadyEvent 的二级事件路由。实现阶段将无法确定正确的事件订阅链路。
- **所在位置**：§7.2 链路 B 第 3 条分支描述（L982） vs §3.5 S5 协作关系事件订阅声明（L371） vs §8.3 时序图（L1205–1206）
- **严重程度**：严重
- **改进建议**：将 §7.2 链路 B 的 S5 分支描述修正为"S5 EmergencyRescueServiceImpl（通过 RescueReportReadyEvent 路径，该事件由 DS-12 消费 EmergencyActivatedEvent 后产出）→ RescueReportPort 投递 120"，使三处描述统一。

---

### 问题 3：远程车窗控制功能未在应用服务接口中体现

- **问题描述**：需求 §3.4 明确要求家属在授权下可通过"车窗控制"进行远程安抚或协助（如紧急通风、配合救援），且 §6.3 安全门控原则中也将"车窗控制"列为危险操作之一（L905）。然而：
  - S3 IRemoteGuardianshipService 的六个接口方法中无任何车窗控制方法（仅有音视频对讲和手动救援触发）；
  - S5 IEmergencyRescueService 的 RescueOperation 枚举仅含 RemoteUnlock 和 HealthProfileAccess（L679），不含远程车窗操作；
  - 安全门控原则中列举了车窗控制但没有任何应用服务方法对应此操作。
- **所在位置**：§3.3 S3 接口方法契约表（L249–256，缺少车窗控制方法）、§4.5 RescueOperation 枚举定义（L679）、§6.3 安全门控原则（L905）
- **严重程度**：一般
- **改进建议**：在 S3 IRemoteGuardianshipService 中新增 `controlVehicleWindow` 方法（输入含 familyAccountId、driverId、windowOperation、二次验证凭证；输出操作结果）；或在 S5 IEmergencyRescueService 的 RescueOperation 枚举中增加 RemoteWindowControl 变体，明确车窗控制与车门解锁为不同操作。

---

### 问题 4：SensorType 枚举缺少后排红外摄像头类型

- **问题描述**：需求 §3.1 明确新增"后排红外摄像头"作为独立数据源（"雷达负责判定有无活体，红外摄像头负责提供可视画面"）。但 §5.1 SensorType 枚举（L780）仅包含 DMS_CAMERA / MILLIMETER_WAVE_RADAR / ACCELEROMETER / PHYSIOLOGICAL_MONITOR，未包含后排红外摄像头类型。下游家属 APP 实时红外视频功能将无法确定数据来源。
- **所在位置**：§5.1 SensorType 枚举定义（L780）
- **严重程度**：一般
- **改进建议**：在 SensorType 枚举中新增 REAR_IR_CAMERA 变体；或在 §5.5 领域层引用类型表中确认该类型已在领域层定义，并在此处明确引用。

---

### 问题 5：BR-08 失效保护（fail-safe）的应用层编排链路缺失

- **问题描述**：需求 BR-08 规定：自检发现关键传感器（摄像头/雷达）遮挡或链路故障时，系统须在 3 秒内通过 HMI 持续语音提示"安全监测系统已失效，请注意驾驶安全"，并在车队大屏同步标记该车为"监测脱线"。该链路的应用层编排设计存在以下缺口：
  1. 边缘侧：S1/S2 中未定义接收传感器自检故障信号、编排 HMI 语音报警的方法或事件订阅；
  2. 云端侧：S4 FleetManagementService 职责描述中虽提及"监测脱线车辆列表"（L290），但无对应的方法返回脱线车辆列表，也无事件订阅来接收传感器故障事件以驱动列表更新；
  3. 3 秒时效性约束在应用层设计中未被提及或作为 SLA 标注。
- **所在位置**：§3.1 S1 接口方法契约表（L153–160，无传感器故障相关方法）、§3.2 S2 接口方法契约表（L195–199，无 fail-safe 干预指令）、§3.4 S4 接口方法契约表（L317–324，无监测脱线查询方法）、§3.4 S4 协作关系事件订阅列表（L311，无传感器故障事件）
- **严重程度**：一般
- **改进建议**：
  1. 在 S1 或与其协同的边缘侧组件中声明对传感器自检故障信号的订阅，并描述其编排"3 秒内发起 HMI 语音告警"的路径；
  2. 在 S4 协作关系中补充对 SensorFaultEvent 或 VehicleMonitoringOfflineEvent 的订阅声明，说明该事件驱动"监测脱线车辆列表"的更新；
  3. 在 S4 方法中新增 `getOfflineVehicles` 查询方法或明确 `getFatigueDistribution` 等看板方法中已包含脱线车辆数据。

---

### 问题 6：S1 协作关系中缺失 VehicleIgnitionOffLockedEvent 订阅声明

- **问题描述**：§8.2 时序图（L1133–1134）明确显示 S1 通过 VehicleIgnitionOffLockedEvent 触发活体检测启动："车辆-->>EventBus: VehicleIgnitionOffLockedEvent → EventBus-->>S1: 触发活体检测启动"。但 §3.1 S1 协作关系中仅声明订阅 AlertTriggeredEvent（L147），未列出 VehicleIgnitionOffLockedEvent。时序图与协作声明不一致。
- **所在位置**：§3.1 S1 协作关系事件订阅列表（L147） vs §8.2 路径 2 时序图（L1133–1134）
- **严重程度**：一般
- **改进建议**：在 §3.1 S1 协作关系的领域事件订阅列表中补充 VehicleIgnitionOffLockedEvent，并说明其触发 `startLifeDetection` 方法的编排语义。

---

### 问题 7：60 分阈值绩效预警的事件生产链未在应用层明确

- **问题描述**：需求 BR-05 规定"行程级评分或周期级评分 <60 分时，系统自动向车队管理员推送绩效预警通知"。设计 §3.4 S4 订阅了 PerformanceWarningEvent（L311），并在方法 `subscribePerformanceWarning` 中说明"当领域层产出 PerformanceWarningEvent 时，本服务消费并推送"（L323）。但该描述仅覆盖了消费侧——PerformanceWarningEvent 由哪个领域服务产出、评分计算（DS-09 ScoringService）如何触发预警事件的完整链路在应用层设计中未说明。此外，该预警是评分计算完成后的即时触发还是周期触发也未明确。
- **所在位置**：§3.4 S4 协作关系（L309–311）、方法契约 `subscribePerformanceWarning`（L323）
- **严重程度**：一般（与迭代第 3 轮问题 5"领域服务方法覆盖率不一致"性质相近但指向不同——此处关注的是事件生产链的完整性）
- **改进建议**：在 §3.4 协作关系中补充说明 PerformanceWarningEvent 由 DS-09 ScoringService 在每次评分计算完成后判断并产出（low-score → 即时事件触发），或补充一条委托说明："委托 ScoringService.calculatePeriodScore 后检查评分是否 <60，若是则 ScoringService 内部产出 PerformanceWarningEvent"。

---

### 问题 8：§7.1 协作关系图未包含事件总线节点，视觉上误导为服务间直接调用

- **问题描述**：§7.1 协作关系总览图（L922–942）中箭头直接从 S1 指向 S2/S3/S4，视觉上呈现为应用服务间的直接方法调用关系。v4 修订已在图下方补充文字注记说明"箭头表示事件流向（经领域事件总线 EventBus 路由）"（L944），但该注记与 §7.3 明确的原则"应用服务间零直接调用"形成事实一致、视觉矛盾——对于不细读脚注的下游开发者，可能误以为应用服务间存在直接依赖。
- **所在位置**：§7.1 协作关系总览图（L922–942）
- **严重程度**：轻微
- **改进建议**：在图中新增一个 EventBus 节点作为事件路由中介，将箭头从 S1 指向 EventBus 再分发至 S2/S3/S4，使图的视觉语义与文字原则一致。

---

## 二、总结

本轮审查共发现 8 个问题：2 个严重问题（BR-03 路怒覆盖缺失、§7.2 链路 B 内部矛盾）、5 个一般问题（远程车窗控制缺失、红外摄像头 SensorType 缺失、BR-08 fail-safe 链路缺失、VehicleIgnitionOffLockedEvent 订阅缺失、PerformanceWarningEvent 生产链不完整）、1 个轻微问题（§7.1 图缺失 EventBus 节点）。

以上问题集中在"需求响应充分度"维度——v4 文档在历经三轮内部审议修订后，接口契约完备性和 DTO/错误/事务等维度已较成熟，但在需求 BR 规则的覆盖完整性（特别是路怒、fail-safe、远程车窗控制三条需求）上仍存在盲区。建议优先修复两个严重问题，再逐一处理一般问题。
