## 迭代第 1 轮

1. **问题描述**：S3 缺失家属权限查询 REST 端点。需求 `requirement.md:23` 要求 S3 RemoteGuardianshipService 覆盖"家属权限查询/管理"，但产出 §1.3 REST 端点表中无独立的权限查询端点（如 `GET /api/v1/guardianship/permissions/{driverId}`），家属仅能通过 WebSocket 推送被动获知权限状态，缺乏主动查询能力。
   - 所在位置：§1.3 REST 端点表
   - 严重程度：中等
   - 改进建议：在 S3 REST 端点表中补充 `GET /api/v1/guardianship/{driverId}/permissions` 端点，返回家属对指定驾驶员的当前授权状态（是否已授权、授权范围、授予时间、有效期），对应的应用层 `IRemoteGuardianshipService` 需新增相应查询方法。
2. **问题描述**：MQTT Payload JSON Schema 覆盖不完整。产出 §2.1 定义约 20 个 Topic 路由，但 §2.2 仅提供 4 个核心 Payload 的 JSON Schema。关键 Topic（车窗控制指令、OTA 指令、指令 Ack、传感器故障、心跳、各推送消息等）的 Payload 格式缺失。
   - 所在位置：§2.2
   - 严重程度：中等
   - 改进建议：为 §2.1 路由表中每个 Topic 提供至少字段级的 Payload 格式定义（可简化为表格形式，核心 Topic 提供完整 Schema，次要用表格），至少补充车窗控制指令、OTA 指令、指令 Ack、传感器故障、心跳、各推送消息的 Payload 结构。

## 迭代第 2 轮

1. **问题描述**：新增API端点未映射到应用层方法——v2新增的3个REST端点（`GET /guardianship/{driverId}/permissions`、`POST /sparkrtc/token`、`DELETE /ota/upgrade-tasks/{taskId}`）在应用层OOD中无对应的方法签名，违反需求§2约束
   - 所在位置：§1.3端点表（行128, 129）、§1.6端点表（行537）；对比`docs/ood_application.md`§3.3、§3.6
   - 严重程度：严重
   - 改进建议：在应用层OOD中为S3补充`queryGuardianshipPermissions`和`issueSparkRTCToken`方法，为S6补充`cancelUpgradeTask`方法；或在API OOD中明确标注对应应用层待新增方法的具体预签名
2. **问题描述**：家属手动救援触发端点归属与需求分组不一致——`POST /api/v1/guardianship/manual-rescue`归入S3，但需求`requirement.md:27`将其列入S5 EmergencyRescueService覆盖范围
   - 所在位置：§1.3 S3端点表（行125）；对比`requirement.md:27`、`docs/ood_application.md`§3.3 vs §3.5
   - 严重程度：一般
   - 改进建议：在文档开篇或对应分节处添加说明，解释设计层面基于职责内聚的归口调整；或将端点从§1.3移至§1.5并在应用层S5中补充方法
3. **问题描述**：S5 queryRescueHistory端点认证标注与其他S5端点及§5.1角色映射矛盾——§1.5标注`JWT`（无角色限定），但§5.1限定S5全部端点仅`RESCUE`角色可访问
   - 所在位置：§1.5端点表（行440）；对比§5.1角色→权限映射表（行1643）
   - 严重程度：一般
   - 改进建议：将§1.5中`queryRescueHistory`端点的认证列统一标注为`JWT (RESCUE)`
4. **问题描述**：SparkRTC Token独立端点的消费者未阐明——`POST /api/v1/sparkrtc/token`端点未说明目标调用方，§4.1和§4.3也未列出该端点
   - 所在位置：§1.3端点表（行129）、§3.2（行1294-1326）；对比§4.1（行1371-1378）
   - 严重程度：轻微
   - 改进建议：补充调用场景说明，若面向家属APP应在§4.1 REST调用列表中补入
5. **问题描述**：TriggerRollbackResponse示例未覆盖ROLLING_BACK中间状态——JSON示例仅展示`ROLLED_BACK`，缺失`ROLLING_BACK`状态
   - 所在位置：§1.6 TriggerRollbackResponse JSON示例（行597-602）；对比`docs/ood_application.md`§4.6
   - 严重程度：轻微
   - 改进建议：补充`newStatus`的可能取值及两种状态的含义说明
6. **问题描述**：S4 report download端点响应未指定Content-Type——不同format参数应返回不同Content-Type
   - 所在位置：§1.4端点表（行309）
   - 严重程度：轻微
   - 改进建议：补充Content-Type说明及Content-Disposition头
7. **问题描述**：MQTT主题模板语法不一致——`${sensorType}`与其余`{variable}`语法不统一
   - 所在位置：§2.1主题路由总表第一行（行649）
   - 严重程度：轻微
   - 改进建议：将`${sensorType}`统一为`{sensorType}`
8. **问题描述**：DELETE请求的成功响应码约定不统一——S3返回204，S6返回200+响应体
   - 所在位置：§1.3（行123）、§1.6（行537）
   - 严重程度：轻微
   - 改进建议：在文档开篇统一DELETE响应码策略

## 迭代第 3 轮

1. **问题描述**：S3 手动救援触发返回 `rescueRequestId`，S5 救援历史等所有端点使用 `rescueReportId`，未定义两者关联关系及跨服务流转逻辑
   - 所在位置：§1.3 TriggerManualRescueResponse（:195）、§1.5 各端点（:459,:468,:516）、§3.1 WebSocket 下行（:1293）
   - 严重程度：严重
   - 改进建议：在 TriggerManualRescueResponse 中补充 `rescueReportId` 字段，或添加说明块阐明 S3 手动救援触发后如何在 S5 创建救援记录并获取 `rescueReportId` 的流转逻辑
2. **问题描述**：3 个新增端点（`queryGuardianshipPermissions`、`issueSparkRTCToken`、`cancelUpgradeTask`）仅有"预签名"注释，应用层 OOD（`docs/ood_application.md`）中不存在对应方法定义，违反需求约束 §49
   - 所在位置：§1.3（:132-133）、§1.6（:550），对比 `docs/ood_application.md` §3.3（:260-271）、§3.6（:451-456）
   - 严重程度：严重
   - 改进建议：在 `docs/ood_application.md` 中为 S3 补充 `queryGuardianshipPermissions` 和 `issueSparkRTCToken` 方法签名及 DTO，为 S6 补充 `cancelUpgradeTask` 方法签名及 DTO
3. **问题描述**：多个 S3 端点请求体包含 `familyAccountId` 字段，但未校验其与 JWT `sub` 一致，存在横向越权风险
   - 所在位置：§1.3 各请求体（:141-211）、§5.1 JWT Payload（:1652）、§5.6 隐私校验表（:1773）
   - 严重程度：一般
   - 改进建议：补充安全门控规则——校验 `familyAccountId` 与 JWT `sub` 一致，不一致则拒绝请求；或从请求体移除该字段改为从 JWT 隐式提取
4. **问题描述**：MQTT Topic `cmd/media/join/down` 在主题路由总表和 Payload 定义中均缺失，流程图（:1325）为其唯一引用
   - 所在位置：§2.1 主题路由总表（:666-692）、§2.2 Payload 定义（:693-1245）
   - 严重程度：一般
   - 改进建议：在 §2.1 路由表新增该主题行，在 §2.2 补充 Payload 定义（至少含 `sparkRTCRoomId`、`sparkRTCJoinToken`、`commandId`）
5. **问题描述**：§1.4 QueryTrajectoryResponse 端点描述提及 `dataConsistency` 字段但响应 JSON 示例中未包含，且枚举值未定义
   - 所在位置：§1.4 端点描述（:371）与 JSON 示例（:362-369）
   - 严重程度：一般
   - 改进建议：在 QueryTrajectoryResponse 中补充 `dataConsistency` 字段（枚举 `CONSISTENT | INCONSISTENT`），并在 JSON 示例中体现
6. **问题描述**：§4.1 家属 APP REST API 调用列表遗漏 `requestMediaSession` 端点，对应 ArkTS DTO 类型也未定义
   - 所在位置：§1.3（:126）、§4.1 REST API 调用列表（:1390-1398）、ArkTS DTO 定义（:1402-1536）
   - 严重程度：一般
   - 改进建议：在 §4.1 中明确调用方式并补充对应 ArkTS 数据模型


## 迭代第 4 轮

1. **问题描述**：S3→S5 手动救援流转引用了 `S5.createRescueReport()` 方法，但 `IEmergencyRescueService` 接口方法表中不存在该方法签名——API OOD 与应用层 OOD 两文档均有共识性引用，接口层缺失形式化契约
   - 所在位置：`a_v4_output_v2.md` §1.3 TriggerManualRescueResponse（行 207-211），关联 `docs/ood_application.md` §3.5 IEmergencyRescueService 接口方法表及 §4.3 TriggerManualRescueResponse DTO
   - 严重程度：严重
   - 改进建议：在 `docs/ood_application.md` §3.5 `IEmergencyRescueService` 接口方法表中补充 `createRescueReport` 方法行（含输入/输出 DTO、事务属性、异常处理），并在 §4.5 补充对应的 Request/Response DTO 定义
2. **问题描述**：API OOD 的 `RequestMediaSessionRequest` 包含 `secondaryAuthToken` 字段，但应用层 OOD 中同名 DTO 缺少该字段，导致前端传入的二次验证凭证在反序列化时丢失
   - 所在位置：`a_v4_output_v2.md` §1.3（行 149-156）vs `docs/ood_application.md` §4.3（行 576-579）
   - 严重程度：一般
   - 改进建议：在 `docs/ood_application.md` §4.3 `RequestMediaSessionRequest` DTO 中补充 `secondaryAuthToken: String` 字段
3. **问题描述**：API OOD 的 `TriggerManualRescueRequest` 包含 `secondaryAuthToken` 字段，但应用层 OOD 中同名 DTO 缺少该字段，与问题 2 性质相同
   - 所在位置：`a_v4_output_v2.md` §1.3（行 189-195）vs `docs/ood_application.md` §4.3（行 595-598）
   - 严重程度：一般
   - 改进建议：在 `docs/ood_application.md` §4.3 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段
4. **问题描述**：高危失能场景下 SparkRTC 会话时长豁免机制未定义——未说明豁免触发条件、Token 10 分钟硬编码有效期如何与豁免后延长会话协调、是否需要重新签发 Token
   - 所在位置：`a_v4_output_v2.md` §3.2 SparkRTC 房间参数表（行 1378）及 Token 签发（行 1404）
   - 严重程度：一般
    - 改进建议：补充豁免机制的触发条件、Token 续期策略，或明确标注此场景下的 Token 处理方式为待定项

## 迭代第 5 轮

1. **问题描述**：S3→S5 手动救援流转的核心依赖方法 `IEmergencyRescueService.createRescueReport()` 未在应用层 OOD 中形式化定义，API OOD 编排逻辑依赖该方法但接口契约缺失
    - 所在位置：`a_v5_copy_from_v4.md` §1.3 行207–216；对比 `docs/ood_application.md` §3.5 行397–402（缺少方法）、§4.5 行775–824（缺少DTO）
    - 严重程度：严重
    - 改进建议：在 `docs/ood_application.md` §3.5 中补充 `createRescueReport` 方法及对应 DTO（CreateRescueReportRequest/CreateRescueReportResponse），完成后移除 API OOD 中的"⚠ 接口契约待补"注释
2. **问题描述**：跨层 DTO 不一致 — RequestMediaSessionRequest 和 TriggerManualRescueRequest 在 API OOD 中均含 `secondaryAuthToken` 字段，但应用层 OOD 同名 DTO 缺少该字段，导致二次验证凭证无法传入应用服务入口
    - 所在位置：`a_v5_copy_from_v4.md` §1.3 行154、行193；对比 `docs/ood_application.md` §4.3 行576–579、行595–598
    - 严重程度：中等
    - 改进建议：在应用层 OOD 的 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段
3. **问题描述**：跨层 DTO 不一致 — API OOD 的 AlertSummary 包含 `gpsLocation` 可选字段，但应用层 OOD 同名 DTO 缺少此字段
    - 所在位置：`a_v5_copy_from_v4.md` §1.1 行51；对比 `docs/ood_application.md` §4.1 行511–517
    - 严重程度：中等
    - 改进建议：在应用层 OOD 的 `AlertSummary` DTO 中补充 `gpsLocation: Optional<GeoPoint>` 字段
4. **问题描述**：ArkTS DTO 类型定义不完整 — `AlertType` 枚举遗漏 `PERFORMANCE_WARNING`，`RiskLevel` 枚举遗漏 `L1_HINT`
    - 所在位置：`a_v5_copy_from_v4.md` §4.1 行1462–1463、行1485
    - 严重程度：中等
    - 改进建议：在 §4.1 新增独立的 `AlertType` 和 `RiskLevel` 类型声明，统一引用以替代各处的行内注释
5. **问题描述**：S5 和 S6 端点表使用 7 列格式（缺失"查询参数"列），与 S1/S2/S4 的 8 列格式不一致
    - 所在位置：`a_v5_copy_from_v4.md` §1.5 行467–472、§1.6 行563–569
    - 严重程度：中等
    - 改进建议：将 S5 和 S6 端点表扩展为 8 列格式，补充"查询参数"列
6. **问题描述**：S2 §1.2 完全缺失错误响应文档，调用方无法获取可能的错误状态码和处理方式
    - 所在位置：`a_v5_copy_from_v4.md` §1.2 行71–113
    - 严重程度：中等
    - 改进建议：在 §1.2 末尾补充错误响应小节（400/503 等）
7. **问题描述**：跨层 DTO 不一致 — API OOD 的 `QueryTrajectoryResponse` 包含 `dataConsistency` 字段，但应用层 OOD 同名 DTO 缺少该字段
    - 所在位置：`a_v5_copy_from_v4.md` §1.4 行385；对比 `docs/ood_application.md` §4.4 行765–768
    - 严重程度：一般
    - 改进建议：在应用层 OOD 的 `QueryTrajectoryResponse` DTO 中补充 `dataConsistency: DataConsistency` 字段及枚举定义

## 迭代第 6 轮

1. **问题描述**：StatusColor 枚举值跨层不一致——应用层引入未定义的 ORANGE 值。领域层 OOD 定义 StatusColor 为 GREEN | YELLOW | RED（三值），API OOD 和 ArkTS 类型也均为三值，但应用层 OOD 列出 GREEN / YELLOW / ORANGE / RED（四值），多了 ORANGE，构成跨层定义矛盾。
   - 所在位置：API OOD §1.1 行 37，对比 `docs/ood_application.md` §4.1 行 498
   - 严重程度：一般
   - 改进建议：在 `docs/ood_application.md` §4.1 `GetDriverRiskStatusResponse` 的 `derivedStatusColor` 字段说明中，移除 ORANGE 或明确 ORANGE 的语义来源与触发条件。建议统一为三值 GREEN / YELLOW / RED，与领域层定义保持一致。
2. **问题描述**：§3.2 高危失能豁免机制引入了 `LIFE_DETECTION_PROLONGED`（活体检测持续异常 ≥60s）概念，但该概念在领域层（AlertType 仅定义 LIFE_DETECTION，无对应的独立事件或判定方法）和应用层（高危激活流程未涉及"持续 ≥60s"的独立判定）均无形式化定义，API 层直接引用将导致下游无法确定判定依据和数据来源。
   - 所在位置：API OOD §3.2，行 1427
   - 严重程度：一般
    - 改进建议：二选一：(a) 在领域层 OOD 或应用层 OOD 中补充对"活体检测持续异常 ≥60s"的判定逻辑形式化定义；(b) 在 API OOD 中将 `LIFE_DETECTION_PROLONGED` 替换为对既有概念的引用，明确此为判定逻辑描述而非新的事件类型或 AlertType。

## 迭代第 7 轮

1. **问题描述**：API OOD 的 `RequestMediaSessionResponse` 定义了 4 个字段但应用层 OOD 同名 DTO 仅定义了 2 个字段，缺少 `sparkRTCRoomId` 和 `sparkRTCJoinToken`，前端无法获取入房凭证
   - 所在位置：API OOD §1.3（行 166-174）及 §4.1（行 1487-1492），对比 `docs/ood_application.md` §4.3（行 583-585）
   - 严重程度：严重
   - 改进建议：在 `docs/ood_application.md` §4.3 的 `RequestMediaSessionResponse` DTO 中补充 `sparkRTCRoomId: String` 和 `sparkRTCJoinToken: String` 字段
2. **问题描述**：需求要求覆盖 JWT/OAuth2，API OOD §5.1 改为仅含 JWT 并声明不采用 OAuth2，但设计正文未提供排除 OAuth2 的设计理由或适用性评估
   - 所在位置：API OOD §5.1 标题及 v8 修订说明（行 1954），对比 `requirement.md:43`
   - 严重程度：一般
   - 改进建议：在 §5.1 开篇补充 OAuth2 排除说明，阐述不作采用的设计理由
3. **问题描述**：§5.2 引用的 `POST /api/v1/auth/secondary-verify` 端点未出现在 §1 REST API 任何端点表中，也未列入 §4.1 REST API 调用清单
   - 所在位置：§5.2（行 1773-1781），对比 §1 全部端点表及 §4.1 REST API 调用列表
   - 严重程度：一般
   - 改进建议：在 §1 新增端点定义小节正式定义该端点，或标注其归属及外部参考位置
4. **问题描述**：13 个 ArkTS 接口引用的枚举/字面量类型仅以行内注释形式存在，缺少独立的 `type` 别名声明
   - 所在位置：§4.1 ArkTS 数据模型定义（行 1462-1616）
   - 严重程度：一般
   - 改进建议：为 13 个类型各新增独立的 `type` 别名声明，统一替换行内注释为类型引用
5. **问题描述**：文件名 `a_v7_copy_from_v6.md`、文档标题 "a_v8 / v8"、迭代轮次 7 三者版本号不一致
   - 所在位置：文件名 vs 文档标题行（行 1）vs 迭代轮次编号
   - 严重程度：轻微
    - 改进建议：统一版本标识，更新文件名或修正标题

## 迭代第 8 轮

1. **问题描述**：§1.6 `TriggerRollbackResponse` 的 JSON 代码块之后存在多余 ` ``` ` 独立行（第 646 行），导致 Markdown 解析器将后续所有内容错误渲染为代码块外普通文本，影响所有后续 JSON 示例的结构可读性
   - 所在位置：产出文档 §1.6 第 646 行
   - 严重程度：严重
   - 改进建议：删除第 646 行的多余 ` ``` `
2. **问题描述**：文档标题为"a_v9 / v9"，但当前实际迭代轮次为第 8 轮，修订说明块声称"与当前迭代轮次（v9）一致"构成事实错误
   - 所在位置：产出文档第 1 行（标题）及第 2017 行（修订说明块）
   - 严重程度：中等
   - 改进建议：将标题从"a_v9 / v9"修正为"a_v8 / v8"，将"修订说明（v9）"更新为"修订说明（v8）"
3. **问题描述**：`POST /api/v1/sparkrtc/token` 端点的 `role` 取值包含 `publisher`，但当前角色体系下无任何角色能合法通过此 REST 端点请求该值，保留此值增加误用风险
   - 所在位置：§1.3 `IssueSparkRTCTokenRequest.role`（行 304）、§4.1 ArkTS `SparkRTCRole`（行 1514）、§1.3 安全约束块（行 306）
   - 严重程度：中等
   - 改进建议：将 REST 端点和 ArkTS 类型的 `role` 取值限定为 `'subscriber'`，移除 `publisher`；或在 API 契约层面显式标注"FAMILY 角色请求此端点时仅可使用 subscriber"
4. **问题描述**：`POST /api/v1/fleet/performance-warning-subscription` 端点仅给出了 `SubscribePerformanceWarningResponse` 的 JSON 示例，未提供 `SubscribePerformanceWarningRequest` 的 JSON 示例，API 使用者无法直接获知请求字段
   - 所在位置：§1.4 端点表行 340 及缺少对应 Request JSON 示例处
   - 严重程度：中等
   - 改进建议：补充 `SubscribePerformanceWarningRequest` 的 JSON 示例（字段为 `adminId`、`fleetId`）
5. **问题描述**：§3.1 WebSocket 下行消息 `rescue_triggered` 的 Payload 中 `status` 字段未枚举可能取值，下游实现者需从上下文推断
   - 所在位置：§3.1 WebSocket 下行消息表 `rescue_triggered` 行（行 1372）
   - 严重程度：一般
   - 改进建议：将 `"status": "..."` 改为 `"status": "PENDING | CONFIRMED | REJECTED"`，并添加交叉引用
6. **问题描述**：S1（§1.1）和 S5（§1.5）的错误响应列表中未包含 `401 Unauthorized` 状态码，与其他服务（如 S4）不一致
   - 所在位置：§1.1 错误响应（行 66–69）、§1.5 错误响应（行 562–564）
   - 严重程度：轻微
   - 改进建议：统一策略：在所有认证端点一致列出 401，或在总述段落后统一声明"401 由 API 网关统一处理，各端点不单独标注"

## 迭代第 9 轮

1. **问题描述**：`IEmergencyRescueService` 接口缺少 `createRescueReport` 方法签名及对应 DTO，导致 S3→S5 手动救援流转依赖的接口契约未在应用层 OOD 中落地。该问题自 v4 起以"⚠ 接口契约待补"标注形式存在，至今未解决。
   - 所在位置：产出 §1.3 TriggerManualRescueResponse / S3→S5 手动救援流转说明（:214–221）；对比 `docs/ood_application.md` §3.5 接口方法表（:397–402）
   - 严重程度：严重
   - 改进建议：在 `docs/ood_application.md` §3.5 接口方法表中补齐 `createRescueReport` 方法行（含输入 DTO、输出 DTO、事务属性、异常处理），并在 §4.5 补充对应的 `CreateRescueReportRequest`/`CreateRescueReportResponse` DTO 定义。完成后移除产出中的"⚠ 接口契约待补"标注。
2. **问题描述**：产出末尾存在两个独立的"修订说明（v9）"修订块（第一个 :2028–2038，第二个 :2053–2059），结构混乱，阅读者无法判断应参考哪一版修订信息。
   - 所在位置：产出 :2028–2038 与 :2053–2059
   - 严重程度：中等
   - 改进建议：将第二个 v9 修订块的内容合并至第一个 v9 修订块末尾，删除重复的"修订说明（v9）"标题和独立的修订块结构。
3. **问题描述**：`TriggerRollbackResponse.newStatus` 包含 `ROLLING_BACK` 值，但 `QueryUpgradeProgressResponse.currentStage` 枚举不含该值，导致回滚执行期间的过渡状态不可观测，前端无法有效区分"正常升级"与"正在回滚"。
   - 所在位置：产出 §1.6 TriggerRollbackResponse（:657）与 QueryUpgradeProgressResponse（:637）
   - 严重程度：中等
   - 改进建议：二选一：(a) 在 `currentStage` 枚举中补充 `ROLLING_BACK` 值；(b) 若回滚为瞬时操作，则在 TriggerRollbackResponse 中移除 `ROLLING_BACK`，仅保留 `ROLLED_BACK`。
4. **问题描述**：`vehicle/state/up` VehicleStateSnapshot 中缺少车窗状态字段，但 `QueryWindowStatusResponse` 和 `DriverStatusSnapshot.windowStatus` 均依赖车窗状态数据，导致数据链路不完整。
   - 所在位置：产出 §2.2 VehicleStateSnapshot（:1156–1180）vs §1.3 QueryWindowStatusResponse（:239–256）、§2.2 DriverStatusSnapshot.windowStatus（:879–891）
   - 严重程度：一般
   - 改进建议：在 `vehicle/state/up` VehicleStateSnapshot 中增加 `windowStatuses` 字段，或新增独立的车窗状态上报 topic。
5. **问题描述**：MQTT SafetyAlertEvent 中 GPS 字段命名为 `gps`，REST QueryAlertHistoryResponse 中对应字段命名为 `gpsLocation`，文档自述"保持一致"但实际命名不同。
   - 所在位置：产出 §2.2 SafetyAlertEvent（:830）vs §1.1 QueryAlertHistoryResponse（:53）
   - 严重程度：轻微
   - 改进建议：统一为 `gpsLocation` 或增加显式字段映射说明。

## 迭代第 10 轮

1. **问题描述**：缺少JWT登录/Token签发端点，认证链路不完整
   - 所在位置：§1 REST API 契约（缺失），§5.1（:1826–1832 描述了签发流程但缺少对应端点定义）
   - 严重程度：严重
   - 改进建议：在 §1.7 或新增 §1.8 中补充 `POST /api/v1/auth/login` 端点定义（含 `LoginRequest` / `LoginResponse`，支持用户名/密码和手机验证码两种登录方式），并明确其跳过 JWT 校验（为匿名端点）。
2. **问题描述**：MQTT AlertTriggeredEvent（family push）GPS 字段命名未随 SafetyAlertEvent 同步更新，文档内部不一致
   - 所在位置：§2.2 AlertTriggeredEvent 表（:1288）、TripStatus 事件（:1126）、OverrideSignal（:1207），对比 SafetyAlertEvent（:830）和 DriverStatusSnapshot（:862）
   - 严重程度：一般
   - 改进建议：二选一：(a) 将文档内所有 MQTT Payload 的 GPS 字段统一为 `gpsLocation`；(b) 在 §2.2 开头新增字段命名约定说明块，明确解释各 Topic 的 GPS 字段命名规则及其设计理由。
3. **问题描述**：跨文档 DTO 不一致 — `RequestMediaSessionResponse` 应用层 OOD 缺少 `sparkRTCRoomId` 和 `sparkRTCJoinToken`
   - 所在位置：API OOD §1.3（:167–175）、§4.1（:1563–1568），对比 `docs/ood_application.md` §4.3（:584–586）
   - 严重程度：一般
   - 改进建议：在 `docs/ood_application.md` §4.3 `RequestMediaSessionResponse` DTO 中补充 `sparkRTCRoomId: String` 和 `sparkRTCJoinToken: String` 字段。
4. **问题描述**：跨文档 DTO 不一致 — `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` 缺少 `secondaryAuthToken`
   - 所在位置：API OOD §1.3（:161, :200），对比 `docs/ood_application.md` §4.3（:577–580, :596–598）
   - 严重程度：一般
   - 改进建议：在 `docs/ood_application.md` §4.3 的 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段。
5. **问题描述**：S3 家属权限管理只有查询端点，缺少主动管理入口
   - 所在位置：§1.3 S3 REST 端点表（:131–140），对比 `requirement.md:23`
   - 严重程度：轻微
   - 改进建议：二选一：(a) 补充 `DELETE /api/v1/guardianship/{driverId}/permissions`；(b) 在文档中明确说明设计理由。
6. **问题描述**：跨文档 `StatusColor` 枚举值不一致 — 应用层引入了领域层未定义的 `ORANGE`
   - 所在位置：API OOD §1.1（:37–39），对比 `docs/ood_domain.md:357`、`docs/ood_application.md:499`
   - 严重程度：轻微
   - 改进建议：在 `docs/ood_application.md` §4.1 中将 `ORANGE` 移除，统一为 GREEN / YELLOW / RED。
7. **问题描述**：跨文档 DTO 不一致 — 多处 DTO 字段在应用层 OOD 中缺失（`AlertSummary.gpsLocation`、`QueryTrajectoryResponse.dataConsistency`）
   - 所在位置：见诊断报告问题7行号
   - 严重程度：轻微
   - 改进建议：在 `docs/ood_application.md` 中补充对应字段。
8. **问题描述**：§3.2 豁免触发条件表述不精确
   - 所在位置：§3.2（:1490）
   - 严重程度：轻微
   - 改进建议：将豁免触发条件修改为引用具体领域事件，移除"持续异常 ≥60s"的计时描述。

## 迭代第 11 轮

1. **问题描述**：JWT refresh token 端点缺失。`LoginResponse` 包含 `refreshToken` 字段，§5.1 步骤5 明确描述刷新流程，但 §1.7 未定义 `POST /api/v1/auth/refresh` 端点，JWT 刷新链路在 API 契约层面断裂
   - 所在位置：§1.7 Auth 端点表（行 713–716），对比 §5.1 步骤 5（行 1882）
   - 严重程度：中等
   - 改进建议：在 §1.7 新增 `POST /api/v1/auth/refresh` 端点，定义 `RefreshTokenRequest` 和 `RefreshTokenResponse`，参照现有 `POST /api/v1/auth/login` 的端点定义风格
2. **问题描述**：S5 和 S6 错误响应列表缺少基础错误码（400、404、503）。S5 缺少 `400`（查询参数无效）和 `404`（driverId/vehicleId 不存在），S6 缺少 `404`（vehicleId 不存在）、`503`（IoTDA 通道不可达）
   - 所在位置：§1.5 错误响应（行 582–584）、§1.6 错误响应（行 703–706）
   - 严重程度：中等
   - 改进建议：在 §1.5 补充 `400` 和 `404`；在 §1.6 补充 `404` 和 `503`。同时建议一并检查 S3 是否同样缺少 400/404 基础错误码
3. **问题描述**：LoginRequest JSON 示例同时包含 `credential`/`secret` 和 `phone`/`smsCode` 两组字段，形成语义冗余，且同时传入非 null 值行为未定义
   - 所在位置：§1.7 LoginRequest JSON 示例（行 720–727）
   - 严重程度：一般
   - 改进建议：二选一：(a) 移除 `phone`/`smsCode`，仅保留 `credential`+`secret`+`authMethod`；(b) PASSWORD 用 `credential`+`secret`，SMS_CODE 用 `phone`+`smsCode`
4. **问题描述**：整份 API OOD 未定义 REST API 的标准错误响应体格式。应用层 OOD §6.1 已定义完整 `AppError` 枚举，但 API OOD 未承接为 REST 错误响应契约
   - 所在位置：§1 各节错误响应块
   - 严重程度：一般
   - 改进建议：在 §一 总述段落后新增"REST 错误响应体约定"块，建议格式为 `{ "errorCode": "...", "message": "...", "requestId": "..." }`
5. **问题描述**：S4 仅有 `POST` 订阅端点，无对应 `DELETE` 取消订阅端点，与 S3 的对称设计不一致
   - 所在位置：§1.4 S4 端点表（行 350）
   - 严重程度：一般
   - 改进建议：新增 `DELETE /api/v1/fleet/performance-warning-subscription/{subscriptionId}` 端点或通过 PUT 更新订阅状态

## 迭代第 12 轮

1. **问题描述**：MQTT 干预指令 Topic `{deviceId}/cmd/intervention/down` 的 Payload 说明引用"车队管理员远程鸣笛"用例，但 §1.2 S2 InterventionService 和 §1.4 S4 FleetManagementService 均无对应 REST 端点支持车队管理员发起干预指令下发，导致 Topic 触发入口缺失
   - 所在位置：§2.1 主题路由总表 `{deviceId}/cmd/intervention/down` 行（Payload 说明列）
   - 严重程度：一般
   - 改进建议：二选一：(a) 在 S2 或 S4 补充对应 REST 端点（如 `POST /api/v1/fleet/{fleetId}/interventions`）；(b) 移除 Payload 说明中的"如车队管理员远程鸣笛等"，改为仅描述由 S1 风险判定驱动的干预指令下发场景
2. **问题描述**：`RequestMediaSessionResponse` 同时包含 `sessionToken` 和 `sparkRTCJoinToken`，两字段语义高度重叠（均为家属端接入 SparkRTC 的凭证），文档未说明区别和使用场景，API 使用者无法确认该用哪个字段调用 SparkRTC SDK join 方法
   - 所在位置：§1.3 RequestMediaSessionResponse JSON 示例、§3.1 access_granted 下行消息、§4.1 ArkTS RequestMediaSessionResp
   - 严重程度：一般
   - 改进建议：明确区分两字段用途：`sessionToken` 为会话级授权凭证（用于后续媒体会话管理操作），`sparkRTCJoinToken` 为实际传入 SparkRTC SDK 的入房 Token；在 §4.1 ArkTS 代码示例中明确展示使用 `sparkRTCJoinToken` 调用 join 方法
3. **问题描述**：§4.1 家属 APP REST API 调用清单缺少 `POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`、`POST /api/v1/auth/secondary-verify` 三个认证端点，家属 APP 开发者仅阅读 §4.1 无法获知完整接口调用序列
   - 所在位置：§4.1 REST API 调用列表，对比 §1.7 Auth 端点表
   - 严重程度：一般
   - 改进建议：在 §4.1 REST API 调用列表中补充三个 Auth 端点行，标注请求/响应模型引用；或在列表前添加说明块交叉引用 §1.7 认证端点
4. **问题描述**：§5.1 JWT Payload 示例包含 `scope` 字段，但角色→权限映射表仅定义了三个角色到端点范围的映射，未说明 scope 的角色分配规则、scope 与角色的优先级，API 实现者无法确定授权判断应基于 `role` 还是 `scope`
   - 所在位置：§5.1 JWT Token 结构，对比 §5.1 角色→权限映射表
   - 严重程度：一般
   - 改进建议：二选一：(a) 补充 scope 与角色映射表，说明应用服务入口校验规则；(b) 若仅用角色做权限判断，移除 JWT Payload 中的 scope 字段
5. **问题描述**：系统处理驾驶员生理体征、GPS 轨迹、车内音视频等高度敏感数据，但告警历史、行程评分、生理体征快照、GPS 轨迹、家属监护关系等关键数据的保留周期、归档策略和到期清理规则均未定义
   - 所在位置：全文（缺失）
   - 严重程度：一般
   - 改进建议：在 §5.6 之后新增"数据生命周期"小节，覆盖告警历史、行程记录及评分、生理体征快照、车辆遥测数据、家属监护关系、救援记录等类别，每类数据明确保留时长及到期处理方式
6. **问题描述**：S4 `GET /api/v1/fleet/{fleetId}/trajectory` 端点未声明最大查询时间跨度、分页 size 上限等约束，与 S1 queryAlertHistory（max 100）和 S6 OTA（批量上限 100 辆）的约束水平不一致
   - 所在位置：§1.4 端点表 `车辆轨迹查询` 行及查询参数说明
   - 严重程度：一般
   - 改进建议：补充约束：最大时间跨度（如 ≤30 天）、size 默认值和上限（如默认 100，最大 500）、超阈值时的截断处理策略
7. **问题描述**：§4.1 ArkTS 数据模型定义遗漏了 `TokenRenewedMessage`、`SubscribeStatusAckMessage`、`RescueTriggeredMessage` 三个 WebSocket 消息的 TypeScript 接口定义（§3.1 下行消息表已定义但 §4.1 未纳入）
   - 所在位置：§4.1 ArkTS 数据模型定义，对比 §3.1 下行消息表
   - 严重程度：一般
   - 改进建议：在 §4.1 补充三个接口的 TypeScript 定义，包含对应字段
8. **问题描述**：§5.6 隐私校验表覆盖了家属主动查询场景的 GPS 权限校验（pull），但 MQTT 推送（push）Topic 中的 GPS 位置信息缺少对应的推送阶段脱敏/过滤规则
   - 所在位置：§5.6 隐私边界安全校验点表，对比 §2.2 家属告警推送及 DriverStatusSnapshot
   - 严重程度：轻微
   - 改进建议：在 §5.6 表中新增推送场景 GPS 权限校验规则：推送时校验家属当前监护权限有效性，无权限时 GPS 字段填 null
