# 再审议判定报告（v4）

## 判定结果

RETRY

## 判定理由

诊断报告识别出 8 个问题：2 个严重（BR-03 路怒链路覆盖缺失、§7.2 链路 B 内部矛盾）、5 个一般（远程车窗控制缺失、红外摄像头 SensorType 缺失、BR-08 fail-safe 链路缺失、VehicleIgnitionOffLockedEvent 订阅缺失、PerformanceWarningEvent 生产链不完整）、1 个轻微（§7.1 图缺失 EventBus 节点）。

质询报告结果为 LOCATED，确认诊断报告证据充分、逻辑自洽、无矛盾。组件 B 内部循环实际轮次（1）< 最大轮次（12），质询已提前确认问题定位有效。

根据判定标准，审查报告包含严重和一般等级问题，判定为 RETRY。

## 需要解决的问题

- **问题描述**：BR-03 路怒判定与调节链路覆盖率不足——SensorType 缺少语音/声学传感器类型（MICROPHONE/ACOUSTIC）、InterventionService 干预类型缺少空调温度调节和音视频控制指令（AC_ADJUSTMENT、INFOTAINMENT_PLAYBACK）、路怒语音存证链路完全缺失（触发逻辑、存储生命周期、加密清除策略）
- **所在位置**：§5.1 SensorType 枚举（L780）、§3.1 S1 职责描述、§3.2 S2 干预类型体系、全文缺失路怒语音存证设计
- **严重程度**：严重
- **改进建议**：在 SensorType 补充 MICROPHONE/ACOUSTIC；在 S2 补充 AC_ADJUSTMENT、INFOTAINMENT_PLAYBACK 等干预指令；补充路怒语音存证的应用层编排策略（S1 处理语音数据 → 领域层判定路怒 → S2 编排存证录制与边缘存储），明确存证生命周期管理与 BR-04 隐私边界衔接

- **问题描述**：§7.2 链路 B 描述称 S5 通过 EmergencyActivatedEvent→AlertTriggeredEvent 路径触发（L982），与 §3.5 S5 协作声明（订阅 RescueReportReadyEvent，L371）和 §8.3 时序图（S5 通过 RescueReportReadyEvent 接收触发，L1205–1206）矛盾
- **所在位置**：§7.2 链路 B（L982） vs §3.5 S5 协作关系（L371） vs §8.3 时序图（L1205–1206）
- **严重程度**：严重
- **改进建议**：将 §7.2 链路 B 的 S5 分支统一为"S5 EmergencyRescueServiceImpl（通过 RescueReportReadyEvent 路径，该事件由 DS-12 消费 EmergencyActivatedEvent 后产出）→ RescueReportPort 投递 120"

- **问题描述**：远程车窗控制功能（需求 §3.4 要求家属授权下远程车窗操作）未在应用服务接口中体现——S3 六个接口方法无车窗控制、S5 RescueOperation 枚举不含车窗操作、安全门控原则列举车窗控制但无服务方法对应
- **所在位置**：§3.3 S3 接口方法契约表（L249–256）、§4.5 RescueOperation 枚举（L679）、§6.3 安全门控原则（L905）
- **严重程度**：一般
- **改进建议**：在 S3 IRemoteGuardianshipService 新增 controlVehicleWindow 方法，或在 S5 RescueOperation 枚举增加 RemoteWindowControl 变体

- **问题描述**：SensorType 枚举缺少后排红外摄像头类型，需求 §3.1 明确新增"后排红外摄像头"作为独立数据源
- **所在位置**：§5.1 SensorType 枚举定义（L780）
- **严重程度**：一般
- **改进建议**：在 SensorType 枚举中新增 REAR_IR_CAMERA 变体

- **问题描述**：BR-08 失效保护（fail-safe）的应用层编排链路缺失——边缘侧 S1/S2 未定义接收传感器自检故障信号和编排 HMI 语音报警的方法或事件订阅；云端 S4 无监测脱线车辆查询方法及传感器故障事件订阅；3 秒时效性约束未标注
- **所在位置**：§3.1 S1 方法契约表（L153–160）、§3.2 S2 方法契约表（L195–199）、§3.4 S4 方法契约表（L317–324）、§3.4 S4 协作关系事件订阅列表（L311）
- **严重程度**：一般
- **改进建议**：在 S1/S2 声明对传感器自检故障信号的订阅及"3 秒内发起 HMI 语音告警"路径；在 S4 补充 SensorFaultEvent 或 VehicleMonitoringOfflineEvent 订阅和新增加 getOfflineVehicles 查询方法

- **问题描述**：S1 协作关系中缺失 VehicleIgnitionOffLockedEvent 订阅声明——§8.2 时序图（L1133–1134）明确 S1 通过此事件触发活体检测，但 §3.1 S1 协作关系事件订阅列表（L147）未列出
- **所在位置**：§3.1 S1 协作关系事件订阅列表（L147） vs §8.2 路径 2 时序图（L1133–1134）
- **严重程度**：一般
- **改进建议**：在 §3.1 S1 协作关系事件订阅列表补充 VehicleIgnitionOffLockedEvent

- **问题描述**：60 分阈值绩效预警的事件生产链未在应用层明确——S4 订阅 PerformanceWarningEvent 仅覆盖消费侧，生产侧（DS-09 ScoringService 如何产出、即时触发还是周期触发）未说明
- **所在位置**：§3.4 S4 协作关系（L309–311）、方法契约 subscribePerformanceWarning（L323）
- **严重程度**：一般
- **改进建议**：在 §3.4 协作关系补充说明 PerformanceWarningEvent 由 DS-09 ScoringService 在每次评分完成后判断并产出

- **问题描述**：§7.1 协作关系图未包含 EventBus 节点，视觉上误导为服务间直接调用，与"应用服务间零直接调用"原则形成视觉矛盾
- **所在位置**：§7.1 协作关系总览图（L922–942）
- **严重程度**：轻微
- **改进建议**：在图中新增 EventBus 节点作为事件路由中介，箭头从 S1 指向 EventBus 再分发至 S2/S3/S4
