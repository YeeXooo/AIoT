根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

以下问题来自组件B诊断报告（b_v1_diag_v1.md），质询结果确认为 LOCATED，共 6 个问题：

1. **问题 1（中等严重）— S3 缺失家属权限查询 REST 端点**
   - 问题描述：需求要求 S3 RemoteGuardianshipService 覆盖"家属权限查询/管理"，但产出 §1.3 REST 端点表中无独立的权限查询端点（如 `GET /api/v1/guardianship/{driverId}/permissions`）。家属仅能通过 WebSocket 被动获知权限状态，缺乏主动查询能力。
   - 所在位置：§1.3 REST 端点表
   - 改进建议：在 S3 REST 端点表中补充 `GET /api/v1/guardianship/{driverId}/permissions` 端点，返回家属对指定驾驶员的当前授权状态（是否已授权、授权范围、授予时间、有效期），映射到应用层 IRemoteGuardianshipService 需新增相应查询方法。

2. **问题 2（中等严重）— MQTT Payload JSON Schema 覆盖不完整**
   - 问题描述：产出 §2.1 定义了约 20 个 Topic 路由，但 §2.2 仅提供 4 个核心 Payload 的 JSON Schema。缺失的 Payload 包括：车窗控制指令、OTA 指令、指令 Ack、传感器故障、心跳、各推送消息等。
   - 所在位置：§2.2
   - 改进建议：为 §2.1 路由表中每个 Topic 提供至少字段级的 Payload 格式定义（核心 Topic 提供完整 Schema，次要用表格）。至少补充：车窗控制指令、OTA 指令、指令 Ack、传感器故障、心跳、各推送消息的 Payload 结构。

3. **问题 3（轻微）— SparkRTC Token 端点未归属到应用服务**
   - 问题描述：产出 §3.2 定义了 `POST /api/v1/sparkrtc/token` 端点，但该端点未出现在 §1.3 S3 REST 端点表中，也未出现在任何其他服务的端点表中。
   - 所在位置：§3.2
   - 改进建议：将 SparkRTC Token 签发端点纳入 §1.3 S3 REST 端点表，明确其归属 S3 RemoteGuardianshipService。

4. **问题 4（轻微）— QueryAlertHistoryResponse 缺失 GPS 字段**
   - 问题描述：产出 §2.2 SafetyAlertEvent JSON Schema 明确包含 `gps: { latitude, longitude }` 字段，但 §1.1 QueryAlertHistoryResponse 缺失 GPS 坐标。
   - 所在位置：§1.1 QueryAlertHistoryResponse
   - 改进建议：在 QueryAlertHistoryResponse 的 AlertSummary 中补充 `gpsLocation` 可选字段（含 latitude、longitude），与 MQTT SafetyAlertEvent 的 gps 字段保持一致。

5. **问题 5（轻微）— OTA 升级管理缺少取消未启动任务的 REST 端点**
   - 问题描述：产出 §1.6 包含创建、查询进度、回滚、查询历史四个端点，但对于已创建但尚未下发（PENDING 状态）的升级任务，缺少取消操作的端点。
   - 所在位置：§1.6
   - 改进建议：在 S6 REST 端点中补充 `DELETE /api/v1/ota/upgrade-tasks/{taskId}` 端点，允许取消 PENDING 或 TRANSMITTING 阶段的升级任务（终态任务拒绝取消）。

6. **问题 6（轻微）— 安全隐私校验点表缺少家属权限查询的隐私保护规则**
   - 问题描述：产出 §5.6 隐私边界安全校验点表缺少对"家属主动查询自身监护权限"的隐私校验规则。
   - 所在位置：§5.6
   - 改进建议：在 §5.6 表中新增一行：`家属查询监护权限 | S3 权限查询入口 | 仅返回与请求方 accountId 关联的监护权限；拒绝查询非本人持有的权限关系`。此建议与问题 1 联动。

## 历史迭代回顾

| 状态 | 问题 | 说明 |
|------|------|------|
| **持续存在** | S3 缺失家属权限查询 REST 端点 | 第 1 轮迭代反馈中已指出（iteration_history.md 问题 1），本轮诊断报告问题 1 再次确认。需重点解决。 |
| **持续存在** | MQTT Payload JSON Schema 覆盖不完整 | 第 1 轮迭代反馈中已指出（iteration_history.md 问题 2），本轮诊断报告问题 2 再次确认。需重点解决。 |
| **新发现** | SparkRTC Token 端点未归属到应用服务 | 本轮诊断报告首次发现（问题 3） |
| **新发现** | QueryAlertHistoryResponse 缺失 GPS 字段 | 本轮诊断报告首次发现（问题 4） |
| **新发现** | OTA 升级管理缺少取消未启动任务的 REST 端点 | 本轮诊断报告首次发现（问题 5） |
| **新发现** | 安全隐私校验点表缺少家属权限查询的隐私保护规则 | 本轮诊断报告首次发现（问题 6） |

## 上一轮产出路径

/home/jasper/AIoT/redeliberations/202606291304_api_ood/a_v1_output_v1.md

## 用户需求

/home/jasper/AIoT/redeliberations/202606291304_api_ood/requirement.md
