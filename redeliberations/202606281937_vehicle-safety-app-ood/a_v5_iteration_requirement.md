根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

本轮审查（b_v4_diag_v1，质询结果为 LOCATED）共发现 8 个问题：

**严重问题（2项）：**

1. **BR-03 路怒判定与调节链路覆盖率不足**（严重）：SensorType 枚举缺少 MICROPHONE/ACOUSTIC 语音传感器类型；S2 InterventionService 干预类型缺少 AC_ADJUSTMENT、INFOTAINMENT_PLAYBACK 等空调/娱乐系统控制指令；路怒语音存证链路完全缺失（触发逻辑、存储生命周期、加密清除策略均未体现）。所在位置：§5.1 SensorType 枚举（L780）、§3.1 S1 职责描述、§3.2 S2 干预类型体系、全文缺失存证设计。改进建议：(1) 在 §5.1 SensorType 中补充 MICROPHONE/ACOUSTIC 变体；(2) 在 S2 干预类型体系中补充 AC_ADJUSTMENT、INFOTAINMENT_PLAYBACK 等变体；(3) 补充路怒语音存证应用层编排策略（S1 处理语音传感器数据 → 领域层判定路怒 → S2 编排存证录制与边缘存储），明确存证生命周期管理与 BR-04 隐私边界衔接。

2. **§7.2 链路 B 描述与 §3.5 订阅声明及 §8.3 时序图存在逻辑矛盾**（严重）：§7.2 链路 B（L982）称 S5 通过 EmergencyActivatedEvent→AlertTriggeredEvent 路径触发，但 §3.5 S5 协作声明（L371）订阅 RescueReportReadyEvent，§8.3 时序图（L1205–1206）显示 S5 通过 RescueReportReadyEvent 接收触发。三处描述不一致。改进建议：将 §7.2 链路 B 的 S5 分支描述统一为"S5 EmergencyRescueServiceImpl（通过 RescueReportReadyEvent 路径，该事件由 DS-12 消费 EmergencyActivatedEvent 后产出）→ RescueReportPort 投递 120"。

**一般问题（5项）：**

3. **远程车窗控制功能未在应用服务接口中体现**（一般）：需求 §3.4 要求家属授权下远程车窗操作，但 S3 六个接口方法无车窗控制、S5 RescueOperation 枚举不含车窗操作、§6.3 安全门控列举车窗控制却无对应方法。所在位置：§3.3 S3 方法契约表（L249–256）、§4.5 RescueOperation 枚举（L679）、§6.3 安全门控（L905）。改进建议：在 S3 IRemoteGuardianshipService 新增 controlVehicleWindow 方法（输入含 familyAccountId、driverId、windowOperation、二次验证凭证），或在 S5 RescueOperation 枚举增加 RemoteWindowControl 变体。

4. **SensorType 枚举缺少后排红外摄像头类型**（一般）：需求 §3.1 明确新增"后排红外摄像头"作为独立数据源，但 §5.1 SensorType 枚举（L780）仅含 DMS_CAMERA/MILLIMETER_WAVE_RADAR/ACCELEROMETER/PHYSIOLOGICAL_MONITOR，缺失 REAR_IR_CAMERA。改进建议：在 SensorType 枚举中新增 REAR_IR_CAMERA 变体。

5. **BR-08 失效保护（fail-safe）的应用层编排链路缺失**（一般）：(1) 边缘侧 S1/S2 未定义接收传感器自检故障信号、编排 HMI 语音报警的方法或事件订阅；(2) 云端 S4 无监测脱线车辆查询方法及传感器故障事件订阅；(3) 3 秒时效性约束未标注。所在位置：§3.1 S1 方法契约表（L153–160）、§3.2 S2 方法契约表（L195–199）、§3.4 S4 方法契约表（L317–324）和事件订阅列表（L311）。改进建议：(1) 在 S1/S2 声明对传感器自检故障信号的订阅及"3 秒内发起 HMI 语音告警"路径；(2) 在 S4 补充 SensorFaultEvent 或 VehicleMonitoringOfflineEvent 订阅和新增 getOfflineVehicles 查询方法；(3) 标注 3 秒 SLA。

6. **S1 协作关系中缺失 VehicleIgnitionOffLockedEvent 订阅声明**（一般）：§8.2 时序图（L1133–1134）显示 S1 通过 VehicleIgnitionOffLockedEvent 触发活体检测，但 §3.1 S1 协作关系事件订阅列表（L147）未列出此事件。改进建议：在 §3.1 S1 协作关系的事件订阅列表中补充 VehicleIgnitionOffLockedEvent。

7. **60 分阈值绩效预警的事件生产链未在应用层明确**（一般）：S4 订阅 PerformanceWarningEvent 仅覆盖消费侧（§3.4 L311、L323），PerformanceWarningEvent 由哪个领域服务（DS-09 ScoringService）在何种条件下产出（即时触发还是周期触发）未说明。改进建议：在 §3.4 协作关系补充说明 PerformanceWarningEvent 由 DS-09 ScoringService 在每次评分计算完成后判断（low-score → 即时事件触发）并产出。

**轻微问题（1项）：**

8. **§7.1 协作关系图未包含 EventBus 节点**（轻微）：§7.1 图（L922–942）箭头从 S1 直接指向 S2/S3/S4，虽已有文字注记说明事件流向，但视觉效果与"应用服务间零直接调用"原则矛盾。改进建议：在图中新增 EventBus 节点作为事件路由中介，箭头从 S1 指向 EventBus 再分发至 S2/S3/S4。

## 历史迭代回顾

**已解决的问题（前 3 轮反馈已修复，本轮未再出现）：**
- 第 1 轮全部 10 项问题（接口方法签名、DTO 定义、关键类型定义、错误枚举、事务边界、WebSocket 生命周期、看板缓存、时序图跨层通信、SOS 重试策略、验收测试场景）已在后续迭代中修复。
- 第 2 轮全部 10 项问题（车辆轨迹查询、S5 事件订阅、路径 3 缺失 S1、分心/异常驾驶覆盖、DTO 枚举定义、HeatmapPoint/UpgradeOptions 字段、幂等性、批量上限、WebSocket 心跳动作、批量事务段落位置）已在第 3 轮修复。
- 第 3 轮全部 7 项问题（S2 LifeDetectedEvent 订阅、TokenVerifyResult INVALID 冗余、ReportData 类型、交叉引用章节号、领域服务方法调用签名覆盖率、操作匹配校验、subscribeDriverStatus 快照行为）已在第 4 轮修复。

**持续存在的问题（与前 3 轮反馈存在关联但以新形式出现的结构性缺陷）：**
- 事件订阅声明与时序图不一致的模式问题：第 2 轮问题 2（S5 订阅 EmergencyActivatedEvent vs RescueReportReadyEvent）、第 3 轮问题 1（S2 订阅缺少 LifeDetectedEvent）、本轮问题 2（§7.2 S5 触发路径描述矛盾）和问题 6（S1 缺少 VehicleIgnitionOffLockedEvent 订阅）均属同一模式——多个文档位置的事件订阅声明未与时序图统一。建议本轮修复后进行系统性的交叉校验，避免遗漏其他实例（如质询报告中建议的 §8.2 L1156 LifeDetectedEvent 驱动 S5 救援预判与 §3.5 S5 订阅列表不匹配）。
- 协作关系图视觉语义问题：第 3 轮已新增文字注记说明事件路由方式，但本轮问题 8 指出视觉层面仍存在"服务间直接箭头"的误导——文字注记不足以消除阅读歧义，需从图结构层面修正。

**新发现的问题（本轮首次识别）：**
- 问题 1（BR-03 路怒覆盖缺失）、问题 3（远程车窗控制缺失）、问题 4（后排红外摄像头 SensorType 缺失）、问题 5（BR-08 fail-safe 链路缺失）、问题 7（PerformanceWarningEvent 生产链不完整）——均为需求响应充分度的盲区，前 3 轮审查主要集中在接口契约完备性、DTO/类型/错误定义、事务边界等维度，本轮迭代首次深入检查各 BR 业务规则的端到端覆盖完整性。

## 上一轮产出路径

D:\软件测试\redeliberations\202606281937_vehicle-safety-app-ood\a_v4_copy_from_v3.md

## 用户需求

D:\软件测试\redeliberations\202606281937_vehicle-safety-app-ood\requirement.md
