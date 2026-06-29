根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

以下为本轮（v7 第1轮）组件B诊断报告识别的问题，经质询全部确认（LOCATED）：

1. **严重·跨层 DTO 不一致**：API OOD 的 `RequestMediaSessionResponse` 定义了 4 个字段（`sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken`），但应用层 OOD 的同名 DTO 仅定义 2 个字段，缺少后两者。前端需要这两个字段才能加入 SparkRTC 房间，应用层 DTO 缺失将导致字段在反序列化时丢失。
   - 位置：API OOD §1.3 行166-174、§4.1 行1487-1492；对比 `docs/ood_application.md` §4.3 行583-585
   - 改进：在 `docs/ood_application.md` §4.3 的 `RequestMediaSessionResponse` DTO 中补充 `sparkRTCRoomId` 和 `sparkRTCJoinToken` 字段。

2. **一般·需求响应缺陷**：需求 `requirement.md:43` 要求安全设计覆盖 "JWT/OAuth2"，API OOD §5.1 经 v8 修订仅保留 JWT，注明"本设计不采用 OAuth2 协议"，但设计正文未提供排除 OAuth2 的设计理由或适用性评估，读者无法判断 OAuth2 是被评估后排除还是被遗漏。
   - 位置：API OOD §5.1 标题及 v8 修订说明（行1954）；对比 `requirement.md:43`
   - 改进：在 §5.1 开篇补充 OAuth2 排除说明，阐述不作采用的设计理由。

3. **一般·接口契约不完整**：§5.2 二次身份验证流程引用 `POST /api/v1/auth/secondary-verify` 端点，但该端点未出现在 §1 REST API 契约的任何端点表中，也未列入 §4.1 家属 APP REST API 调用清单。
   - 位置：§5.2 行1773-1781；对比 §1 及 §4.1
   - 改进：在 §1 新增独立小节正式定义该端点，或明确标注其归属及外部参考位置。

4. **一般·ArkTS DTO 类型定义不完整**：v7 已为 `AlertType` 和 `RiskLevel` 新增独立 TypeScript 类型别名，但以下 13 个枚举/字面量类型仍仅以行内注释存在，缺少独立 `type` 声明：`StatusColor`、`MediaSessionType`、`RescueRequestStatus`、`WindowControlOperation`、`WindowPosition`、`WindowState`、`WindowOperationResult`、`GuardianshipPermissionType`、`CareRelationshipStatus`、`SparkRTCRole`、`TripStatus`、`AccessGrantReason`、`AccessRevokeReason`。
   - 位置：§4.1 行1462-1616
   - 改进：为上述 13 个类型各新增独立的 `type` 别名声明，统一替换接口中的行内注释为类型引用。

5. **轻微·版本标识不一致**：文件名 `a_v7_copy_from_v6.md`、文档标题 "a_v8 / v8"、迭代轮次 7 三者版本号不一致。
   - 位置：文件名 vs 文档标题行1 vs 迭代轮次编号
   - 改进：统一版本标识。

## 历史迭代回顾

### 已解决的问题
- 第1轮：S3 家属权限查询端点缺失 → 已补充 `GET /api/v1/guardianship/{driverId}/permissions`
- 第1轮：MQTT Payload JSON Schema 覆盖不完整 → 已补充全部 Topic 的 Payload 定义
- 第2轮：新增端点未映射到应用层方法 → 已补充应用层方法映射注释
- 第2轮：家属手动救援端点归属争议 → 已补充设计说明并完成 S3→S5 流转逻辑
- 第2轮：S5 认证标注矛盾、SparkRTC Token 消费者未阐明、TriggerRollbackResponse 状态缺失、报告下载 Content-Type 缺失、MQTT 模板语法不一致、DELETE 响应码不统一 → 均已修复
- 第3轮：S3→S5 rescueRequestId/rescueReportId ID 断裂 → 已补充 `TriggerManualRescueResponse.rescueReportId` 及流转说明
- 第3轮：MQTT cmd/media/join/down 主题缺失 → 已补充
- 第3轮：QueryTrajectoryResponse 缺 dataConsistency → 已补充
- 第3轮：家属 APP REST API 调用列表遗漏 requestMediaSession → 已补充
- 第4轮：高危失能豁免机制 → 已补充豁免触发条件和 Token 续期逻辑
- 第5轮：AlertType/RiskLevel 枚举遗漏 → 已补充独立 type 声明
- 第5轮：S5/S6 端点表格式不一致 → 已统一为 8 列
- 第5轮：S2 错误响应缺失 → 已补充
- 第6轮：StatusColor 跨层不一致 → API OOD 侧已标注以领域层为准
- 第6轮：LIFE_DETECTION_PROLONGED 概念未形式化 → 已补充说明

### 持续存在的问题（多轮反复出现，需重点解决）
以下问题的根因均在下游应用层 OOD（`docs/ood_application.md`），API OOD 无法单方面修复，但仍需持续追踪并在迭代说明中标注：

1. **`IEmergencyRescueService.createRescueReport()` 方法未在应用层接口表形式化定义**（第4轮首次识别，第5轮持续，本轮诊断"已知持续性问题"表列出）
2. **`RequestMediaSessionRequest` 缺 `secondaryAuthToken`（应用层 DTO）**（第4轮首次，第5轮持续，本轮"已知持续性问题"表列出）
3. **`TriggerManualRescueRequest` 缺 `secondaryAuthToken`（应用层 DTO）**（第4轮首次，第5轮持续，本轮"已知持续性问题"表列出）
4. **`AlertSummary` 缺 `gpsLocation`（应用层 DTO）**（第5轮首次，本轮"已知持续性问题"表列出）
5. **`QueryTrajectoryResponse` 缺 `dataConsistency`（应用层 DTO）**（第5轮首次，本轮"已知持续性问题"表列出）
6. **`StatusColor` 应用层引入 `ORANGE`，与领域层三值不一致**（第6轮首次，本轮"已知持续性问题"表列出）

### 新发现的问题（本轮首次识别）
- **严重**：`RequestMediaSessionResponse` 跨层 DTO 不一致（缺少 `sparkRTCRoomId` 和 `sparkRTCJoinToken`）——此为之前 7 轮未被识别的跨层不一致
- **一般**：OAuth2 排除说明缺失
- **一般**：`secondary-verify` 端点缺失
- **一般**：13 个 ArkTS 枚举类型缺独立 type 声明
- **轻微**：版本标识不一致

## 上一轮产出路径
/home/jasper/AIoT/redeliberations/202606291304_api_ood/a_v7_copy_from_v6.md

## 用户需求
/home/jasper/AIoT/redeliberations/202606291304_api_ood/requirement.md
