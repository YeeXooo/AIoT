根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

审查结论选自 b_v12_diag_v1.md，质询结果为 LOCATED（全部 8 个问题均确认成立）：

1. **（中等）MQTT 干预指令 Topic 存在无对应 API 端点的用例描述**：§2.1 `{deviceId}/cmd/intervention/down` 的 Payload 说明引用"如车队管理员远程鸣笛等"，暗示存在车队管理员发起干预指令下发的用例，但 S2 仅提供只读端点（查询干预状态/历史），S4 的端点均为看板/报告/订阅，均无任何端点支持车队管理员发起干预指令下发。建议：(a) 在 S2 或 S4 补充对应 REST 端点；(b) 移除该 Payload 说明中的"如车队管理员远程鸣笛等"，改为仅描述由 S1 风险判定驱动的干预指令下发场景。

2. **（中等）RequestMediaSessionResponse 中 sessionToken 与 sparkRTCJoinToken 双字段语义重叠**：两字段均描述为家属端接入 SparkRTC 房间的凭证，但文档未说明两者的区别和使用场景，API 使用者无法确认该用哪个字段调用 SparkRTC SDK 的 join 方法。建议明确区分：sessionToken 为会话级授权凭证（用于后续媒体会话管理操作），sparkRTCJoinToken 为实际传入 SparkRTC SDK 的入房 Token。在 ArkTS 代码示例中明确展示 `sparkRTCClient.joinRoom(msg.sparkRTCRoomId, msg.sparkRTCJoinToken)`。

3. **（中等）§4.1 家属 APP REST API 调用清单缺少认证端点**：需求 `requirement.md:38` 要求覆盖家属 APP 调用的全部后端接口清单，但 §4.1 当前仅列出 10 个 S3 端点及 1 个 S1 端点，缺少 `POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`、`POST /api/v1/auth/secondary-verify` 三个认证端点。建议在 §4.1 补充这三个端点行，或添加交叉引用说明块指向 §1.7。

4. **（一般）§5.1 JWT scope 字段与角色权限模型的关系未定义**：JWT Payload 示例包含 `"scope": ["read:risk-status", "write:window-control"]`，但角色→权限映射表仅定义了 FAMILY/MANAGER/RESCUE 到端点范围的映射，未说明 scope 的角色分配规则、scope 与角色的优先级。建议：(a) 补充 scope 与角色映射表；(b) 或移除 JWT Payload 中的 scope 字段。

5. **（一般）缺乏数据生命周期/保留策略**：系统处理高度敏感数据（BR-04 隐私边界约束），但告警历史、行程评分、生理体征快照、GPS 轨迹、家属监护关系等关键数据的保留周期、归档策略和到期清理规则均未定义。建议在 §5.6 之后新增"数据生命周期"小节，至少覆盖告警历史、行程记录及评分、生理体征快照、车辆遥测数据、家属监护关系、救援记录六类数据的最小保留策略。

6. **（一般）S4 QueryTrajectoryResponse 缺少时间范围约束和分页限制**：`GET /api/v1/fleet/{fleetId}/trajectory` 端点未声明最大查询时间跨度、`size` 的上限约束、大数据量时的截断处理策略。相比之下 S1 明确标注 `size` 最大 100，S6 OTA 约束批量上限 100 辆。建议补充：(a) startTime 到 endTime 的最大跨度（如 ≤30 天）；(b) size 默认值和上限（如默认 100，最大 500）；(c) 超过阈值的截断策略。

7. **（一般）§4.1 ArkTS WebSocket 消息模型缺少 token_renewed 等多个类型定义**：§3.1 下行消息表定义了 `token_renewed`、`subscribe_status_ack`、`rescue_triggered` 消息类型，但 §4.1 ArkTS 数据模型定义区遗漏了 `TokenRenewedMessage`、`SubscribeStatusAckMessage` 和 `RescueTriggeredMessage` 的 TypeScript 接口定义。建议补充这三个接口。

8. **（轻微）§5.6 隐私边界校验表缺少家属告警推送中的 GPS 权限校验规则**：校验表覆盖了主动查询（pull）场景（家属查询驾驶员位置），但 MQTT `family/{accountId}/alert/push` 和 `family/{accountId}/status/push` 推送同样包含 GPS 位置信息，未见对应的推送阶段 GPS 脱敏/过滤规则。建议在 §5.6 表中新增一行，说明推送时也需校验家属当前监护权限有效性。

## 历史迭代回顾

### 已解决的问题

以下问题在历史迭代（第 1–11 轮）中被识别并已通过对应轮次的修订解决，当前 v12 诊断报告中不再出现：

- 缺失家属权限查询 REST 端点（第 1 轮）→ v2 修复
- MQTT Payload JSON Schema 覆盖不完整（第 1 轮）→ v2 修复
- 新增端点未映射到应用层方法（第 2 轮）→ v5 修复
- S5 queryRescueHistory 认证标注矛盾（第 2 轮）→ v3 修复
- MQTT 主题模板语法不一致（第 2 轮）→ v3 修复
- DELETE 响应码约定不统一（第 2 轮）→ v3 修复
- S3→S5 救援记录 ID 体系断裂（第 3 轮）→ v4 修复
- MQTT Topic cmd/media/join/down 定义缺失（第 3 轮）→ v4 修复
- S3 请求体 familyAccountId 横向越权风险（第 3 轮）→ v4 修复
- createRescueReport 方法未形式化定义（第 4/5/7/9 轮）→ v10 修复
- ArkTS AlertType/RiskLevel 枚举不完整（第 5 轮）→ v7 修复
- S5/S6 端点表缺失查询参数列（第 5 轮）→ v7 修复
- S2 缺失错误响应文档（第 5 轮）→ v7 修复
- LIFE_DETECTION_PROLONGED 缺乏跨层定义（第 6 轮）→ v8 修复
- OAuth2 排除理由未说明（第 7 轮）→ v9 修复
- secondary-verify 端点未在 REST API 中定义（第 7 轮）→ v9 修复
- ArkTS 13 个枚举类型缺失独立 type 声明（第 7 轮）→ v9 修复
- Markdown 格式错误（第 8 轮）→ v9 修复
- SparkRTCRole publisher 为设计死值（第 8 轮）→ v9 修复
- SubscribePerformanceWarningRequest 示例缺失（第 8 轮）→ v9 修复
- rescue_triggered status 字段枚举值缺失（第 8 轮）→ v9 修复
- S1/S5 错误响应 401 缺失（第 8 轮）→ v9 修复
- 文档结构错误——多个独立修订块（第 9 轮）→ v10 修复
- TriggerRollbackResponse.newStatus 与 currentStage 枚举不一致（第 9 轮）→ v10 修复
- vehicle/state/up 缺少车窗状态字段（第 9 轮）→ v10 修复
- MQTT SafetyAlertEvent GPS 命名不一致（第 9 轮）→ v10 修复
- JWT login 端点缺失（第 10 轮）→ v11 修复
- GPS 字段命名未统一（第 10 轮）→ v11 修复
- S3 家属权限管理缺少主动管理入口——已补充设计理由（第 10 轮）→ v11 修复
- §3.2 豁免触发条件表述不精确（第 10 轮）→ v11 修复
- JWT refresh token 端点缺失（第 11 轮）→ v12 修复
- S5/S6 错误响应列表缺少基础错误码（第 11 轮）→ v12 修复
- LoginRequest JSON 字段设计冗余（第 11 轮）→ v12 修复
- REST 错误响应体结构未定义（第 11 轮）→ v12 修复
- S4 缺失取消订阅端点（第 11 轮）→ v12 修复
- 令牌桶容量参数未定义（第 11 轮）→ v12 修复
- S2 404 语义与其他服务不一致（第 11 轮）→ v12 修复

### 持续存在的问题

以下问题在多轮迭代中反复出现，但均属于跨文档 DTO 不一致问题，根源在 `docs/ood_application.md`（非本轮 API OOD 迭代范围），API OOD 侧接口契约定义已完整，每轮均标记为"保留（无需修改本文件）"：

- `secondaryAuthToken` 字段在应用层 DTO（`RequestMediaSessionRequest`、`TriggerManualRescueRequest`）中缺失（自 v4 起标记）
- `sparkRTCRoomId`/`sparkRTCJoinToken` 在应用层 `RequestMediaSessionResponse` DTO 中缺失（自 v7 起标记）
- `gpsLocation` 字段在应用层 `AlertSummary` DTO 中缺失（自 v5 起标记）
- `dataConsistency` 字段在应用层 `QueryTrajectoryResponse` DTO 中缺失（自 v5 起标记）
- `StatusColor` ORANGE 值在应用层 OOD 中多余（自 v6 起标记）——API OOD 已为三值 GREEN/YELLOW/RED

以上问题不影响本轮 API OOD 迭代，需在应用层 OOD 的独立迭代中解决。

### 新发现的问题

本轮 v12 诊断报告新识别 8 个问题（即上方的"当前审查结果"全部 8 项），均未在历史迭代中出现过，为本轮迭代的重点修复对象。

## 上一轮产出路径

/home/jasper/AIoT/redeliberations/202606291304_api_ood/a_v12_copy_from_v11.md

## 用户需求

/home/jasper/AIoT/redeliberations/202606291304_api_ood/requirement.md
