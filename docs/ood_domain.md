# 车载安全监测系统 领域层 OOD 设计方案（a_v10 / v1）

> 本文档为「智能物联——基于多传感器融合的车载安全监测系统」的架构级 OOD 设计方案，采用 DDD（领域驱动设计）分层思想，聚焦领域层的实体、值对象、聚合根、领域服务和领域事件设计。**后端实现语言为 Java，框架采用 Spring Boot**，领域层以纯 POJO + DDD 模式组织。方案在 Java 类型系统能力范围内做抽象，不涉及具体实现代码。本版（a_v10_v1）在 a_v9_v2 基础上，修复审查确认的 8 个持续问题：VehicleRepository 仓储接口契约补充、DS-06 EmergencyResponseService 方法签名补充、DS-15 OTAUpdateService 方法签名补充、碰撞失能告警持久化路径歧义修正、救援授权模型形式化、DomainEvent aggregateId 类型安全性修正、评分周期边界语义定义、OTA 与传感器自检并发策略补充，详见文末「修订说明（a_v10_v1）」。

---

## 一、概述

### 设计目标

本系统领域层的核心使命是：**接收多维传感器感知输入，基于业务规则执行融合风险判定，按风险等级驱动分级干预与通知，同时支撑远程监护、车队管理及应急救援等协作场景**。

设计遵循以下目标：

- **安全优先**：边缘侧本地兜底的核心判定链路不可降级，断网时安全告警链路仍成立。
- **职责内聚**：感知、判定、干预、通知、管理各自归属清晰的领域模块，模块间通过领域事件解耦。
- **隐私内建**：原始敏感数据（人脸图像、语音存证）的存储边界与脱敏要求直接体现在领域对象的设计约束中。
- **可验收导向**：本期设计聚焦软件判定逻辑与控制指令的正确性，不依赖真实硬件/算法，所有领域逻辑在模拟数据下可复现验证。

### 核心抽象层次

系统在领域层分为四个抽象层次：

1. **聚合根** — 事务一致性边界，统领一组关联实体与值对象，外部只能通过聚合根访问其内部对象。
2. **实体** — 有唯一标识、有生命周期的领域对象，其相等性由标识决定。
3. **值对象** — 无独立标识、由属性值定义相等性的不可变领域概念。
4. **领域服务** — 无状态的操作抽象，封装不属于任何单一聚合根的业务规则与协调逻辑。

### 风险等级与干预映射的总览约定

为消除前一轮「通用 RiskLevel→干预映射」与「具体场景行为契约」之间的矛盾，本版确立如下总览约定（细节见 VO-01、DS-07、决策 15）：

- 干预策略**不是**由单一 RiskLevel 决定，而是由 **AlertType × RiskLevel 的二维组合**决定。同一 RiskLevel 在不同 AlertType 下对应不同的干预指令集合（如疲劳 L2 = 氛围灯变橙、路怒 L2 = 环境调节、分心 L2 = 告警）。
- **RiskLevel.L1 为预留等级**：本期 BR-01~BR-08 无任一规则映射到 L1，L1 无触发路径，保留以备未来扩展（见 VO-01、决策 15）。
- 风险**解除**不是 RiskLevel 的取值，而是独立的领域事件 **RiskResolvedEvent**（见 VO-02 说明、决策 16），用以表达「此前成立的某类风险已不再持续」这一状态转换。

---

## 二、模块划分

系统领域层按职责拆分为以下模块，模块间依赖方向为单向——上层模块可依赖下层模块，下层不可反向依赖上层。

### 2.1 模块一览

| 模块 | 职责 | 依赖方向 |
|------|------|----------|
| `domain.model` | 核心领域对象：实体、值对象、聚合根的纯数据结构定义，不含业务逻辑 | 无外部依赖 |
| `domain.risk` | 风险判定领域服务，含两类调用模型：①**流式融合判定门面**（RiskDeterminationService + 子服务 BR-01 疲劳、BR-03 路怒、分心检出）处理持续到达的 DMS/生理/语音流式感知，并在风险由成立转为解除时产出 RiskResolvedEvent；②**事件触发型独立判定服务**（BR-02 活体遗留、BR-06 碰撞失能），由各自的领域事件触发、独立产出领域事件，不经门面汇总；以及三级风险等级的统一映射；**告警持久化服务**（AlertPersistenceService）订阅判定事件、通过 TripRepository 创建 SafetyAlertEvent 并发出 AlertTriggeredEvent | 依赖 `domain.model`、`domain.event` |
| `domain.intervention` | 干预与反馈领域服务：基于 **AlertType × RiskLevel 二维映射**的分级指令生成、HMI 反馈控制、CAN 标准指令下发逻辑、驾驶员覆盖检测 | 依赖 `domain.model`、`domain.event` |
| `domain.family` | 远程监护领域服务：家属权限管理与常规撤销闭环（BR-07）、家属端自动激活接入（高危场景）、远程对讲/视频/车窗控制授权与执行、**家属端常态状态快照周期同步（≥1Hz 推送绿/黄/红状态）** | 依赖 `domain.model`、`domain.event` |
| `domain.fleet` | 车队运营管理领域服务：看板聚合查询、钻取查询、驾驶评分（BR-05，含 Driver 综合风险评分计算与写回）、驾驶行为追踪（DS-17，急刹/急加速全程计数）、报告生成与导出、绩效预警推送 | 依赖 `domain.model`、`domain.event` |
| `domain.emergency` | 应急救援领域服务：SOS 自动呼叫、车辆状态共享、远程解锁授权、健康档案调取 | 依赖 `domain.model`、`domain.event` |
| `domain.privacy` | 隐私保护领域服务：BR-04 脱敏边界约束、数据授权校验、路怒语音存证的生命周期管理（通过 RoadRageVoiceRecordRepository 操作 AR-05 RoadRageVoiceRecord 聚合根） | 依赖 `domain.model`、`domain.event` |
| `domain.ota` | OTA 升级管理领域服务：版本管理、升级包下发、断点续传、完整性校验、失败回滚、静默升级（升级状态由 Vehicle 聚合的 OTAUpgradeStatus 值对象承载，服务保持无状态） | 依赖 `domain.model`、`domain.event` |
| `domain.monitor` | 系统自检领域服务：BR-08 传感器故障检测、监测脱线标记、失效告警 | 依赖 `domain.model`、`domain.event` |
| `domain.event` | 领域事件定义：系统中所有领域事件的类型与载体，供各模块间解耦通信 | 仅依赖 `domain.model` 中的标识类型 |

### 2.2 依赖原则

- `domain.model` 是最底层，不依赖任何其他模块。
- `domain.event` 仅依赖 `domain.model` 中的标识类型（如司机 ID、行程 ID），不依赖聚合根内部结构。
- 各领域服务模块之间**禁止直接调用**，跨模块协作统一通过领域事件完成。
- 各模块**允许同时依赖** `domain.model` 和 `domain.event`。

---

## 三、核心抽象

### 3.1 聚合根

#### AR-01：Trip（行驶行程）

**角色与职责**：系统的核心聚合根，代表一次从点火到熄火的完整行驶行程。Trip 是数据汇聚与告警生成的枢纽——它持有该行程中产生的所有生理体征快照，并负责在该行程生命期内产出的安全告警事件建立关联。Trip 不直接执行风险判定（由领域服务完成），但它是判定结果与干预指令的作用上下文。此外，Trip 聚合持有该行程中的**驾驶行为计数器**（DrivingBehaviorCounters，VO-16），用于统计急刹/急加速等非告警型驾驶行为事件，作为评分计算的输入。Trip 聚合同时持有该行程的 **TripScore（VO-05）**——由 ScoringService 在行程结束时计算并通过 TripRepository 写入，作为该行程的评分结果持久存储。

**类型形态**：`class`（聚合根）。Trip 具有独立的生命周期（点火创建、熄火终结）和唯一标识（行程 ID），其内部一致性需要事务边界保护，因此是聚合根而非普通实体。

**协作关系**：
- 与 Driver、Vehicle 是一对多归属关系（一个 Trip 属于一个 Driver 和一个 Vehicle），Trip 通过标识引用它们。
- 内部持有 PhysiologicalSnapshot 值对象集合（快照不可变，新增即追加）。
- 内部持有 **DrivingBehaviorCounters（VO-16）** 值对象，由基础设施层的持续加速度监测组件在检测到急刹/急加速事件时增量更新。
- 内部持有 **TripScore（VO-05）** 值对象，由 ScoringService（DS-09）在行程结束时计算并通过 TripRepository 写入，作为该行程的持久化评分结果。
- 内部持有 **L3DurationTracker（VO-17）** 值对象，由 DS-08 PermissionService 在 L3 检出/解除时创建和更新，用于追踪当前 Driver 的 L3 持续时长以判定 60s 阈值授予（Trip:Driver=1:1，一个行程仅有一个 Driver，故一个 Trip 内至多存在一个活跃的 L3DurationTracker）。
- 产出 SafetyAlertEvent，事件归属 Trip 但可独立于 Trip 生命周期存在。
- 领域服务 RiskDeterminationService 以 Trip 为判定上下文，判定结果写入 Trip 关联的告警列表。
- 领域服务 ScoringService 在行程结束时计算 TripScore，通过 TripRepository 将 TripScore 值对象写入 Trip 聚合；同时 ScoringService 负责聚合 TripScore 为 Driver 级综合风险评分并通过 DriverScoreUpdatedEvent 写回 Driver 聚合（见 DS-09、AR-02）。

---

#### AR-02：Driver（驾驶员）

**角色与职责**：代表被监测的驾驶员，是被监护的主体。Driver 聚合管理驾驶员的基础身份信息、脱敏人脸特征向量、生理健康基准、综合风险评分，以及与其 1:1 绑定的健康档案（DriverHealthProfile）。Driver 不持有行程列表（行程独立为聚合根），仅通过标识关联。

**综合风险评分的赋值与更新链路**：Driver 的"综合风险评分"是**跨行程聚合**指标——其取值由 ScoringService（DS-09）在每次行程评分完成后重新计算。计算口径为 Driver 名下近期（如近 30 天或近 N 次行程）所有 TripScore 的加权平均，clamp 至 [0,100]。ScoringService 计算完成后通过 **DriverScoreUpdatedEvent** 事件写回 Driver 聚合——由 DriverScoreUpdateService（DS-18）消费该事件后通过 DriverRepository 更新 Driver 的综合风险评分字段。该链路闭合了"谁计算、何时更新、通过什么机制写回"的完整闭环。

**类型形态**：`class`（聚合根）。Driver 拥有唯一标识（驾驶员 ID），其生命周期独立于行程与车辆（换车、换行程不影响 Driver 身份），且内部 DriverHealthProfile 需要通过 Driver 聚合根才能访问和修改，符合聚合根的事务一致性边界。

**协作关系**：
- 与 Vehicle 通过 Trip 间接关联。
- 内部持有 DriverHealthProfile 实体（1:1）。
- 与 SystemAccount（家属角色）是多对多监护关系，监护关系的变更通过领域事件通知。
- 接收 PerformanceWarningEvent（当评分 < 60 时），但不直接消费，由通知模块路由给管理员。
- 接收 **DriverScoreUpdatedEvent**，其综合风险评分字段由 DriverScoreUpdateService（DS-18）消费该事件后通过 DriverRepository 写回——与 DS-09、DS-18 的写回路径表述一致。
- 驾驶员注销/账号删除时，产出 **DriverDeactivatedEvent**，其监护关系清理与历史行程数据处理策略见 §5.4 边界条件 (2)。

---

#### AR-03：Vehicle（车辆）

**角色与职责**：代表搭载监测终端的物理车辆资产。Vehicle 聚合管理车辆标识（车牌号、VIN）、终端序列号、传感器自检状态（在线/离线/故障），当前监测脱线标记（BR-08），以及当前固件版本和 OTA 升级状态。Vehicle 不负责判定逻辑，但为判定引擎提供设备状态上下文，并为 OTA 升级管理提供版本比对基准。

**类型形态**：`class`（聚合根）。Vehicle 有唯一标识（VIN/终端序列号），其生命周期独立于行程和驾驶员，设备状态和固件升级状态需要以事务一致性方式更新（如一次自检结果需原子性覆盖多个传感器的状态），因此适合作为聚合根。

**协作关系**：
- 与 Driver 通过 Trip 间接关联。
- 传感器自检状态由领域服务 SensorSelfCheckService 更新，故障时触发 SensorFailureEvent。
- 与管理员角色的 SystemAccount 是管理关系。
- 车门锁状态由 EmergencyRescueService（远程解锁授权）与远程车窗/车门控制流程（场景 11）经授权后更新。
- 持有当前固件版本（以 VO-08 OTAVersion 表达）——DS-15 OTAUpdateService 在执行版本比对时，通过 Vehicle 聚合获取当前运行版本以决定是否启动升级。OTA 升级完成后，领域层通过 Vehicle 聚合更新固件版本号。
- 持有 **OTAUpgradeStatus（VO-19）** 值对象，封装本次升级的状态机阶段（待下发→传输中→校验中→已就绪→升级中→完成/回滚）及断点续传偏移量——将原本由 DS-15 持有的"升级包状态机"状态建模为 Vehicle 聚合内部的值对象，DS-15 每次处理升级事件（传输进度、校验结果、刷写结果）时作为无状态服务读取和更新 Vehicle 聚合中的 OTAUpgradeStatus。

---

#### AR-04：SystemAccount（系统账户）

**角色与职责**：代表外部监护与管理主体——家属、车队管理员或救援机构。SystemAccount 聚合管理账户标识、联系方式、**角色（AccountRole，见 VO-14）**、通知权限和监护/管理范围。不同角色拥有不同的数据访问和操作权限。

**类型形态**：`class`（聚合根）。SystemAccount 拥有唯一标识（账号 ID）和独立生命周期，角色变更、权限授予等操作需要事务一致性，因此是聚合根。

**协作关系**：
- 家属角色（AccountRole.FAMILY）：与 Driver 存在监护关系，权限受 Permission 值对象约束（BR-07）。其通知行为受 **NotificationPreference（VO-18）** 值对象约束——家属可配置订阅的风险等级集合（如仅订阅 L2+L3 或全部等级），告警推送模块依据该偏好按等级过滤后推送。
- 管理员角色（AccountRole.MANAGER）：拥有全局统计权限，接收车队级告警与绩效预警。
- 救援机构角色（AccountRole.RESCUE）：接收 SOS 自动呼叫与事故上报，享有远程解锁授权与健康档案调取权限（由 EmergencyRescueService 经救援机构身份校验后授予）。
- 接收 SafetyAlertEvent 并根据角色（AccountRole）与通知偏好（家属按 NotificationPreference 过滤）决定是否推送。
- 权限路由与判定（DS-08）以 AccountRole 枚举进行类型安全分支，而非字符串/魔法值比较。

---

#### AR-05：RoadRageVoiceRecord（路怒语音存证）

**角色与职责**：路怒告警成立时生成的语音留存记录，存储于车载边缘侧。它封装了录制起止时间、加密音频引用、脱敏标记和保留到期时间。其生命周期受保留策略约束——到期后自动清除。

**类型形态**：`class`（聚合根）。RoadRageVoiceRecord 有唯一标识（存证 ID），有独立的生命周期（创建→到期→清除），且其存储于边缘侧的物理策略与 Trip 不同（不上云、仅边缘留存），不宜归属 Trip 聚合。作为独立聚合根，拥有自己的 RoadRageVoiceRecordRepository 进行持久化操作——PrivacyProtectionService 通过该仓储操作实体生命周期（创建、标记封闭、到期清除），遵循 DDD 实体必须通过其所属聚合根仓储进行持久化的原则。不同于原设计（E-02 作为 Trip 聚合内部实体与决策 6 的矛盾），独立为聚合根后其读写均通过自身仓储，读侧按需查询（按告警 ID、按保留到期时间），不再受 Trip 聚合根一致性边界约束。

**协作关系**：
- 1:1 关联路怒类型的 SafetyAlertEvent（通过标识引用）。
- PrivacyProtectionService 通过 RoadRageVoiceRecordRepository 管理其生命周期：创建（消费 RiskDeterminedEvent，AlertType=ROAD_RAGE）、封闭（消费 RiskResolvedEvent，AlertType=ROAD_RAGE）、到期清除（按保留策略周期执行）。
- Default never uploaded or transmitted externally; accessed exclusively by PrivacyProtectionService in authorized audit scenarios.

---

#### 聚合根-仓储对照

| 聚合根 | 对应仓储 | 仓储基本能力 |
|--------|---------|-------------|
| AR-01 Trip | TripRepository | 按 TripId 加载聚合、持久化聚合（含乐观锁冲突检测） |
| AR-02 Driver | DriverRepository | 按 DriverId 加载聚合、持久化聚合、原子更新综合风险评分字段 |
| AR-03 Vehicle | VehicleRepository | 按 VehicleId 加载聚合、持久化聚合（含乐观锁冲突检测），接口契约见 §3.7.5 |
| AR-04 SystemAccount | SystemAccountRepository | 按 AccountId 加载聚合、按 DriverId 查询关联家属列表、持久化聚合 |
| AR-05 RoadRageVoiceRecord | RoadRageVoiceRecordRepository | 按 RecordId / AlertId 加载聚合、按到期时间批量查询待清除记录、持久化聚合、物理删除 |



---

### 3.2 实体（非聚合根）

#### E-01：SafetyAlertEvent（安全告警事件）

**角色与职责**：满足判定规则时生成的异常记录，是系统告警链路的统一载体。它记录告警类型（疲劳/分心/路怒/活体遗留/碰撞失能/绩效预警）、风险等级（L1/L2/L3）、发生时间、GPS 位置和异常特征快照。告警事件一旦生成即不可修改（只追加），其生命周期独立于 Trip。

**类型形态**：`class`（实体）。SafetyAlertEvent 有唯一标识（告警 ID），可独立于 Trip 被查询、推送和归档，它在数据库中可能有自己的存储表，因此是实体而非值对象。它不设计为聚合根，因为：告警事件的生成总是以 Trip 为上下文（绩效预警除外，以评分计算为上下文），通过 Trip 聚合根的一致性边界来关联。

**协作关系**：
- 关联到一个 Trip（绩效预警关联到一次评分计算上下文）。
- 触发时生成 AlertTriggeredEvent 领域事件，由通知模块消费。
- 路怒类型的告警事件与 RoadRageVoiceRecord（AR-05）存在 1:1 标识引用关系。

> **设计约束**：SafetyAlertEvent 作为聚合内实体与"独立查询"需求之间存在张力。设计层面采用 **CQRS 读模型投影**策略倾向——写侧仍通过 Trip 聚合根访问 SafetyAlertEvent 以保证事务一致性；读侧（车队管理员跨行程查询告警历史、看板聚合）通过独立的只读投影或物化视图完成，不穿透聚合根边界加载。实现时可将告警写入主存储后异步投射至读模型。

---

> **编号说明**：原 E-02（RoadRageVoiceRecord）已在 a_v6 设计迭代中因所有制/持久化原则矛盾升级为聚合根 AR-05（见 §3.1 AR-05 及决策 6），故实体编号从 E-01 直接跳至 E-03。E-02 编号保留为历史记录，不重新分配给其他实体以避免下游引用混淆。

#### E-03：DriverHealthProfile（驾驶员健康档案）

**角色与职责**：与实时生理快照区分的档案级对象，包含血型、过敏史、慢性病史、用药史、基础生命体征基线和紧急医疗联系人等。仅在 SOS/救援授权场景下、经权限校验后可被救援机构调取。

**类型形态**：`class`（实体）。DriverHealthProfile 有独立的存储模型和业务含义，与实时监测数据（PhysiologicalSnapshot）是不同粒度和访问约束的数据，但其生命周期完全依附于 Driver——一个 Driver 仅有一份档案，档案的修改须通过 Driver 聚合根操作。因此它是 Driver 聚合根内部的实体，而非独立聚合根。

**协作关系**：
- 与 Driver 是 1:1 聚合内包含关系。
- 由 EmergencyRescueService 在救援场景下经权限校验后调取，并作为摘要纳入 RescueReport（VO-13）。

---

### 3.3 值对象

#### VO-01：RiskLevel（风险等级）

**角色与职责**：统一的三级风险**严重度**标签，贯穿系统所有判定和干预逻辑。它表达"风险有多严重"，而非"风险是否解除"——后者由独立的 RiskResolvedEvent 表达（见 VO-02、决策 16）。

**类型形态**：`enum`。风险等级是有限、固定的三个取值（L1_HINT / L2_WARNING / L3_CRITICAL），且不同等级在 AlertType × RiskLevel 二维映射下对应不同的干预策略分支，使用枚举可确保类型安全和编译期穷尽检查。

> **L1 取值边界（预留等级声明）**：经核对 BR-01~BR-08，本期无任一业务规则映射到 L1——疲劳轻度为 L2、路怒为 L2、分心为 L2，重度疲劳/活体遗留/碰撞失能为 L3。因此 **L1_HINT 为预留等级，本期无触发路径**，保留于枚举中以备未来扩展（如新增"提示级"轻量风险），不构成需删除的死代码。各 match 分支应对 L1 给出"无干预/仅记录"的明确处理以满足穷尽检查（见 DS-07、决策 15）。

---

#### VO-02：AlertType（告警类型）

**角色与职责**：标识告警的种类，驱动通知路由和干预策略选择。

**类型形态**：`enum`。告警类型集合是有限的、稳定的（FATIGUE / DISTRACTION / ROAD_RAGE / LIFE_DETECTION / COLLISION_DISABILITY / PERFORMANCE_WARNING），使用枚举确保各模块对类型的引用保持一致。

> **取值来源边界（消除重复判定事件）**：AlertType 是 **SafetyAlertEvent（告警实体）** 的完整分类维度，覆盖以上全部取值。但各**判定事件**仅携带其中互不重叠的子集：`RiskDeterminedEvent` 仅携带流式融合判定子集 `{FATIGUE, DISTRACTION, ROAD_RAGE}`；`LIFE_DETECTION` 仅由 `LifeDetectedEvent`（DS-05 独立产出）承载；`COLLISION_DISABILITY` 仅由 `EmergencyActivatedEvent`（DS-06 独立产出）承载。三类判定事件的 AlertType 取值集合两两不相交，因此同一判定结果**不会同时产出两条判定事件**，从根上消除了重复事件歧义。`PERFORMANCE_WARNING` 则由 `PerformanceWarningEvent`（DS-09）承载，属离线评分触发，亦不与上述判定事件重叠。

> **风险解除的表达方式（避免污染 AlertType/RiskLevel 取值）**：风险"已解除"**不**作为 AlertType 或 RiskLevel 的新增枚举值，而是由独立领域事件 **RiskResolvedEvent** 承载（见 §3.5、决策 16）。RiskResolvedEvent 复用既有的 AlertType 取值（如 AlertType=ROAD_RAGE）来标识"哪一类风险已解除"，并以独立事件类型与 RiskDeterminedEvent 区分，使消费方能明确区分"新告警"与"解除信号"，避免出现路怒解除后录制不停止、空调持续低温等问题。如此 RiskLevel 保持纯粹的严重度阶梯，AlertType 保持纯粹的类别维度，二者语义不被状态转换标记污染。

---

#### VO-03：PhysiologicalSnapshot（生理体征快照）

**角色与职责**：传感器按固定频率采集的瞬时生理数据，是一个不可变的时间点数据切片。包含采集时间戳、实时心率、血氧饱和度、情绪指数。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。PhysiologicalSnapshot 没有独立标识，其相等性由属性值决定（同一时间点的相同读数为同一快照）。它被 Trip 聚合根持有为集合，新增即追加，不修改已有快照。设计为值对象可避免快照被修改导致的并发一致性问题。

> **Java 实现约束**：Java 中没有 `struct` 值类型，值对象使用 `class` 实现，必须重写 `equals()` 和 `hashCode()` 方法以满足值相等语义。结合 Spring Data JPA 时可使用 `@Embeddable` 注解将值对象嵌入聚合根。

**协作关系**：
- 被 Trip 聚合根持有为不可变集合。
- 作为 RiskDeterminationService 的输入数据。
- 不独立持久化，随 Trip 聚合根统一存储。

---

#### VO-04：GeoLocation（地理位置）

**角色与职责**：GPS/北斗坐标信息，用于告警定位、热力图展示和救援定位。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。经纬度坐标对由值定义相等性，无独立标识，是不可变的值对象。

---

#### VO-05：TripScore（行程评分）

**角色与职责**：驾驶行为评分值，范围 [0, 100]，按 BR-05 公式计算。行程级评分和周期级评分使用同一值对象类型，区别仅在于计算上下文。TripScore 由 ScoringService 在行程结束时计算并通过 TripRepository 写入 Trip 聚合持久存储。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。评分是一个纯数值概念，由值定义相等性。设计为值对象而非裸数值的理由：评分需要携带 clamp 至 [0,100] 的不变式约束，值对象的构造器可确保非法值（如负数、>100）无法被创建。

---

#### VO-06：SensorStatus（传感器状态）

**角色与职责**：描述单个传感器或设备通道的健康状态（在线/离线/故障），供 BR-08 失效保护逻辑使用。

**类型形态**：`enum`。状态集合有限且互斥（ONLINE / OFFLINE / FAULT），使用枚举便于模式匹配和类型安全的状态机转换。

---

#### VO-07：Permission（访问权限）

**角色与职责**：描述家属账户对某驾驶员的授权级别，决定可执行哪些远程操作（查看状态、对讲、视频、车窗控制）。BR-07 的"60 秒持续 L3 后授权"和"高危场景自动激活"是权限授予的两种路径，而非权限类型本身。权限授予不自动豁免高敏操作的**二次身份验证**要求——远程控车（含车窗控制）、语音/视频对讲等高敏操作在权限校验通过后，仍须完成二次身份验证（指纹/人脸/动态短信）方可执行；二次身份验证由基础设施层的身份认证模块完成，领域层 PermissionService 在授权流程中作为门控点要求并校验其结果。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。权限由一组可执行操作的集合定义，不同账户获得的权限实例不同，但权限本身由操作集合的值定义相等性。不可变，修改权限意味着创建新的 Permission 实例（包括撤销——撤销表现为以"空操作集合/已撤销标记"的新实例替换旧实例，见 DS-08、场景 7）。

---

#### VO-08：OTAVersion（固件版本）

**角色与职责**：描述车载终端固件的版本号、适用车型范围和升级包摘要信息。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。版本号由值定义相等性（同一版本号即同一版本），不可变。用于 OTA 升级管理中的版本比对和兼容性校验。

---

#### VO-09：VehicleStateSnapshot（车辆状态快照）

**角色与职责**：事故前 30 秒或特定时刻的车辆状态摘要，包含车速、加速度、车门锁状态、起火风险标志、燃油泄漏标志等。用于 BR-06 应急救援上报。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。快照是一个时间点的不可变状态切面，由属性值定义相等性，无独立标识。

> **生产者与缓存归属（消除"快照从何而来"的不确定性）**：BR-06 要求在碰撞时刻回取**事故前 30 秒**的车辆状态，意味着系统必须在碰撞发生**之前**就持续采集并缓存近期车辆状态。该"持续采样 + 滚动缓存"职责**不属于任何领域服务**（领域服务保持无状态、按需触发），而是一个**基础设施层职责**：由边缘侧的车辆状态采集组件按固定频率生成 VehicleStateSnapshot，并维护一个覆盖至少 30s 时间窗的**滚动缓冲（ring buffer）**。领域层仅声明一个依赖接口 **VehicleStateBuffer**（端口），暴露"按时间窗回取快照序列"的能力契约（端口方法契约见决策 14）；EmergencyResponseService（DS-06）在碰撞时刻通过该端口回取事故前 30s 窗内的快照。该端口由基础设施层实现，从而将"有状态的持续缓存"与"无状态的领域判定"清晰分离。

---

#### VO-10：TimeRange（时间范围）

**角色与职责**：表示一段连续的时间区间（起止时间戳），用于报告查询的周期约束以及 VehicleStateBuffer 的时间窗回取参数。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。时间区间由起止值定义相等性，不可变。需确保起始时间不晚于结束时间的不变式。

---

#### VO-11：SensorReading（传感器读数）

**角色与职责**：五大感知通道（DMS 视觉、生理体征、语音情绪、毫米波雷达、后排红外摄像头）的**统一感知数据抽象**。封装感知通道类型、采集时间戳、通道级原始载荷引用和已提取的特征向量。为各判定服务提供统一的输入契约，避免各判定服务以松散的自然语言描述各自理解输入格式。其中，毫米波雷达负责"判定有无活体/微动"，后排红外摄像头负责"提供可视画面/影像流"——二者分工明确：雷达提供活体检出所依赖的运动信号，红外摄像头为家属 APP 视频巡视、车机后排画中画等影像类功能提供数据来源。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。SensorReading 是一个不可变的感知数据切片，无独立标识，由通道类型、时间戳和特征向量的组合值定义相等性。

**协作关系**：
- 由感知采集层（基础设施层）按固定频率生成并送入领域层。
- 作为 RiskDeterminationService 及其子判定服务的统一输入；后排红外摄像头影像流不流经融合判定门面，而是按消费方路由直接供视频/影像功能模块使用（毫米波雷达流按消费方路由供 LifeDetectionService 使用，与此类似）。
- 本身不持久化在领域模型中，由 Trip 聚合中的 PhysiologicalSnapshot 和 VehicleStateSnapshot 等提取所需维度后独立存储。

---

#### VO-12：InterventionInstruction（干预指令）

**角色与职责**：DS-07 InterventionService 依据 **AlertType × RiskLevel 二维映射**生成的单条分级干预指令，是领域层向基础设施层（HMI/CAN）下发干预意图的统一载体。它表达"对哪个目标设备、以什么参数、什么优先级执行何种干预"，由基础设施层翻译为具体硬件指令。一次判定可产出一个 InterventionInstruction 的有序集合（如重度疲劳 L3 同时含语音播报、座椅震动、双闪、CAN 减速指令）。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。指令由其内容（指令类型 + 目标设备 + 参数映射 + 优先级）定义相等性，无独立标识、不可变。其概念维度包括：
- **指令类型（InterventionInstructionType）**：以 `enum` 穷举闭合的指令种类——AMBIENT_LIGHT_COLOR（氛围灯调色）、VOICE_BROADCAST（语音播报）、SEAT_VIBRATION（座椅震动）、HAZARD_LIGHTS（双闪）、AIR_CONDITIONING（空调调节）、AUDIO_PLAYBACK（音频播放）、CAN_DECELERATION_REQUEST（CAN 减速请求）、NAVIGATE_DECELERATION（建议/请求减速）、NAVIGATE_TO_SHOULDER（引导靠边）、ALERT（通用告警）。枚举为闭合穷举，基础设施层可据此确信需处理的全部指令类型及其对应的参数结构。与原设计不同——NAVIGATE_TO_SHOULDER 被拆分为 NAVIGATE_DECELERATION 和 NAVIGATE_TO_SHOULDER 两个独立枚举值，分别表达"建议减速"和"引导靠边"两个干预强度与 HMI 表现不同的阶段，基础设施层可直接凭指令类型区分而无需额外阶段标记参数。
- **目标设备标识**：指令作用的车载设备/通道。
- **参数映射**：指令的可变参数（如颜色、温度增量、音量、震动强度），以键值映射表达以适配异构设备。各 InterventionInstructionType 均有其明确对应的参数维度——DS-07 二维映射中逐条补充到类型 + 参数映射的对应关系。
- **优先级**：用于多指令并存时的下发/抢占次序（如 L3 安全指令优先于 L2 提示）。

**协作关系**：
- 由 InterventionService（DS-07）按二维映射生成。
- 集合形式输出，由基础设施层 HMI/CAN 适配器消费并翻译为硬件指令。

---

#### VO-13：RescueReport（救援报告）

**角色与职责**：EmergencyRescueService（DS-12）在 SOS 上报时产出的救援信息聚合载体，向救援中心/120 一次性提供定位、体征与车辆状态的完整快照集。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。报告由其内容快照定义相等性，无独立标识、不可变（一次上报即定格一份）。其概念维度包括：
- **GeoLocation**：事故精准定位（VO-04）。
- **生命体征摘要**：从 Trip 聚合中**最新的 PhysiologicalSnapshot（VO-03）** 提取的实时生命体征摘要（如心率、血氧饱和度、情绪指数），与 DriverHealthProfile（E-03）的档案级基线数据相区分——前者反映事故时刻的瞬时生理状态（"此刻如何"），后者反映长期的健康档案基线（"平时如何"）。
- **VehicleStateSnapshot 集合**：经 VehicleStateBuffer 回取的事故前 30s 车辆状态快照序列（VO-09）。
- **健康档案摘要**：经授权后从 DriverHealthProfile（E-03）提取的关键医疗信息摘要（血型、过敏史、紧急联系人等）。

**协作关系**：
- 由 EmergencyRescueService 在消费 EmergencyActivatedEvent 后组装。
- 由基础设施层投递至救援中心；健康档案摘要部分受授权校验约束（见 §五）。

---

#### VO-14：AccountRole（账户角色）

**角色与职责**：标识 SystemAccount（AR-04）的角色类别，驱动 DS-08 PermissionService 的权限判定与通知路由分支。

**类型形态**：`enum`。角色集合有限且稳定（FAMILY / MANAGER / RESCUE），使用枚举可在权限路由与通知分发中进行类型安全、编译期可穷尽校验的分支判断，避免以字符串大写标识/魔法值做比较。**RESCUE（救援机构）** 为新增取值——对应 req_v4 所列五类参与者中的"救援机构"角色，该角色在 DS-12 EmergencyRescueService 中享有 SOS 自动呼叫接收、远程解锁授权、健康档案调取等限定权限。

**协作关系**：
- 作为 SystemAccount 聚合的角色字段（AR-04）。
- DS-08 依据 AccountRole 决定权限授予/撤销与可执行远程操作集合；通知模块依据 AccountRole 决定推送路由（家属端 vs 管理端 vs 救援机构）。
- DS-12 EmergencyRescueService 通过 AccountRole.RESCUE 进行类型安全的救援机构权限分支。

---

#### VO-15：DriverStatusSnapshot（驾驶员状态快照）

**角色与职责**：用于**家属端常态状态同步**的轻量周期快照，表达驾驶员当前的总体安全状态色（绿/黄/红），与离散的告警事件互补——告警事件仅在判定成立时触发，而本快照按 ≥1Hz 周期持续生成，使家属端在无告警时也能看到实时的"绿色平稳"状态（见 DS-16、场景 10）。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。快照由其内容（Driver 标识 + 状态色 + 时间戳）定义相等性，无独立标识、不可变。其概念维度包括：
- **Driver 标识**：所属驾驶员。
- **状态色（StatusColor）**：建议以 `enum`（GREEN / YELLOW / RED）表达，由当前风险状态派生——无风险/L1 → 绿、L2 → 黄、L3 → 红。
- **时间戳**：采样时刻。

**协作关系**：
- 由 DriverStatusBroadcastService（DS-16）按 ≥1Hz 周期采样当前 Driver 风险状态生成。
- 经推送通道下发至家属端（端到端 ≤2s 上报约束），不持久化为领域核心数据（属常态遥测）。

---

#### VO-16：DrivingBehaviorCounters（驾驶行为计数器）

**角色与职责**：记录一次行程中累计的急刹次数和急加速次数。与 SafetyAlertEvent（告警事件）不同——急刹/急加速是**驾驶行为指标**而非安全告警，它们不触发分级干预、不产生 AlertTriggeredEvent，仅作为 BR-05 评分公式的扣分项输入和报告统计的数据来源。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。计数器由两个不可变整数值定义相等性，无独立标识。Trip 聚合持有本计数器，基础设施层在检测到急刹/急加速事件时以"替换为新计数器"的方式更新 Trip 聚合中的该值对象（不可变值对象的更新模式）。

**协作关系**：
- 被 Trip 聚合根持有，随行程创建初始化（计数均为 0），随行程结束冻结。
- 由**基础设施层的持续加速度监测组件**在整个行程中持续检测加速度信号——当加速度突变超过阈值（急刹：减速度 > 阈值；急加速：加速度 > 阈值）时，通过领域层声明的端口（**DrivingBehaviorTrackingPort**，见决策 17）通知领域层更新计数器。**急刹/急加速检出阈值为基础设施层调优参数**——阈值由车型/传感器标定确定，领域层仅在 DrivingBehaviorTrackingPort 方法契约中声明阈值参数（`DecelerationThreshold` / `AccelerationThreshold`，值对象，约束为正实数），具体数值由基础设施层配置并注入（见决策 20）。验收时固定阈值以确保可复现性。
- 领域层 **DrivingBehaviorTrackingService（DS-17）** 接收计数器增量事件并更新 Trip 聚合的 DrivingBehaviorCounters。
- 领域服务 ScoringService 在行程结束时读取 Trip 聚合的 DrivingBehaviorCounters 作为评分公式中急刹/急加速计次输入。

> **设计约束（与 VehicleStateSnapshot ring buffer 的区分）**：VehicleStateSnapshot 的 30s ring buffer 仅服务于碰撞时刻的**事故前景回取**（决策 14），不覆盖整个行程。而急刹/急加速计数需要**全程持续监测**——这是两个不同的基础设施职责，不可用 ring buffer 替代全程计数。基础设施层需分别为二者提供独立的实现：ring buffer 组件负责滚动缓存近期车辆状态快照；加速度事件检测组件负责全程检测急刹/急加速模式并以计数器增量事件上报。

---

#### VO-17：L3DurationTracker（L3 持续时长追踪器）

**角色与职责**：为 DS-08 PermissionService 的"L3 持续 >60 秒授予家属权限"规则提供 L3 时长追踪能力。封装当前 Driver 的 L3 起始时刻、累计持续时长和当前是否仍处于 L3 状态。与 VO-16 DrivingBehaviorCounters 同属 Trip 聚合内持有的值对象——一个 Trip 内至多存在一个活跃的 L3DurationTracker（Trip:Driver=1:1）。Tracker 在 L3 首次检出时由 PermissionService 创建并存入 Trip 聚合，在 L3 解除时由 PermissionService 标记终止，行程结束时随 Trip 聚合一起冻结。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。Tracker 的属性值定义相等性，无独立标识。L3 持续时长的推进表现为以"新 Tracker 实例替换旧实例"的方式更新——PermissionService 接收每个 RiskDeterminedEvent / RiskResolvedEvent 后，读取 Trip 聚合中当前 Tracker，计算更新后的 Tracker 并写回 Trip 聚合（不可变值对象的更新模式）。

**协作关系**：
- 被 Trip 聚合根持有，在当前 Driver 的 L3 首次检出时创建，L3 解除后标记终止但保留已累计时长（供审计/统计查询），行程结束时随 Trip 冻结。
- 由 DS-08 PermissionService 在每次处理 RiskDeterminedEvent 时读取更新——若 Driver 当前 L3，推进累计时长；若 L3 持续时长首次达到 60s 阈值，触发家属权限授予。
- 由 DS-08 在收到 RiskResolvedEvent 或 L3 下降（RiskDeterminedEvent 携带 < L3 等级）时标记 L3 不再持续，触发常规自动撤销。边缘侧单线程环境保证无并发竞争。

---

#### VO-18：NotificationPreference（通知偏好）

**角色与职责**：描述家属账户对告警推送的通知偏好配置，为 req_v4 要求的"可设置并接收轻/中/重度不同等级"提供形式化建模。封装家属订阅的风险等级集合（`Set<RiskLevel>`），告警推送模块在推送 SafetyAlertEvent 前按此偏好做等级过滤——仅当告警的 RiskLevel 落在家属已订阅的等级集合中时，才向其推送。未配置（nil/None）时默认接收全部等级。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。通知偏好由一组风险等级集合的值定义相等性，不可变。家属修改偏好配置意味着创建新的 NotificationPreference 实例替换旧实例。

**协作关系**：
- 作为 SystemAccount（AR-04）聚合的内部值对象（仅家属角色有意义，管理员与救援机构不使用）。
- 由 DS-16（DriverStatusBroadcastService）关联的告警推送模块在每次推送 SafetyAlertEvent 前查询家属的 NotificationPreference，按 `alert.riskLevel ∈ preference.subscribedLevels` 过滤后决定是否推送。
- 家属端通过应用层接口修改偏好时，创建新 NotificationPreference 实例并更新 SystemAccount 聚合。

---

#### VO-19：OTAUpgradeStatus（OTA 升级状态）

**角色与职责**：描述一次 OTA 固件升级会话的当前阶段与断点续传偏移量，封装升级状态机——将原本由 DS-15 OTAUpdateService 以"本服务维护"方式持有的持久状态，建模为 Vehicle 聚合内部的值对象，以消除领域服务持状态的违反 DDD 无状态原则问题。状态由 DS-15 以无状态方式读取和更新（不可变值对象的替换模式）。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。升级状态由阶段标识、目标版本、已传输偏移量和阶段时间戳的组合值定义相等性，无独立标识、不可变。状态推进表现为以新实例替换旧实例。其概念维度包括：
- **阶段（UpgradeStage）**：以 `enum` 穷举有限状态——PENDING / TRANSMITTING / VERIFYING / READY / UPGRADING / COMPLETED / ROLLED_BACK。
- **目标版本**：本次升级的目标 OTAVersion（VO-08）。
- **断点续传偏移量**：已传输的字节偏移量，DS-15 据此通过 OTADeliveryPort 执行断点续传。
- **阶段时间戳**：进入当前阶段的时刻，用于超时判定。

**协作关系**：
- 被 Vehicle 聚合（AR-03）持有——在升级启动时创建初始实例（PENDING 阶段），升级结束（COMPLETED 或 ROLLED_BACK）后冻结。
- DS-15 OTAUpdateService 在每次处理升级事件（传输进度、校验结果、刷写结果）时，从 Vehicle 聚合读取当前 OTAUpgradeStatus，计算下一阶段的新实例并写回 Vehicle 聚合，DS-15 自身保持无状态语义。
- 生命周期与一次升级会话一致——Vehicle 聚合在同一时刻至多存在一个活跃的 OTAUpgradeStatus（不支持并行升级）。

---

#### VO-20：DetectionWindow（活体检测判定窗口）

**角色与职责**：封装 LifeDetectionService（DS-05）在一次活体检测会话中的判定窗口状态，包括窗口剩余时长、起始时刻、累计微动观测次数和容差阈值。将 DS-05 的窗口状态建模为独立值对象，使 DS-05 对外保持纯函数语义——以当前 DetectionWindow + 新雷达信号作为输入，返回更新后的 DetectionWindow + 判定结论。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。DetectionWindow 由窗口各属性的组合值定义相等性，无独立标识、不可变。窗口状态的推进表现为以"新 DetectionWindow 实例替换旧实例"的方式。其概念维度包括：
- **剩余时长**：判定窗口的倒计时剩余时间，从窗口启动时的 60 秒开始递减。递减至零时窗口到期，DS-05 返回 `DetectionError.WindowExpired`。
- **起始时刻**：窗口的绝对起始时间戳，供审计与日志追踪。
- **累计微动观测次数**：窗口内已累计的有效微动检测次数，用于判定"是否持续感应到微动"，达到置信阈值时 DS-05 产出 LifeDetectedEvent。
- **容差阈值**：雷达信号短暂中断的最大容忍时长。中断在容差阈值内时保持窗口并暂停累计；超过容差阈值时重置窗口并重新计时（见 §5.4 边界条件 (3)）。

**协作关系**：
- 由 EdgeSessionContext（§6.2）在活体检测会话存续期间持有——随 VehicleIgnitionOffLockedEvent 触发会话时创建初始 DetectionWindow，随会话结束（判定成立或窗口到期自然终止）后销毁。
- 作为 DS-05 `evaluateLifeDetection(radarSignal, window)` 的输入参数和返回结果中的更新值——DS-05 以纯函数方式消费当前窗口、应用雷达信号判定、返回新窗口与判定结论。
- 边缘侧单线程环境（§6.1）保证 DetectionWindow 的无并发竞争更新。

---

#### VO-21：OverrideSignal（驾驶员覆盖信号）

**角色与职责**：描述驾驶员在干预进行中执行的有效操作（转向、制动、加速），是 DS-07 InterventionService 判断是否中止干预升级的输入。封装操作类型和时间戳，使"驾驶员主动控制是否覆盖系统干预"这一决策在领域层有明确的类型支撑。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。OverrideSignal 由操作类型和时间戳的组合值定义相等性，无独立标识、不可变。其概念维度包括：
- **操作类型（OverrideType）**：以 `enum` 穷举有限的操作类型——`TURNING`（转向）、`BRAKING`（制动）、`ACCELERATING`（加速）。三种类型覆盖驾驶员通过方向盘和踏板表达"我已接管控制"的主要意图通道。
- **时间戳**：操作发生的时刻，供 DS-07 判断覆盖信号的时序有效性（如仅在渐进升级启动后才采纳）。

**协作关系**：
- 由感知层（基础设施层）在检测到驾驶员主动操作（转向/制动/加速，且操作幅度超过基础噪声阈值）时生成，经领域层声明的端口输入 DS-07。
- 作为 DS-07 `handleOverride(overrideSignal)` 方法的形式化参数——DS-07 据此判断是否中止当前干预升级（如疲劳 L3 渐进升级中的语音唤醒阶段，驾驶员一旦执行有效转向或减速，即停止升级并归还控制权）。

---

#### VO-22：DriverComprehensiveScore（驾驶员综合风险评分）

**角色与职责**：表达驾驶员的跨行程综合风险评分，是 ScoringService（DS-09）在每次行程评分完成后按近期所有 TripScore 加权平均计算得出的聚合指标。与 VO-05 TripScore（行程级评分）相区分——TripScore 衡量单次行程的驾驶行为质量，DriverComprehensiveScore 衡量驾驶员近期的整体风险水平。两者使用同一数值约束（`clamp` 至 [0,100]）但作为独立值对象类型以区分语义和用途。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。DriverComprehensiveScore 由评分值定义相等性，无独立标识、不可变。Driver 综合风险评分的更新以"新 DriverComprehensiveScore 实例替换 Driver 聚合中的旧实例"方式完成，不可变性使评分更新的事务边界清晰。构造时须确保值在 [0,100] 范围内。

**协作关系**：
- 由 ScoringService（DS-09）在每次行程评分完成后计算，通过 DriverScoreUpdatedEvent 携带。
- DriverScoreUpdateService（DS-18）消费 DriverScoreUpdatedEvent 后，通过 DriverRepository 的 `updateScore` 方法将新 DriverComprehensiveScore 写入 Driver 聚合的综合风险评分字段。
- 与 TripScore（VO-05）的关系：TripScore 是 DriverComprehensiveScore 的计算输入（多行程加权平均），两者数值范围一致（[0,100]）但类型独立——TripScore 归属 Trip 聚合（VO-05），DriverComprehensiveScore 归属 Driver 聚合（本 VO-22），分别由各自的聚合持有并持久化。

---

#### VO-23：RescueAuthorizationToken（救援授权凭证）

**角色与职责**：为救援机构的远程解锁操作与健康档案调取提供形式化授权凭证。授权凭证由云端救援协调系统在险情核实后签发，由救援机构（AccountRole.RESCUE）持有并在每次执行高敏操作（远程车门解锁、健康档案调取）时提交给 EmergencyRescueService 校验。将救援授权建模为值对象，使授权的生命周期（签发→校验→消费/过期）在领域层具有明确的类型支撑，消除此前仅以自然语言描述授权流程的模糊性。

**类型形态**：`class`（值对象，JPA `@Embeddable`）。RescueAuthorizationToken 由凭证标识、签发机构、目标 Vehicle/Driver 标识、授权操作集合（远程解锁/健康档案调取）、签发时间戳和有效期组合定义相等性，无独立标识、不可变。校验通过后凭证被标记为已消费——以新的已消费 RescueAuthorizationToken 实例替换旧实例。

**协作关系**：
- 由云端救援协调系统在险情核实后签发，经外部通信通道传递给救援机构的 SystemAccount（AccountRole.RESCUE）。
- 由 EmergencyRescueService（DS-12）在远程解锁和健康档案调取操作前校验：校验凭证有效性（未过期、未消费、目标匹配）、校验持有者 AccountRole 为 RESCUE；校验通过后执行操作并标记凭证为已消费。校验失败则返回 `AccessDenied`。
- 与 AccountRole.RESCUE 的关系：只有 AccountRole.RESCUE 角色的 SystemAccount 才可持有 RescueAuthorizationToken；角色为授权的前提条件，Token 为授权的能力凭证——二者共同构成救援机构授权的完整校验链。
- 授权生命周期：① 签发（云端救援协调系统在险情核实后生成，经外部通道传递）；② 校验（EmergencyRescueService 执行三重校验：有效期、消费状态、目标匹配 + 角色校验）；③ 消费（操作执行后标记已消费，防重放）；④ 过期（超过有效期自动失效，审计归档）。

---

### 3.4 领域服务

#### DS-01：RiskDeterminationService（风险判定服务）

**职责**：作为**流式感知融合判定的门面**，接收持续到达的流式感知通道统一输入（SensorReading），按 BR-01 疲劳、BR-03 路怒、分心检出规则执行融合判定，输出统一的 RiskLevel 和 AlertType（取值限定于 `{FATIGUE, DISTRACTION, ROAD_RAGE}` 子集）。它是边缘侧流式判定链路的入口——在边缘本地完成轻量判定以保证 500ms 端到端时延和断网可用性。此外，当某类此前成立的流式风险不再持续（条件不再满足）时，门面产出 **RiskResolvedEvent**（携带对应 AlertType）以表达风险解除（见决策 16）。云端承担更重的多维融合行为风控建模，但那是云侧服务而非本领域服务范畴。

> **判定模型边界（与 DS-05/DS-06 的关系）**：本门面只负责**持续流式数据驱动**的判定（DMS 视觉、生理、语音三条持续到达的感知流）。BR-02 活体遗留与 BR-06 碰撞失能属于**事件触发型判定**——其触发源是离散的领域事件（熄火落锁、碰撞冲击），判定逻辑、生命周期与产出事件均不同于流式融合，故由 LifeDetectionService（DS-05）与 EmergencyResponseService（DS-06）作为**独立领域服务**承担，**不纳入本门面的委托列表**（见决策 2、决策 13）。

> **解除判定的状态边界**：判定"风险由成立转为解除"需对比当前判定与此前状态，该"当前活跃风险集"为**会话级状态**，由边缘侧流式会话上下文持有并随每次 SensorReading 作为输入参数传入门面，门面返回更新后的会话状态与（可选的）RiskDeterminedEvent / RiskResolvedEvent。门面本身对外仍是纯函数，可变状态归会话上下文，与 DS-05 的 DetectionWindow 处理方式一致，不引入服务内部 mutable 字段，边缘侧单线程环境（§6.1）保证无并发竞争。

**协作**：
- 输入：多条流式 SensorReading（分别来自 DMS 视觉、生理体征、语音情绪通道）。生理通道同时也被 EmergencyResponseService 用于失能判定，毫米波雷达通道被 LifeDetectionService 使用，后排红外摄像头通道按消费方路由供影像功能模块使用、不流经本门面，加速度通道被 EmergencyResponseService 和 DrivingBehaviorTrackingService 使用——这些通道由感知分发层按消费方路由，不流经本门面。
- 内部委托：将各通道数据分派给 FatigueDeterminationService、DistractionDetectionService、RoadRageDeterminationService 各自判定。
- 汇总各子判定服务的判定结果，产出 RiskDeterminedEvent（携带 RiskLevel 和 AlertType ∈ `{FATIGUE, DISTRACTION, ROAD_RAGE}`），驱动干预模块和通知模块；在风险解除时产出 RiskResolvedEvent。
- 门面职责：子判定服务不直接产出领域事件，也不直接调用其他模块的领域服务。各子判定服务仅将判定结果返回给 RiskDeterminationService，由门面统一产出 RiskDeterminedEvent / RiskResolvedEvent。跨模块协作（如路怒成立触发语音存证、环境调节）由消费方订阅事件完成，而非在子判定服务中直接调用。

**为何是领域服务**：风险判定操作跨越多个聚合（Trip、Driver、Vehicle），不属于任何单一聚合的行为，且判定过程是无状态的——给定相同的输入（含会话状态），判定结果确定。

---

#### DS-02：FatigueDeterminationService（疲劳判定服务 — BR-01）

**职责**：依据 DMS 视觉数据执行疲劳分级：轻度疲劳（L2）与重度疲劳（L3）。判定条件如 BR-01 所述——连续 3 分钟内频繁眨眼或视线偏离累计 >15s → L2；眼睑闭合 >1.5s 或点头频率 >2 次/10s → L3。

> **与分心 L2 共用"视线偏离"信号的并发处理**：疲劳 L2（连续 3 分钟窗口内视线偏离累计 >15s）与分心 L2（最近 60 秒滑动窗口内视线偏离累计 ≥3s）共用同一行为信号"视线偏离"，仅窗口时长和累计阈值不同。当两个条件同时满足时，两子判定服务各自独立产出判定结果，RiskDeterminationService 门面会为此产出两个独立的 RiskDeterminedEvent（一个 Fatigue×L2、一个 Distraction×L2），干预模块按各自二维映射独立执行——疲劳 L2 驱动氛围灯变橙提醒，分心 L2 驱动通用告警，二者互不冲突。两个事件均经 AlertPersistenceService 创建各自的 SafetyAlertEvent。此处理策略标注为"需与需求方确认"——若后续需求要求冲突消解（如分心 L2 优先级高于疲劳 L2），可在门面汇总阶段实现，不影响子判定服务的独立性。

**协作**：被 RiskDeterminationService 委托调用。输出疲劳等级和判定依据快照，**仅返回给门面，不自行产出领域事件或调用其他模块**。

**接口契约**：
- `determineFatigue(sensorReading: SensorReading): Result<FatigueResult, DeterminationError>` — 依据单帧或时间窗内的 DMS 视觉特征判定疲劳等级。`FatigueResult` 至少表达风险等级（L2/L3/None）、判定置信度和判定所依据的时间窗摘要（如眨眼频率、眼睑闭合时长、视线偏离累计值）；`DeterminationError` 至少表达 `InputInvalid`（输入数据不可用，如传感器离线导致数据缺失）。调用方（RiskDeterminationService 门面）负责时间窗的维护（连续多帧数据的滑动窗口），本服务仅对给定的窗口数据执行判定规则。

---

#### DS-03：DistractionDetectionService（分心检出服务）

**职责**：依据 DMS 视觉数据判定分心行为——在最近 60 秒滑动窗口内视线偏离前方累计达到 3 秒即判定为分心成立（L2），判定成立后须在 0.5 秒内发出告警。累计时间的计算限定在滑动窗口内（而非整个行程的单调累积），以避免长期行程中累计值无限增长导致所有驾驶员均被误判为分心。滑动窗口时长（60s）和累计阈值（3s）为可配置参数，标注为需与需求方确认的推断。

> **设计约束**：60s 滑动窗口与 3s 累计阈值为当前设计推断——窗口时长可根据实测数据与需求方协商调整；领域层应将窗口参数建模为可配置值，便于验收测试注入不同参数进行覆盖验证。

**协作**：被 RiskDeterminationService 委托调用。输出分心标志和判定时间戳，**仅返回给门面，不自行产出领域事件或调用其他模块**。完整端到端链路与 0.5s 时延约束见场景 8。

**接口契约**：
- `detectDistraction(sensorReading: SensorReading): Result<DistractionResult, DeterminationError>` — 依据 DMS 视觉特征判定分心是否成立。`DistractionResult` 至少表达判定结论（Distracted/NotDistracted）、判定时间戳和窗口内视线偏离累计值；`DeterminationError` 同 DS-02。滑动窗口由门面维护并以窗口内多帧数据序列作为输入参数传入本服务，确保判定逻辑独立于窗口管理。

---

#### DS-04：RoadRageDeterminationService（路怒判定服务 — BR-03）

**职责**：融合语音情绪特征（声压级 >85dB + 谩骂关键词）和生理特征（心率较静息上升 20%+）判定路怒状态（L2）。

**协作**：
- 被 RiskDeterminationService 委托调用。
- 判定成立时，将判定结果（含 AlertType=ROAD_RAGE 及判定依据快照）**返回给 RiskDeterminationService 门面**，不直接调用任何其他模块的领域服务。
- RiskDeterminationService 汇总后产出 RiskDeterminedEvent（携带 AlertType=ROAD_RAGE），由以下消费方各自订阅处理：
  - **PrivacyProtectionService**（`domain.privacy`）消费 RiskDeterminedEvent（AlertType=ROAD_RAGE），触发路怒语音存证录制。
  - **InterventionService**（`domain.intervention`）消费 RiskDeterminedEvent（AlertType=ROAD_RAGE），触发车内环境调节指令（空调调低 2°C + 白噪音/舒缓音乐）。
- 路怒解除（条件不再满足）时，门面产出 RiskResolvedEvent（AlertType=ROAD_RAGE），驱动 PrivacyProtectionService 封闭存证、InterventionService 恢复环境（见决策 16、场景 3）。

**接口契约**：
- `determineRoadRage(voiceReading: SensorReading, physioReading: SensorReading): Result<RoadRageResult, DeterminationError>` — 融合语音情绪特征与生理特征判定路怒状态。`RoadRageResult` 至少表达判定结论（Raging/NotRaging）、判定置信度、声压级峰值、心率偏差幅度；`DeterminationError` 同 DS-02（语音或生理输入任一方不可用时返回 InputInvalid）。

---

#### DS-05：LifeDetectionService（活体遗留检测服务 — BR-02，**独立事件触发型领域服务**）

**职责**：本服务是**独立的事件触发型判定服务**，**不属于 RiskDeterminationService 门面的委托子服务**（见决策 2、决策 13）。其触发源是离散的"熄火且车门落锁"事件（而非持续到达的流式感知），触发后接收毫米波雷达扫描信号，在 60 秒判定窗口内持续监测呼吸/移动微动。若判定窗口内持续感应到微动，判定为遗留生命风险（L3，AlertType=LIFE_DETECTION）。判定成立后，须在 10 秒内**直接产出** LifeDetectedEvent 驱动告警推送（家属 APP 红色高频振动报警 + 车辆双闪 + 短促鸣笛），其产出路径**不经过 RiskDeterminationService 汇总**，因此不会与门面产生重复的判定事件。

**协作**：
- 触发：订阅 `VehicleIgnitionOffLockedEvent` 事件（由车辆状态变更产生，见 §3.5 领域事件表），据此启动一次判定窗口。
- 输入：毫米波雷达活体信号（按消费方路由直送本服务，不流经融合门面）。
- 输出：LifeDetectedEvent（携带 Vehicle 标识、判定置信度、时间戳）；AlertPersistenceService 消费 LifeDetectedEvent 后完成 SafetyAlertEvent 创建与 AlertTriggeredEvent 发出。

> **设计约束（判定窗口状态边界澄清）**：判定窗口（60s 倒计时）是本服务在一次活体监测会话内的**会话级临时状态**，而非领域服务的持久成员状态。设计层面明确其归属为：将窗口的剩余时长、起始时刻、累计微动观测等封装为独立的 **`DetectionWindow` 值对象**，由调用上下文（边缘侧的活体监测会话）持有并在每次雷达信号到达时作为**输入参数**传入本服务、由本服务返回**更新后的 `DetectionWindow` 与判定结论**。如此本服务对外仍是**纯函数**（输入 = 当前 DetectionWindow + 新雷达信号，输出 = 新 DetectionWindow + 可选 LifeDetectedEvent），窗口的可变状态由会话上下文管理而非服务内部 mutable 字段，消除了对可测试性的影响。边缘侧单线程运行环境（见 §6.1）进一步保证该会话状态无并发竞争。雷达信号在窗口内短暂中断又恢复的窗口处理策略见 §5.4 边界条件 (3)。

**接口契约**：
- `evaluateLifeDetection(radarSignal: SensorReading, window: DetectionWindow): Result<DetectionResult, DetectionError>` — 依据雷达信号与当前判定窗口状态判定活体遗留风险。`DetectionResult` 入含更新后的 `DetectionWindow`（无论判定成立与否均返回更新窗口）、可选 `LifeDetectedEvent`（仅在判定成立时携带）；`DetectionError` 至少表达 `InputInvalid`（雷达信号不可用）和 `WindowExpired`（窗口已超出 60s 上限自动终止，需由调用方决定是否重启新窗口）。判定会话状态（一次检测周期）由调用上下文管理。

---

#### DS-06：EmergencyResponseService（应急响应服务 — BR-06，**独立事件触发型领域服务**）

**职责**：本服务是**以碰撞冲击为触发条件、依赖持续生理数据缓冲的独立判定服务**，**不属于 RiskDeterminationService 门面的委托子服务**（见决策 2、决策 13）。其触发源是离散的"碰撞特征冲击"信号（而非持续流式感知）。当加速度传感器检测到碰撞冲击，本服务通过基础设施层维护的**生理数据滚动缓冲区**（至少覆盖最近 10 秒的生理读数，由 PhysiologicalDataBuffer 端口提供，端口契约见决策 21）回取碰撞发生前至碰撞后 ≥10s 时间窗的生理数据，判定是否满足"心率骤停或意识丧失 >10 秒"条件。满足时跳过人工确认，立即**直接产出** EmergencyActivatedEvent（L3，AlertType=COLLISION_DISABILITY），驱动救援上报与家属端自动激活；其产出路径**不经过 RiskDeterminationService 汇总**，不会与门面产生重复判定事件。

> **设计约束**：生理数据滚动缓冲区的持续维护（≥10s 生理数据）是**基础设施层职责**——由边缘侧的生理数据采集组件在正常行车过程中持续缓存在一个 ≥10s 的滚动缓冲（ring buffer）中（类比 VehicleStateBuffer 的 30s ring buffer，但维度不同：前者为生理读数时间窗，后者为车辆状态时间窗）。DS-06 在碰撞冲击触发时通过领域层声明的依赖接口（**PhysiologicalDataBuffer** 端口）回取该缓冲窗内数据。该端口由基础设施层实现，领域层仅声明接口契约，从而将"有状态的持续缓冲"与"无状态的领域判定"清晰分离，同时修正了原决策 13 中"纯事件触发型"分类的偏差——DS-06 的判定需要碰撞事件触发 + 持续生理数据回溯的双重特征。端口核心方法契约见决策 21。

**协作**：
- 触发：碰撞冲击信号（加速度通道按消费方路由直送本服务，不流经融合门面）+ 生理特征（共享生理通道）。
- 事故前 30 秒 VehicleStateSnapshot 的获取：本服务**不负责持续采集与缓存**车辆状态；它在碰撞时刻通过一个由基础设施层实现的**车辆状态滚动缓冲端口**（VehicleStateBuffer，领域层声明的依赖接口，方法契约见决策 14）回取覆盖事故前 30s 时间窗的 VehicleStateSnapshot（见 VO-09、决策 14）。
- 碰撞前后生理数据的获取：本服务在碰撞冲击触发时，通过基础设施层实现的 **PhysiologicalDataBuffer** 端口（领域层声明的依赖接口，方法契约见决策 21）回取碰撞前至碰撞后 ≥10s 时间窗的生理数据，用于判定是否满足"心率骤停或意识丧失 >10 秒"。该端口与 VehicleStateBuffer 同为领域层依赖接口但服务于不同数据维度（生理读数 vs 车辆状态），均由基础设施层独立实现并注入 DS-06。
- 输出：EmergencyActivatedEvent（携带 GeoLocation、回取的 VehicleStateSnapshot、Driver 标识、时间戳）；AlertPersistenceService 消费 EmergencyActivatedEvent 后完成 SafetyAlertEvent 创建。
- 消费方：EmergencyRescueService（救援上报）、PermissionService（家属端自动激活）。

**接口契约**：
- `determineDisability(collisionSignal: CollisionImpactSignal): Result<DisabilityAssessment, DeterminationError>` — 以碰撞冲击信号为输入执行碰撞失能判定。`CollisionImpactSignal` 载荷包含碰撞特征（加速度包络剖面、碰撞强度估算值、碰撞时刻时间戳），由基础设施层加速度采集组件在检测到碰撞冲击模式时组装并送入领域层。本方法在内部通过 `PhysiologicalDataBuffer` 端口回取碰撞前后 ≥10s 的生理数据、通过 `VehicleStateBuffer` 端口回取事故前 30s 的车辆状态快照，判定是否满足"心率骤停或意识丧失 >10 秒"条件。`DisabilityAssessment` 至少表达判定结论（Disabled/NotDisabled/Indeterminate）、判定置信度、判定所依据的生理数据时间窗摘要和车辆状态快照引用。`DeterminationError` 至少表达 `InputInvalid`（碰撞信号载荷不可解析）、`BufferUnavailable`（生理或车辆状态缓冲不可用，此时判定降级为 Indeterminate 并标注置信度降低）。与 DS-02~DS-05 的方法契约风格一致（参照已确立的 Result<T, E> 模式）。

---

#### DS-07：InterventionService（干预执行服务）

**职责**：接收 RiskDeterminedEvent，按 **AlertType × RiskLevel 二维映射**生成对应的分级干预指令集合（InterventionInstruction，VO-12）。干预策略由"告警类型 + 风险等级"共同决定，而非单一 RiskLevel，从而与各场景行为契约保持一致。负责"语音唤醒 → 建议/请求减速 → 引导靠边"的渐进式指令升级逻辑（疲劳 L3）。同时监控驾驶员覆盖（override）信号，一旦检测到转向/踩踏板等有效操作即停止升级并归还控制权。当收到 RiskResolvedEvent 时，停止对应类型的干预升级并恢复车内环境（如路怒解除恢复空调）。

**二维干预映射（AlertType × RiskLevel，本期取值）**：

| AlertType ＼ RiskLevel | L2 | L3 |
|---|---|---|
| FATIGUE（疲劳） | AMBIENT_LIGHT_COLOR（氛围灯变橙提醒，参数：橙色） | VOICE_BROADCAST（语音播报）+ SEAT_VIBRATION（座椅强力震动）+ 渐进升级（VOICE_BROADCAST 语音唤醒→NAVIGATE_DECELERATION 建议/请求减速→NAVIGATE_TO_SHOULDER 引导靠边）+ HAZARD_LIGHTS（双闪）+ CAN_DECELERATION_REQUEST（CAN 减速指令下发） |
| ROAD_RAGE（路怒） | AIR_CONDITIONING（空调调节，参数：温度增量 -2°C）+ AUDIO_PLAYBACK（音频播放，参数：白噪音/舒缓音乐） | 本期无（路怒仅 L2） |
| DISTRACTION（分心） | ALERT（通用告警，判定成立后 0.5s 内发出，参数：语音/视觉告警） | 本期无（分心仅 L2） |

> **L1 处理**：L1 为预留等级（VO-01），本期 RiskDeterminedEvent 不会携带 L1（无规则触发）。二维映射对 L1 给出"无干预/仅记录"分支以满足穷尽检查，构成显式占位而非死代码——一旦未来新增映射到 L1 的规则，仅需在此补充对应指令。LIFE_DETECTION / COLLISION_DISABILITY 的响应不经 InterventionService 的 RiskDeterminedEvent 路径，分别由 LifeDetectedEvent / EmergencyActivatedEvent 的专属消费方（HMI 双闪鸣笛、救援上报）处理。

> **NAVIGATE_DECELERATION 与 NAVIGATE_TO_SHOULDER 拆分说明**：原设计使用同一 NAVIGATE_TO_SHOULDER 枚举值同时表达"建议减速"和"引导靠边"两个阶段——二者干预强度、HMI 表现和驾驶员覆盖检测触发阈值均不同，基础设施层无法仅凭指令类型区分。本版将 VO-12 的 InterventionInstructionType 枚举拆分为两个独立枚举值：**NAVIGATE_DECELERATION**（建议/请求减速，干预强度较低，HMI 提示为主）和 **NAVIGATE_TO_SHOULDER**（引导靠边，干预强度较高，触发车道级导航引导），基础设施层可据此明确区分并执行对应级别的硬件指令。

**协作**：
- 订阅 RiskDeterminedEvent（生成干预指令集合）、RiskResolvedEvent（停止升级/恢复环境）和 AlertTriggeredEvent。
- 输出 InterventionInstruction（VO-12）有序集合，由基础设施层翻译为具体硬件指令。
- 与驾驶员覆盖检测（override detection）交互——覆盖信号由感知层提供，本服务决定是否中止干预升级。

**接口契约**：
- `generateIntervention(alertType: AlertType, riskLevel: RiskLevel): Array<InterventionInstruction>` — 按 AlertType × RiskLevel 二维映射生成干预指令集合，为空集合时表达"该组合无干预动作"。本方法为纯函数，相同输入始终返回相同结果。
- `handleOverride(overrideSignal: OverrideSignal): InterventionResult` — 处理驾驶员覆盖信号（转向/踩踏板等有效操作），决定是否中止当前干预升级。`OverrideSignal` 携带操作类型和时间戳；`InterventionResult` 至少表达当前干预状态（Aborted/Continuing/Resumed）和驾驶员已取回控制权的时间。

---

#### DS-08：PermissionService（权限管理服务 — BR-07）

**职责**：管理家属账户对驾驶员的访问权限，依据 SystemAccount 的 AccountRole（VO-14）判定授权范围。维护家属常规权限的**完整状态机**：授予、临时撤销、常规自动撤销。
- **常规授予**：当驾驶员持续处于 L3 风险 >60 秒时授予"远程对讲 + 视频监控"权限。
- **高危自动激活**：BR-06 触发的高危失能场景下自动激活接入，不受 60 秒约束。
- **临时撤销**：驾驶员物理遮挡摄像头时，DS-14 通过感知通道检测到遮挡并产出 CameraOcclusionDetectedEvent，PermissionService 消费后触发权限临时撤销。遮挡移除后，DS-14 产出 CameraOcclusionRemovedEvent，PermissionService 据此判断是否恢复权限（见场景 7 步骤 5）。
- **常规自动撤销（新增闭环）**：当驾驶员的风险等级由 L3 下降——后续 RiskDeterminedEvent 携带 < L3 的等级，或收到该类型的 RiskResolvedEvent，即"L3 不再持续"——自动撤销此前因常规路径授予的家属权限，并发出 **FamilyAccessRevokedEvent**，使家属端对讲/视频接入闭环结束。高危自动激活路径的接入由救援流程另行管理，不走此常规撤销。

**二次身份验证门控（新增约束）**：本服务在每次执行高敏操作（远程对讲、视频监控、远程车窗控制）前，作为安全门控点要求调用方完成**二次身份验证**——操作请求方（家属端）须先通过基础设施层的身份认证模块完成二次身份验证（指纹/人脸/动态短信），本服务校验二次验证结果通过后，方才进入 AccountRole 与 Permission 的权限判定环节。若二次验证未通过或未执行，直接返回 `SecondaryAuthRequired` 拒绝。高危失能场景下的家属端**自动激活接入**为安全优先特例——由系统侧基于场景判定与既有的权限校验驱动，可豁免家属侧手动二次发起身份验证，但仍须经系统侧的场景有效性校验。该门控逻辑是需求明确的安全规则（req_v4 §六），必须在授权流程中建模。

**协作**：
- 订阅 RiskDeterminedEvent（跟踪 L3 持续时长、识别 L3 下降）与 RiskResolvedEvent（识别风险解除）。
- 订阅 EmergencyActivatedEvent（触发自动激活）。
- 订阅 **CameraOcclusionDetectedEvent**（触发权限临时撤销）与 **CameraOcclusionRemovedEvent**（判断是否恢复此前临时撤销的权限）。
- 在执行高敏操作前，调用（或要求调用方提供）二次身份验证结果；该验证机制由基础设施层实现，本服务仅作为门控点校验其结果。
- 输出 FamilyAccessGrantedEvent（携带被授权账户、驾驶员、权限范围、授权原因）与 FamilyAccessRevokedEvent（携带被撤销账户、驾驶员、撤销原因：常规风险下降/物理遮挡/驾驶员注销）。
- 权限授予/撤销结果写回 SystemAccount 聚合中的 Permission 值对象（撤销表现为以已撤销的新 Permission 实例替换旧实例）。
- 订阅 **DriverDeactivatedEvent**（驾驶员注销/账号删除触发），对在途的常规家属权限发出 FamilyAccessRevokedEvent 以收束家属接入会话。

> **设计约束（L3 计时状态的归属与隔离）**：L3 持续时长追踪属**会话级临时状态**，而非领域服务的持久成员字段。设计层面参照 DS-05 的 DetectionWindow 模式——将 L3 计时信息封装为独立的 **`L3DurationTracker` 值对象（VO-17）**，由 Trip 聚合持有，随 Driver 的 L3 首次检出创建、L3 解除标记终止。PermissionService（DS-08）在每次处理 RiskDeterminedEvent / RiskResolvedEvent 时，从 Trip 聚合中读取对应 Driver 的 L3DurationTracker 作为输入，返回更新后的 Tracker（含是否达到 60s 阈值），由本服务据此决策是否授予/撤销权限，并将更新后的 Tracker 写回 Trip 聚合。如此 DS-08 对外保持**无状态**（输入 = 当前 Tracker + 事件，输出 = 新 Tracker + 可选的 Grant/Revoke 事件），计时状态归 Trip 聚合。边缘侧单线程环境保证无并发竞争。Tracker 的生命周期与 Trip 会话一致——行程创建时无 L3Tracker，L3 首次检出时创建，L3 解除后标记终止但保留累计时长供审计/统计，行程结束时随 Trip 冻结。

---

#### DS-09：ScoringService（驾驶评分服务 — BR-05）

**职责**：按 BR-05 公式计算行程级评分：`max(0, 100 − 重度疲劳×10 − 分心×5 − 路怒×8 − 急刹/急加速×2)`，clamp 至 [0,100]。计算完成后，**通过 TripRepository 将 TripScore（VO-05）值对象写入 Trip 聚合持久存储**，随后发出 TripScoredEvent。周期级评分按行程时长加权平均。当评分 < 60 时，触发绩效预警通知推送。此外，本服务负责计算 Driver 级**综合风险评分**并写回 Driver 聚合。

**数据来源全链路闭合**：
- 重度疲劳次数、分心次数、路怒触发次数：来源于 Trip 关联的 SafetyAlertEvent 统计（分别按 AlertType=FATIGUE × RiskLevel=L3、AlertType=DISTRACTION、AlertType=ROAD_RAGE 筛选计数）。路怒触发次数按 Trip 关联的 SafetyAlertEvent 中 AlertType=ROAD_RAGE 的告警实体数量计，一次路怒判定（触发 RiskDeterminedEvent，AlertType=ROAD_RAGE）→ 解除（触发 RiskResolvedEvent，AlertType=ROAD_RAGE）→ 再判定视作两次触发，与疲劳统计口径原则一致（每次 RiskDeterminedEvent 对应一次告警实体创建，累加计数）。
- **急刹/急加速次数**：来源于 Trip 聚合持有的 **DrivingBehaviorCounters（VO-16）**——基础设施层的持续加速度监测组件在整个行程中检测加速度突变并驱动计数器增量更新，形成从"传感器数据 → 计数器 → 评分公式"的完整数据链路（详见 VO-16、DS-17、决策 17）。不可用 VehicleStateSnapshot 的 30s ring buffer 替代全程计数。

**周期评分边界语义**：
- **周期归属规则**：一次行程的 TripScore 归属于其**行程结束时间戳所在的自然周期**。若行程跨越两个周期（如月末行程在次月结束），以结束时间戳所在周期为准，不将行程拆分归属。
- **加权计算公式**：周期评分 = **∑(TripScore_i × duration_i) / ∑duration_i**，其中 `duration_i` 为该周期内每个行程的时长（行程结束时间戳 − 行程开始时间戳）。加权平均以行程时长作为权重的理由：时长更长的行程对驾驶员的整体安全画像贡献更大，应给予更高权重。若周期内无行程，返回 `Option.None`（非错误，表示该周期无可评分数据）。公式结果 clamp 至 [0,100]。
- **计算时机**：周期评分采用**按需即时计算**，而非定时预计算。触发时机为管理员请求周期评分（DS-11 ReportGenerationService 调用）或车队看板刷新周期统计（DS-10 FleetAnalyticsService 调用）时，ScoringService 即时查询该周期内所有已完成行程的 TripScore 并按时长加权平均。即时计算避免了预计算结果的时效性不一致问题（如行程评分在周期结束后才完成落库）。
- **行程评分与周期评分的关系**：行程评分（TripScore）在行程结束时计算并持久化至 Trip 聚合；周期评分不单独持久化，作为聚合查询结果即时返回。周期评分是派生值，其唯一数据来源为 Trip 聚合中已持久化的 TripScore 和行程起止时间戳，保证了数据溯源性和幂等计算。

**Driver 综合风险评分的计算与写回闭环**（修复悬空链路）：
- Driver 的综合风险评分是**跨行程聚合指标**，计算口径为 Driver 名下近期（如近 30 天或近 N 次行程）所有 TripScore 的加权平均，clamp 至 [0,100]。
- 计算时机：每次行程评分完成（TripScoredEvent 发出后）或按周期（如每日）重算。
- **写回路径**：ScoringService 完成 Driver 级评分计算后，产出 **DriverScoreUpdatedEvent**（领域事件），由轻量领域服务 **DriverScoreUpdateService** 消费——该服务订阅 DriverScoreUpdatedEvent，通过 DriverRepository 读取 Driver 聚合、更新其综合风险评分字段后写回（以乐观锁保证一致性）。该写回链路遵循 DDD 分层惯例：领域事件由领域服务消费，仓储仅负责持久化操作，聚合不直接订阅事件。
- 该设计闭合了"Driver 的综合风险评分由谁计算、何时更新、通过什么机制写回"的完整赋值/更新链路。

**协作**：
- 输入：Trip 聚合中的告警统计（重度疲劳次数、分心次数、路怒触发次数）+ Trip 聚合中的 **DrivingBehaviorCounters**（急刹/急加速次数）。
- **TripScore 持久化**：计算完成后，通过 **TripRepository** 将 TripScore（VO-05）值对象写入 Trip 聚合持久存储，确保行程评分作为 Trip 聚合的持久化属性、不因进程崩溃而丢失。
- 输出：TripScoredEvent（行程级评分完成信号，携带 Trip 标识、TripScore 值、扣分项明细）、TripScore 值对象（周期级）。
- 评分 < 60 时发出 PerformanceWarningEvent。
- 行程评分完成后，计算 Driver 综合风险评分并发出 **DriverScoreUpdatedEvent**（携带 Driver 标识、新评分、旧评分、计算周期）。

---

#### DS-10：FleetAnalyticsService（车队分析服务）

**职责**：按车队维度聚合疲劳指数分布——正常/轻度/重度占比、风险热力图（地理位置 + 风险等级）。支持默认每 5 分钟周期刷新和手动即时刷新。支持钻取：点击某风险等级板块下钻至高风险司机明细列表。

**接口契约**（参照决策 19 端口定义风格）：
- `getFleetFatigueDistribution(): Result<FatigueDistribution, QueryError>` — 按车队维度聚合疲劳指数分布（正常/轻度/重度占比）和风险热力图（地理位置 × 风险等级）。`FatigueDistribution` 至少包含各风险等级占比映射、热力图坐标序列及对应风险等级；`QueryError` 至少表达数据源不可用和查询超时语义。
- `drillDown(riskLevel: RiskLevel): Result<Array<DriverSummary>, QueryError>` — 按指定风险等级下钻至高风险司机明细列表。`DriverSummary` 至少包含司机标识、综合风险评分、最近行程摘要和主要扣分项；`QueryError` 同上。

**协作**：
- 查询多个 Trip 和 Driver 聚合的数据（经 CQRS 读模型投影，不穿透聚合根）。
- 输出聚合结果（纯数据，不产生副作用）。
- 看板刷新和钻取查询由本服务提供接口，缓存和刷新策略由基础设施层支持。钻取交互的行为契约见场景 12。

---

#### DS-11：ReportGenerationService（报告生成服务）

**职责**：按指定司机 + 时间范围（周/月/季）生成驾驶行为分析报告，含重度疲劳次数、分心触发次数、路怒触发次数、急刹次数、急加速次数等关键指标的统计与趋势，确保与 BR-05 评分公式的输入维度一一对应。报告同时单列上述五个维度的周期累计总次数。报告支持导出为 PDF/Excel。须在 15 秒内完成生成。

**接口契约**（参照决策 19 端口定义风格）：
- `generateReport(driverId: DriverId, timeRange: TimeRange): Result<ReportData, ReportError>` — 按指定司机和时间范围生成驾驶行为分析报告。`ReportData` 至少包含各维度（重度疲劳/分心/路怒/急刹/急加速）的计次统计与趋势、周期评分、扣分项明细；`ReportError` 至少表达无数据（返回空报告而非异常）和生成超时（15s SLA 未完成）语义。调用方在 ≤15s 内获取生成结果或超时错误。

**协作**：
- 输入：Driver 标识 + TimeRange 值对象。
- 查询 Trip 聚合中的告警和快照数据。
- 调用 ScoringService 获取周期评分。
- 输出报告数据结构（由基础设施层负责 PDF/Excel 渲染）。15s SLA 行为契约见场景 10。

---

#### DS-12：EmergencyRescueService（应急救援服务）

**职责**：SOS 自动呼叫与上报——向救援中心/120 发送精准定位、事故前 30 秒车辆状态快照、驾驶员实时生命体征。管理远程解锁授权：救援机构核实险情后云端授权开启车门锁。管理驾驶员健康档案调取：救援机构授权后可调取 DriverHealthProfile。

**救援机构的远程解锁授权模型**：救援机构的远程解锁与健康档案调取采用**形式化授权凭证模型**——云端救援协调系统在险情核实后，签发一个 **RescueAuthorizationToken（VO-23）** 值对象，授权凭证由救援机构的 SystemAccount（AccountRole.RESCUE）持有。授权生命周期如下：
1. **签发**：云端救援协调系统在险情核实后，生成 RescueAuthorizationToken（含目标 Vehicle/Driver 标识、授权操作集合、有效期），经外部通信通道传递给救援机构。
2. **校验**：EmergencyRescueService 在执行远程解锁或健康档案调取操作前，对 RescueAuthorizationToken 执行三重校验：① 凭证未过期（当前时间 ≤ 签发时间戳 + 有效期）；② 凭证未被消费（未被标记为已使用）；③ 目标 Vehicle/Driver 标识与凭证中记录一致。同时校验持有者 SystemAccount 的 AccountRole 为 RESCUE——角色为授权前提条件，Token 为授权能力凭证。
3. **消费**：校验通过后，执行对应操作（远程解锁车门、或调取健康档案），操作完成后标记 RescueAuthorizationToken 为已消费（以新已消费实例替换旧实例），防止重放攻击。
4. **过期**：超过有效期的凭证自动失效，校验时返回 `AccessDenied`（拒绝原因为"授权已过期"）。过期凭证不自动清理，由审计模块定期归档。
5. 校验失败——包括凭证过期、已消费、目标不匹配、持有者角色非 RESCUE——均返回 `AccessDenied`，携带具体拒绝原因供审计与前端提示。

**家属手动应急救援联动（手动升级路径）**：除 BR-06 自动激活路径外，家属端在视频巡视时若发现紧急情况（如驾驶员昏迷），可一键触发应急救援联动。本服务提供 `triggerManualRescue(driverId: DriverId, requesterAccount: AccountId): Result<Unit, RescueError>` 方法契约——家属端通过应用层调用此方法，本服务不依赖碰撞/失能判定，直接复用现有的数据组装与上报能力（查询 Trip 聚合的最新 PhysiologicalSnapshot、经 VehicleStateBuffer 回取近 30s VehicleStateSnapshot、组装 RescueReport），产出 **FamilyManualRescueRequestedEvent** 驱动救援上报与家属端通知。该方法仅允许 AccountRole=FAMILY 角色调用，且调用方须持有对该 Driver 的有效监护授权（经 PermissionService 校验）。与 BR-06 自动路径的区别：手动路径不产出 EmergencyActivatedEvent（无碰撞判定语义）、不上报 AlertPersistenceService（非安全告警类型），仅触发救援链路的事件上报。

**协作**：
- 输入：EmergencyActivatedEvent。
- 查询 Trip 聚合（**最新 PhysiologicalSnapshot**，提取实时生命体征摘要）、Driver 聚合（DriverHealthProfile，提取档案级健康信息）、Vehicle 聚合（车门锁状态）。
- 输出：RescueReport（VO-13，包含 GeoLocation、生命体征摘要——来源为 Trip 聚合的最新 PhysiologicalSnapshot、VehicleStateSnapshot 集合、健康档案摘要——来源为 DriverHealthProfile）。
- 远程解锁/车窗控制的授权校验与执行链路见场景 11。

---

#### DS-13：PrivacyProtectionService（隐私保护服务 — BR-04）

**职责**：确保 BR-04 的隐私数据边界被遵守——DMS 原始图像在边缘侧完成脱敏（人脸关键点提取/模糊化），云端仅接收脱敏后的数值特征向量；未经授权严禁原始高清视频上云。管理路怒语音存证的全生命周期：录制→加密→留存→到期清除。

**协作**：
- 在数据上云路径中作为**守门人**校验数据是否已脱敏。
- **订阅 RiskDeterminedEvent**：当 AlertType=ROAD_RAGE 时，通过 **RoadRageVoiceRecordRepository**（AR-05 的聚合根仓储）创建 RoadRageVoiceRecord 聚合根，设置录制开始时间、加密存储和保留到期时间。
- **订阅 RiskResolvedEvent**：当 AlertType=ROAD_RAGE 解除时，通过 RoadRageVoiceRecordRepository 加载对应存证，标记录制封闭并写回（见决策 16、场景 3）。
- 通过 RoadRageVoiceRecordRepository 按到期时间查询待清除存证，执行自动清除。
- 处理审计场景下的授权访问请求（通过 RoadRageVoiceRecordRepository 按告警 ID 查询）。
- **订阅 DriverDeactivatedEvent**：当驾驶员注销/账号删除时，删除或脱敏路怒语音存证（RoadRageVoiceRecord）等隐私敏感数据。

**接口契约**：
- `validateDataDesensitization(data: SensorReading): Result<Unit, PrivacyViolation>` — 在数据上云路径中校验感知数据是否已完成脱敏。未通过脱敏校验时返回 `PrivacyViolation`（携带违规详情供审计），调用方据此阻断上云操作。
- `startVoiceRecording(alertId: AlertId, driverId: DriverId): Result<RecordId, PrivacyError>` — 启动路怒语音录制，返回存证标识。`PrivacyError` 至少表达 `RecordingAlreadyActive`（已有进行中的录制）和 `StorageFull`（边缘侧存储空间不足）。
- `sealVoiceRecord(recordId: RecordId): Result<Unit, PrivacyError>` — 封闭指定路怒语音存证，标记录制结束并写入终止时间戳。`PrivacyError` 至少表达 `RecordNotFound`。
- `purgeExpiredRecords(): Result<Int, PrivacyError>` — 按保留策略清除已到期存证，返回本次清除的记录数。清除过程中的单条失败不中断整体清除流程，失败记录保留至下一轮。

---

#### DS-14：SensorSelfCheckService（传感器自检服务 — BR-08）

**职责**：对关键传感器（摄像头/雷达等）执行周期性自检。区分两类异常并产出不同事件，避免与 BR-08 传感器故障路径产生歧义：

① **传感器硬件/链路故障（BR-08 路径）**：传感器无响应、通信中断或持续返回无效数据——产出 **SensorFailureEvent**（携带车辆标识、故障传感器列表、时间戳），触发 HMI 持续语音提示"安全监测系统已失效"并在车队大屏标记"监测脱线"。此为全系统级失效告警。
② **摄像头物理遮挡**：传感器正常通信且自检响应正常，但通过画面对比/遮挡检测算法判定摄像头画面被物理遮挡——本检测能力以领域端口 **CameraOcclusionDetectionPort** 形式声明于领域层（输入契约），由基础设施层感知通道实现并在装配阶段注入 DS-14：
  - `onOcclusionDetected(event: OcclusionDetectedSignal): Unit` — 基础设施层检测到摄像头物理遮挡时回调，`OcclusionDetectedSignal` 携带车辆标识、被遮挡传感器标识、时间戳。
  - `onOcclusionRemoved(event: OcclusionRemovedSignal): Unit` — 基础设施层检测到遮挡移除时回调，`OcclusionRemovedSignal` 携带车辆标识、恢复传感器标识、时间戳。
  DS-14 在此回调中产出 **CameraOcclusionDetectedEvent**（携带车辆标识、被遮挡传感器标识、时间戳），仅影响依赖该摄像头的功能（如家属视频监控权限临时撤销），不触发全系统失效告警。遮挡移除后，通过相同回调产出 **CameraOcclusionRemovedEvent**（携带车辆标识、恢复传感器标识、时间戳）。

**协作**：
- 输入：传感器自检信号 + 感知通道的遮挡检测结果。
- 输出：SensorFailureEvent（故障）、**CameraOcclusionDetectedEvent**（遮挡开始）、**CameraOcclusionRemovedEvent**（遮挡移除）。
- 更新 Vehicle 聚合中的 SensorStatus（故障路径）；遮挡路径不更新 SensorStatus（传感器健康但被遮挡），仅驱动 PermissionService 的权限临时撤销/恢复。

**接口契约**：
- `runSelfCheck(vehicleId: VehicleId): Result<SelfCheckResult, SelfCheckError>` — 执行一次全传感器自检。`SelfCheckResult` 至少入各传感器通道的状态集合（`Map<SensorId, SensorStatus>`）和被判定为物理遮挡的传感器列表；`SelfCheckError` 至少表达 `CheckTimeout`（自检超时）和 `VehicleNotFound`。
- `onOcclusionDetected(event: OcclusionDetectedSignal): Unit` — 基础设施层感知通道检测到摄像头物理遮挡时回调（CameraOcclusionDetectionPort 的实现方调用），本服务据此产出 CameraOcclusionDetectedEvent。
- `onOcclusionRemoved(event: OcclusionRemovedSignal): Unit` — 基础设施层感知通道检测到遮挡移除时回调，本服务据此产出 CameraOcclusionRemovedEvent。

---

#### DS-15：OTAUpdateService（OTA 升级管理服务）

**职责**：管理车载终端固件的版本管理、升级包下发与校验、断点续传、完整性校验、失败回滚、静默升级。本服务**不持有升级状态**——升级状态机由 Vehicle 聚合中的 **OTAUpgradeStatus（VO-19）** 值对象承载，DS-15 每次处理升级事件时以无状态方式读取 Vehicle 聚合的当前 OTAUpgradeStatus、计算下一阶段的新实例并写回 Vehicle 聚合。

**协作**：
- 管理 OTAVersion 值对象的新旧版本比对：通过 Vehicle 聚合获取当前固件版本（VO-08），与待升级的 OTAVersion 比对以决定是否启动升级。
- 升级状态机的推进由本服务以纯函数方式驱动（输入 = 当前 OTAUpgradeStatus + 升级事件，输出 = 新 OTAUpgradeStatus + 可选领域事件），状态转换触发条件与失败回滚/断点续传语义见场景 9。
- 升级完成时发出 OTAUpgradeCompletedEvent，同时通过 Vehicle 聚合将固件版本更新为新版本；升级失败回滚时发出 OTAUpgradeFailedEvent，Vehicle 聚合保持原固件版本不变。

**接口契约**：
- `initiateUpgrade(vehicleId: VehicleId, targetVersion: OTAVersion): Result<Unit, OTAUpgradeError>` — 对比 Vehicle 聚合的当前固件版本与目标版本，判定是否需要启动升级。若需升级，在 Vehicle 聚合中创建初始 OTAUpgradeStatus（PENDING 阶段），通过 OTADeliveryPort 开始下发升级包并推进至 TRANSMITTING 阶段。`OTAVersion` 比对逻辑（版本号比较、车型兼容性校验）由本方法内部完成。`OTAUpgradeError` 至少表达 `AlreadyUpToDate`（当前已是最新版本，无需升级）、`IncompatibleTarget`（目标版本与车辆型号不兼容）和 `UpgradeInProgress`（已有升级会话正在进行，不支持并行升级）。
- `handleTransferProgress(vehicleId: VehicleId, progress: DeliveryProgress): Result<UpgradeStage, OTAUpgradeError>` — 处理 OTADeliveryPort 上报的传输进度。`DeliveryProgress` 携带已传输字节数、总字节数和传输状态（传输中/传输完成/传输中断）。若传输中断且未超重试上限，本方法返回当前 TRANSMITTING 阶段（DS-15 调用方据此执行重试）；若传输完成，推进至 VERIFYING 阶段；若传输中断且超重试上限，推进至 ROLLED_BACK 阶段并产出 OTAUpgradeFailedEvent。`OTAUpgradeError` 至少表达 `TransmissionRetryExhausted`（重试已达上限）。
- `handleVerificationResult(vehicleId: VehicleId, checksumValid: Bool): Result<UpgradeStage, OTAUpgradeError>` — 处理升级包完整性校验结果。校验通过（checksumValid=true）时推进至 READY 阶段，按静默升级时机进入 UPGRADING 阶段执行刷写；校验失败（checksumValid=false）时推进至 ROLLED_BACK 阶段，产出 OTAUpgradeFailedEvent（失败阶段=校验）。`OTAUpgradeError` 至少表达 `ChecksumMismatch`（校验摘要不匹配，已触发回滚）。
- `handleFirmwareFlashResult(vehicleId: VehicleId, flashSuccess: Bool): Result<UpgradeStage, OTAUpgradeError>` — 处理固件刷写结果。刷写成功（flashSuccess=true）时推进至 COMPLETED 阶段，产出 OTAUpgradeCompletedEvent（含旧/新版本、升级耗时），通过 Vehicle 聚合将固件版本更新为新版本；刷写失败（flashSuccess=false）时推进至 ROLLED_BACK 阶段，产出 OTAUpgradeFailedEvent（失败阶段=刷写），Vehicle 聚合保持原固件版本不变。`OTAUpgradeError` 至少表达 `FlashFailed`（刷写失败，已回滚至旧固件，安全回滚不变式已保持）。

---

#### DS-16：DriverStatusBroadcastService（驾驶员状态广播服务 — 家属常态同步）

**职责**：为 BR-07 远程监护提供**常态状态同步**机制——离散的告警事件仅在判定成立时触发，无法满足家属端持续展示驾驶员实时状态（绿/黄/红）的需求。本服务按 **≥1Hz** 周期采样驾驶员当前风险状态，生成 DriverStatusSnapshot（VO-15）并经推送通道下发至家属端，端到端 ≤2s 上报。状态色由当前风险派生：无风险/L1 → 绿、L2 → 黄、L3 → 红。

**协作**：
- 输入：驾驶员当前风险状态（由边缘侧会话上下文维护的当前活跃风险集派生，与 DS-01 解除判定共用同一会话状态来源，避免重复维护）。
- 输出：DriverStatusSnapshot（VO-15），经基础设施层推送通道下发家属端。
- **跨端推送中转路径**：边缘侧生成快照后，经以下两级网络跃点到达家属 APP：**边缘侧终端 → 云端推送服务（MQTT/长连接网关，华为云）→ 家属 APP**。该中转路径独立于 Trip 同步上报链路（Trip 同步为周期性批量上报，时延不可控），采用轻量推送通道直达云端推送网关，由网关向已建立长连接的家属 APP 实时下发。两端到端 **≤2s** 时延分配预算：边缘侧采样+序列化 < 100ms，边缘→云端网络传输 < 800ms，云端推送服务路由+下发 < 600ms，家属 APP 接收+渲染 < 500ms。
- 与离散告警链路互补：告警链路负责"事件性"高优先级推送，本服务负责"常态性"低优先级遥测，二者不互相阻塞。
- 作为高频遥测，其推送走非安全攸关的异步链路（§6.2），不占用 ≤500ms 安全判定链路资源。

---

#### DS-17：DrivingBehaviorTrackingService（驾驶行为追踪服务 — 急刹/急加速计数）

**职责**：为 BR-05 评分公式与报告统计提供急刹/急加速的**全行程计数**。不同于 SafetyAlertEvent（安全告警），急刹/急加速是驾驶行为指标而非安全告警——不触发分级干预、不产生 AlertTriggeredEvent，仅在行程结束时作为 ScoringService 评分公式的输入。

**协作**：
- 输入：由基础设施层的**持续加速度监测组件**（与 VehicleStateBuffer 的 30s ring buffer 相独立的模块）在检测到加速度突变超过阈值时，通过领域层声明的依赖接口（**DrivingBehaviorTrackingPort**，见决策 17）以增量事件形式上报至本服务。
- 处理：本服务接收增量事件，更新当前活跃 Trip 聚合中的 **DrivingBehaviorCounters（VO-16）**——急刹次数或急加速次数递增 1。
- 输出：计数器更新本身不产生领域事件（属 Trip 聚合内部状态变更，已有 Trip 聚合的持久化机制保障）；仅当该行程结束由 ScoringService 读取时作为评分输入生效。
- 本服务在边缘侧持续运行，维持 trip 级会话内的增量累加语义。

**接口契约**：
- `onHardBrakingDetected(event: HardBrakingEvent): Unit` — 基础设施层（持续加速度监测组件）检测到急刹事件时回调。本服务接收后递增当前活跃 Trip 聚合的 DrivingBehaviorCounters 中急刹计数。回调在边缘侧进程内同步执行，保证计数的确定性。
- `onHardAccelerationDetected(event: HardAccelerationEvent): Unit` — 同上，针对急加速事件。`HardBrakingEvent` / `HardAccelerationEvent` 由基础设施层组装，携带时间戳和加速度幅值，具体检出阈值由基础设施层按车型/传感器标定配置（见决策 20）。

---

#### DS-18：DriverScoreUpdateService（驾驶员评分更新服务）

**职责**：轻量领域服务，消费 DriverScoreUpdatedEvent 后通过 DriverRepository 更新 Driver 聚合的综合风险评分字段。ScoringService（DS-09）在计算出新的 Driver 综合风险评分后产出 DriverScoreUpdatedEvent，本服务订阅该事件并执行 Driver 聚合的字段更新——通过仓储读、写回，确保评分写回路径遵循 DDD 分层惯例。本服务不承担其他业务逻辑，仅作为事件消费者与仓储操作者之间的桥梁。

**协作**：
- 订阅 DriverScoreUpdatedEvent。
- 通过 DriverRepository 读取 Driver 聚合，更新综合风险评分字段，并通过仓储写回。

---

#### DS-19：AlertPersistenceService（告警持久化服务）

**职责**：负责将风险判定结果转化为 SafetyAlertEvent 实体并将其持久化至 Trip 聚合，发出 AlertTriggeredEvent 驱动通知链路。解决原设计中 SafetyAlertEvent 创建者未显式指定的问题——原设计各行为契约场景以被动语态"生成 SafetyAlertEvent"表述，DS-01 仅产出 RiskDeterminedEvent/RiskResolvedEvent，创建者的悬空阻塞了编码阶段的实体创建职责分配。

**协作**：
- 订阅 RiskDeterminedEvent（由 RiskDeterminationService 门面产出）和 LifeDetectedEvent（由 DS-05 产出）和 EmergencyActivatedEvent（由 DS-06 产出）——三类判定事件均经本服务统一完成告警实体创建。
- 对于 RiskDeterminedEvent / LifeDetectedEvent：通过 TripRepository 加载 Trip 聚合，创建 SafetyAlertEvent 并关联至 Trip 的告警列表，通过 TripRepository 写回，随后发出 AlertTriggeredEvent。
- 对于 EmergencyActivatedEvent（碰撞失能）：创建 SafetyAlertEvent（AlertType=COLLISION_DISABILITY）并通过 TripRepository 持久化，**同样发出 AlertTriggeredEvent**。碰撞失能的实时救援推送由 EmergencyRescueService 路径独立处理（SOS 呼叫与救援上报），但 AlertTriggeredEvent 同时发出以确保碰撞失能告警可通过常规告警查询路径被管理员审计和追溯——碰撞失能告警作为 SafetyAlertEvent 可被检索（如车队管理员查询全队告警历史），其 AlertTriggeredEvent 驱动通知推送模块按管理员通知偏好推送，解决了"碰撞失能 SafetyAlertEvent 不可检索"的暗数据问题。两条通知路径（紧急救援推送 + 常规告警通知）互为补充，不互斥。
- 本服务为轻量领域服务，不承担判定逻辑，仅负责"判定结果→告警实体→持久化→事件发出"的编排。将判定与告警实体持久化解耦，使各判定服务（DS-01/DS-05/DS-06）专注于判定逻辑而不需感知实体创建和持久化细节。

**为何是领域服务**：本操作涉及 Trip 聚合的跨仓储操作（TripRepository）和领域事件发布，不适合归属任何单一聚合。

---

### 3.5 领域事件

#### 3.5.0 DomainEvent 公共抽象

领域事件总线和各消费方依赖一个统一的公共抽象来确定"一个类型如何才能被视为领域事件"。本设计将 `DomainEvent` 定义为一个**Java `interface`（marker interface）**，所有领域事件类型在实现时实现该接口。

为消除决策 8（聚合标识使用值对象 class）与 aggregateId 使用 `String` 类型之间的不一致，将 `aggregateId` 从裸 `String` 改为带类型标记的 `AggregateId` 值对象——该 class 封装聚合根标识的字面值及其聚合类型枚举，使事件消费方可在编译期区分不同聚合根标识类型，避免手动解析字符串：

```java
public class AggregateId {
    private final String value;
    private final AggregateType aggregateType;  // enum: TRIP | DRIVER | VEHICLE | SYSTEM_ACCOUNT | ROAD_RAGE_VOICE_RECORD
    // constructor, equals(), hashCode()
}
```

其中 `AggregateType` 为 `enum`，穷举系统中五类聚合根类型。该 class 不可变、重写 `equals()`/`hashCode()` 满足值相等语义——与决策 8"聚合标识使用值对象 class"一致。

```java
public interface DomainEvent {
    String getEventId();
    Timestamp getOccurredAt();
    AggregateId getAggregateId();
}
```

- **`eventId`**：事件唯一标识（UUID），供事件总线进行幂等去重与审计追踪。由事件构造方在创建事件实例时生成。
- **`occurredAt`**：事件发生时间戳（UTC），记录业务事实的发生时刻，非事件持久化时刻。由事件构造方填入。
- **`aggregateId`**：关联的聚合根标识，以 `AggregateId` class 表达，标识此事件源于哪个聚合根的状态变更。`AggregateId.value` 承载标识字面值（如 TripId、DriverId 等），`AggregateId.aggregateType` 提供编译期可区分的聚合根类型标签，使事件消费方无需手动解析字符串即可在编译期区分聚合根标识类型。

各具体领域事件类型（如 `RiskDeterminedEvent`、`FamilyAccessGrantedEvent` 等）在 Java 中以不可变 `class` 定义并 `implements DomainEvent` 接口——`class`（通过全字段 final + 构造器赋值）保证事件载荷的不可变性，`implements DomainEvent` 保证事件总线可在编译期校验事件类型的有效性。`eventId`、`occurredAt`、`aggregateId` 这三个元数据字段由各事件 class 自行携带（Java `interface` 可声明 getter 方法要求），供事件总线提取后写入 outbox 表或在日志/审计中使用，领域消费方可通过事件实例直接访问这些公共元数据而不必依赖 outbox 表。

#### 3.5.1 领域事件一览

| 事件 | 触发时机 | 携带关键信息 | 主要消费方 |
|------|----------|-------------|-----------|
| `RiskDeterminedEvent` | RiskDeterminationService 完成一次**流式融合判定**（仅 AlertType ∈ `{FATIGUE, DISTRACTION, ROAD_RAGE}`） | Trip 标识、RiskLevel、AlertType、判定时间戳、异常特征快照 | InterventionService（按 AlertType×RiskLevel 生成干预）、PermissionService（跟踪 L3 持续时长/识别 L3 下降）、PrivacyProtectionService（AlertType=ROAD_RAGE 时触发语音存证录制）、**AlertPersistenceService（通过 TripRepository 创建 SafetyAlertEvent 并发出 AlertTriggeredEvent）** |
| `RiskResolvedEvent` | RiskDeterminationService 判定某类此前成立的流式风险**不再持续**（条件不再满足） | Trip 标识、已解除的 AlertType、解除时间戳 | PrivacyProtectionService（AlertType=ROAD_RAGE 时停止录制、封闭存证）、InterventionService（停止对应类型干预升级、恢复车内环境）、PermissionService（风险解除参与常规撤销判定） |
| `AlertTriggeredEvent` | AlertPersistenceService 通过 TripRepository 创建 SafetyAlertEvent 后发出 | 告警 ID、AlertType、RiskLevel、GPS、Timestamp | 通知推送模块、车队看板刷新 |
| `VehicleIgnitionOffLockedEvent` | 车辆熄火且车门落锁后，由车辆状态变更产生（基础设施层信号） | Vehicle 标识、时间戳 | LifeDetectionService（触发活体判定窗口） |
| `LifeDetectedEvent` | **LifeDetectionService（独立事件触发型服务，不经门面汇总）** 在 60s 窗口内判定成立（AlertType=LIFE_DETECTION，独占此取值） | Vehicle 标识、判定置信度、Timestamp | 家属推送模块、车辆 HMI 控制（双闪/鸣笛）、**AlertPersistenceService（创建 SafetyAlertEvent 并发出 AlertTriggeredEvent）** |
| `EmergencyActivatedEvent` | **EmergencyResponseService（独立事件触发型服务，不经门面汇总）** 判定 BR-06 碰撞+失能条件满足（AlertType=COLLISION_DISABILITY，独占此取值） | Driver 标识、GeoLocation、VehicleStateSnapshot、Timestamp | EmergencyRescueService、PermissionService（自动激活家属端）、**AlertPersistenceService（创建 SafetyAlertEvent 并发出 AlertTriggeredEvent）** |
| `SensorFailureEvent` | SensorSelfCheckService 检测到关键传感器故障 | Vehicle 标识、故障传感器列表、Timestamp | 车队看板（标记脱线）、HMI 语音提示 |
| `CameraOcclusionDetectedEvent` | SensorSelfCheckService 通过感知通道检测到摄像头被物理遮挡（区别于故障） | Vehicle 标识、被遮挡传感器标识、Timestamp | **PermissionService（触发权限临时撤销）**、HMI 提示驾驶员传感器被遮挡 |
| `CameraOcclusionRemovedEvent` | SensorSelfCheckService 检测到摄像头物理遮挡已移除，画面恢复正常 | Vehicle 标识、恢复传感器标识、Timestamp | **PermissionService（判断是否恢复此前临时撤销的权限）**、HMI 撤销遮挡告警提示 |
| `FamilyAccessGrantedEvent` | 家属获得对某 Driver 的访问权限（常规 60s 或自动激活、或物理遮挡解除恢复） | SystemAccount 标识、Driver 标识、Permission、授权原因（常规/高危/遮挡恢复） | 家属 APP 推送、远程对讲/视频通道建立、**HMI 层（向驾驶员发出声光提示：对讲/视频已接通，并保留驾驶员物理遮挡权）** |
| `FamilyAccessRevokedEvent` | 家属对某 Driver 的常规权限被撤销（L3 风险下降不再持续、驾驶员物理遮挡、或驾驶员注销/账号删除） | SystemAccount 标识、Driver 标识、撤销原因（风险下降/物理遮挡/驾驶员注销） | 家属 APP（断开对讲/视频接入）、SystemAccount 聚合（清除 Permission） |
| `TripScoredEvent` | ScoringService 完成一次行程级评分并通过 TripRepository 写入 TripScore 后发出 | Trip 标识、TripScore、扣分项明细 | 报告生成、趋势统计 |
| `DriverScoreUpdatedEvent` | ScoringService 完成 Driver 级综合风险评分计算 | Driver 标识、新评分、旧评分、计算周期 | **DriverScoreUpdateService（轻量领域服务）** 消费后通过 DriverRepository 更新 Driver 聚合的综合风险评分字段 |
| `PerformanceWarningEvent` | 行程级或周期级评分 < 60 | Driver 标识、Score、评估周期、主要扣分项 | 管理员通知推送 |
| `OTAUpgradeCompletedEvent` | 车载终端固件升级成功 | Vehicle 标识、旧版本、新版本、升级耗时 | 车队管理日志、版本追踪 |
| `OTAUpgradeFailedEvent` | OTA 升级因校验/传输失败回滚 | Vehicle 标识、目标版本、失败阶段、回滚结果 | 车队管理日志、升级重试调度 |
| `DriverDeactivatedEvent` | 驾驶员注销/账号删除 | Driver 标识、注销时间戳 | PermissionService（清理监护关系、发出 FamilyAccessRevokedEvent 收束家属会话）、PrivacyProtectionService（删除/脱敏敏感数据如 RoadRageVoiceRecord）、各模块按隐私合规策略执行历史行程数据匿名化保留或删除 |
| `FamilyManualRescueRequestedEvent` | 家属端在视频巡视时一键触发应急救援联动（手动升级路径，不经碰撞/失能判定） | Driver 标识、请求家属 AccountId、位置信息、时间戳 | EmergencyRescueService（组装 RescueReport 并投递至救援中心）、家属 APP（推送救援已触发的确认通知） |

> **常态状态同步说明**：DriverStatusSnapshot（VO-15）的 ≥1Hz 周期推送属高频常态遥测，不建模为离散领域事件（避免污染事件总线与 outbox），由 DS-16 经独立的异步推送通道下发（见 §6.2）。

---

### 3.6 领域事件总线契约

领域事件发布/订阅机制基于 Spring Framework 的 `ApplicationEventPublisher` 和 `@EventListener` / `@TransactionalEventListener` 实现，保持对 Spring 上下文的具体实现解耦。本节定义领域事件总线的接口契约，基础设施层以 Spring 事件机制为底层实现。

#### 契约 1：事件发布接口

领域事件总线的核心发布操作以单一泛型方法定义，消费方不感知底层传输机制（进程内同步回调 vs outbox + 消息队列）：

```java
void publish(E event);
```

- 行为语义：
  - 边缘侧安全攸关事件（RiskDeterminedEvent / RiskResolvedEvent 等）在进程内同步回调已注册的消费方后返回成功。
  - 云端侧或跨进程事件写入 outbox 表、与聚合根状态更新在同一事务提交后返回成功（事务回滚时事件不对外可见）。
- 错误语义：`EventPublishError` 至少表达 `OutboxPersistFailed`（outbox 持久化失败，事务已回滚）和 `NoHandlerRegistered`（无注册消费方时可选策略——静默成功或返回该错误，由具体实现决定）。post-transaction 阶段的异步投递失败（消息代理不可达）由基础设施层 outbox 投递器负责重试，不通过此接口返回。

#### 契约 2：同步消费注册

边缘侧同步消费链路（安全攸关的判定→干预管线，见 §6.2、决策 10）通过以下注册契约绑定消费方：

```java
<E extends DomainEvent> void registerSyncHandler(String eventTypeName, Consumer<E> handler);
```

- **`eventTypeName`**：以字符串表达的事件类型判别符（如 `"RiskDeterminedEvent"`）。Java 原生支持运行时类型反射（`Class<T>`），实现阶段可直接以 `registerSyncHandler<E extends DomainEvent>(Class<E> eventType, Consumer<E> handler)` 进行类型安全注册，`eventTypeName` 由 `Class<E>.getSimpleName()` 推导，提供编译期推导和更强的类型安全性。
- **行为语义**：注册后，当 `publish` 被调用且当前运行环境为边缘侧时，在 publish 返回前同步执行 handler。handler 内部异常不阻断其他 handler 的执行（由事件总线捕获并记录），但单个 handler 异常不影响 publish 返回成功。
- **生命周期**：同步 handler 在边缘侧进程生命周期内始终有效，注销通过配套的 `unregisterSyncHandler(eventTypeName: String)` 完成。

#### 契约 3：异步消费注册与生命周期

云端侧异步消费链路（通知推送、看板刷新、报告生成等非实时路径）通过以下注册契约绑定消费方：

```java
<E extends DomainEvent> Subscription registerAsyncHandler(String eventTypeName, Consumer<E> handler);
```

- **`eventTypeName`**：与同步消费注册一致（契约 2），以字符串表达事件类型判别符。实现阶段可直接以 `registerAsyncHandler(Class<E> eventType, Consumer<E> handler)` 利用 Java 运行时类型反射进行类型安全注册。
- 行为语义：注册后，当事件从 outbox 表投递至消息队列并被消费方拉取时，执行 handler。`Subscription` 为句柄类型，调用方可通过其 `cancel()` 方法注销该异步消费。
- 投递保证级别：**at-least-once**——outbox 投递器保证每条已持久化的事件至少被投递一次，消费方 handler 须自行实现幂等处理（基于事件标识去重）。投递失败由基础设施层 outbox 投递器按退避策略无限重试或转入死信队列，不属于领域层职责。

#### 契约 4：Outbox 表结构约定

outbox 表作为事件持久化的存储载体，其最小表结构约定如下（具体字段类型由基础设施层按存储引擎适配，领域层仅定义信息需求）：

- `event_id`：事件唯一标识（UUID/有序序号），供消费方幂等去重。
- `event_type`：事件类型判别符（如 `"RiskDeterminedEvent"`），供消费方路由。
- `aggregate_id`：关联的聚合根标识（如 Trip 标识），供调试与审计追踪。
- `payload`：事件完整载荷，以序列化形式存储（JSON/二进制，由基础设施层选择）。
- `occurred_at`：事件发生时间戳。
- `published`：投递状态标记（0 = 待投递、1 = 已投递），由 outbox 投递器更新。

领域层**不直接访问** outbox 表——事件发布通过 `publish` 接口完成，领域服务与聚合根不感知 outbox 表的存在。

---

### 3.7 聚合根仓储接口契约

领域服务与 AlertPersistenceService 等多处依赖仓储接口进行聚合根的持久化和加载操作。为统一契约标准，本节定义四大聚合根仓储的核心接口契约。仓储接口以Java `interface` 声明于领域层（如 `domain.model` 模块或独立 `domain.repository` 模块），由基础设施层提供具体实现并在装配阶段注入各领域服务。

#### 3.7.1 TripRepository

核心方法契约：

```java
Optional<Trip> findById(TripId tripId) throws PersistenceException;
void save(Trip trip) throws PersistenceException;
```

- **findById**：按行程标识加载 Trip 聚合。返回值使用 `Optional<Trip>`——`Optional.empty()` 表达"行程不存在"的正常业务情形（非错误），`PersistenceException` 表达数据源不可用等系统级故障（以 unchecked exception 抛出，调用方按需捕获）。
- **save**：持久化 Trip 聚合（创建或更新）。通过 Trip 聚合内部的乐观锁版本号检测并发写冲突——若保存时发现版本号与数据源不一致（已被其他操作更新），抛出 `OptimisticLockException`（`PersistenceException` 的子类）。调用方（领域服务）据此决定重试或返回业务错误。
- **PersistenceException**：至少包含 `ConnectionFailedException`（数据源不可用）、`OptimisticLockException`（乐观锁版本冲突）、`UnknownPersistenceException`（未分类的持久化故障）。领域服务对乐观锁冲突按场景分别处理——评分计算等非实时场景可重试，安全判定等实时场景记录错误并降级。

#### 3.7.2 DriverRepository

核心方法契约：

```java
Optional<Driver> findById(DriverId driverId) throws PersistenceException;
void save(Driver driver) throws PersistenceException;
void updateScore(DriverId driverId, DriverComprehensiveScore newScore) throws PersistenceException;
```

- **findById**：按驾驶员标识加载 Driver 聚合。语义同 TripRepository.findById。
- **save**：持久化 Driver 聚合。乐观锁冲突处理同 TripRepository.save。
- **updateScore**：轻量更新方法，仅更新 Driver 综合风险评分字段（DriverScoreUpdateService 使用）。以原子条件更新实现乐观锁——`WHERE version = expectedVersion`，冲突时抛出 `OptimisticLockException`。与 `findById + save` 模式等价但性能更优（避免全量加载 → 修改 → 写回），适用于 DS-18 仅需更新单一字段的场景。

#### 3.7.3 SystemAccountRepository

核心方法契约：

```java
Optional<SystemAccount> findById(AccountId accountId) throws PersistenceException;
List<SystemAccount> findByDriver(DriverId driverId) throws PersistenceException;
void save(SystemAccount account) throws PersistenceException;
```

- **findByDriver**：按驾驶员的 Driver 标识查询与该驾驶员存在监护关系的所有家属账户（AccountRole=FAMILY）。返回空 `List` 时表达"无关联家属"的正常业务情形。供 PermissionService 在权限授予/撤销时查找关联家属。
- **save**：乐观锁冲突处理与其他仓储一致。

#### 3.7.4 RoadRageVoiceRecordRepository

核心方法契约：

```java
Optional<RoadRageVoiceRecord> findById(RecordId recordId) throws PersistenceException;
Optional<RoadRageVoiceRecord> findByAlertId(AlertId alertId) throws PersistenceException;
List<RoadRageVoiceRecord> findByExpiryBefore(Instant deadline) throws PersistenceException;
void save(RoadRageVoiceRecord record) throws PersistenceException;
void delete(RecordId recordId) throws PersistenceException;
```

- **findByAlertId**：按关联的 SafetyAlertEvent 标识查找存证。供 PrivacyProtectionService 在路怒解除（RiskResolvedEvent）时定位对应存证以执行封闭操作。
- **findByExpiryBefore**：按保留到期时间查询待清除的存证列表。供 PrivacyProtectionService 按保留策略周期执行到期清除。
- **delete**：物理删除过期的存证记录。删除失败时抛出 `PersistenceException`，由 PrivacyProtectionService 降级为标记状态并等待下一轮清除。

#### 3.7.5 VehicleRepository

核心方法契约（遵循与其他仓储一致的接口模式——findById + save + 乐观锁冲突检测）：

```java
Optional<Vehicle> findById(VehicleId vehicleId) throws PersistenceException;
void save(Vehicle vehicle) throws PersistenceException;
```

- **findById**：按车辆标识加载 Vehicle 聚合。返回 `Optional<Vehicle>`——`Optional.empty()` 表达"车辆不存在"的正常业务情形，`PersistenceException` 表达数据源不可用等系统级故障。供 SensorSelfCheckService（DS-14）读取和更新传感器自检状态、OTAUpdateService（DS-15）读取当前固件版本和 OTA 升级状态、EmergencyRescueService（DS-12）更新车门锁状态。
- **save**：持久化 Vehicle 聚合（创建或更新）。乐观锁冲突检测与其他仓储一致——通过 Vehicle 聚合内部的乐观锁版本号检测并发写冲突（如 OTA 升级状态更新与传感器自检状态更新同时对 Vehicle 聚合执行写操作），冲突时抛出 `OptimisticLockException`，调用方按场景分别处理：传感器自检立即重试一次（自检为周期性操作），OTA 升级在重试上限内重试（见 §6.3）。
- **PersistenceException**：语义同 TripRepository 中的 PersistenceException 定义——`ConnectionFailedException`、`OptimisticLockException`、`UnknownPersistenceException`。

#### 3.7.6 仓储设计原则

- **接口在领域层、实现在基础设施层**：所有仓储接口以Java `interface` 声明于领域层，基础设施层提供基于具体存储引擎（金仓数据库/边缘侧本地存储）的实现并在装配阶段注入。金仓兼容 PostgreSQL 协议，JPA 方言使用 PostgreSQLDialect。
- **乐观锁版本号对领域层透明**：领域服务通过捕获 `OptimisticLockException` 感知冲突，不直接接触版本号字段。版本号由仓储实现在 save 时管理。
- **返回类型统一使用 Optional + Exception**：以 `Optional<T>` 表达"实体不存在"的正常业务情形，以 `PersistenceException`（unchecked）表达系统级持久化故障，调用方据此决定降级策略（§5.3）。

---

## 四、关键行为契约

> 本节覆盖全部 16 个领域服务的核心交互场景。本版新增场景 8（分心检出）、场景 9（OTA 升级）、场景 10（报告生成与状态同步）、场景 11（远程车窗/车门控制）、场景 12（看板钻取），并修订场景 3、场景 7 以反映风险解除语义与权限撤销闭环。

### 场景 1：疲劳驾驶判定与干预（BR-01 + 干预链）

1. 感知通道持续送入 DMS 视觉 SensorReading。
2. RiskDeterminationService 委托 FatigueDeterminationService 按 BR-01 条件判定。
3. 判定为轻度疲劳（L2）时：
   - RiskDeterminationService 产出 RiskDeterminedEvent（AlertType=FATIGUE，RiskLevel=L2）。
   - AlertPersistenceService 消费 RiskDeterminedEvent，通过 TripRepository 创建 SafetyAlertEvent（AlertType=FATIGUE，RiskLevel=L2）并发出 AlertTriggeredEvent。
   - InterventionService 按二维映射（FATIGUE × L2）生成"氛围灯变橙提醒"InterventionInstruction。
4. 判定为重度疲劳（L3）时：
   - RiskDeterminationService 产出 RiskDeterminedEvent（AlertType=FATIGUE，RiskLevel=L3）。
   - AlertPersistenceService 消费 RiskDeterminedEvent，创建 SafetyAlertEvent（AlertType=FATIGUE，RiskLevel=L3）并发出 AlertTriggeredEvent。
   - InterventionService 按二维映射（FATIGUE × L3）生成"语音播报 + 座椅强力震动 + 渐进升级 + 双闪 + CAN 减速指令"InterventionInstruction 集合（渐进升级序列：VOICE_BROADCAST 语音唤醒 → NAVIGATE_DECELERATION 建议/请求减速 → NAVIGATE_TO_SHOULDER 引导靠边）。
   - 若 L3 持续超过 60 秒，PermissionService 则向关联家属授予远程对讲/视频权限（BR-07 常规路径）。
5. 整个过程在边缘侧完成，端到端时延 ≤ 500ms（判定到指令下发）。

---

### 场景 2：车内活体遗留检测与报警（BR-02）

1. 车辆熄火、车门落锁后，产生 VehicleIgnitionOffLockedEvent，该事件触发活体监测会话；毫米波雷达自动开始扫描。
2. LifeDetectionService（独立事件触发型服务，不经 RiskDeterminationService 门面）以会话级 DetectionWindow 值对象承载 60 秒判定窗口状态，随每次雷达信号到达迭代更新窗口与判定结论（见 DS-05 设计约束）。
3. 若窗口内持续感应到微动：
   - 判定为"遗留生命风险"（L3，AlertType=LIFE_DETECTION）。
   - 直接产出 LifeDetectedEvent（不与门面产生重复判定事件）。
   - AlertPersistenceService 消费 LifeDetectedEvent，创建 SafetyAlertEvent（AlertType=LIFE_DETECTION）并发出 AlertTriggeredEvent。
   - 10 秒内：家属 APP 收到红色高频振动报警；车载双闪开启、短促鸣笛。
4. 若窗口内微动消失（存在时间 < 60s），判定取消，不触发告警（抑制误报）。雷达信号短暂中断又恢复时的窗口处理见 §5.4 边界条件 (3)。

---

### 场景 3：路怒检测、存证与解除（BR-03）

1. 语音情绪通道检测到声压级 >85dB 且含谩骂关键词。
2. 生理通道显示心率较静息上升 20%+。
3. RoadRageDeterminationService 两条件同时满足时判定路怒成立（L2），**将判定结果返回给 RiskDeterminationService 门面**。
4. RiskDeterminationService 汇总后产出 RiskDeterminedEvent（AlertType=ROAD_RAGE，RiskLevel=L2）。
5. 领域事件驱动的后续行为：
   - **InterventionService** 消费 RiskDeterminedEvent（ROAD_RAGE × L2）→ 按二维映射触发环境调节指令：空调温度调低 2°C、播放白噪音/舒缓音乐。
    - **PrivacyProtectionService** 消费 RiskDeterminedEvent（AlertType=ROAD_RAGE）→ 通过 RoadRageVoiceRecordRepository 创建 RoadRageVoiceRecord 聚合根，开始录制当前时段语音片段，存储于边缘侧，标记脱敏/加密，设置保留到期时间。
6. **路怒解除**：当路怒判定条件不再满足，RiskDeterminationService 产出 **RiskResolvedEvent（AlertType=ROAD_RAGE）**（而非携带某 RiskLevel 的 RiskDeterminedEvent）：
    - PrivacyProtectionService 消费后**通过 RoadRageVoiceRecordRepository 加载对应 AR-05 存证、标记录制封闭并写回**。
   - InterventionService 消费后**恢复车内环境**（解除空调调低/停止白噪音）。
   - 消费方据事件类型即可明确区分"新告警"与"解除信号"，不会出现录制不停止、空调持续低温（见决策 16）。

---

### 场景 4：碰撞事故应急响应（BR-06）

1. 加速度传感器检测到碰撞特征冲击。
2. EmergencyResponseService（独立事件触发型服务，不经 RiskDeterminationService 门面）通过 **PhysiologicalDataBuffer** 端口（决策 21）回取碰撞前至碰撞后 ≥10s 时间窗的生理数据，判定是否满足"心率骤停或意识丧失 >10 秒"条件。
3. 判定 BR-06 条件成立（L3，AlertType=COLLISION_DISABILITY）：
   - 经 VehicleStateBuffer 端口（决策 14 方法契约）回取事故前 30 秒窗内的 VehicleStateSnapshot 序列。
   - 直接产出 EmergencyActivatedEvent（不与门面产生重复判定事件）。
   - AlertPersistenceService 消费 EmergencyActivatedEvent，创建 SafetyAlertEvent（AlertType=COLLISION_DISABILITY）。
   - 跳过人工确认，立即驱动救援链路。
4. PermissionService 收到 EmergencyActivatedEvent，自动激活关联家属端对讲/视频接入（无需家属手动发起）。
5. EmergencyRescueService 消费 EmergencyActivatedEvent，组装 RescueReport（VO-13：GPS + 事故前 30s VehicleStateSnapshot 集合 + 实时生命体征摘要 + 授权后的健康档案摘要），向救援中心/120 上报；并处理远程解锁授权和健康档案调取（授权链路见场景 11）。

---

### 场景 4b：家属手动应急救援联动（手动升级路径）

> 本场景为与 BR-06 自动激活路径并存的**手动升级路径**——家属在视频巡视中发现紧急情况时，无需等待系统自动触发，可一键发起应急救援联动。

1. 家属端在视频巡视中观察到驾驶员异常（如昏迷、无反应）。
2. 家属通过家属 APP 触发"紧急救援"按钮，应用层调用 DS-12 `triggerManualRescue(driverId, requesterAccount)`。
3. PermissionService 校验请求方角色（须为 AccountRole=FAMILY）和对该 Driver 的有效监护授权；校验不通过则返回 `PermissionDenied`。
4. DS-12 接收请求后，不依赖碰撞/失能判定——直接执行数据组装与上报：
   - 查询 Trip 聚合的最新 PhysiologicalSnapshot，提取实时生命体征摘要。
   - 经 VehicleStateBuffer 端口回取近 30s 的 VehicleStateSnapshot 序列（少于 30s 时以可得数据降级上报）。
   - 经授权后从 DriverHealthProfile 提取健康档案摘要。
   - 组装 RescueReport（VO-13）。
5. DS-12 产出 **FamilyManualRescueRequestedEvent**（携带 Driver 标识、请求家属 AccountId、位置信息、时间戳）。
6. 家属 APP 消费 FamilyManualRescueRequestedEvent，收到"救援请求已触发"确认通知。
7. 救援链路消费 FamilyManualRescueRequestedEvent，通过 RescueReportPort 向救援中心/120 投递 RescueReport。
8. 与 BR-06 自动路径的区别：手动路径**不产出** EmergencyActivatedEvent（无碰撞判定语义）、**不触发** AlertPersistenceService（非安全告警类型），仅触发救援链路的事件上报。

---

### 场景 5：驾驶评分与绩效预警（BR-05 + Driver 综合风险评分闭环）

1. 行程结束时，Trip 聚合被标记为完成。
2. ScoringService 查询 Trip 关联的 SafetyAlertEvent 统计：重度疲劳次数（AlertType=FATIGUE × RiskLevel=L3）、分心次数（AlertType=DISTRACTION）、路怒次数（AlertType=ROAD_RAGE）。
3. ScoringService 读取 Trip 聚合持有的 **DrivingBehaviorCounters（VO-16）** 获取急刹次数和急加速次数——该计数器由 DS-17 DrivingBehaviorTrackingService 在整个行程中持续增量更新，来自基础设施层持续加速度监测组件的急刹/急加速检出（与 VehicleStateBuffer 的 30s ring buffer 独立）。
4. 按公式计算 TripScore：`max(0, 100 − A×10 − B×5 − C×8 − D×2)`，结果 clamp 至 [0,100]。
5. **ScoringService 通过 TripRepository 将 TripScore（VO-05）值对象写入 Trip 聚合持久存储**，随后发出 TripScoredEvent。
6. ScoringService 计算 Driver 综合风险评分：汇总 Driver 名下近期（如近 30 天）所有 TripScore 的加权平均，clamp 至 [0,100]，产出 **DriverScoreUpdatedEvent** 并写回 Driver 聚合的综合风险评分字段。
7. 若 TripScore < 60，发出 PerformanceWarningEvent，通知模块将绩效预警推送给关联车队管理员。
8. 周期评分（周/月/季）由 ScoringService 按该周期内所有行程的 TripScore 按时长加权平均计算，同样 clamp 至 [0,100] 并在 < 60 时触发绩效预警。

---

### 场景 6：传感器自检与异常处理（BR-08，含物理遮挡检测路径）

1. SensorSelfCheckService 周期性对关键传感器执行自检。

**6a 传感器硬件/链路故障路径（BR-08）**：
2a. 若传感器无响应、通信中断或持续返回无效数据——判定为传感器硬件/链路故障。
3a. 在 3 秒内发出 SensorFailureEvent。
4a. HMI 层消费事件：持续语音提示"安全监测系统已失效，请注意驾驶安全"。
5a. 车队看板消费事件：标记该 Vehicle 为"监测脱线"。
6a. 故障恢复后再次自检通过，清除脱线标记并停止语音提示。

**6b 摄像头物理遮挡检测路径**：
2b. 若传感器正常通信但通过画面对比/遮挡检测算法判定摄像头画面被物理遮挡——产出 **CameraOcclusionDetectedEvent**（携带车辆标识、被遮挡传感器标识、时间戳）。
3b. HMI 层消费 CameraOcclusionDetectedEvent：向驾驶员发出传感器被遮挡提示（如"摄像头被遮挡，部分安全功能暂时不可用"，区别于故障路径的"安全监测系统已失效"提示）。
4b. **PermissionService** 消费 CameraOcclusionDetectedEvent：触发权限临时撤销——如当前家属持有视频监控权限，立即撤销并以 FamilyAccessRevokedEvent（撤销原因=物理遮挡）通知家属端断开视频接入。
5b. 驾驶员移除遮挡后，传感器自检/遮挡检测算法判定画面恢复正常——产出 **CameraOcclusionRemovedEvent**（携带车辆标识、恢复传感器标识、时间戳）。
6b. HMI 层消费 CameraOcclusionRemovedEvent：撤销遮挡告警提示，恢复正常安全监测状态提示。
7b. **PermissionService** 消费 CameraOcclusionRemovedEvent：判断是否恢复此前临时撤销的权限——若该 Driver 的 L3 风险在此遮挡期间持续未中断且 L3 持续时长已达 60s 阈值，则自动恢复权限，发出 FamilyAccessGrantedEvent（授权原因=遮挡恢复）；若 L3 已不再持续或尚未达 60s 阈值，维持无权限状态（见场景 7 步骤 5）。

---

### 场景 7：家属常规权限授予与撤销（BR-07 常规路径，授予—撤销闭环）

1. PermissionService 订阅 RiskDeterminedEvent，依据 Trip 聚合中对应 Driver 的 **L3DurationTracker（VO-17）** 跟踪每个 Driver 的 L3 风险持续时长。
2. **授予**：当某 Driver 的 L3 连续超过 60 秒时：
   - 授予关联家属账户"远程对讲 + 视频监控"权限（创建新 Permission 实例，更新 SystemAccount 聚合）。
   - 发出 FamilyAccessGrantedEvent（授权原因=常规）。
   - 家属 APP 收到事件后可发起对讲/视频接入。
   - **同时通过 HMI 向驾驶员发出声光提示**——告知远程对讲/视频已建立，并保留驾驶员物理遮挡权（驾驶员可随时遮挡摄像头以触发权限临时撤销）。
3. **常规自动撤销**：当该 Driver 的风险由 L3 下降不再持续——后续 RiskDeterminedEvent 携带 < L3 等级，或收到 RiskResolvedEvent——PermissionService：
   - 撤销此前常规授予的权限（以已撤销的新 Permission 实例替换旧实例）。
   - 发出 **FamilyAccessRevokedEvent**（撤销原因=风险下降）。
   - 家属端据此断开对讲/视频接入，闭环结束。
4. **临时撤销**：驾驶员可在任何时候物理遮挡摄像头。DS-14 通过感知通道检测到物理遮挡后，产出 CameraOcclusionDetectedEvent；PermissionService 消费该事件后，触发权限临时撤销并发出 FamilyAccessRevokedEvent（撤销原因=物理遮挡）。
5. **遮挡恢复后的权限恢复**：当驾驶员移除物理遮挡，DS-14 检测到摄像头恢复正常，产出 CameraOcclusionRemovedEvent。PermissionService 消费该事件后——若该 Driver 的 L3 风险在此遮挡期间持续未中断且 L3 持续时长已达 60s 阈值，则自动恢复此前临时撤销的权限（以新 Permission 实例写回），发出 FamilyAccessGrantedEvent（授权原因=遮挡恢复）；若 L3 已不再持续或尚未达 60s 阈值，维持无权限状态，正常监测恢复。权限恢复不自动豁免二次身份验证——家属端重新发起高敏操作前仍须完成二次身份验证。
6. 高危失能场景（BR-06）的家属端自动激活接入不走本常规撤销路径，由救援流程结束后另行收束。

---

### 场景 8：分心检出与告警（DS-03，端到端时延约束）

1. DMS 视觉通道持续送入 SensorReading（视觉特征：视线方向）。
2. RiskDeterminationService 委托 DistractionDetectionService 判定：在最近 60 秒滑动窗口内，视线偏离前方累计达到 3 秒，判定分心成立（L2）。累计值限定在滑动窗口内计算，避免长期行程中累计值单调递增导致误判。
3. 子服务将判定结果（分心标志、判定时间戳）**仅返回门面**，不自行产出事件。
4. RiskDeterminationService 汇总后产出 RiskDeterminedEvent（AlertType=DISTRACTION，RiskLevel=L2）。
5. AlertPersistenceService 消费 RiskDeterminedEvent，创建 SafetyAlertEvent（AlertType=DISTRACTION）并发出 AlertTriggeredEvent。
6. InterventionService 消费 RiskDeterminedEvent（DISTRACTION × L2），按二维映射生成"告警"InterventionInstruction。
7. **时延约束**：从判定成立到告警下发须在 **0.5 秒**内完成；整链路在边缘侧同步消费完成（§6.2 边缘侧同步链路），满足该约束。

---

### 场景 9：OTA 固件升级全流程（DS-15，状态机与失败处理）

1. **待下发**：OTAUpdateService 比对 OTAVersion，确认目标车载终端需升级，进入待下发态。
2. **传输中**：下发升级包；支持**断点续传**——传输中断后，按已传输偏移量从断点继续，不重传已完成分片。
   - **下发失败重试**：传输失败时按退避策略重试；超过重试上限则转入失败处理（回滚态，发出 OTAUpgradeFailedEvent，失败阶段=传输）。
3. **校验中**：传输完成后执行完整性校验（摘要比对）。
   - **校验失败回滚**：校验不通过则丢弃升级包、保持原固件不变，转入回滚态，发出 OTAUpgradeFailedEvent（失败阶段=校验）。
4. **已就绪 → 升级中**：校验通过后进入已就绪态，按静默升级时机进入升级中态执行刷写。
5. **完成 / 回滚**：
   - 刷写成功 → 完成态，发出 OTAUpgradeCompletedEvent（含旧/新版本、耗时）。
   - 刷写失败 → 回滚至旧固件，发出 OTAUpgradeFailedEvent（失败阶段=刷写）。
6. 状态转换均由 OTAUpdateService 的静默升级状态机驱动；各失败路径均保证终端可回退至可用旧固件（安全回滚不变式）。

---

### 场景 10：报告生成与导出 + 家属常态状态同步

**10a 报告生成与导出（DS-11，15s SLA）**
1. 管理员（AccountRole=MANAGER）请求生成报告，输入 Driver 标识 + TimeRange（周/月/季）。
2. ReportGenerationService 查询该范围内 Trip 聚合的告警与快照统计（重度疲劳次数、分心触发次数、路怒触发次数、急刹次数、急加速次数——五个维度与 BR-05 评分公式输入一一对应）。
3. 调用 ScoringService 获取周期评分。
4. 组装报告数据结构，交由基础设施层渲染导出为 PDF/Excel。
5. **SLA**：从请求到生成完成须在 **15 秒**内；超时或范围内无数据按 §五 Result 语义返回业务结果（无数据 → 空报告/明确提示，而非异常）。

**10b 家属常态状态快照同步（DS-16，≥1Hz/≤2s）**
1. DriverStatusBroadcastService 按 ≥1Hz 周期采样当前 Driver 风险状态（取自边缘侧会话上下文的当前活跃风险集）。
2. 派生状态色：无风险/L1 → 绿、L2 → 黄、L3 → 红，生成 DriverStatusSnapshot（VO-15）。
3. 经异步推送通道下发家属端，端到端 ≤2s 上报。
4. 该常态同步与离散告警链路互补：即便当前无告警，家属端也能持续看到"绿色平稳"状态。

---

### 场景 11：远程车窗/车门控制授权与执行（DS-08 / DS-12，含二次身份验证门控）

1. 请求方发起远程车窗/车门控制：家属端（AccountRole=FAMILY，依 Permission 含车窗控制权限）或救援机构（经 EmergencyRescueService 险情核实授权）。
2. **二次身份验证门控**（req_v4 §六安全规则）：
   - 家属端发起高敏操作（远程对讲、视频监控、车窗控制）前，须先完成**二次身份验证**（指纹/人脸/动态短信，由基础设施层身份认证模块实现）。
   - PermissionService（DS-08）在权限校验前作为门控点校验二次验证结果：未通过或未执行则直接返回 `SecondaryAuthRequired` 拒绝，不进入后续权限判定。
   - 高危失能场景（BR-06）下家属端自动激活接入为安全优先特例——由系统侧基于场景判定驱动，豁免家属手动二次发起，但仍须经系统侧场景有效性校验。
3. **授权校验**：
   - 家属请求：二次验证通过后，PermissionService 依 AccountRole 与 Permission 校验是否具备车窗控制授权；不具备则按 §五 Result 语义返回 `PermissionDenied`（携带拒绝原因供前端提示）。
   - 救援请求：EmergencyRescueService 校验救援机构远程解锁授权（云端授权开启车门锁）；未授权返回 `AccessDenied`。
4. **执行**：授权通过后，生成控制指令经基础设施层 CAN/HMI 下发执行，更新 Vehicle 聚合的车门锁/车窗状态。
5. 执行失败（设备无响应/链路故障）作为 C 类系统级故障向上抛给基础设施层重试/降级（§5.3）。

---

### 场景 12：车队看板钻取交互（DS-10）

1. 管理员看板默认每 5 分钟周期刷新疲劳指数分布（正常/轻度/重度占比）与风险热力图（GeoLocation × RiskLevel）。
2. 管理员可手动触发即时刷新。
3. **钻取**：点击某风险等级板块，FleetAnalyticsService 以该风险等级为条件下钻，返回高风险司机明细列表（只读聚合查询，经 CQRS 读模型投影，不穿透聚合根）。
4. 钻取结果即时返回；缓存与刷新频率由基础设施层控制（§6.3）。

---

## 五、错误处理策略

### 5.1 错误分类

系统领域层的错误分为三类：

**A 类——判定失败**：风险判定无法完成（如感知数据缺失、传感器故障导致输入不可用）。此类错误应由 SensorSelfCheckService 提前检测并发出 SensorFailureEvent，判定服务在输入不可用时返回 `None` 而非抛出异常——"无法判定"本身是一种合法的系统状态，不应打断其他正常运行的判定链路。

**B 类——业务规则违反**：操作违反了业务约束（如未经授权的家属尝试调取原始视频、评分时行程尚未结束）。此类错误使用 `Result<T, Error>` 模式，调用方可显式处理业务拒绝，不抛异常。

**C 类——系统级故障**：数据持久化失败、基础设施层面的异常。此类错误在领域服务中不捕获，向上抛给基础设施层统一处理（重试、降级、熔断等）。

### 5.2 错误表达方式选择

| 场景 | 策略 | 理由 |
|------|------|------|
| 感知数据缺失导致无法完成判定 | `Option<RiskDeterminedEvent>` — 返回 None | "无风险"与"无法判定"是不同的概念，None 明确表达后者，消费方据此决定是否降级处理 |
| 家属在无授权状态下请求对讲/视频/车窗控制 | `Result<Unit, PermissionDenied>` | 调用方需要知道拒绝原因以生成适当的用户提示 |
| 高敏操作前二次身份验证未通过或未执行 | `Result<T, SecondaryAuthRequired>` | 家属端须先完成二次身份验证（指纹/人脸/动态短信），PermissionService 门控校验未通过时返回此错误 |
| 救援机构远程解锁/调取健康档案但未获授权 | `Result<T, AccessDenied>` | 隐私合规要求显式处理未授权访问 |
| 评分时行程尚未结束（状态错误） | `Result<TripScore, ScoringError>` | 属于逻辑错误，应阻止操作并向上反馈 |
| 报告生成时间范围内无数据 | `Result<ReportData, ReportError>` 或空报告 | 范围内无数据是正常业务结果，应返回明确提示而非异常 |
| VehicleStateBuffer 回取窗口超出缓冲覆盖范围 | `Result<Array<VehicleStateSnapshot>, BufferError>` | 缓冲未覆盖请求窗口是可预期的业务情形，调用方（DS-06）需据此降级上报（见决策 14） |
| PhysiologicalDataBuffer 回取窗口超出缓冲覆盖范围 | `Result<Array<PhysiologicalSnapshot>, BufferError>` | 生理数据缓冲未覆盖请求窗口是可预期的业务情形，调用方（DS-06）需据此降级判定（见决策 21） |
| 活体检测 60 秒窗口内微动消失 | `Option<LifeDetectedEvent>` — 返回 None | 正常业务结果，非错误 |
| 领域事件发布失败（outbox 模式下持久化失败） | 抛异常，由基础设施层兜底（重试 + 死信） | outbox 模式将事件持久化与聚合根状态更新置于同一事务——若事务提交失败（含事件持久化失败），则聚合根状态更新也不生效，事务回滚后由调用方重试。此为 C 类系统级故障，领域层不捕获 |

### 5.3 整体原则

- 领域服务对外接口优先使用 `Option<T>` 表达"可能没有结果"的语义，使用 `Result<T, E>` 表达"可能成功也可能因业务原因失败"的语义。
- 仅在调用方错误使用 API（如传入非法参数、违反前置条件）时使用异常。
- 领域事件的发布采用 outbox 模式：事件持久化与聚合根状态更新在同一事务中提交，事务提交成功后事件才对消费方可见。若持久化失败，事务回滚，聚合根状态不变，事件不发布——保证"状态变更"与"事件已发布"的一致性。消费方自行负责事件处理的错误与重试。
- post-transaction 阶段的异步投递失败（outbox 表已持久化但消息代理投递失败）由基础设施层的 outbox 投递器负责重试，不属于领域层职责。

### 5.4 边界条件处理策略

**(1) 边缘侧断网时 Trip 本地持久化与云端同步一致性**：边缘侧采用"本地优先"——感知判定结果与 Trip 状态变更先在边缘侧本地持久化（保证断网时安全告警链路成立），再异步上报云端。云端同步采用**重试 + 幂等去重**：每条上报记录携带稳定的业务幂等键（如 Trip 标识 + 告警标识/事件序号），云端按幂等键去重，使断网恢复后的批量重传不产生重复行程/重复告警；上报失败不回滚本地状态，仅排队重试。该机制属基础设施层职责，领域层通过为聚合根/事件提供稳定标识予以支撑。

**(2) 驾驶员注销/账号删除时的监护关系清理与历史数据处理**：Driver 注销触发产出 **DriverDeactivatedEvent**（见 §3.5 领域事件表），各模块消费该事件后异步收尾——① PermissionService 消费 DriverDeactivatedEvent，清理该 Driver 与 SystemAccount（家属）的监护关系，对在途的常规家属权限发出 FamilyAccessRevokedEvent 收束接入会话，终止家属端对讲/视频连接；② PrivacyProtectionService 消费 DriverDeactivatedEvent，按隐私规则删除或脱敏敏感数据（RoadRageVoiceRecord 等）；③ 历史行程数据（Trip、SafetyAlertEvent、评分）由各仓储模块在收到 DriverDeactivatedEvent 后按合规策略处理：默认对统计/审计所需数据做**匿名化保留**（解除与 Driver 身份的关联但保留聚合统计价值），对隐私敏感数据（DriverHealthProfile 等）执行删除或脱敏。该事件驱动机制替代了跨聚合同步级联删除，确保各模块独立收束。

**(3) 毫米波雷达在 60s 判定窗口内短暂中断又恢复**：LifeDetectionService 的 DetectionWindow（DS-05）对信号中断采取"**保持窗口、暂停累计**"策略——短暂中断（小于约定的容差时长）不重置窗口、不清零已累计微动观测，信号恢复后在原窗口内继续累计；仅当中断超过容差时长（视为判定不可靠）才重置窗口并重新计时，避免因瞬时丢帧导致误判取消或误判成立。容差阈值作为 DetectionWindow 的判定参数由会话上下文携带，服务保持纯函数语义。

---

## 六、并发设计

### 6.1 整体线程模型

系统部署拓扑分为两个主要运行时环境：

**边缘侧（车载终端）**：单机部署，核心判定链路运行在有限 CPU 资源上。感知数据采集、风险判定、HMI 干预指令生成在同一个进程中按流水线方式串行处理——保证从判定到指令下发的 500ms 端到端时延在确定的计算资源上可度量。边缘侧不承受高并发压力，重点在于**确定性实时响应**而非并发吞吐。

**云端侧（华为云）**：Spring Boot 服务部署，承受车队级多车辆并发数据上报、多家属并发查询、多管理员并发看板操作。云端侧需要处理并发请求，但并发控制的重点在基础设施层（数据库连接池、缓存、消息队列），领域层本身保持无状态。

### 6.2 共享状态管理策略

**聚合根级别的乐观并发控制**：Trip、Driver、Vehicle 等聚合根的持久化更新采用乐观锁（版本号），避免多请求并发修改同一聚合根时产生的写冲突。冲突发生时由基础设施层重试或向调用方返回冲突错误。

**边缘侧状态管理**：边缘侧的"当前行程"（活跃 Trip 聚合）是单线程写入的——一次只有一次行程，感知数据按时间序列顺序到达。因此边缘侧的 Trip 聚合不存在并发写冲突，无需加锁。

**边缘侧会话上下文（EdgeSessionContext）**：边缘侧除持久化的 Trip 聚合外，存在一组**会话级临时状态**，这些状态不持久化、随行程结束而失效，承载于独立的 `EdgeSessionContext` 组件中。EdgeSessionContext 的职责与边界如下：

- **生命周期**：与边缘侧一次行程会话的进程生命周期一致——行程开始（点火）时创建，行程结束（熄火且数据同步完成后）时销毁。它不是聚合根、不持久化，是边缘侧进程内存中的临时容器。
- **与 Trip 聚合的关系**：EdgeSessionContext **不归属** Trip 聚合（它是基础设施层的运行时容器，非领域模型）。它与 Trip 聚合是**协作关系**——Trip 持有持久化的领域状态（PhysiologicalSnapshot 集合、DrivingBehaviorCounters、L3DurationTracker），EdgeSessionContext 持有判定过程中的临时状态。领域服务（DS-01、DS-05、DS-16）以 EdgeSessionContext 中的临时状态和 Trip 聚合中的持久状态共同作为输入，返回更新后的状态。
- **持有内容**（每类内容有明确归属边界）：
  - **当前活跃风险集（ActiveRiskSet）**：`Map<AlertType, RiskLevel>`，由 DS-01 每次流式判定后更新，用于判定"风险由成立转为解除"（对比本次判定结果与已有风险集，产出 RiskResolvedEvent），同时供 DS-16 派生状态色。归属 EdgeSessionContext。
  - **DetectionWindow（活体监测判定窗口）**：仅在活体检测会话存续期间存在，由 DS-05 以纯函数方式消费与返回更新（见 DS-05 设计约束）。归属 EdgeSessionContext。
  - **不持有的内容**：**L3DurationTracker（VO-17）归属 Trip 聚合**（持久化状态，供 DS-08 在 L3 持续时长判定中读写和持久化），**DrivingBehaviorCounters（VO-16）归属 Trip 聚合**（持久化状态，供 DS-17 增量更新和 DS-09 评分读取），**TripScore（VO-05）归属 Trip 聚合**（持久化状态，由 DS-09 在行程结束时写入）。三者均不归 EdgeSessionContext 持有——这一归属划分消除了"L3DurationTracker 归 Trip 聚合持有"与"归会话上下文持有"之间的双重归属张力：计时状态需在断网/进程重启后存活（持久化在 Trip 中），判定过程中的临时风险集无需持久化（在 EdgeSessionContext 中）。
- **线程安全**：边缘侧单线程环境使 EdgeSessionContext 无并发竞争，无需内部加锁。

**领域事件的发布与消费**：领域事件采用"先持久化事件、再异步投递"的 outbox 模式，确保事件不丢失且发布与聚合根状态更新在同一事务中。事件的消费方（如通知推送、看板刷新）异步处理，不阻塞核心判定链路。

**领域事件总线的实现策略**：领域事件总线的接口契约已在 **§3.6 领域事件总线契约** 中完整定义（发布接口 `publish`、同步消费注册 `registerSyncHandler`、异步消费注册 `registerAsyncHandler`、outbox 表结构约定与投递保证级别 at-least-once），此处仅阐明部署层面的实现策略。设计层面区分两种消费链路：

- **边缘侧同步消费**：安全攸关的判定→干预链路（如 InterventionService 消费 RiskDeterminedEvent/RiskResolvedEvent、分心 0.5s 告警），在边缘侧采用**进程内同步回调**——门面产出事件后直接同步调用注册的消费方，确保 ≤500ms（分心 ≤0.5s）端到端时延和断网可用性（见决策 10）。
- **云端侧异步消费**：通知推送、看板刷新、报告生成、家属常态状态快照推送（DS-16）等非实时路径，采用**outbox + 消息队列异步投递**（DriverStatusSnapshot 高频遥测走独立轻量推送通道）模式，消费方独立部署，允许秒级延迟。

**家属常态状态快照的跨端推送路径（DS-16，≤2s 端到端时延）**：DriverStatusSnapshot（VO-15）的推送采用独立的轻量推送通道，不依赖 Trip 同步上报链路（Trip 同步为周期性批量上报，不可控时延）。中转路径为 **边缘侧终端 → 云端推送服务（MQTT/长连接网关）→ 家属 APP**，两级网络跃点。端到端 ≤2s 时延预算分配见 DS-16 协作说明。边缘侧仅负责采样与序列化，云端推送网关负责连接管理、路由下发，家属 APP 通过长连接实时接收。

### 6.3 并发场景的具体策略

| 场景 | 策略 |
|------|------|
| 家属查询驾驶员当前状态 | 只读查询，无需锁，读已提交即可 |
| 家属端常态状态快照推送 | 单向高频遥测，无共享写状态，走异步推送通道，不与安全链路争用资源 |
| 多个管理员同时刷新车队看板/钻取 | 看板数据聚合为只读查询，缓存层面控制刷新频率（5 分钟），避免重复计算 |
| 评分计算与告警事件并发写入同一 Trip | Trip 聚合根使用乐观锁，冲突时评分计算重试 |
| 边缘侧判定写入与云端同步上报 | 边缘侧先本地持久化再异步上报云端，上报失败不影响本地判定；云端按幂等键去重（§5.4） |
| 家属权限授予/撤销的并发 | 对 SystemAccount 聚合使用乐观锁，权限变更以最后一次成功写入为准 |
| OTA 升级状态更新（DS-15）与传感器自检状态更新（DS-14）同时对 Vehicle 聚合执行 | Vehicle 聚合使用乐观锁，后提交者检测到冲突后重试：OTA 升级在重试上限内重试（与场景 9 一致），传感器自检立即重试一次（自检为周期性操作，单次冲突不影响下一周期自检）。如乐观锁冲突持续无法解决，以最近一次成功写入为准 |

---

## 七、设计决策

### 决策 1：以 Trip 而非 Driver/Vehicle 为核心聚合根

**理由**：系统的核心业务——风险判定、干预执行、评分计算——全部以"一次行驶行程"为上下文发生。Trip 是感知数据、告警事件、生理快照的自然汇聚点。以 Trip 为核心聚合根使得一次行程的所有关联数据在同一事务边界内保持一致，查询也最为自然。

**Java 实现考量**：Trip 内部持有 PhysiologicalSnapshot 集合和 SafetyAlertEvent 标识集合，通过 Java 泛型集合（`List<...>`）表达。聚合根的标识使用不可变值对象 class（重写 `equals()`/`hashCode()`）确保标识的不可变性。

---

### 决策 2：RiskDeterminationService 作为门面，内部委托子判定服务

**理由**：疲劳、分心、路怒三类判定均由**持续到达的流式感知**（DMS 视觉、生理、语音）驱动，各有独立的判定条件和阈值（BR-01、BR-03、分心规则），若将它们全部塞入一个判定服务将导致职责过重。采用门面+委托模式——RiskDeterminationService 负责流式感知数据的路由与融合判定结果的汇总，具体的判定逻辑委托给各自的子领域服务。这既保持了对调用方（流式感知通道）的统一入口，又保证了各判定规则的独立演进。

**门面委托范围（明确边界）**：本门面的委托子服务**仅限**流式融合判定的三个服务——**FatigueDeterminationService、DistractionDetectionService、RoadRageDeterminationService**。BR-02 活体遗留与 BR-06 碰撞失能**不在委托范围内**：二者是**事件触发型判定**，被设计为独立领域服务（DS-05、DS-06），自行产出 LifeDetectedEvent / EmergencyActivatedEvent，理由详见决策 13。

**Java语言考量**：各子判定服务的接口定义为Java `interface`——这使得不同判定算法可互替换（如边缘侧使用轻量规则判定，云端使用 AI 模型判定），符合接口隔离原则。

**补充约束**：作为门面委托子服务的（仅）三个流式判定服务**不得直接调用其他模块的领域服务或直接产出领域事件**——判定结果一律返回给 RiskDeterminationService 门面，由门面统一产出 RiskDeterminedEvent / RiskResolvedEvent。跨模块协作由消费方订阅事件完成。该约束**不适用于** DS-05/DS-06 这两个独立事件触发型服务。

---

### 决策 3：SafetyAlertEvent 为实体而非值对象，但非聚合根

**理由**：告警事件有独立标识（告警 ID），可在 Trip 之外被独立查询（如车队管理员查询全队告警历史），且告警有独立生命周期（可被归档、统计）。但它不是聚合根，因为每个告警都产生于一个具体的 Trip 上下文——告警的创建与 Trip 的告警列表变更应在同一事务内完成。

**补充设计约束（CQRS 投影）**：写侧仍通过 Trip 聚合根访问 SafetyAlertEvent 以保持事务一致性；读侧（跨行程告警查询、看板聚合、钻取）使用独立只读投影，避免穿透聚合根加载大量数据。

---

### 决策 4：Permission 为值对象而非实体

**理由**：权限由一组可执行操作定义，修改权限意味着创建新的权限集合，而非修改原有权限的"状态"。将 Permission 设计为值对象（不可变）避免了"修改权限时可能影响正在进行的对讲会话"这类并发问题——每次授予或撤销权限都创建新的 Permission 实例，旧实例在会话结束后自然过期。

---

### 决策 5：BR-04 隐私保护不设计为聚合约束，而设计为领域服务

**理由**：隐私保护是一个横切关注点——它跨越数据采集（DMS 脱敏）、数据上云（过滤原始图像）、语音存证（加密留存与到期清除）、数据调取（授权审计）。将它建模为单一聚合根的约束既不合理，也容易遗漏。独立为 PrivacyProtectionService 领域服务，在各数据流动路径上作为守门人校验，更符合横切关注点的本质。

**补充**：PrivacyProtectionService 通过消费 RiskDeterminedEvent（AlertType=ROAD_RAGE，触发录制）与 RiskResolvedEvent（AlertType=ROAD_RAGE，停止录制）获知路怒判定的成立与解除，遵循模块间解耦原则，不产生对 `domain.risk` 模块的直接编译期依赖。

---

### 决策 6：路怒语音存证的生命周期归属

**理由**：RoadRageVoiceRecord 的创建时机与路怒判定（BR-03）紧密耦合，其清除策略又受隐私规则约束。它拥有独立标识和独立生命周期（创建→到期→清除），且存储于边缘侧、不上云，与 Trip 的存储策略不同。因此设计为独立聚合根（AR-05），拥有自己的 RoadRageVoiceRecordRepository。其生命周期管理由 PrivacyProtectionService 通过仓储操作完成——录制起于 RiskDeterminedEvent、止于 RiskResolvedEvent，通过领域事件与告警事件保持松耦合。读侧按需通过仓储直接查询（按告警 ID、保留到期时间），不穿透 Trip 聚合根边界。与上一版（E-02 作为非聚合根实体、既暗示归属 Trip 聚合又与决策 6 排除 Trip 归属相矛盾）相比，独立为聚合根消除了所有者矛盾，并遵循了 DDD 实体必须通过其所属聚合根仓储进行持久化的原则。

---

### 决策 7：评分模块与通知模块的耦合方式

**理由**：BR-05 要求"评分 <60 时自动向管理员推送绩效预警"。设计上，ScoringService 在评分完成后发出 TripScoredEvent；若评分 <60，额外发出 PerformanceWarningEvent。通知推送模块消费 PerformanceWarningEvent 完成预警推送。评分服务不直接依赖通知服务——它只产出事件、不关心谁消费，解耦程度最大化。

---

### 决策 8：聚合间引用一律使用标识而非对象引用

**理由**：Trip 引用 Driver 和 Vehicle 时通过标识（ID）而非对象引用。这是 DDD 聚合设计的核心原则。使用标识引用使聚合可以独立加载和持久化，避免将多个聚合锁在同一事务中，也为未来的微服务拆分预留了清晰的边界，并为断网重传去重（§5.4）提供稳定幂等键。

**Java 实现考量**：聚合标识使用不可变值对象 class（重写 `equals()`/`hashCode()`），轻量、不可变，适合作为跨聚合引用。

---

### 决策 9：使用 enum 表达有限的、稳定的分类体系

**理由**：RiskLevel（L1/L2/L3）、AlertType、SensorStatus、AccountRole（FAMILY/MANAGER）、StatusColor（绿/黄/红）等取值集合固定且极少变化。使用Java `enum` 可确保：编译期穷尽检查、类型安全（不会误将字符串常量当作状态值/角色值）、代码可读性。AccountRole 的引入使 DS-08 权限路由以类型安全分支替代字符串/魔法值比较。

---

### 决策 10：领域事件的异步消费与边缘侧的顺序性

**理由**：边缘侧核心判定的端到端时延要求 ≤500ms（分心 ≤0.5s），判定→干预链路上的事件消费不能引入不可控的异步延迟。设计上，边缘侧的 RiskDeterminedEvent/RiskResolvedEvent → InterventionService 的消费是**同步的**（同一进程内直接调用）。云端侧和通知推送、家属常态状态推送的事件消费才采用异步模式。这体现了"安全链路优先"的原则。

---

### 决策 11：统一感知数据抽象 SensorReading

**理由**：四条感知通道的数据以自然语言分散描述于各判定服务，缺少统一契约。新增 SensorReading 值对象作为统一的感知数据抽象，为各判定服务提供一致的输入接口，使感知通道扩展不影响判定服务接口定义。

---

### 决策 12：值对象在 Java 中使用 class 并重写 equals/hashCode

**理由**：Java 中没有 `struct` 值类型，值对象使用 `class` 实现。值对象的核心语义是"由属性值定义相等性"，必须显式重写 `equals()` 和 `hashCode()` 方法以满足值相等语义（而非默认的引用相等）。结合 Spring Data JPA 时使用 `@Embeddable` 注解将值对象嵌入聚合根持久化。所有值对象（含本版新增的 VO-12~VO-15）均声明为不可变 class，构造时完成所有字段赋值，不提供 setter。

---

### 决策 13：区分"流式融合判定门面"与"事件触发型独立判定服务"两类判定模型

**理由**：系统的风险判定本质上存在两种截然不同的输入与触发模型：

- **流式数据驱动判定**（疲劳 BR-01、分心、路怒 BR-03）：输入是持续到达的感知流，判定是对当前时间窗内流式特征的连续评估，多通道结果可被有意义地**融合**为单一的 RiskDeterminedEvent。适合门面+委托模式（决策 2）。
- **事件触发型判定**（活体遗留 BR-02、碰撞失能 BR-06）：触发源是离散领域事件，判定逻辑、生命周期和产出事件都与流式融合无关，无法被"融合"进 RiskDeterminedEvent。其中 BR-06（碰撞失能）进一步细分为**以碰撞冲击为触发条件、但依赖持续生理数据缓冲的判定服务**——其触发依赖碰撞冲击（离散事件），但失能判定需要 ≥10 秒的生理数据（持续数据流）作回溯分析，因此 DS-06 在碰撞触发后需访问一个维护 ≥10s 生理数据滚动缓冲区（由生理采集组件在边缘侧持续维护，领域层通过 PhysiologicalDataBuffer 端口获取，端口契约见决策 21），以判定"心率骤停或意识丧失 >10 秒"。

**因此本设计将 BR-02、BR-06 明确为独立领域服务**：它们有独立触发条件、独立判定逻辑、独立领域事件，不经 RiskDeterminationService 门面。三类判定事件的 AlertType 取值两两不相交（见 VO-02），从结构上杜绝重复判定事件。

**Java语言考量**：独立判定服务与门面子服务同样以Java `interface` 定义行为契约，与门面同处 `domain.risk` 模块，依赖方向仍单向。

---

### 决策 14：事故前车辆状态的滚动缓存归属基础设施层，领域层以端口依赖

**理由**：BR-06 需要在碰撞时刻回取"事故前 30 秒"的 VehicleStateSnapshot，这要求系统在碰撞**之前**就持续采集并缓存近期车辆状态。该"持续采样 + 时间窗滚动缓存"是一种**有状态的、与领域判定正交的技术职责**——交给领域服务会破坏其无状态基线，塞进 Trip 聚合又会让聚合背负高频写入。因此归属**基础设施层**：边缘侧采集组件维护覆盖 ≥30s 的滚动缓冲（ring buffer），领域层只声明依赖接口（端口）**VehicleStateBuffer**。

**端口能力契约（核心方法签名）**：为解除上下游并行开发阻塞，明确 VehicleStateBuffer 端口的核心方法契约：

```java
List<VehicleStateSnapshot> getSnapshots(TripId tripId, TimeRange window) throws BufferException;
```

- **输入**：`tripId`（当前活跃行程标识，定位边缘侧对应缓冲）、`window`（TimeRange，事故前 30s 的回取时间窗）。
- **返回**：成功返回该时间窗内按时序排列的 `List<VehicleStateSnapshot>`。
- **错误语义**：`BufferException` 表达可预期的业务情形，至少含：`WindowNotCoveredException`（请求窗口超出缓冲保留范围，如行程刚开始不足 30s）、`BufferUnavailableException`（缓冲未初始化/采集组件异常）。DS-06 据此降级（如以可得的部分快照上报、或在报告中标注快照不完整），不抛异常。

**Java语言考量**：VehicleStateBuffer 以 Java `interface` 声明于领域层，基础设施层提供实现并通过 Spring 装配阶段注入 DS-06；`List<T>`/`TimeRange` 均为 Java 标准类型构造。

---

### 决策 15：干预策略采用 AlertType × RiskLevel 二维映射

**理由**：前一轮以单一 RiskLevel→干预的通用映射与各场景行为契约矛盾——同为 L2，疲劳应氛围灯、路怒应环境调节、分心应告警，三者不同，无法用一维 RiskLevel 表达。本版将 DS-07 的干预决策改为 **AlertType × RiskLevel 二维映射**：干预指令集合由"告警类型 + 风险等级"共同确定，与场景 1/3/8 的具体契约严格一致。L1 为预留维度（无规则触发，给出"无干预/仅记录"占位分支以满足穷尽检查），避免死代码同时保留扩展位。

**Java语言考量**：二维映射以 AlertType（`enum`）× RiskLevel（`enum`）的组合在 match/查表中分派，编译期可对（AlertType, RiskLevel）组合做穷尽性检查；输出统一为 InterventionInstruction（VO-12，`class`）集合。

---

### 决策 16：风险解除建模为独立领域事件 RiskResolvedEvent，而非扩展 RiskLevel/AlertType 取值

**理由**：场景 3 需表达"路怒已解除"以驱动停止录制、恢复空调，但 RiskLevel（L1/L2/L3 严重度阶梯）与 AlertType（类别维度）均无"已解除"语义。审查给出两条路径：(a) 为 RiskLevel 增加 RESOLVED 值；(b) 单独定义 RiskResolvedEvent。**本版采纳 (b)**：

- RESOLVED 并非一个"严重度"，将其塞入 RiskLevel 会污染该枚举的语义纯粹性（审查自身亦指出 RESOLVED "不属于 L1/L2/L3 体系"），且每个消费 RiskLevel 的 match 分支都须额外处理一个非严重度取值。
- 独立的 RiskResolvedEvent 以**事件类型本身**区分"新告警"与"解除信号"，消费方订阅语义清晰，无需在事件载荷里做特例判断，从根上杜绝"录制不停止、空调持续低温"。
- RiskResolvedEvent 复用既有 AlertType 标识"哪类风险解除"，对 FATIGUE/DISTRACTION/ROAD_RAGE 通用，可自然支撑 InterventionService 恢复干预、PermissionService 参与常规撤销（决策见 DS-08、场景 7）。

**Java语言考量**：RiskResolvedEvent 为新增领域事件类型，复用 §6.2 既定事件总线（边缘侧同步、云端异步），无新增语言能力诉求；解除判定所需的"当前活跃风险集"为会话级状态，由会话上下文持有（DS-01 解除判定的状态边界说明），门面保持纯函数。

---

### 决策 17：急刹/急加速计数采用独立的全程监测端口，与碰撞回取 ring buffer 分离

**理由**：诊断反馈指出 BR-05 评分公式所需的"急刹/急加速次数"全链路无数据来源——VehicleStateSnapshot 的 30s ring buffer 仅为碰撞时刻事故前景回取服务，不覆盖整个行程的计数。急刹/急加速计数需要**全程持续监测**加速度数据，与 ring buffer 是两种不同职责。因此：

- **基础设施层分离为两个独立组件**：(a) 车辆状态 ring buffer 组件（已有，30s 时间窗，为碰撞回取服务）；(b) 加速度事件检测组件（新增，全程持续运行，按阈值检出急刹/急加速事件并以增量事件上报）。
- 领域层声明依赖接口 **DrivingBehaviorTrackingPort**，契约类似"基础设施层检测到急刹/急加速事件时，以增量事件通知领域层"；端口由基础设施层实现并在装配阶段注入 DS-17。
- 领域层新增值对象 **DrivingBehaviorCounters（VO-16）**，被 Trip 聚合持有，随行程创建初始化、随行程结束冻结。
- 领域层新增领域服务 **DrivingBehaviorTrackingService（DS-17）**，接收端口增量事件并更新 Trip 的计数器。

如此闭合了"传感器数据 → 加速度监测 → 急刹/急加速检出 → 计数器增量 → 评分公式输入"的完整数据链路。

**Java语言考量**：DrivingBehaviorTrackingPort 以Java `interface` 声明于领域层；DrivingBehaviorCounters 为 `class` 值对象；DS-17 保持无状态、无持久字段（服务的计数器更新表现为接收事件→更新 Trip 聚合，通过仓储持久化），与现有 DS-14 SensorSelfCheckService 模式一致。

---

### 决策 18：二次身份验证建模为 PermissionService 的门控约束，而非独立值对象

**理由**：req_v4 §六明确要求远程控车（含车窗）、语音/视频对讲等高敏操作前必须二次身份验证。二次身份验证的**实现**（指纹/人脸/动态短信）是纯基础设施层职责，但**"高敏操作执行前必须经过此门控"这一规则**是领域层的行为约束。因此建模为：

- **PermissionService（DS-08）在每次高敏操作授权流程中，作为门控点校验二次身份验证结果**——调用方须提供已验证的二次身份凭据，PermissionService 在权限判定前先校验其有效性；未通过则直接拒绝（返回 `SecondaryAuthRequired`），不进入权限判定。
- **VO-07 Permission 的职责描述中纳入此约束**——权限授予不自动豁免二次验证要求。
- 高危失能场景（BR-06 自动激活）为安全优先特例：家属端自动激活接入由系统侧场景判定驱动，豁免家属手动二次发起验证，但须经系统侧的场景有效性校验。
- 场景 11 的授权流程显式加入二次验证步骤，使安全门控在行为契约层面可被追踪和验证。

不设计为独立值对象（如 SecondaryAuthToken）的理由：二次验证凭据的生命周期极短（一次性事务上下文）、由基础设施层管理（会话/令牌），其"通过/失败"结果在领域层仅表现为 PermissionService 的一个返回值分支——建模为独立领域值对象会过度升格实际上不属于持久化领域模型的技术细节。

**Java语言考量**：门控逻辑在 PermissionService 中以 Result 模式表达——`Result<AuthorizationResult, SecondaryAuthRequired>`，与现有业务错误处理策略（§五）一致。

---

### 决策 19：领域层声明外部系统依赖端口，统一接口契约标准

**理由**：DS-12（EmergencyRescueService）、DS-15（OTAUpdateService）、DS-16（DriverStatusBroadcastService）等多处领域服务以自然语言描述其与外部系统（SMN 推送、120 救援中心、SparkRTC 音视频、IoTDA OTA 下发）的交互，但未声明领域层的依赖端口接口。与此形成对比的是，VehicleStateBuffer（决策 14）和 DrivingBehaviorTrackingPort（决策 17）已有完整端口契约。为统一标准并解除领域服务与外部系统具体实现的编译期耦合，本决策新增四个领域端口接口，均以Java `interface` 声明于领域层，由基础设施层实现并在装配阶段注入。

#### 端口 1：NotificationPort（通知推送端口）

服务于 DS-12（救援报告上报）、DS-16（告警推送与常态快照下发）、DS-09（绩效预警推送）等所有需要向外部推送通知的场景。

核心方法契约：
```java
void pushNotification(AccountId recipient, NotificationPayload payload) throws NotificationException;
```
- `NotificationPayload` 封装通知的内容类型（告警 / 救援报告 / 状态快照 / 绩效预警）、标题、正文和优先级。
- `NotificationException` 至少表达 `DeliveryFailedException`（SMN 投递失败，基础设施层重试或降级）和 `RecipientUnreachableException`（接收方不可达，如未安装 APP 或未建立长连接）。
- 该端口由 DS-12、DS-16 和告警推送模块通过依赖注入获取，替换当前自然语言"通知推送模块"的松散引用。

#### 端口 2：RescueReportPort（救援报告投递端口）

服务于 DS-12 EmergencyRescueService，负责向 120 救援中心投递 SOS 救援报告。

核心方法契约：
```java
void deliverRescueReport(RescueReport report) throws RescueReportException;
```
- `RescueReportException` 至少表达 `DeliveryFailedException`（链路故障）和 `AckTimeoutException`（救援中心未在约定时间内确认接收）。DS-12 据此决定是否降级（如改用备用通信通道、或标记报告为"待补发"）。
- 该端口仅负责"投递"行为，不负责报告内容的组装（组装由 DS-12 完成）。

#### 端口 3：MediaSessionPort（音视频会话端口）

服务于 DS-08 PermissionService（家属远程对讲/视频监控的高敏操作通道建立与拆除），依托 SparkRTC 实现。

核心方法契约：
```java
SessionHandle establishSession(SystemAccount participant, Driver driver, SessionType sessionType) throws MediaSessionException;
void terminateSession(SessionHandle handle) throws MediaSessionException;
```
- `SessionType` 为 `enum`（AUDIO、VIDEO），标识会话类型。
- `SessionHandle` 为会话句柄，领域层不关心其内部实现（如 RTC 房间 ID、Token），仅作为 `establishSession` 的返回值供后续 `terminateSession` 引用。
- `MediaSessionException` 至少表达 `SessionEstablishFailedException`（信令或媒体通道建立失败）和 `SessionNotFoundException`（终止时未找到对应会话）。会话建立失败的处理策略由领域层在调用方决定（如 DS-08 在权限授予后尝试建立会话，失败则返回特定错误）。

#### 端口 4：OTADeliveryPort（OTA 下发端口）

服务于 DS-15 OTAUpdateService，负责将升级包通过 IoTDA 下发至车载终端。

核心方法契约：
```java
DeliveryProgress deliverPackage(VehicleId vehicle, OTAPackage pkg, Optional<ByteOffset> resumeFrom) throws OTADeliveryException;
```
- `OTAPackage` 封装升级包摘要（版本、大小、校验和、分片元信息），领域层不关心物理文件存储位置。
- `resumeFrom` 可选参数标识断点续传的起始偏移字节——`Optional.empty()` 表示全新下发，`Optional.of(offset)` 表示从已传输偏移量继续。
- `DeliveryProgress` 封装当前传输进度（已传输字节、总字节、是否完成），供 DS-15 驱动状态机转换。
- `OTADeliveryException` 至少表达 `TransmissionFailedException`（传输中断，DS-15 据此进入重试/回滚分支）和 `ChecksumMismatchException`（校验失败，DS-15 据此进入回滚分支）。

**Java 实现考量**：四个端口均以 Java `interface` 声明于领域层（可置于 `domain.model` 模块或独立的 `domain.port` 模块），基础设施层提供 `@Component` 标注的实现并通过 Spring 构造器注入。`Optional<T>`、`enum` 等均为 Java 标准类型。

---

### 决策 20：急刹/急加速检出阈值声明为 DrivingBehaviorTrackingPort 的端口参数

**理由**：VO-16（DrivingBehaviorCounters）和 DrivingBehaviorTrackingPort（决策 17）中"急刹超过阈值"的阈值（减速度阈值与加速度阈值）未定义来源——阈值是领域规则还是基础设施调优参数未予区分。本决策明确：

- **阈值定位为基础设施调优参数**：急刹/急加速的检出属于信号层面的模式识别（加速度信号突变检测），阈值由硬件特性（传感器量程、采样率、车身振动特性）决定，**不属于领域规则**（不像疲劳、路怒那样"条件满足即判定"）。
- **领域层通过端口参数声明阈值需求**：DrivingBehaviorTrackingPort 的方法契约以**事件回调风格**声明——基础设施层在检测到急刹/急加速突变时，以回调方式通知领域层（而非领域层主动调用端口执行检测），与 VO-16/DS-17 描述的"基础设施检测→通知领域"数据流方向一致：
```java
void onHardBrakingDetected(HardBrakingEvent event);
void onHardAccelerationDetected(HardAccelerationEvent event);
```
  `HardBrakingEvent` / `HardAccelerationEvent` 由基础设施层在检出时组装（携带时间戳、加速度幅值等），`DecelerationThreshold` 和 `AccelerationThreshold` 为值对象，由领域层定义其数值范围约束（如"减速度阈值必须为正实数"），具体数值由基础设施层按车型/传感器标定后配置注入。验收测试时固定阈值以确保可复现性。回调方法在边缘侧进程内同步执行以保证计数器增量更新的确定性。
- **VO-16 的描述补充**：在 VO-16 协作关系中注明"阈值由基础设施层配置并经 DrivingBehaviorTrackingPort 传入，领域层仅声明端口参数契约而不固化具体数值"。

该设计既承认阈值的调优本质（不将其硬编码为领域常量），又在领域层保留了参数契约的可见性（测试可对其注入固定值），满足验收可复现性要求。

---

### 决策 21：生理数据滚动缓冲端口 PhysiologicalDataBuffer 的方法契约

**理由**：DS-06 EmergencyResponseService 和决策 13 均引用 PhysiologicalDataBuffer 端口，对比 VehicleStateBuffer（决策 14）已有完整方法签名契约——输入参数（TripId + TimeRange）、返回类型（`Array<VehicleStateSnapshot>`）、错误语义（`BufferError`），而 PhysiologicalDataBuffer 是唯一被引用但无接口契约的端口。为解除上下游并行开发阻塞并为编码提供明确的类型约束，参照决策 14 建模方式补充完整端口方法契约。

**端口能力契约（核心方法签名）**：

```java
List<PhysiologicalSnapshot> getReadings(TripId tripId, TimeRange window) throws BufferException;
```

- **输入**：`tripId`（当前活跃行程标识，定位边缘侧对应生理数据缓冲）、`window`（TimeRange，碰撞前至碰撞后 ≥10s 的回取时间窗；正常行车过程中窗口中心在碰撞时刻，前后各覆盖至少 5s，可由 DS-06 按需扩展至更宽的窗口）。
- **返回**：成功返回该时间窗内按时序排列的 `List<PhysiologicalSnapshot>`（VO-03）。
- **错误语义**：`BufferException` 表达可预期的业务情形，至少含：`WindowNotCoveredException`（请求窗口超出缓冲保留范围，如行程刚开始不足 10s）、`BufferUnavailableException`（缓冲未初始化/生理采集组件异常）。DS-06 据此降级——若可得部分快照则以可得数据执行失能判定（降级判定结果标注"数据不完整"），若缓冲完全不可用则无法完成判定并发出退化的 EmergencyActivatedEvent（标注判定置信度降低）。
- **取值范围约束**：`window` 起始不早于碰撞时刻前 10s，终止不早于碰撞时刻后 0s（即窗口至少覆盖碰撞前后 ≥10s），由 DS-06 在调用方确保，端口实现不校验此约束。

**与 VehicleStateBuffer 的对比**：两端口同属领域层依赖接口、均以Java `interface` 声明、均由基础设施层实现并在装配阶段注入 DS-06，区别仅在于：
- VehicleStateBuffer 服务于车辆状态数据维度（VO-09，30s time window，碰撞时刻回取事故前景）
- PhysiologicalDataBuffer 服务于生理数据维度（VO-03，≥10s time window，碰撞时刻回取失能判定所需的生理读数）

两端口由基础设施层分别独立实现各自的 ring buffer 组件，互不共享缓冲空间。

**Java 实现考量**：与 VehicleStateBuffer 一致，`List<T>`/`TimeRange`/`PhysiologicalSnapshot` 均为标准类型构造；`BufferException` 与 VehicleStateBuffer 复用相同异常类型（均为缓冲层问题）。

---

## 修订说明（a_v2_v1）

> 本轮针对诊断（b_v1_diag_v1）+ 质询（LOCATED，b_v1_challenge_v1）确认的 12 个问题（2 严重 / 5 一般 / 5 轻微）逐项修复。其中问题 1、2 为阻塞下游编码的严重逻辑矛盾，已确保 DS-07 / Scene 1 / VO-01 / VO-02 与新增的 RiskResolvedEvent 之间内部一致。本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【严重】DS-07 通用 RiskLevel→干预映射与场景行为契约矛盾** | 采纳。DS-07 改为 **AlertType × RiskLevel 二维映射**，以矩阵明确"疲劳 L2=氛围灯橙 / 路怒 L2=环境调节 / 分心 L2=告警 / 疲劳 L3=语音+震动+双闪+CAN"，与场景 1/3/8 严格一致；新增决策 15 阐述二维化理由；概述新增"风险等级与干预映射总览约定"。消除单一映射与场景契约的矛盾。 |
| **2【严重】路怒解除事件语义在现有类型系统中无法表达** | 采纳路径 (b)。新增独立领域事件 **RiskResolvedEvent**（携带 AlertType 标识解除类别），不污染 RiskLevel/AlertType 枚举；§3.5 事件表新增该事件；DS-01/DS-04/DS-13 增加解除产出与消费；场景 3 步骤 6 改为产出/消费 RiskResolvedEvent 以停止录制、恢复空调；VO-01/VO-02 增加解除语义说明；新增决策 16 阐述选型理由（含对路径 a 的取舍）。 |
| **3【一般】分心检出缺少独立行为契约场景** | 采纳。§四新增**场景 8（分心检出与告警）**：SensorReading(DMS 视觉)→DS-03 判定(L2)→门面汇总 RiskDeterminedEvent(DISTRACTION,L2)→InterventionService 告警，显式标注 0.5s 时延约束与边缘侧同步链路。 |
| **4【一般】OTA 升级管理缺少行为契约场景** | 采纳。§四新增**场景 9（OTA 固件升级全流程）**：明确状态机阶段（待下发→传输中→校验中→已就绪→升级中→完成/回滚）、状态转换触发条件（下发失败→重试、校验失败→回滚、刷写失败→回滚）、断点续传重试语义、安全回滚不变式；DS-15 补充并新增 OTAUpgradeFailedEvent。 |
| **5【一般】VehicleStateBuffer 端口接口契约未定义** | 采纳。决策 14 补充端口核心方法签名 `getSnapshots(tripId: TripId, window: TimeRange): Result<Array<VehicleStateSnapshot>, BufferError>`，明确输入参数、返回类型与错误语义（WindowNotCovered / BufferUnavailable 及 DS-06 降级策略）；VO-09、DS-06、场景 4、§5.2 同步引用。 |
| **6【一般】值对象目录遗漏 InterventionInstruction 与 RescueReport** | 采纳。§3.3 新增 **VO-12 InterventionInstruction**（指令类型枚举、目标设备标识、参数映射、优先级）与 **VO-13 RescueReport**（GeoLocation、生命体征摘要、VehicleStateSnapshot 集合、健康档案摘要）；DS-07/DS-12 引用之。 |
| **7【一般】行为契约场景覆盖不完整（约 67%）** | 采纳并超额覆盖。除场景 8（分心）、场景 9（OTA）外，新增**场景 10（报告生成 15s SLA + 家属常态同步）、场景 11（远程车窗/车门控制授权与执行）、场景 12（看板钻取交互）**，使 16 个领域服务均有场景覆盖，带显式性能/时序约束者（分心 0.5s、报告 15s、OTA 状态机、常态同步 ≥1Hz/≤2s）均有可验证契约。 |
| **8【轻微】DS-07 中 L1 级别干预缺少触发源** | 采纳。VO-01 增加"L1 预留等级声明"、DS-07 二维映射对 L1 给出"无干预/仅记录"占位分支、概述与决策 15 注明 L1 本期无触发路径但保留以备扩展，明确其为显式占位而非死代码。 |
| **9【轻微】SystemAccount 角色（FAMILY/MANAGER）缺少类型定义** | 采纳。§3.3 新增 **VO-14 AccountRole** `enum`（FAMILY \| MANAGER）；AR-04 关联角色字段；DS-08 权限判定与通知路由改为基于 AccountRole 的类型安全分支；决策 9 补充说明。 |
| **10【轻微】家属常规状态推送机制缺少设计描述** | 采纳。新增 **VO-15 DriverStatusSnapshot**（Driver 标识、状态色绿/黄/红、时间戳）与 **DS-16 DriverStatusBroadcastService**（≥1Hz 周期采样、≤2s 上报）；§四场景 10b 描述常态同步契约；§2.1 domain.family、§6.2/§6.3 补充其异步遥测链路与并发策略。 |
| **11【轻微】家属权限常规撤销路径不完整** | 采纳。DS-08 补充常规自动撤销状态机——L3 风险下降不再持续（后续 < L3 等级或 RiskResolvedEvent）时自动撤销并发出 **FamilyAccessRevokedEvent**（§3.5 新增该事件）；场景 7 改为"授予—撤销闭环"，区分常规撤销/物理遮挡撤销/高危激活的收束路径。 |
| **12【轻微】异常场景与边界条件覆盖不足** | 采纳。§五新增 **5.4 边界条件处理策略**：(1) 边缘侧断网 Trip 本地优先持久化 + 幂等键去重的云端同步一致性；(2) 驾驶员注销/账号删除的监护关系清理（FamilyAccessRevokedEvent）与历史数据匿名化保留/敏感数据删除脱敏处理；(3) 毫米波雷达短暂中断的"保持窗口、暂停累计、超容差重置"窗口策略。§6.3 同步补充断网去重并发条目。 |

---

## 修订说明（a_v3_v1）

> 本轮针对诊断（b_v2_diag_v1）确认的 4 个问题（2 一般 / 2 轻微）逐项修复。四个问题在迭代第 2 轮反馈中已提出但 a_v2_v1 未予修复，本轮为第 3 轮迭代补修。本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【一般】BR-05 评分与报告所需的"急刹/急加速次数"全链路无数据来源** | 采纳。新增值对象 **VO-16 DrivingBehaviorCounters**（急刹/急加速计数），被 Trip 聚合持有，随行程创建初始化、随行程结束冻结；新增领域服务 **DS-17 DrivingBehaviorTrackingService**，接收基础设施层持续加速度监测组件的增量事件并更新计数器；新增决策 17 阐述急刹/急加速的独立全程监测端口（**DrivingBehaviorTrackingPort**）与碰撞回取 ring buffer 的分离设计，闭合"传感器→监测→检出→计数→评分输入"的全链路。AR-01 Trip 协作关系补充引用 VO-16；DS-09 ScoringService 明确急刹/急加速读数来源于 VO-16；场景 5 补充 VO-16 数据读取步骤。 |
| **2【一般】远程控车/对讲/车窗控制的"二次身份验证"未建模** | 采纳。DS-08 PermissionService 新增**二次身份验证门控**职责——高敏操作执行前须校验二次验证结果，未通过返回 `SecondaryAuthRequired` 拒绝；VO-07 Permission 补充二次验证约束说明（权限授予不豁免验证）；场景 11 显式加入二次身份验证步骤并区分家属手动发起路径与高危自动激活豁免路径；§5.2 错误表新增 `SecondaryAuthRequired` 错误类型；新增决策 18 论述建模为门控约束（而非独立值对象）的理由。 |
| **3【轻微】后排红外摄像头感知源在感知抽象中未承认** | 采纳。VO-11 SensorReading 从"四大感知通道"扩展为"**五大感知通道**"，纳入后排红外摄像头；明确摄像头与毫米波雷达的分工——雷达管"有无活体"，红外摄像头管"提供可视画面"；DS-01 协作说明补充后排红外摄像头按消费方路由、不流经融合门面。 |
| **4【轻微】Driver"综合风险评分"属性的更新来源未闭合** | 采纳。AR-02 Driver 新增"综合风险评分的赋值与更新链路"说明——ScoreService（DS-09）在每次行程评分后重新计算 Driver 综合风险评分（近期 TripScore 加权平均），通过 **DriverScoreUpdatedEvent**（§3.5 事件表新增）写回 Driver 聚合；DS-09 ScoringService 新增 Driver 评分计算与写回职责；场景 5 新增 Driver 评分写回步骤（步骤 6）；§2.1 domain.fleet 模块补充"含 Driver 综合风险评分计算与写回"。 |

---



---

## 修订说明（a_v4_v1）

> 本轮针对诊断与质询确认的 6 个问题（2 一般 / 4 轻微）逐项修复。本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【一般】DS-05 LifeDetectionService 的触发源"熄火落锁事件"未在领域事件表中建模** | 采纳。§3.5 领域事件表新增 **VehicleIgnitionOffLockedEvent** 事件类型（携带 Vehicle 标识、时间戳），消费方为 LifeDetectionService；DS-05 协作-触发改为引用该事件名称；场景 2 步骤 1 同步引用。消除触发路径在设计层面的悬空。 |
| **2【一般】DS-08 PermissionService 跟踪 L3 持续 60 秒的计时状态归属未明确** | 采纳。新增 **VO-17 L3DurationTracker** 值对象，参照 DS-05 DetectionWindow 模式——封装 per-Driver 的 L3 起始时刻与累计时长，由 Trip 聚合以 Driver 标识做 key 持有；DS-08 新增设计约束，明确 L3 计时信息以 VO-17 为载体、归属 Trip 聚合（生命周期与 Trip 会话一致），DS-08 对外保持无状态（输入 Tracker + 事件，输出新 Tracker + 可选事件）；AR-01 Trip 协作关系补充引用 VO-17；场景 7 步骤 1 引用 L3DurationTracker；§6.2 边缘侧状态管理补充 L3DurationTracker 归属说明。 |
| **3【轻微】摄像头物理遮挡的检测源与解除后的权限恢复路径缺失** | 采纳。（a）DS-14 职责重构——区分两类异常：传感器硬件/链路故障（产出 SensorFailureEvent，全系统失效告警）与摄像头物理遮挡（产出 **CameraOcclusionDetectedEvent** / **CameraOcclusionRemovedEvent**，仅影响依赖该摄像头的功能），明确检测源为感知通道遮挡检测算法，与 BR-08 传感器故障路径分流；（b）§3.5 事件表新增 CameraOcclusionDetectedEvent 与 CameraOcclusionRemovedEvent；（c）DS-08 协作补充订阅两事件；DS-08 职责-临时撤销补充遮挡移除后的恢复说明；（d）场景 7 新增步骤 5「遮挡恢复后的权限恢复」——若 L3 持续未中断且已达 60s 阈值则自动恢复权限，否则维持无权限状态；权限恢复不豁免二次身份验证。 |
| **4【轻微】DriverScoreUpdatedEvent 的消费方建模与 DDD 分层惯例存在张力** | 采纳方案 (a)。§3.5 事件表 DriverScoreUpdatedEvent 消费方由"Driver 聚合"改为轻量领域服务 **DriverScoreUpdateService**；DS-09 写回路径删除方案 (b) 的备选替代，统一为 DriverScoreUpdatedEvent → DriverScoreUpdateService → DriverRepository 的写回链路，遵循"领域事件由领域服务消费、仓储仅负责持久化、聚合不直接订阅事件"的 DDD 分层惯例；§3.4 新增 **DS-18 DriverScoreUpdateService**。 |
| **5【轻微】VO-13 RescueReport 中"生命体征摘要"的具体数据提取路径与时机未指定** | 采纳。VO-13 生命体征摘要维度补充数据来源标注——从 Trip 聚合的最新 PhysiologicalSnapshot（VO-03）提取实时体征，与 DriverHealthProfile（E-03）的档案级基线相区分；DS-12 协作-输入新增"查询 Trip 聚合（最新 PhysiologicalSnapshot）"；输出中明确生命体征摘要来源为 Trip 聚合的最新 PhysiologicalSnapshot、健康档案摘要来源为 DriverHealthProfile，二者路径清晰分离。 |
| **6【轻微】DS-16 DriverStatusBroadcastService 跨端推送中转路径未描述** | 采纳。DS-16 协作新增「跨端推送中转路径」——明确两级网络跃点 **边缘侧终端 → 云端推送服务（MQTT/长连接网关）→ 家属 APP**，独立于 Trip 同步上报链路（非周期性批量上报），并给出 ≤2s 端到端时延的两级预算分配（边缘采样 < 100ms、网络传输 < 800ms、云端路由下发 < 600ms、家属 APP 渲染 < 500ms）；§6.2 补充家属常态状态快照的跨端推送路径架构描述。 |

---

## 修订说明（a_v5_v1）

> 本轮针对诊断（a_v5_iteration_requirement）确认的 8 个问题（2 严重 / 3 一般 / 3 轻微）逐项修复。本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【严重】§3.5 领域事件章节结构错误** | 采纳。（a）将 DS-18 DriverScoreUpdateService 从 §3.5 领域事件章节内移至 §3.4 领域服务末尾（DS-17 之后），与其余领域服务处于同一级别；（b）删除第613行的重复 `### 3.5 领域事件` 标题，确保 §3.5 标题仅在领域事件表前出现一次，恢复章节编号唯一性，消除下游引用歧义。 |
| **2【严重】Trip:Driver=1:1 基数约束与 L3DurationTracker 的多 Driver key 映射假设自相矛盾** | 采纳方案 (a) 维持 Trip:Driver=1:1。（a）AR-01 第81行：移除"按 Driver 标识做 key"与"per-Driver"表述，改为"追踪当前 Driver 的 L3 持续时长"，并显式标注 Trip:Driver=1:1 基数约束；（b）VO-17 全文：移除"per-Driver"与"多 Driver 并发判定时，各 Driver 的 L3 状态由各自的 Tracker 独立追踪，Trip 聚合内以 Driver 标识做 key 映射"表述，改为简单持有语义（一个 Trip 内至多存在一个活跃的 L3DurationTracker，边缘侧单线程无并发竞争）；（c）DS-08 第489行：移除"各 Driver 的 Tracker 以 Driver 标识做 key 隔离"表述，改为 Tracker 直接归 Trip 聚合持有。四处表述现已一致。 |
| **3【一般】领域事件总线仅声明"需自定义实现"，未给出设计契约** | 采纳。§3.5 后新增 **§3.6 领域事件总线契约**，定义四项契约：（a）事件发布接口 `publish<E>(event): Result<Unit, EventPublishError>`，含边缘侧同步与云端 outbox 两种行为语义；（b）同步消费注册 `registerSyncHandler<E>(eventType, handler)`，用于边缘侧安全攸关链路；（c）异步消费注册 `registerAsyncHandler<E>(eventType, handler) -> Subscription`，含 Subscription 句柄与 cancel 生命周期管理；（d）Outbox 表结构约定（event_id / event_type / aggregate_id / payload / occurred_at / published）与投递保证级别 at-least-once。§6.2 事件总线实现策略同步引用 §3.6 契约。 |
| **4【一般】"会话上下文"未正式建模，与 L3DurationTracker 存在双重归属张力** | 采纳。（a）§6.2 边缘侧状态管理中新增**EdgeSessionContext 正式定义**——明确其生命周期（行程开始创建、行程结束销毁）、与 Trip 聚合的协作关系（非聚合归属，为基础设施层运行时容器）、持有内容边界（ActiveRiskSet + DetectionWindow，均为不持久化的临时状态）；（b）明确 L3DurationTracker 与 DrivingBehaviorCounters **不归 EdgeSessionContext 持有**——二者归属 Trip 聚合（持久化状态），消除了"L3DurationTracker 归 Trip 聚合"与"归会话上下文"之间的双重归属张力，论证依据为"计时状态需在断网/重启后存活（持久化在 Trip 中），判定过程中的临时风险集无需持久化（在 EdgeSessionContext 中）"。 |
| **5【一般】外部系统端口契约缺失（SMN/SparkRTC/IoTDA/120救援中心）** | 采纳。新增**决策 19**，参照决策 14/17 的端口建模方式，定义四个领域端口契约（均以Java `interface` 声明于领域层，基础设施层实现并注入）：（a）**NotificationPort**（`pushNotification`，含 NotificationPayload 与 NotificationError 语义，服务于 DS-12/DS-16/DS-09）；（b）**RescueReportPort**（`deliverRescueReport`，含 RescueReportError 语义，服务于 DS-12 向 120 救援中心投递）；（c）**MediaSessionPort**（`establishSession` / `terminateSession`，含 SessionType enum 与 MediaSessionError 语义，服务于 DS-08 远程对讲/视频通道管理）；（d）**OTADeliveryPort**（`deliverPackage`，含断点续传 resumeFrom 参数与 OTADeliveryError 语义，服务于 DS-15 IoTDA 下发）。统一了外部系统依赖的接口契约标准。 |
| **6【轻微】急刹/急加速检出阈值未定义来源** | 采纳。（a）新增**决策 20**，明确阈值定位为**基础设施层调优参数**（非领域规则），DrivingBehaviorTrackingPort 方法契约中显式声明 `DecelerationThreshold` 与 `AccelerationThreshold` 值对象参数（领域层约束为正实数，具体数值由基础设施层按车型/传感器标定注入，验收时固定）；（b）VO-16 协作关系补充阈值来源说明，注明"阈值由基础设施层配置并经端口传入，领域层仅声明参数契约而不固化具体数值"，满足验收可复现性要求。 |
| **7【轻微】救援机构角色未建模（AccountRole 缺 RESCUE 枚举值）** | 采纳方案 (a)。（a）VO-14 AccountRole 枚举新增 **RESCUE** 取值，并补充其职责说明（对应 req_v4 五类参与者中的救援机构，享有 SOS 呼叫接收、远程解锁授权、健康档案调取等限定权限）；（b）AR-04 SystemAccount 协作关系新增救援机构角色的权限与推送路由描述；（c）DS-12 EmergencyRescueService 协作说明引用 AccountRole.RESCUE 进行类型安全权限分支。 |
| **8【轻微】家属通知偏好被引用但未形式化为可配置字段/值对象** | 采纳。（a）§3.3 新增 **VO-18 NotificationPreference** 值对象，封装家属订阅的风险等级集合（`Set<RiskLevel>`），满足 req_v4 要求的"可设置并接收轻/中/重度不同等级"；（b）AR-04 SystemAccount 协作关系补充 NotificationPreference 约束——告警推送模块在推送前按 `alert.riskLevel ∈ preference.subscribedLevels` 过滤后决定是否推送；未配置时默认接收全部等级。家属修改偏好以不可变值对象替换模式更新。 |

---

## 修订说明（a_v6_v1）

> 本轮针对诊断与质询确认的 8 个问题（5 一般 / 3 轻微）逐项修复。8 项问题均在迭代第 5 轮反馈中已识别但 a_v5_v1 未予修复，本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【一般】RoadRageVoiceRecord (E-02) 无明确所有者聚合，违反 DDD 实体归属原则——E-02 暗示归属 Trip 聚合，但决策 6 明确排除，PrivacyProtectionService 直接管理实体生命周期违反持久化原则** | 采纳方案 (a)。（a）将 RoadRageVoiceRecord 从 §3.2 实体（E-02）提升为 **§3.1 聚合根（AR-05）**，拥有独立 RoadRageVoiceRecordRepository；（b）更新其类型形态论述——说明其独立标识、独立生命周期和边缘侧存储策略使其更适合作为独立聚合根；（c）DS-13 PrivacyProtectionService 的协作改为通过 RoadRageVoiceRecordRepository 操作 AR-05 的生命周期（创建、封闭、清除），遵循 DDD 实体须通过聚合根仓储持久化的原则；（d）决策 6 修订以对齐此变更；（e）§2.1 domain.privacy 模块、场景 3、E-01 协作关系同步更新。 |
| **2【一般】DS-15 OTAUpdateService 声称"升级包状态机由本服务维护"，领域服务持状态违反 DDD 无状态原则** | 采纳。（a）新增 **VO-19 OTAUpgradeStatus** 值对象（`class`），以 `enum` UpgradeStage 穷举状态阶段（PENDING→TRANSMITTING→VERIFYING→READY→UPGRADING→COMPLETED/ROLLED_BACK），含断点续传偏移量——将升级状态建模为 Vehicle 聚合内部值对象；（b）AR-03 Vehicle 协作关系新增持有 OTAUpgradeStatus；（c）DS-15 职责和协作重述为无状态语义——以纯函数方式读取/更新 Vehicle 聚合的 OTAUpgradeStatus；（d）§2.1 domain.ota 模块描述同步修改。 |
| **3【一般】Vehicle 聚合 (AR-03) 缺少固件版本属性，但场景 9 和 DS-15 均要求版本比对，导致领域层无 Vehicle 当前版本可读** | 采纳。（a）AR-03 Vehicle 角色与职责和协作关系中新增"持有当前固件版本（以 VO-08 OTAVersion 表达）"，供 DS-15 执行版本比对；（b）DS-15 协作中补充"通过 Vehicle 聚合获取当前固件版本"；（c）OTAUpgradeCompletedEvent 处理链路中补充"通过 Vehicle 聚合更新固件版本号"。 |
| **4【一般】VO-12 InterventionInstruction 指令类型以"建议以 enum 表达…如…等"描述，未正式穷举闭合——"建议"暗示非强制契约、"等"暗示未穷举** | 采纳。（a）VO-12 指令类型改为正式定义的 **InterventionInstructionType `enum`**，穷举九种指令：AMBIENT_LIGHT_COLOR / VOICE_BROADCAST / SEAT_VIBRATION / HAZARD_LIGHTS / AIR_CONDITIONING / AUDIO_PLAYBACK / CAN_DECELERATION_REQUEST / NAVIGATE_TO_SHOULDER / ALERT；（b）DS-07 二维映射逐条补充到 InterventionInstructionType + 参数映射的明确对应（如 FATIGUE×L2 = AMBIENT_LIGHT_COLOR(参数：橙色)、ROAD_RAGE×L2 = AIR_CONDITIONING(参数：温度增量-2°C) + AUDIO_PLAYBACK(参数：白噪音/舒缓音乐)）。 |
| **5【一般】SafetyAlertEvent 的创建者在设计中未显式指定——各场景以被动语态"生成 SafetyAlertEvent"表述，DS-01 仅产出判定事件，创建者悬空阻塞编码** | 采纳。（a）§3.4 新增 **DS-19 AlertPersistenceService** 轻量领域服务——订阅 RiskDeterminedEvent / LifeDetectedEvent / EmergencyActivatedEvent，通过 TripRepository 创建 SafetyAlertEvent 并发出 AlertTriggeredEvent，将判定与告警实体持久化解耦；（b）§2.1 domain.risk 模块描述补充该服务；（c）场景 1/2/4/8 中"生成 SafetyAlertEvent"改为 AlertPersistenceService 消费判定事件后执行创建；（d）DS-05/DS-06 协作中的"告警落库时生成"改为引用 AlertPersistenceService；（e）§3.5 事件表 RiskDeterminedEvent / LifeDetectedEvent / EmergencyActivatedEvent 消费方补充 AlertPersistenceService，AlertTriggeredEvent 触发时机修正为"AlertPersistenceService 创建 SafetyAlertEvent 后发出"。 |
| **6【轻微】分心检出"累计达 3 秒"的时间窗口未定义——DS-03 仅给出累计阈值而无窗口时长，无窗口将导致累计值随行程单调递增，长期行程中所有驾驶员均被误判为分心** | 采纳。（a）DS-03 职责补充**最近 60 秒滑动窗口**约束——累计时间的计算限定在滑动窗口内（而非整个行程的单调累积）；（b）标注 60s 窗口时长为"需与需求方确认的推断"，定义为可配置参数；（c）DS-03 新增设计约束说明滑动窗口为可配置值；（d）场景 8 步骤 2 补充滑动窗口判定条件。 |
| **7【轻微】Decision 20 端口方法命名 detectHardBraking/HardAcceleration 暗示"领域层调用端口执行检测"，与 VO-16/DS-17 描述的"基础设施检测→通知领域"数据流方向相反** | 采纳方案 (a)——保持"基础设施检测→通知领域"方向不变。将 DrivingBehaviorTrackingPort 方法重命名为**事件回调风格**：`onHardBrakingDetected(event: HardBrakingEvent): Unit` / `onHardAccelerationDetected(event: HardAccelerationEvent): Unit`——基础设施层在检测到急刹/急加速时回调领域层，命名方向与数据流方向一致；回调方法在边缘侧进程内同步执行以保证计数器增量更新的确定性。 |
| **8【轻微】DS-06 在决策 13 被归类为"事件触发型判定服务"，但其判定依赖"10 秒生理监测"要求持续接收生理数据流，分类偏差可能误导实现者为 DS-06 设计错误的数据接收模型** | 采纳。（a）决策 13 中 DS-06 的分类修正为**"以碰撞冲击为触发条件、依赖持续生理数据缓冲的判定服务"**——强调其触发依赖离散碰撞事件、但失能判定需要 ≥10s 持续生理数据回溯分析的双重特征；（b）DS-06 职责新增**生理数据滚动缓冲区**的设计约束——由基础设施层维护 ≥10s 的生理数据滚动缓冲（ring buffer），领域层声明 PhysiologicalDataBuffer 端口，DS-06 在碰撞触发时通过该端口回取缓冲窗内数据；（c）DS-06 职责和设计约束同步补充缓冲端口说明，将"有状态的持续缓冲"与"无状态的领域判定"清晰分离。 |

---

## 修订说明（a_v7_v1）

> 本轮针对诊断（a_v7_iteration_requirement）确认的 5 个问题（1 一般 / 4 轻微）逐项修复。本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【一般】PhysiologicalDataBuffer 端口契约缺失**：DS-06 和决策 13 均引用 PhysiologicalDataBuffer 端口，但设计文档中该端口的方法签名、输入参数类型、返回类型、错误语义均未定义。对比 VehicleStateBuffer（决策 14）已有完整契约。 | 采纳。新增**决策 21**，参照决策 14 建模方式定义 PhysiologicalDataBuffer 端口契约——核心方法签名 `getReadings(tripId, window): Result<Array<PhysiologicalSnapshot>, BufferError>`，明确输入参数（TripId + TimeRange）、返回类型（`Array<PhysiologicalSnapshot>`）、错误语义（`WindowNotCovered` / `BufferUnavailable` 及 DS-06 降级策略）、与 VehicleStateBuffer 的对比以及取值范围约束。DS-06 职责与协作同步更新引用决策 21，场景 4 步骤 2 改为引用 PhysiologicalDataBuffer 端口，§5.2 错误表新增该端口对应的错误行。 |
| **2【一般】TripScore 写入 Trip 聚合的职责归属不一致**：AR-01 表述 ScoringService 将 TripScore 以值对象形式存入 Trip，但 DS-09 仅描述查询/读取操作和事件产出，未提及将 TripScore 写回 Trip 的持久化操作；场景 5 也未写"写入 Trip"。 | 采纳方案 (a)——DS-09 显式补充通过 TripRepository 将 TripScore（VO-05）写入 Trip 聚合持久存储。具体修改：(a) DS-09 职责中补充"通过 TripRepository 将 TripScore 值对象写入 Trip 聚合持久存储，随后发出 TripScoredEvent"；(b) DS-09 协作中新增"TripScore 持久化"小节，明确 TripRepository 写入操作；(c) 场景 5 步骤 5 改为"ScoringService 通过 TripRepository 将 TripScore 值对象写入 Trip 聚合持久存储，随后发出 TripScoredEvent"；(d) VO-05 角色与职责补充"由 ScoringService 在行程结束时计算并通过 TripRepository 写入 Trip 聚合持久存储"；(e) AR-01 角色与职责和协作关系同步补充 Trip 持有 TripScore 值对象及 ScoringService 通过 TripRepository 写入；(f) §3.5 事件表 TripScoredEvent 触发时机修正为"ScoringService 完成评分并通过 TripRepository 写入 TripScore 后发出"；(g) §6.2 EdgeSessionContext 不持有内容中补充 TripScore 归属 Trip 聚合。七处表述现已一致。 |
| **3【轻微】Scene 6 传感器故障场景未覆盖物理遮挡检测路径**：DS-14 已分化为双路异常检测模型（SensorFailureEvent 与 CameraOcclusionDetectedEvent/CameraOcclusionRemovedEvent），但 Scene 6 仅覆盖 SensorFailureEvent 处理路径，未涉及遮挡检测事件的处理路径。 | 采纳。Scene 6 重构为双路径结构——**6a 传感器硬件/链路故障路径**（保留原有 SensorFailureEvent 处理：HMI 语音提示 + 车队看板脱线标记）和**6b 摄像头物理遮挡检测路径**（新增 CameraOcclusionDetectedEvent → HMI 遮挡提示 + PermissionService 权限临时撤销；CameraOcclusionRemovedEvent → HMI 解除遮挡提示 + PermissionService 权限恢复判断）。双路径以相同的结构化步骤编号区分，消费方的 HMI 提示措辞明确差异化（故障路径"安全监测系统已失效" vs 遮挡路径"摄像头被遮挡，部分安全功能暂时不可用"）。同时更新 §3.5 事件表中 CameraOcclusionDetectedEvent / CameraOcclusionRemovedEvent 的消费方补充 HMI 提示消费者，FamilyAccessGrantedEvent / FamilyAccessRevokedEvent 授权/撤销原因取值补充"遮挡恢复"/"驾驶员注销"。 |
| **4【轻微】驾驶员注销/账号删除的触发领域事件未列入事件表**：§5.4 边界条件 (2) 描述驾驶员注销/账号删除时的清理动作以领域事件驱动各模块异步收尾，但 §3.5 领域事件一览表中无 DriverDeactivatedEvent 事件类型。 | 采纳。§3.5 事件表新增 **DriverDeactivatedEvent**（携带 Driver 标识、注销时间戳），消费方为 PermissionService（清理监护关系、发出 FamilyAccessRevokedEvent）、PrivacyProtectionService（删除/脱敏 RoadRageVoiceRecord 等敏感数据）、各模块按合规策略执行匿名化保留或删除。同步更新：AR-02 协作关系改为"产出 DriverDeactivatedEvent"引导至 §5.4；DS-08 协作新增订阅 DriverDeactivatedEvent 并发出 FamilyAccessRevokedEvent 收束家属会话；DS-13 协作新增订阅 DriverDeactivatedEvent 删除/脱敏隐私敏感数据；§5.4 边界条件 (2) 重述为以 DriverDeactivatedEvent 驱动的异步收尾机制，替代原有模糊的"领域事件驱动"表述。FamilyAccessRevokedEvent 撤销原因取值补充"驾驶员注销"。 |
| **5【轻微】NAVIGATE_TO_SHOULDER 指令类型承载两个语义不同阶段的干预意图**：VO-12 定义 NAVIGATE_TO_SHOULDER 指令类型，DS-07 二维映射中 FATIGUE×L3 渐进升级链使用同一枚举值同时表达"建议减速"和"引导靠边"两个阶段，二者干预强度、HMI 表现和驾驶员覆盖检测触发阈值均不同，基础设施层无法仅凭指令类型区分。 | 采纳方案 (a)——拆分枚举值。VO-12 的 InterventionInstructionType 枚举从 9 个拆为 10 个：NAVIGATE_TO_SHOULDER（原值）拆分为 **NAVIGATE_DECELERATION**（建议/请求减速，干预强度较低，HMI 提示为主）和 **NAVIGATE_TO_SHOULDER**（引导靠边，干预强度较高，触发车道级导航引导）。DS-07 二维映射中 FATIGUE×L3 的渐进升级序列改为"VOICE_BROADCAST 语音唤醒 → NAVIGATE_DECELERATION 建议/请求减速 → NAVIGATE_TO_SHOULDER 引导靠边"。DS-07 补充「NAVIGATE_DECELERATION 与 NAVIGATE_TO_SHOULDER 拆分说明」，阐述拆分理由（双语义歧义导致基础设施层无法区分）。场景 1 步骤 4 的渐进升级序列同步使用拆分后的枚举值。 |

---

## 修订说明（a_v8_v1）

> 本轮针对诊断（a_v8_iteration_requirement）确认的 7 个问题（3 一般 / 4 轻微）逐项修复。本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【一般】场景8步骤编号重复，下游引用产生歧义**：第883–884行出现两个"6."，需改为7。 | 采纳。场景 8 步骤 6 之后的"6. **时延约束**"改为"7. **时延约束**"。同步检查全部 12 个场景的步骤编号连续性，确认其余场景编号均连续无重复（场景 6 的 6a/6b 双路径子编号亦规则一致）。 |
| **2【一般】DS-10 FleetAnalyticsService 与 DS-11 ReportGenerationService 缺少形式化接口定义** | 采纳。为 DS-10 定义 `getFleetFatigueDistribution(): Result<FatigueDistribution, QueryError>` 和 `drillDown(riskLevel: RiskLevel): Result<Array<DriverSummary>, QueryError>` 两个方法契约，含 `FatigueDistribution`/`DriverSummary`/`QueryError` 返回类型结构概要和错误语义，参照决策 19 端口定义风格。为 DS-11 定义 `generateReport(driverId: DriverId, timeRange: TimeRange): Result<ReportData, ReportError>` 方法契约，含 `ReportData`/`ReportError` 结构概要和 15s SLA 声明。 |
| **3【一般】AR-02 Driver 综合风险评分写回路径残留歧义表述**：第93行仍保留"消费方（或 ScoringService 自身直接操作 Driver 仓储）更新"括号内备选路径，与 DS-09/DS-18 已确立的 DriverScoreUpdateService 路径不一致。 | 采纳。（a）AR-02 综合风险评分赋值与更新链路中将"消费方（或 ScoringService 自身直接操作 Driver 仓储）更新"改为"由 DriverScoreUpdateService（DS-18）消费该事件后通过 DriverRepository 更新"；（b）协作关系中第 102 行"据此更新自身综合风险评分字段（或由 ScoringService 通过仓储直接写回）"改为明确引用 DS-18 路径并注明与 DS-09/DS-18 表述一致。两处备选路径均已删除，AR-02、DS-09、DS-18 三处表述完全对齐。 |
| **4【轻微】实体章节编号空缺 E-02**：§3.2 从 E-01 直接跳至 E-03，E-02 编号空缺。 | 采纳方案 (b)。在 E-03 之前新增编号说明备注——原 E-02（RoadRageVoiceRecord）已在 a_v6 设计迭代中因所有制/持久化原则矛盾升级为聚合根 AR-05，故实体编号从 E-01 直接跳至 E-03；E-02 编号保留为历史记录不重新分配，避免下游引用混淆。 |
| **5【轻微】DS-14 SensorSelfCheckService 的"遮挡检测结果"输入来源未形式化为端口**：与决策 14/20/21 和决策 19 中已形式化的端口契约形成不一致。 | 采纳方案 (a)。定义 **CameraOcclusionDetectionPort** 端口接口（以Java `interface` 声明于领域层，基础设施层实现并注入 DS-14），以事件回调风格声明 `onOcclusionDetected(event: OcclusionDetectedSignal): Unit` 和 `onOcclusionRemoved(event: OcclusionRemovedSignal): Unit`，与 DrivingBehaviorTrackingPort（决策 20）的回调风格一致。DS-14 在回调中产出 CameraOcclusionDetectedEvent / CameraOcclusionRemovedEvent 领域事件。 |
| **6【轻微】BR-05 评分公式中"路怒触发次数"的口径未做语义一致化说明**：与疲劳统计口径的"一次判定→解除→再判定"循环计数口径缺少语义一致化衔接。 | 采纳。在 DS-09 数据来源中补充路怒计数口径说明："路怒触发次数按 Trip 关联的 SafetyAlertEvent 中 AlertType=ROAD_RAGE 的告警实体数量计，一次路怒判定（触发 RiskDeterminedEvent）→ 解除（触发 RiskResolvedEvent）→ 再判定视作两次触发，与疲劳统计口径原则一致（每次 RiskDeterminedEvent 对应一次告警实体创建，累加计数）。" |
| **7【轻微】BR-05 周期级评分的"报告同时单列累计次数"未在 DS-11 ReportGenerationService 中显式覆盖**：DS-11 职责中"疲劳、分心、急加速、急刹车等"列表未穷举至与 BR-05 评分公式五维度对齐，且未提及"累计次数"单列要求。 | 采纳。DS-11 职责描述中的指标列表从"疲劳、分心、急加速、急刹车等"改为穷举表述"重度疲劳次数、分心触发次数、路怒触发次数、急刹次数、急加速次数"，确保与 BR-05 评分公式的五个输入维度一一对应；新增"报告同时单列上述五个维度的周期累计总次数"语句。同步更新场景 10a 步骤 2 的报告统计维度为穷举列表，与 DS-11 职责表述一致。 |

---

## 修订说明（a_v9_v1）

> 本轮针对审查反馈（a_v9_iteration_requirement）确认的 6 个问题（2 一般 / 4 轻微）逐项修复。本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【轻微·需求响应缺失】家属手动应急救援联动路径未建模** | 采纳方案 (a)。DS-12 EmergencyRescueService 新增 `triggerManualRescue(driverId, requesterAccount): Result<Unit, RescueError>` 方法契约——家属端在视频巡视中一键触发应急救援，不依赖碰撞/失能判定，复用已有数据组装与上报能力；§3.5 事件表新增 `FamilyManualRescueRequestedEvent` 领域事件（携带 Driver 标识、请求家属 AccountId、位置、时间戳），消费方为 EmergencyRescueService 与家属 APP；该方法仅允许 AccountRole=FAMILY 且有监护授权者调用，不产出 EmergencyActivatedEvent（非安全告警路径），仅触发救援链路的事件上报。 |
| **2【一般·关键遗漏】聚合根仓储（Repository）接口契约未定义** | 采纳。§三新增 **§3.7 聚合根仓储接口契约**，定义 TripRepository、DriverRepository、SystemAccountRepository、RoadRageVoiceRecordRepository 四大仓储的核心方法契约——采用统一的 `Result<Option<T>, PersistenceError>` / `Result<Unit, PersistenceError>` 返回类型模式、乐观锁冲突通过 `PersistenceError.OptimisticLockConflict` 表达、领域层 `interface` 声明 + 基础设施层实现并在装配阶段注入；补充仓储设计原则（接口在领域层/实现在基础设施层、乐观锁对领域层透明、Result 统一封装）。 |
| **3【一般·逻辑遗漏】远程对讲/视频介入时的驾驶员声光提示未建模** | 采纳。§3.5 事件表 `FamilyAccessGrantedEvent` 消费方新增"HMI 层（向驾驶员发出声光提示：对讲/视频已接通，并保留驾驶员物理遮挡权）"；§四场景 7 步骤 2 新增"同时通过 HMI 向驾驶员发出声光提示——告知远程对讲/视频已建立，并保留驾驶员物理遮挡权（驾驶员可随时遮挡摄像头以触发权限临时撤销）"。 |
| **4【轻微·接口一致性】部分领域服务仍缺少形式化方法签名** | 采纳。为 DS-02（determineFatigue）、DS-03（detectDistraction）、DS-04（determineRoadRage）、DS-05（evaluateLifeDetection）、DS-07（generateIntervention + handleOverride）、DS-13（validateDataDesensitization + startVoiceRecording + sealVoiceRecord + purgeExpiredRecords）、DS-14（runSelfCheck + onOcclusionDetected + onOcclusionRemoved）、DS-17（onHardBrakingDetected + onHardAccelerationDetected）共计 8 个领域服务补充核心方法契约，参照 DS-10/DS-11 已确立的接口定义风格——每个方法含输入参数概述、返回类型结构概要（Result<T, Error> 或 Unit/Array<T>）和错误语义说明。 |
| **5【轻微·语义重叠】BR-01 疲劳 L2 与分心 L2 共用"视线偏离"信号未显式区分** | 采纳方案 (a)。DS-02 职责描述新增「与分心 L2 共用"视线偏离"信号的并发处理」设计约束——明确两子判定服务在各自窗口/阈值条件下独立判定，同时满足时门面产出两个独立 RiskDeterminedEvent（Fatigue×L2 + Distraction×L2），干预模块按各自二维映射独立执行（氛围灯变橙 + 告警互不冲突），各经 AlertPersistenceService 创建独立 SafetyAlertEvent；标注为"需与需求方确认"以预留未来冲突消解空间（如分心 L2 优先于疲劳 L2 时可在门面汇总阶段处理，不影响子服务独立性）。 |
| **6【轻微·完整性】缺少聚合根-仓储对照表** | 采纳。§3.1 末尾新增「聚合根-仓储对照」汇总表，列出 AR-01~AR-05 五个聚合根各自对应的仓储及其基本能力，附 VehicleRepository 与其余仓储同模式（findById + save + 乐观锁）的声明。 |

---

## 修订说明（a_v9_v2）

> 本轮针对审查反馈（a_v9_review_v1）确认的 4 个问题（2 一般 / 2 轻微）逐项修复。本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【一般】DetectionWindow 值对象未在 §3.3 值对象目录中正式定义** | 采纳。§3.3 新增 **VO-20 DetectionWindow** 条目——定义为 `class` 值对象，明确其概念维度（剩余时长、起始时刻、累计微动观测次数、容差阈值）、协作关系（由 EdgeSessionContext 持有、作为 DS-05 `evaluateLifeDetection` 的输入输出参数）。三栏格式仿照现有值对象。 |
| **2【一般】DomainEvent 类型/接口在设计文档中未定义** | 采纳。§3.5 新增 **§3.5.0 DomainEvent 公共抽象**——以Java `interface`（marker interface）定义，声明三个公共元数据字段：`eventId: String`（事件唯一标识）、`occurredAt: Timestamp`（发生时间戳）、`aggregateId: String`（关联聚合根标识）。各具体事件类型以 `class` 定义并 `implements DomainEvent`，保证事件载荷不可变性和事件总线编译期类型校验。原 §3.5 事件表标题调整为 §3.5.1 领域事件一览。 |
| **3【轻微】OverrideSignal 和 DriverComprehensiveScore 类型未正式定义** | 采纳方案 (a)。§3.3 新增 **VO-21 OverrideSignal**（`class`，含 `OverrideType` 枚举——TURNING/BRAKING/ACCELERATING——和时间戳）和 **VO-22 DriverComprehensiveScore**（`class`，范围 [0,100]，独立于 VO-05 TripScore 的语义，归属 Driver 聚合）。两类型均作为形式化接口（DS-07 `handleOverride`、DriverRepository `updateScore`）的参数类型得到正式定义。 |
| **4【轻微】事件总线契约中的 Type\<E\> 运行时类型标记能力未确认** | 采纳方案 (b) 为主 + 方案 (a) 为优化保留。§3.6 契约 2（同步消费注册）和契约 3（异步消费注册）的 `eventType` 参数从 `Type<E>` 改为 **`eventTypeName: String`**（如 `"RiskDeterminedEvent"`），以字符串表达事件类型判别符，确保事件总线接口契约在Java实现时不依赖运行时类型反射能力。同时在各契约中注明"实现阶段若确认Java支持运行时类型反射，可优化为类型安全注册 `registerSyncHandler<E: DomainEvent>(eventType: Type<E>, ...)`"。`unregisterSyncHandler` 参数同步改为 `eventTypeName: String`。 |

---

## 修订说明（a_v10_v1）

> 本轮针对审查反馈（a_v10_iteration_requirement）确认的 8 个问题（3 一般 / 5 轻微）逐项修复。8 项问题均为迭代第 9 轮已识别但未修复的持续问题，本轮全部采纳，无分歧。

| 审查意见 | 修改措施 |
|---------|---------|
| **1【一般·接口完备性】VehicleRepository 仓储接口未在 §3.7 中形式化定义** — §3.7 仅对 TripRepository、DriverRepository、SystemAccountRepository、RoadRageVoiceRecordRepository 四个仓储定义了接口契约，VehicleRepository 仅在 §3.1 对照表中以注释代替。但 DS-12（更新车门锁状态）、DS-15（固件版本读写、OTA 状态变更）、DS-14（传感器自检状态更新）均需要对 Vehicle 聚合执行持久化操作。 | 采纳。（a）§3.7 新增 **§3.7.5 VehicleRepository**，定义 `findById(vehicleId): Result<Option<Vehicle>, PersistenceError>` 和 `save(vehicle): Result<Unit, PersistenceError>` 两个核心方法契约，乐观锁冲突通过 `PersistenceError.OptimisticLockConflict` 表达，调用方（DS-12/DS-14/DS-15）按场景分别处理冲突；（b）原 §3.7.5 仓储设计原则重编号为 §3.7.6；（c）§3.1 聚合根-仓储对照表中 VehicleRepository 行更新为正式引用 §3.7.5。 |
| **2【一般·接口完备性】DS-06 EmergencyResponseService 缺少形式化方法签名** — DS-06 承载碰撞失能判定的完整链路（碰撞信号→生理数据回取→车辆状态回取→失能判定→事件产出），但 a_v9_v2 修订清单为 DS-02~DS-05、DS-07、DS-13~DS-14、DS-17 等 8 个服务补充了方法契约，DS-06 未获任何方法签名。 | 采纳。DS-06 新增 **`determineDisability(collisionSignal: CollisionImpactSignal): Result<DisabilityAssessment, DeterminationError>`** 方法契约——`CollisionImpactSignal` 载荷包含碰撞特征（加速度包络剖面、碰撞强度估算值、时间戳），`DisabilityAssessment` 至少表达判定结论（Disabled/NotDisabled/Indeterminate）、置信度和判定依据摘要；`DeterminationError` 至少表达 `InputInvalid` 和 `BufferUnavailable`（此时判定降级为 Indeterminate 并标注置信度降低）。与 DS-02~DS-05 的方法契约风格一致。 |
| **3【一般·接口完备性】DS-15 OTAUpdateService 缺少形式化方法签名** — DS-15 驱动完整 OTA 升级状态机，协作涉及版本比对、传输进度处理、校验结果处理、刷写结果处理等多个独立操作，但没有任何方法签名。与 DS-07（两个方法签名）、DS-14（三个方法签名）的契约完备性标准不一致。 | 采纳。DS-15 新增四项方法契约：（a）**`initiateUpgrade(vehicleId, targetVersion): Result<Unit, OTAUpgradeError>`** — 版本比对与升级启动；（b）**`handleTransferProgress(vehicleId, progress): Result<UpgradeStage, OTAUpgradeError>`** — 传输进度处理与重试/回滚判定；（c）**`handleVerificationResult(vehicleId, checksumValid): Result<UpgradeStage, OTAUpgradeError>`** — 校验结果处理与状态推进/回滚；（d）**`handleFirmwareFlashResult(vehicleId, flashSuccess): Result<UpgradeStage, OTAUpgradeError>`** — 刷写结果处理与版本更新/回滚。四项方法覆盖 OTA 升级状态机的四个关键阶段，错误语义与场景 9 状态转换一致。 |
| **4【一般·逻辑矛盾】DS-06 EmergencyResponseService 在 DS-19 AlertPersistenceService 中的消费路径存在歧义** — DS-19 明确"不产生 AlertTriggeredEvent"但创建 SafetyAlertEvent 并持久化，与 §3.5 事件表中 EmergencyActivatedEvent 的消费方列有 AlertPersistenceService、场景 4 步骤 3 的描述存在矛盾：SafetyAlertEvent 实体存在但无法通过常规告警查询路径检索到，形成"暗数据"。 | 采纳方案 (a)——碰撞失能告警仍需发出 AlertTriggeredEvent 使管理员可审计。DS-19 中 EmergencyActivatedEvent 的处理路径改为"**同样发出 AlertTriggeredEvent**"，碰撞失能的实时救援推送由 EmergencyRescueService 路径独立处理，但 AlertTriggeredEvent 同时发出以确保碰撞失能告警可通过常规告警查询路径被管理员审计和追溯。两条通知路径互为补充，不互斥。同步更新：（a）§3.5 事件表中 EmergencyActivatedEvent 消费方补充"并发出 AlertTriggeredEvent"；（b）场景 4 步骤 3 同步引用。 |
| **5【轻微·需求响应缺失】救援机构的远程解锁授权模型未形式化** — 需求明确要求"云端授权开启车门锁"和"调取驾驶员健康档案"，但设计文档中授权模型仅以自然语言存在，授权的生命周期、与 AccountRole.RESCUE 的关系、授权凭证建模方式均无形式化描述。 | 采纳方案 (a)——补充救援机构授权建模。（a）§3.3 新增 **VO-23 RescueAuthorizationToken** 值对象（`class`），封装授权凭证（凭证标识、签发机构、目标 Vehicle/Driver 标识、授权操作集合、有效期）及其生命周期（签发→校验→消费/过期）；（b）DS-12 新增「救援机构的远程解锁授权模型」段落，形式化描述三重校验流程（有效期、消费状态、目标匹配）+ AccountRole 角色校验，以及消费标记防重放机制；（c）VO-23 中明确与 AccountRole.RESCUE 的关系——角色为授权前提条件，Token 为授权能力凭证，二者共同构成完整校验链。 |
| **6【轻微·接口设计】DomainEvent 接口的 aggregateId 字段使用 String 类型丢失类型安全性** — §3.5.0 将 aggregateId 定义为 String，与决策 8"聚合标识使用Java的 struct 类型"不一致，导致事件消费方需手动解析字符串、无法在编译期区分聚合根标识类型。 | 采纳。（a）§3.5.0 中 `aggregateId` 从 `String` 改为 **`AggregateId` struct**——封装 `value: String`（标识字面值）和 `aggregateType: AggregateType`（`enum`：TRIP/DRIVER/VEHICLE/SYSTEM_ACCOUNT/ROAD_RAGE_VOICE_RECORD），使事件消费方可在编译期通过 `aggregateType` 枚举区分聚合根标识类型；（b）更新 DomainEvent interface 的说明描述以反映 AggregateId 的类型安全语义；（c）§3.6 契约 4 Outbox 表结构中 `aggregate_id` 字段说明同步更新为"以 AggregateId.value 字面值存储"。 |
| **7【轻微·边界条件缺失】DS-09 ScoringService 评分周期边界语义未定义** — 跨周期行程的归属规则、加权平均计算公式、计算时机（即时预计算还是按需计算）均缺失。 | 采纳。（a）DS-09 新增「周期评分边界语义」段落，定义三项边界规则：（i）**周期归属规则**：一次行程的 TripScore 归属于其行程结束时间戳所在的自然周期，跨周期不拆分；（ii）**加权计算公式**：周期评分 = ∑(TripScore_i × duration_i) / ∑duration_i，以行程时长作为加权因子，clamp 至 [0,100]，周期内无行程时返回 `Option.None`；（iii）**计算时机**：按需即时计算（管理员请求周期评分或报告生成时触发），而非定时预计算。即时计算避免了预计算结果时效性不一致问题。场景 5 步骤 8 同步引用此周期归属规则与加权公式。 |
| **8【轻微·并发策略遗漏】OTAUpgradeStatus 与传感器自检的并发一致性场景在 §6.3 未覆盖** — §6.3 并发策略表已覆盖 6 个场景，但未覆盖 OTA 升级状态更新与传感器自检状态更新同时对 Vehicle 聚合执行的并发场景。 | 采纳。§6.3 并发策略表新增条目「OTA 升级状态更新（DS-15）与传感器自检状态更新（DS-14）同时对 Vehicle 聚合执行」——Vehicle 聚合使用乐观锁，后提交者检测到冲突后重试：OTA 升级在重试上限内重试（与场景 9 一致），传感器自检立即重试一次（自检为周期性操作，单次冲突不影响下一周期自检）。如乐观锁冲突持续无法解决，以最近一次成功写入为准。 |
