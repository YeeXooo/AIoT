# API/接口层 OOD 设计方案 质量审查报告（b_v2 / v1）

> 审查对象：`a_v2_copy_from_v1.md`（API/接口层 OOD 设计方案 v2）
> 审查轮次：第 2 轮迭代
> 审查视角：使用者视角（产出是否可直接投入使用、是否覆盖显式/隐式需求、边界与异常处理是否完备）

---

## 一、审查发现

### 问题 1：新增 API 端点未映射到应用层方法（严重）

**问题描述**：v2 修订中新增的 3 个 REST 端点在应用层 OOD（`docs/ood_application.md`）中无对应的方法签名，违反需求 §2 约束——"所有 API 端点应映射到应用层 OOD 中已定义的应用服务方法"。

具体缺失：

| API 端点 | 归属服务 | 应用层缺失方法 |
|---------|---------|-------------|
| `GET /api/v1/guardianship/{driverId}/permissions` | S3 | `IRemoteGuardianshipService` 无 `queryGuardianshipPermissions` 方法 |
| `POST /api/v1/sparkrtc/token` | S3 | `IRemoteGuardianshipService` 无 `issueSparkRTCToken` 方法 |
| `DELETE /api/v1/ota/upgrade-tasks/{taskId}` | S6 | `IOTAManagementService` 无 `cancelUpgradeTask` 方法 |

v2 修订说明虽提及"映射到应用层 IRemoteGuardianshipService 新增查询方法"，但应用层 OOD（`ood_application.md` a_v5/v1）至今未补充这些方法。这导致接口层与实现层契约断层：接口层开发者依据本文档编写调用代码后，应用层无法提供对应的实现入口。

**所在位置**：§1.3 端点表（行 128, 129）、§1.6 端点表（行 537）；对比 `docs/ood_application.md` §3.3、§3.6 方法契约表

**严重程度**：严重

**改进建议**：
1. 在应用层 OOD（`ood_application.md`）中为 S3 补充 `queryGuardianshipPermissions` 和 `issueSparkRTCToken` 两个方法契约（含输入/输出 DTO、事务标注、异常策略）
2. 在应用层 OOD 中为 S6 补充 `cancelUpgradeTask` 方法契约
3. 或在本 API OOD 中明确标注这三个端点对应应用层待新增方法的具体预签名（方法名 + 输入/输出 DTO 类型），使契约能向下闭包

---

### 问题 2：家属手动救援触发端点归属与需求分组不一致（中等）

**问题描述**：需求 `requirement.md:27` 将"家属手动救援触发"列入 **S5 EmergencyRescueService** 的覆盖范围（`SOS 确认、救援授权管理、家属手动救援触发`）。但本文档 §1.3 将该端点（`POST /api/v1/guardianship/manual-rescue`）归入 **S3 RemoteGuardianshipService**。

应用层 OOD（`ood_application.md` §3.3）在 S3 中定义了 `triggerManualRescue` 方法，而 S5 无此方法——API OOD 的设计选择与应用层一致，但与需求文本指定的服务归属不一致。若下游实现者以需求为权威依据、在 S5 下寻找该端点，将找不到对应实现入口。

**所在位置**：§1.3（S3 端点表，行 125）；对比 `requirement.md:27`、`docs/ood_application.md` §3.3 vs §3.5

**严重程度**：中等

**改进建议**：
1. 方案 A（推荐）：在本文档开篇或 S3/S5 分节处添加说明——"家属手动救援触发端点按设计与应用层 S3 对齐，与需求 §14 中 S5 的文字分组存在差异，此为设计层面基于职责内聚（家属主动操作归入监护服务）的归口调整"
2. 方案 B：将 `POST /api/v1/guardianship/manual-rescue` 从 §1.3 S3 移至 §1.5 S5 端点表，并在应用层 S5 中补充 `triggerManualRescue` 方法

---

### 问题 3：S5 queryRescueHistory 端点认证标注与其他 S5 端点及 §5.1 角色映射矛盾（中等）

**问题描述**：§1.5 S5 REST 端点表中，`confirmSOSReport`、`issueRescueToken`、`verifyRescueToken` 三个操作型端点的认证列标注为 `JWT (RESCUE)`（含角色限定），但 `queryRescueHistory` 仅标注 `JWT`（未限定角色）。然而 §5.1 角色→权限映射表明确将 S5 **全部端点**限定为 `RESCUE` 角色可访问（"S5 全部端点"）。

这意味着 §1.5 和 §5.1 对本端点的角色要求给出了不同信息——前者暗示所有持有 JWT 的角色均可访问，后者限定仅 RESCUE。若网关/权限校验实现仅以 §5.1 为准，则 §1.5 的标注会误导接口层调用者。

**所在位置**：§1.5 端点表（行 440，"JWT" vs "JWT (RESCUE)"）；对比 §5.1 角色→权限映射表（行 1643）

**严重程度**：中等

**改进建议**：将 §1.5 中 `queryRescueHistory` 端点的认证列统一标注为 `JWT (RESCUE)`，与同服务其他端点及 §5.1 保持一致

---

### 问题 4：SparkRTC Token 独立端点的消费者未阐明（一般）

**问题描述**：§1.3 S3 的 `POST /api/v1/sparkrtc/token` 端点独立于 WebSocket `request_media` 流程存在——后者在 `access_granted` 消息中已返回 `sparkRTCJoinToken`，而本端点另行提供获取 SparkRTC 入房 token 的 REST 通道。但文档未说明该端点的目标调用方：是家属 APP 在断线重连时直接调用？是车机端 HMI 获取 publisher token？还是内部服务调用？在 v2 修订说明中仅提及"明确归属 S3 RemoteGuardianshipService"，未阐明其消费场景。

此外，§4.1 家属 APP REST 调用列表和 §4.3 HMI 本地查询接口表中均未列出此端点，进一步加强了消费者不明确的印象。

**所在位置**：§1.3 端点表（行 129）、§3.2 SparkRTC 房间管理流程（行 1294-1326）；对比 §4.1 家属 APP REST 调用列表（行 1371-1378）

**严重程度**：一般

**改进建议**：
1. 在 §1.3 该端点旁补充说明其调用场景（如"车机端 HMI 在收到 `cmd/media/join/down` MQTT 指令后通过此端点获取 publisher token" 或"内部服务调用，外部调用方不直接使用"）
2. 若该端点面向家属 APP，应在 §4.1 REST 调用列表中补入

---

### 问题 5：TriggerRollbackResponse 示例未覆盖 ROLLING_BACK 中间状态（一般）

**问题描述**：§1.6 中 `TriggerRollbackResponse` 的 JSON 示例仅展示 `"newStatus": "ROLLED_BACK"`。但应用层 OOD（`ood_application.md` §4.6）定义 `TriggerRollbackResponse.newStatus` 字段类型为 `OTAUpgradeStatus`，其枚举值包含 `ROLLING_BACK`（回滚进行中）和 `ROLLED_BACK`（回滚已完成）两个状态。此外 OTA 回滚期间的 CAN 干预恢复策略（§3.6 链路 D）也基于这两个状态的区分。本文档的响应示例缺失 `ROLLING_BACK` 状态，可能导致接口层调用者误以为回滚请求同步返回终态。

**所在位置**：§1.6 `TriggerRollbackResponse` JSON 示例（行 597-602）；对比 `docs/ood_application.md` §4.6 `TriggerRollbackResponse.newStatus` 类型定义

**严重程度**：一般

**改进建议**：在 `TriggerRollbackResponse` 字段说明中补充 `newStatus` 的可能取值——`"ROLLING_BACK"` / `"ROLLED_BACK"`，并说明 `ROLLING_BACK` 表示回滚指令已下发但尚未完成，前端应轮询 `queryUpgradeProgress` 确认最终状态

---

### 问题 6：S4 report download 端点响应未指定 Content-Type（一般）

**问题描述**：§1.4 报告文件下载端点（`GET /api/v1/fleet/reports/{reportId}/download?format=pdf|xlsx`）的响应体描述为"二进制文件流"，但未指定 `Content-Type` 响应头。query 参数 `format` 的不同取值应返回不同的 Content-Type（`application/pdf` 或 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`），前端需据此判断文件类型并选择处理方式（浏览器内预览 vs 触发下载）。文档此处缺失信息将迫使前端开发者猜测或查阅后端实现代码。

**所在位置**：§1.4 端点表（行 309）

**严重程度**：一般

**改进建议**：在端点表"响应体"列中补充 Content-Type 说明：`format=pdf → Content-Type: application/pdf`；`format=xlsx → Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`；附加 `Content-Disposition: attachment; filename="report-{reportId}.{format}"`

---

### 问题 7：MQTT 主题模板语法不一致（轻微）

**问题描述**：§2.1 主题路由总表中，`{deviceId}/sensor/${sensorType}/up` 使用了 `${sensorType}` 语法（带 `$` 前缀），而表中其余 24 个主题模板均使用 `{variable}` 语法（如 `{deviceId}`、`{accountId}`、`{fleetId}` 等）。同一张表内混合两种变量占位语法，虽不影响语义理解，但造成格式不统一，在代码生成或自动校验场景下可能引发问题。

**所在位置**：§2.1 主题路由总表第一行（行 649）

**严重程度**：轻微

**改进建议**：将 `${sensorType}` 统一为 `{sensorType}`，与表中其他变量占位语法一致

---

### 问题 8：DELETE 请求的成功响应码约定不统一（轻微）

**问题描述**：S3 的 `DELETE /media-session/{sessionHandle}` 返回 `204 No Content`（无响应体），而 S6 的 `DELETE /upgrade-tasks/{taskId}` 返回 `200 OK` 并携带 `CancelUpgradeTaskResponse` 响应体。两者均为 DELETE 操作，但成功响应码约定不一致——前端若对所有 DELETE 请求统一按 204 处理（不读响应体），则 S6 的取消响应将被忽略；反之若按 200 处理，则 S3 的 204 需特殊处理。在设计层面未给出 DELETE 响应码的统一约定。

**所在位置**：§1.3（行 123，DELETE 204）、§1.6（行 537，DELETE 200 + CancelUpgradeTaskResponse）

**严重程度**：轻微

**改进建议**：
1. 在文档开篇或 REST 设计约定部分统一 DELETE 响应码策略（如"删除操作默认返回 204；若需向调用方返回被删除资源的最终状态信息，可返回 200 + 响应体"）
2. 或对 S6 统一为 204（取消结果通过后续 `queryUpgradeProgress` 查询确认），对 S3 统一为 200 + 空响应体（两种方向均可，要点是统一）

---

## 二、审查总结

本版（v2）已成功修复第 1 轮质询指出的 2 个问题（S3 权限查询端点、MQTT Payload 覆盖），整体结构完整，五个需求部分均有覆盖。本次审查发现 8 个问题：2 个严重（API 端点与应用层方法契约断层）、2 个中等（归属/标注不一致）、4 个一般/轻微（信息缺失与格式不统一）。其中最关键的 3 个映射缺失点（问题 1）直接阻碍文档的"可直接投入使用"目标——接口层调用方可依本文档编写代码，但应用层尚未定义对应入口方法，造成跨层契约不闭合。

---

## 修订说明（v1）

> 首轮审查，无前序质询需要回应。
