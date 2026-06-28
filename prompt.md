# prompt

## 需求澄清与获取

启动需求澄清或者审议框架，对 `D:\软件测试\requirements\202606242158_vehicle-safety-monitoring\req_v3.md` 需求文档进行审议和需求澄清。

---

## OOD设计

### 第一阶段：领域层 OOD ✅ 已完成

> 产出：docs/ood_domain.md

启动再审议框架，执行OOD流程，完成如下任务：

需求文档：
D:\软件测试\requirements\202606242158_vehicle-safety-monitoring\req_v4.md

这是一个「智能物联——基于多传感器融合的车载安全监测系统」软件项目。系统包含多维感知、AI 风险判定引擎（边缘—云协同）、闭环干预与反馈、远程监护（家属 APP）、车队运营管理（大屏/报表）、应急救援联动、OTA 固件升级管理等功能域。

技术栈：前端（家属 APP / 车队大屏）采用 ArkTS（HarmonyOS），后端（云端服务 + 边缘车载端服务）统一 Java Spring Boot，部署于华为云。设备接入基于华为云 IoTDA（MQTT 协议），数据库使用金仓（兼容 PostgreSQL），推送使用 SMN，音视频对讲使用 SparkRTC。

已完成的需求澄清与评审文档在：
D:\软件测试\requirements\202606242158_vehicle-safety-monitoring\
（含 req_v1~v4 四轮迭代及 review_v1~v4 四轮审查，v4 审查结论为 APPROVED。）

请你为本系统做领域层 OOD 设计，产出：实体 / 值对象 / 聚合根 / 领域服务 / 领域事件。需要覆盖需求文档第四~五节的全部业务规则（BR-01~BR-08）和核心业务对象。不要实现。
在流程启动阶段不要读取文件，直接启动流程。

---

### 第二阶段：应用层 OOD ✅ 已完成

> 产出：docs/ood_application.md

启动再审议框架，执行OOD流程，完成如下任务：

承接第一阶段领域层 OOD 产出，为本系统做应用层 OOD 设计。

领域层 OOD 产出在：
{第一阶段最终产出路径}

需求文档：
D:\软件测试\requirements\202606242158_vehicle-safety-monitoring\req_v4.md

（项目概述与技术栈同第一阶段，不再重复。）

请你为本系统做应用层 OOD 设计，产出：各功能域的应用服务定义（RiskMonitoringService / InterventionService / RemoteGuardianshipService / FleetManagementService / EmergencyRescueService / OTAManagementService）及其接口契约、服务间协作关系、核心时序图（至少覆盖疲劳判定→告警→干预链路、活体遗留→报警链路、碰撞失能→SOS+家属自动激活链路三条关键路径）。不要实现。
在流程启动阶段不要读取文件，直接启动流程。

---

### 第三阶段：基础设施/适配层 OOD 🔲 待开始

> 前置依赖：第一阶段（docs/ood_domain.md）、第二阶段（docs/ood_application.md）均已完成。

启动审议式执行框架，完成以下任务：

领域层 OOD 产出：docs/ood_domain.md
应用层 OOD 产出：docs/ood_application.md

需求文档：docs/requirements.md

技术栈：后端 Java Spring Boot，部署华为云。设备接入华为云 IoTDA（MQTT），数据库使用金仓（兼容 PostgreSQL，JPA 方言 PostgreSQLDialect），推送 SMN，音视频 SparkRTC，前端 ArkTS（HarmonyOS）。

请你为本系统做基础设施/适配层 OOD 设计，产出：

1. **数据持久化设计**：各聚合根（AR-01~AR-05）的数据库表结构（含字段、类型、索引、主键/外键策略）、值对象 @Embeddable 映射、乐观锁版本号字段约定。

2. **仓储实现设计**：TripRepository / DriverRepository / VehicleRepository / SystemAccountRepository / RoadRageVoiceRecordRepository 五个仓储的 JPA 实现要点、复杂查询（如按到期时间批量查询 RoadRageVoiceRecord）的 JPQL/原生 SQL 策略、CQRS 读模型投影表（告警历史查询、看板聚合查询）的表结构与同步策略。

3. **领域事件基础设施**：事件总线选型与实现（边缘侧单线程同步 EventBus + 云端 outbox 事务性事件表 + 消息队列异步投递）、事件持久化表结构、重试与死信策略、各事件消费者注册机制。

4. **外部端口适配器**：VehicleStateBuffer（30s ring buffer 实现）、PhysiologicalDataBuffer（≥10s 滚动缓冲实现）、DrivingBehaviorTrackingPort（急刹/急加速阈值注入与增量上报）、CameraOcclusionDetectionPort（遮挡检测回调）、OTADeliveryPort（升级包传输）、NotificationPort（SMN 推送）、RescueReportPort（救援中心投递）等端口的具体实现方案与技术选型。

5. **华为云服务集成**：IoTDA 设备影子/Topic 路由设计、金仓数据库连接与分库分表策略（按 VehicleId 哈希分片）、SMN 消息模板、SparkRTC 房间/音频流管理集成。

6. **安全与隐私基础设施**：数据脱敏（人脸关键点提取）的边缘侧实现策略、语音存证边缘加密存储方案、二次身份验证（指纹/人脸/动态短信）的集成方案。

7. **边缘-云部署拓扑**：边缘侧车载终端（判定引擎 + 本地 HMI + 数据缓冲 + 断网兜底）与云端服务（应用服务 + 事件总线 + 仓储 + 消息队列）的组件部署图、MQTT 信道拓扑、断网状态下边缘侧正常运行与云端功能优雅降级的具体策略。

不要实现具体代码，产出架构级设计文档。


### 第四阶段：接口/API 层 OOD 🔲 待开始

> 前置依赖：第一~第三阶段均已完成后方可启动。

启动审议式执行框架，完成以下任务：

领域层 OOD 产出：docs/ood_domain.md
应用层 OOD 产出：docs/ood_application.md
基础设施层 OOD 产出：{第三阶段最终产出路径}

需求文档：docs/requirements.md

技术栈：前端 ArkTS（HarmonyOS），后端 Java Spring Boot。

请你为本系统做接口/API 层 OOD 设计，产出：

1. **REST API 契约**：六个应用服务的全部 REST 端点清单（路径、方法、请求体/查询参数、响应体、HTTP 状态码、认证头），按 OpenAPI 3.0 风格描述。需覆盖：
   - S1 RiskMonitoringService：流式判定会话管理、历史风险查询
   - S2 InterventionService：干预状态查询、驾驶员覆盖上报
   - S3 RemoteGuardianshipService：家属权限查询/管理、状态订阅、音视频对讲请求、远程车窗控制
   - S4 FleetManagementService：看板查询、钻取查询、报告生成/下载、绩效预警订阅
   - S5 EmergencyRescueService：SOS 确认、救援授权管理、家属手动救援触发
   - S6 OTAManagementService：升级任务创建/查询、回滚指令下发、升级进度查询

2. **MQTT 主题设计**：IoTDA 设备-云通信的完整 Topic 路由表（边缘→云的感知上报、云→边缘的指令下发、云端推送→家属 APP 的告警/状态），按数据分类定义 QoS 等级与 Payload 格式（JSON Schema）。

3. **WebSocket/SparkRTC 集成**：家属 APP 音视频对讲、远程视频监控的 WebSocket 信令协议与 SparkRTC 房间管理接口。

4. **ArkTS 前端对接契约**：家属 APP（HarmonyOS）调用的全部后端接口清单与数据模型（DTO）定义，车队大屏的看板数据订阅模型，HMI（车机端）的本地查询接口。

5. **安全设计**：API 认证（JWT/OAuth2）、接口限流策略（令牌桶/漏桶）、MQTT 设备鉴权（X.509 证书或 Token 认证）、敏感数据传输加密策略。

不要实现具体代码，产出接口契约级设计文档。