## 迭代第 1 轮

1. **问题描述**：应用服务接口方法未提供具体签名——六个应用服务的接口契约全部以自然语言散文形式描述，未提供具体方法名、参数类型名或返回值类型名
   - 所在位置：§3.2–§3.7 各服务接口契约全部方法
   - 严重程度：严重
   - 改进建议：为每个方法提供具体签名，包含方法名、参数类型、返回值类型（如 `startMonitoringSession(driverId: DriverId, vehicleId: VehicleId): Result<SessionHandle, SessionError>`），对临时类型在应用层范围内给出字段定义或引用位置
2. **问题描述**：DTO 定义完全缺失——整个设计文档未定义任何一个 DTO（数据传输对象），下游接口层无法确定数据格式
   - 所在位置：全文缺失
   - 严重程度：严重
   - 改进建议：为每个应用服务的入口方法定义对应的输入 DTO 和输出 DTO，并说明 DTO 与领域对象之间的映射关系
3. **问题描述**：关键类型未定义——SensorReading、DriverStatusSnapshot、OverrideSignal 等处引用的类型未给出字段定义或结构说明
   - 所在位置：§3.2 接口契约第 2 条、§3.4 接口契约第 1 条、§3.3 接口契约第 1 条
   - 严重程度：严重
   - 改进建议：为每个跨层传输类型给出字段级定义；对已在领域层定义的类型明确引用位置；对应用层特有的 DTO/VO 给出完整结构
4. **问题描述**：异常类型仅有名称无定义——§6.1、§6.2 中列举了大量错误类型名称，但未定义任何错误枚举或错误码体系，每个错误携带什么结构化信息均未说明
   - 所在位置：§6.1、§6.2
   - 严重程度：严重
   - 改进建议：定义应用层错误枚举（如 `enum AppError { SessionNotFound, PermissionDenied(reason), ... }`），并为每个错误类别定义携带的上下文信息
5. **问题描述**：事务边界未定义——应用层职责中包含"开启/提交/回滚事务"，但全文未对任何一条业务路径定义具体的事务边界
   - 所在位置：全文缺失
   - 严重程度：一般
   - 改进建议：为每个写操作类应用服务方法标注事务边界和隔离级别，特别对 S6 批量操作说明事务策略
6. **问题描述**：S3 WebSocket 连接生命周期管理缺失——未覆盖意外断开处理、重连恢复、心跳机制、连接数限制、离线消息补推等场景
   - 所在位置：§3.4 接口契约（订阅/取消订阅）
   - 严重程度：一般
   - 改进建议：补充 WebSocket 连接生命周期管理策略，覆盖意外断开处理、重连状态恢复、心跳机制、连接限制、离线消息补推
7. **问题描述**：S4 看板缓存无事件驱动失效机制——高危告警最长可能 5 分钟延迟才反映在看板上，缓存未与领域事件联动
   - 所在位置：§3.5 接口契约第 1 条、§7.2 并发策略
   - 严重程度：一般
   - 改进建议：明确看板缓存失效策略，如订阅高危告警事件时主动使对应缓存失效，或区分宏观统计缓存与高危车辆列表缓存
8. **问题描述**：时序图存在跨层直接通信——§5.2、§5.3 时序图中多处出现领域服务直接向应用服务/HMI 发消息，违反 §4.3 明确的设计原则
   - 所在位置：§5.2（路径 2）、§5.3（路径 3）
   - 严重程度：一般
   - 改进建议：在时序图中显式引入事件总线作为事件路由中介，HMI 交互应经干预应用服务而非领域服务直控
9. **问题描述**：S5 SOS 重试策略未具体化——仅描述"按退避策略重试"，未定义退避算法、最大重试次数、最终失败处理方式
   - 所在位置：§3.6 接口契约第 5 条
   - 严重程度：一般
   - 改进建议：明确退避算法（如指数退避 1s/2s/4s/8s）、最大重试次数（如 5 次）、最终失败处理（转人工干预 + 前端提示）
10. **问题描述**：缺失验收测试场景规范——设计目标声称"可验收导向"，但全文未提供任何验收测试场景或测试用例概要
    - 所在位置：全文缺失
    - 严重程度：一般
    - 改进建议：至少为 3 条核心时序图路径补充验收测试场景概要，覆盖正常路径和关键异常路径

## 迭代第 2 轮

1. **问题描述**：S4 FleetManagementService 缺失车辆轨迹查询功能，需求文档明确将"车辆轨迹"列为 S4 职责，但接口方法契约中无轨迹查询方法
   - 所在位置：设计方案 §3.4 IFleetManagementService 接口方法契约全文
   - 严重程度：严重
   - 改进建议：在 S4 接口契约中新增 `queryVehicleTrajectory` 方法，输入含 VehicleId 或 DriverId + TimeRange，输出轨迹点序列（GPS 坐标 + 时间戳 + 车速），并在 §4.4 补充对应 DTO 定义
2. **问题描述**：S5 领域事件订阅声明与时序图存在逻辑矛盾——§3.5 声明 S5 订阅 EmergencyActivatedEvent，但 §8.3 时序图显示 S5 实际接收 DS-12 产出的 RescueReportReadyEvent
   - 所在位置：设计方案 §3.5 协作关系（L368）vs §8.3 时序图（L1144）
   - 严重程度：严重
   - 改进建议：统一事件订阅声明；若实际为 DS-12 消费 EmergencyActivatedEvent 后产出 RescueReportReadyEvent，则 §3.5 应声明 S5 订阅 RescueReportReadyEvent，并在协作说明中描述二级事件路由关系
3. **问题描述**：路径 3 时序图未按需求指定链路包含 RiskMonitoringService——需求指定链路为 RiskMonitoringService → EmergencyRescueService → RemoteGuardianshipService → InterventionService，但 §8.3 时序图全程未出现 S1
   - 所在位置：设计方案 §8.3 路径 3 时序图全文
   - 严重程度：严重
   - 改进建议：在时序图中补充 S1 作为碰撞信号的编排入口，或在 §10 设计决策中说明碰撞失能判定不走 S1 编排而走独立领域通道的原因，并同步更新需求文档
4. **问题描述**：S1 风险监测未显式覆盖需求中的"分心"与"异常驾驶行为"类别，仅涉及疲劳和活体遗留两类
   - 所在位置：设计方案 §3.1 接口契约与职责描述全文、§8.1 时序图
   - 严重程度：一般
   - 改进建议：在 §3.1 职责描述中补充分心和异常驾驶行为判定覆盖说明，确保需求项可追溯
5. **问题描述**：多个 DTO 中使用的枚举类型（RescueRequestStatus、RescueTriggerType、RescueRecordStatus）缺少完整 enum 定义块
   - 所在位置：设计方案 §4.3 L575、§4.5 L663–664
   - 严重程度：一般
   - 改进建议：参照 §4.2 中 OverrideResult 的定义格式，为上述三个枚举类型补充完整变体定义
6. **问题描述**：HeatmapPoint 和 UpgradeOptions 两个 DTO 结构类型缺少字段级定义
   - 所在位置：设计方案 §4.4 L585、§4.6 L672
   - 严重程度：一般
   - 改进建议：在相应章节中补充 HeatmapPoint（至少含 latitude、longitude、riskIntensity）和 UpgradeOptions（含 batchStrategy、scheduledWindow）的字段定义
7. **问题描述**：S6 批量写操作 createUpgradeTask 缺少幂等性机制，重复请求可能产生重复升级任务
   - 所在位置：设计方案 §3.6 createUpgradeTask 方法（L434）、§7.4 事务边界表（L998）
   - 严重程度：一般
   - 改进建议：在 CreateUpgradeTaskRequest 中增加 idempotencyKey 字段，补充幂等性语义说明
8. **问题描述**：S6 批量操作缺少车辆数量上限约束，无防御性约束说明
   - 所在位置：设计方案 §3.6 createUpgradeTask 方法（L434）、§4.6 CreateUpgradeTaskRequest（L670）
   - 严重程度：轻微
   - 改进建议：在方法或 DTO 说明中标注批量上限（如单次最多 100 辆），并增加超限错误变体
9. **问题描述**：S3 WebSocket 心跳超时后服务端处理动作不明确
   - 所在位置：设计方案 §3.3 WebSocket 连接生命周期管理表（L241–242）
   - 严重程度：轻微
   - 改进建议：在生命周期管理表中明确服务端动作（主动发送 CLOSE 帧、释放推送流和订阅关系、清除连接映射）
10. **问题描述**：S6 批量操作事务策略段落位置不当，位于 §3.5 节末尾而非 §3.6 或 §7.4
   - 所在位置：设计方案 §3.5 节末尾块引用（L383）
   - 严重程度：轻微
   - 改进建议：将该段落移至 §3.6 createUpgradeTask 方法说明之后，或合并到 §7.4 事务边界汇总表中

## 迭代第 3 轮

1. **问题描述**：S2 InterventionServiceImpl 协作关系声明订阅 RiskDeterminedEvent / RiskResolvedEvent，但 §8.2 时序图中 S2 直接接收 LifeDetectedEvent 并生成干预指令，LifeDetectedEvent 未出现在 S2 订阅列表中
   - 所在位置：§3.2 L190 vs §8.2 L1133
   - 严重程度：严重
   - 改进建议：二选一修正——（1）在 §3.2 事件订阅列表中补充 LifeDetectedEvent，并说明其触发干预指令的语义；或（2）修改 §8.2 时序图使 S2 通过 AlertTriggeredEvent（经 Alarm）而非 LifeDetectedEvent 获取干预触发信号
2. **问题描述**：VerifyRescueTokenResponse 中 TokenVerifyResult::INVALID 变体与 AppError 错误体系统存在设计冗余——所有无效情形均已通过 Err(AppError) 路径返回，INVALID 变体无任何使用场景
   - 所在位置：§4.5 VerifyRescueTokenResponse（L673–674）、TokenVerifyResult 枚举定义（L676）
   - 严重程度：严重
   - 改进建议：移除 TokenVerifyResult::INVALID 变体，仅保留 VALID，与现有 AppError 体系统一
3. **问题描述**：ReportData 类型完全未定义——§4.4 GenerateReportResponse 含 reportData: ReportData 字段，但该类型在全文（§4 DTO 定义、§5 跨层类型定义、§5.5 领域层引用类型表）中均未出现
   - 所在位置：§4.4 GenerateReportResponse（L623–627）
   - 严重程度：严重
   - 改进建议：在 §4.4 中补充 ReportData 结构类型定义，至少包含驾驶行为评分 Summary、风险分布 Map、各维度扣分明细等字段；或如已在领域层定义，列入 §5.5 领域层引用类型表并标注引用位置
4. **问题描述**：§10 决策 A4 修订理由中引用的旧版章节号（§5.2、§5.3、§4.3）在当前文档结构下已失效，读者无法定位到正确位置
   - 所在位置：§10 决策 A4 修订理由段落（L1256）
   - 严重程度：一般
   - 改进建议：将引用更新为当前文档实际章节号：§5.2 → §8.2、§5.3 → §8.3、§4.3 → §7.3，并对全文进行交叉引用一致性检查
5. **问题描述**：应用服务编排中对领域服务方法的调用签名覆盖率不一致——部分给出了具体方法名，多数场景仅以自然语言描述，开发人员需自行推断应调用的方法
   - 所在位置：§3.1 方法契约表（L156、L157）、§3.4 方法契约表（L319）、§3.5 方法契约表（L256 等）
   - 严重程度：一般
   - 改进建议：为每个应用服务方法的说明补充其所调用的领域服务方法签名，格式统一为「委托 {领域服务名}.{方法名}」
6. **问题描述**：§3.5 verifyRescueToken 的校验说明未包含对 requestedOperation 是否在凭证 authorizedOperations 集合内的校验，存在权限跨越风险
   - 所在位置：§3.5 接口方法契约 verifyRescueToken 行（L381）、§4.5 VerifyRescueTokenRequest（L668–671）
   - 严重程度：一般
   - 改进建议：增加操作匹配校验，不匹配时返回 AppError.AccessDenied(OperationNotAuthorized)，并在 AccessDenialReason 枚举中新增 OperationNotAuthorized 变体
7. **问题描述**：subscribeDriverStatus 在驾驶员无活跃行程时的 initialSnapshot 行为未定义——各字段是返回空值、默认值还是残留数据未说明
   - 所在位置：§3.3 订阅方法说明（L251）、§4.3 SubscribeDriverStatusResponse（L548–549）、§5.2 DriverStatusSnapshot（L770–780）
   - 严重程度：一般
   - 改进建议：补充无活跃行程时 initialSnapshot 的字段取值规范，同步更新 DriverStatusSnapshot 字段类型为 Option&lt;T&gt;

## 迭代第 4 轮

1. **问题描述**：BR-03 路怒判定与调节链路覆盖率不足——SensorType 缺少语音/声学传感器类型（MICROPHONE/ACOUSTIC）、InterventionService 干预类型缺少空调温度调节和音视频控制指令（AC_ADJUSTMENT、INFOTAINMENT_PLAYBACK）、路怒语音存证链路完全缺失（触发逻辑、存储生命周期、加密清除策略）
   - 所在位置：§5.1 SensorType 枚举（L780）、§3.1 S1 职责描述、§3.2 S2 干预类型体系、全文缺失路怒语音存证设计
   - 严重程度：严重
   - 改进建议：在 SensorType 补充 MICROPHONE/ACOUSTIC；在 S2 补充 AC_ADJUSTMENT、INFOTAINMENT_PLAYBACK 等干预指令；补充路怒语音存证的应用层编排策略（S1 处理语音数据 → 领域层判定路怒 → S2 编排存证录制与边缘存储），明确存证生命周期管理与 BR-04 隐私边界衔接
2. **问题描述**：§7.2 链路 B 描述称 S5 通过 EmergencyActivatedEvent→AlertTriggeredEvent 路径触发（L982），与 §3.5 S5 协作声明（订阅 RescueReportReadyEvent，L371）和 §8.3 时序图（S5 通过 RescueReportReadyEvent 接收触发，L1205–1206）矛盾
   - 所在位置：§7.2 链路 B（L982） vs §3.5 S5 协作关系（L371） vs §8.3 时序图（L1205–1206）
   - 严重程度：严重
   - 改进建议：将 §7.2 链路 B 的 S5 分支统一为"S5 EmergencyRescueServiceImpl（通过 RescueReportReadyEvent 路径，该事件由 DS-12 消费 EmergencyActivatedEvent 后产出）→ RescueReportPort 投递 120"
3. **问题描述**：远程车窗控制功能（需求 §3.4 要求家属授权下远程车窗操作）未在应用服务接口中体现——S3 六个接口方法无车窗控制、S5 RescueOperation 枚举不含车窗操作、安全门控原则列举车窗控制但无服务方法对应
   - 所在位置：§3.3 S3 接口方法契约表（L249–256）、§4.5 RescueOperation 枚举（L679）、§6.3 安全门控原则（L905）
   - 严重程度：一般
   - 改进建议：在 S3 IRemoteGuardianshipService 新增 controlVehicleWindow 方法，或在 S5 RescueOperation 枚举增加 RemoteWindowControl 变体
4. **问题描述**：SensorType 枚举缺少后排红外摄像头类型，需求 §3.1 明确新增"后排红外摄像头"作为独立数据源
   - 所在位置：§5.1 SensorType 枚举定义（L780）
   - 严重程度：一般
   - 改进建议：在 SensorType 枚举中新增 REAR_IR_CAMERA 变体
5. **问题描述**：BR-08 失效保护（fail-safe）的应用层编排链路缺失——边缘侧 S1/S2 未定义接收传感器自检故障信号和编排 HMI 语音报警的方法或事件订阅；云端 S4 无监测脱线车辆查询方法及传感器故障事件订阅；3 秒时效性约束未标注
   - 所在位置：§3.1 S1 方法契约表（L153–160）、§3.2 S2 方法契约表（L195–199）、§3.4 S4 方法契约表（L317–324）、§3.4 S4 协作关系事件订阅列表（L311）
   - 严重程度：一般
   - 改进建议：在 S1/S2 声明对传感器自检故障信号的订阅及"3 秒内发起 HMI 语音告警"路径；在 S4 补充 SensorFaultEvent 或 VehicleMonitoringOfflineEvent 订阅和新增加 getOfflineVehicles 查询方法
6. **问题描述**：S1 协作关系中缺失 VehicleIgnitionOffLockedEvent 订阅声明——§8.2 时序图（L1133–1134）明确 S1 通过此事件触发活体检测，但 §3.1 S1 协作关系事件订阅列表（L147）未列出
   - 所在位置：§3.1 S1 协作关系事件订阅列表（L147） vs §8.2 路径 2 时序图（L1133–1134）
   - 严重程度：一般
   - 改进建议：在 §3.1 S1 协作关系事件订阅列表补充 VehicleIgnitionOffLockedEvent
7. **问题描述**：60 分阈值绩效预警的事件生产链未在应用层明确——S4 订阅 PerformanceWarningEvent 仅覆盖消费侧，生产侧（DS-09 ScoringService 如何产出、即时触发还是周期触发）未说明
   - 所在位置：§3.4 S4 协作关系（L309–311）、方法契约 subscribePerformanceWarning（L323）
   - 严重程度：一般
   - 改进建议：在 §3.4 协作关系补充说明 PerformanceWarningEvent 由 DS-09 ScoringService 在每次评分完成后判断并产出
8. **问题描述**：§7.1 协作关系图未包含 EventBus 节点，视觉上误导为服务间直接调用，与"应用服务间零直接调用"原则形成视觉矛盾
   - 所在位置：§7.1 协作关系总览图（L922–942）
   - 严重程度：轻微
    - 改进建议：在图中新增 EventBus 节点作为事件路由中介，箭头从 S1 指向 EventBus 再分发至 S2/S3/S4

## 迭代第 5 轮

1. **问题描述**：`startLifeDetection` 公开方法调用路径与事件驱动触发路径未明确界定——§3.1 将其列为公开方法契约，§8.2 时序图显示通过 EventBus 触发，两条路径并存造成实现歧义。
   - 所在位置：§3.1 接口方法契约表 `startLifeDetection` 行（L157） vs §8.2 时序图（L1203–1205） vs §3.1 协作关系事件订阅列表（L147）
   - 严重程度：一般
   - 改进建议：二选一明确——（1）若 `startLifeDetection` 仅作为事件处理器内部方法，应从公开接口契约中移除或标注"仅供内部事件驱动调用"；或（2）若保留为公开方法，则应在 §8.2 时序图中改为外部调用方直接调用，而非经 EventBus 路由。
2. **问题描述**：`BatchStrategy` 类型在 §4.6 UpgradeOptions 中被引用但全文任何位置（含 §4 DTO、§5 跨层类型、§5.5 领域层引用表）均未给出定义，下游开发者无法确定分批策略的数据结构。
   - 所在位置：§4.6 UpgradeOptions（L763）
   - 严重程度：一般
   - 改进建议：在 §4.6 UpgradeOptions 之后补充 `BatchStrategy` 的枚举或结构类型定义（如 `BY_MODEL`、`BY_REGION`、`BY_BATCH_SIZE(size: UInt32)`），或列入 §5.5 并标注领域层定义位置。
3. **问题描述**：`controlVehicleWindow` 异步指令执行-确认语义缺失——方法返回 `Result<Unit, AppError>`，将"指令下发成功"与"车窗操作完成"混同为同一个 `Unit` 返回值，前端无法区分指令已下发但未执行、正在运动中、已到位的不同状态，缺少状态确认回调或轮询查询机制。
   - 所在位置：§3.3 接口方法契约 `controlVehicleWindow` 行（L272）、§4.3 ControlVehicleWindowRequest（L600–604）
   - 严重程度：一般
   - 改进建议：（1）明确返回值语义——`Ok(Unit)` 表示"指令已成功下发至 IoTDA"而非"车窗已到位"；（2）新增 `queryWindowStatus` 查询方法或在 DriverStatusSnapshot 中增加车窗位置字段；（3）补充车窗控制超时未确认的异常处理策略。
4. **问题描述**：运营管理界面 DTO 缺少人类可读标识字段——OfflineVehicleInfo、HighRiskDriverSummary、RescueRecordSummary 仅含 VehicleId/DriverId，不含车牌号和驾驶员姓名，导致前端大屏需额外查询，增加接口层调用次数和复杂度。
   - 所在位置：§4.4 OfflineVehicleInfo（L627–632）、HighRiskDriverSummary（L650–654）、§4.5 RescueRecordSummary（L744–748）
   - 严重程度：一般
   - 改进建议：在上述 DTO 中增加 `licensePlate: String` 和 `driverName: String` 字段；若出于数据最小化原则不愿包含，则至少在设计决策中说明理由。
5. **问题描述**：CQRS 读模型访问机制在应用层未定义——`queryAlertHistory` 和 `queryVehicleTrajectory` 标注"数据来源为 CQRS 读模型投影"但未定义访问端口/仓储/查询服务，下游开发者无法确定通过哪个接口获取投影数据。
   - 所在位置：§3.1 `queryAlertHistory` 方法说明（L159）、§3.4 `queryVehicleTrajectory` 方法说明（L337）
   - 严重程度：轻微
   - 改进建议：在方法说明中补充委托目标（如 `AlertProjectionRepository` 或 `TrajectoryQueryService`），或在协作关系中增加读模型端口依赖声明。
6. **问题描述**：`QueryTrajectoryRequest` 双参数交叉校验缺失——`vehicleId` 和 `driverId` 均为可选但约定"至少提供一个"，未覆盖二者同时提供且不匹配的场景，缺少处理策略。
   - 所在位置：§4.4 QueryTrajectoryRequest（L692–696）
   - 严重程度：轻微
   - 改进建议：补充说明：若 vehicleId 与 driverId 同时提供但不匹配，返回空轨迹序列或 `AppError.InvalidParameterCombination`。
7. **问题描述**：离线消息队列 7 天告警保留策略未衔接隐私边界要求——告警内容可能含敏感信息，但与 BR-04 隐私边界的对齐（加密方式、过期清除、家属权限过滤）未明确。
   - 所在位置：§3.3 WebSocket 生命周期表"离线消息补推"行（L260）
   - 严重程度：轻微
   - 改进建议：补充隐私约束——（1）离线告警数据加密存储、到期自动清除；（2）补推时按当前有效授权范围过滤；（3）已撤销监护权限的家属不补推。
8. **问题描述**：OTAManagementService 接口中 S6 依赖领域事件订阅声明不完整——S6 协作关系中仅列出 3 个 OTA 状态事件，OTAUpgradeStartedEvent 在链路 D 和 S6 协作关系中均未显式声明，S6 与 S2 的 OTA 事件消费分工未形成统一交叉引用说明。
   - 所在位置：§3.6 S6 协作关系事件订阅列表（L444） vs §7.2 链路 D（L1052–1067） vs §3.6 OTA 回滚策略（L428–438）
   - 严重程度：轻微
   - 改进建议：在 §3.6 S6 协作关系中明确 OTA 领域事件订阅范围，并与 §7.2 链路 D 中 S2 的订阅分工形成统一交叉引用说明。
