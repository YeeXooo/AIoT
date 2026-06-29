# 再审议判定报告（v12）

## 判定结果

RETRY

## 判定理由

组件B诊断报告（b_v12_diag_v1.md）识别出 8 个质量问题，其中 3 个标记为"中等"（问题 1：MQTT 干预 Topic 无对应 API 端点；问题 2：sessionToken/sparkRTCJoinToken 双字段语义重叠；问题 3：§4.1 家属 APP REST API 调用清单缺少认证端点）、4 个标记为"一般"（问题 4：JWT scope/角色权限关系未定义；问题 5：缺乏数据生命周期/保留策略；问题 6：S4 QueryTrajectoryResponse 缺少时间范围和分页约束；问题 7：§4.1 ArkTS WebSocket 消息模型缺少 token_renewed 等类型定义）、1 个标记为"轻微"（问题 8：§5.6 隐私边界校验表缺少家属告警推送中的 GPS 权限校验规则）。

组件B质询报告（b_v12_challenge_v1.md）结论为 LOCATED（实际轮次 1，最大轮次 12，提前终止），确认审查结论有效，8 个问题均被证实存在。

按照判定标准，"中等"级别问题对应"一般"等级（影响产出完整度或准确性的非致命问题），且诊断报告含多个"一般"级别问题，不满足 PASS 条件（PASS 要求不含严重或一般等级问题，或所有问题均为轻微等级）。因此判定为 RETRY。

## 需要解决的问题

- **问题描述**：MQTT 干预指令 Topic `{deviceId}/cmd/intervention/down` 的 Payload 说明引用"车队管理员远程鸣笛"用例，但 §1.2 S2 InterventionService 和 §1.4 S4 FleetManagementService 均无对应 REST 端点支持车队管理员发起干预指令下发，导致 Topic 触发入口缺失
- **所在位置**：§2.1 主题路由总表 `{deviceId}/cmd/intervention/down` 行（Payload 说明列）
- **严重程度**：一般
- **改进建议**：二选一：(a) 在 S2 或 S4 补充对应 REST 端点（如 `POST /api/v1/fleet/{fleetId}/interventions`）；(b) 移除 Payload 说明中的"如车队管理员远程鸣笛等"，改为仅描述由 S1 风险判定驱动的干预指令下发场景

- **问题描述**：`RequestMediaSessionResponse` 同时包含 `sessionToken` 和 `sparkRTCJoinToken`，两字段语义高度重叠（均为家属端接入 SparkRTC 的凭证），文档未说明区别和使用场景，API 使用者无法确认该用哪个字段调用 SparkRTC SDK join 方法
- **所在位置**：§1.3 RequestMediaSessionResponse JSON 示例、§3.1 access_granted 下行消息、§4.1 ArkTS RequestMediaSessionResp
- **严重程度**：一般
- **改进建议**：明确区分两字段用途：`sessionToken` 为会话级授权凭证（用于后续媒体会话管理操作），`sparkRTCJoinToken` 为实际传入 SparkRTC SDK 的入房 Token；在 §4.1 ArkTS 代码示例中明确展示使用 `sparkRTCJoinToken` 调用 join 方法

- **问题描述**：§4.1 家属 APP REST API 调用清单缺少 `POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`、`POST /api/v1/auth/secondary-verify` 三个认证端点，家属 APP 开发者仅阅读 §4.1 无法获知完整接口调用序列
- **所在位置**：§4.1 REST API 调用列表，对比 §1.7 Auth 端点表
- **严重程度**：一般
- **改进建议**：在 §4.1 REST API 调用列表中补充三个 Auth 端点行，标注请求/响应模型引用；或在列表前添加说明块交叉引用 §1.7 认证端点

- **问题描述**：§5.1 JWT Payload 示例包含 `scope` 字段，但角色→权限映射表仅定义了三个角色到端点范围的映射，未说明 scope 的角色分配规则、scope 与角色的优先级，API 实现者无法确定授权判断应基于 `role` 还是 `scope`
- **所在位置**：§5.1 JWT Token 结构，对比 §5.1 角色→权限映射表
- **严重程度**：一般
- **改进建议**：二选一：(a) 补充 scope 与角色映射表，说明应用服务入口校验规则；(b) 若仅用角色做权限判断，移除 JWT Payload 中的 scope 字段

- **问题描述**：系统处理驾驶员生理体征、GPS 轨迹、车内音视频等高度敏感数据，但告警历史、行程评分、生理体征快照、GPS 轨迹、家属监护关系等关键数据的保留周期、归档策略和到期清理规则均未定义
- **所在位置**：全文（缺失）
- **严重程度**：一般
- **改进建议**：在 §5.6 之后新增"数据生命周期"小节，覆盖告警历史、行程记录及评分、生理体征快照、车辆遥测数据、家属监护关系、救援记录等类别，每类数据明确保留时长及到期处理方式

- **问题描述**：S4 `GET /api/v1/fleet/{fleetId}/trajectory` 端点未声明最大查询时间跨度、分页 size 上限等约束，与 S1 queryAlertHistory（max 100）和 S6 OTA（批量上限 100 辆）的约束水平不一致
- **所在位置**：§1.4 端点表 `车辆轨迹查询` 行及查询参数说明
- **严重程度**：一般
- **改进建议**：补充约束：最大时间跨度（如 ≤30 天）、size 默认值和上限（如默认 100，最大 500）、超阈值时的截断处理策略

- **问题描述**：§4.1 ArkTS 数据模型定义遗漏了 `TokenRenewedMessage`、`SubscribeStatusAckMessage`、`RescueTriggeredMessage` 三个 WebSocket 消息的 TypeScript 接口定义（§3.1 下行消息表已定义但 §4.1 未纳入）
- **所在位置**：§4.1 ArkTS 数据模型定义，对比 §3.1 下行消息表
- **严重程度**：一般
- **改进建议**：在 §4.1 补充三个接口的 TypeScript 定义，包含对应字段

- **问题描述**：§5.6 隐私校验表覆盖了家属主动查询场景的 GPS 权限校验（pull），但 MQTT 推送（push）Topic 中的 GPS 位置信息缺少对应的推送阶段脱敏/过滤规则
- **所在位置**：§5.6 隐私边界安全校验点表，对比 §2.2 家属告警推送及 DriverStatusSnapshot
- **严重程度**：轻微
- **改进建议**：在 §5.6 表中新增推送场景 GPS 权限校验规则：推送时校验家属当前监护权限有效性，无权限时 GPS 字段填 null
