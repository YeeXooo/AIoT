# 再审议判定报告（v11）

## 判定结果

RETRY

## 判定理由

组件B诊断报告（b_v11_diag_v1.md）识别出7个问题，其中包含：
- **中等**（2个）：问题1（JWT refresh token 端点缺失）、问题2（S5/S6 错误响应列表缺少基础错误码）
- **一般**（3个）：问题3（LoginRequest JSON 字段设计冗余）、问题4（错误响应体结构未定义）、问题5（S4 绩效预警订阅缺少取消订阅端点）
- **轻微**（2个）：问题6（令牌桶容量参数未定义）、问题7（S2 404 语义不一致）

组件B质询报告（b_v11_challenge_v1.md）结论为 LOCATED，确认七个问题均与产出内容一致，证据链完整。质询对问题2的对比基线（S3 引用）提出不严谨之处但不否定核心判断，并建议将 S3 的基础错误码缺失一并纳入。实际轮次1（最大12），属于审查被确认后提前终止。

根据判定标准，审查报告包含中等和一般等级的问题，触发 RETRY。

## 需要解决的问题

- **问题描述**：JWT refresh token 端点缺失。`LoginResponse` 包含 `refreshToken` 字段，§5.1 步骤5 明确描述刷新流程，但 §1.7 未定义 `POST /api/v1/auth/refresh` 端点，JWT 刷新链路在 API 契约层面断裂
- **所在位置**：§1.7 Auth 端点表（行 713–716），对比 §5.1 步骤 5（行 1882）
- **严重程度**：中等
- **改进建议**：在 §1.7 新增 `POST /api/v1/auth/refresh` 端点，定义 `RefreshTokenRequest` 和 `RefreshTokenResponse`，参照现有 `POST /api/v1/auth/login` 的端点定义风格

- **问题描述**：S5 和 S6 错误响应列表缺少基础错误码（400、404、503）。S5 缺少 `400`（查询参数无效）和 `404`（driverId/vehicleId 不存在），S6 缺少 `404`（vehicleId 不存在）、`503`（IoTDA 通道不可达）
- **所在位置**：§1.5 错误响应（行 582–584）、§1.6 错误响应（行 703–706）
- **严重程度**：中等
- **改进建议**：在 §1.5 补充 `400` 和 `404`；在 §1.6 补充 `404` 和 `503`。同时建议一并检查 S3 是否同样缺少 400/404 基础错误码

- **问题描述**：LoginRequest JSON 示例同时包含 `credential`/`secret` 和 `phone`/`smsCode` 两组字段，形成语义冗余，且同时传入非 null 值行为未定义
- **所在位置**：§1.7 LoginRequest JSON 示例（行 720–727）
- **严重程度**：一般
- **改进建议**：二选一：(a) 移除 `phone`/`smsCode`，仅保留 `credential`+`secret`+`authMethod`；(b) PASSWORD 用 `credential`+`secret`，SMS_CODE 用 `phone`+`smsCode`

- **问题描述**：整份 API OOD 未定义 REST API 的标准错误响应体格式。应用层 OOD §6.1 已定义完整 `AppError` 枚举，但 API OOD 未承接为 REST 错误响应契约
- **所在位置**：§1 各节错误响应块
- **严重程度**：一般
- **改进建议**：在 §一 总述段落后新增"REST 错误响应体约定"块，建议格式为 `{ "errorCode": "...", "message": "...", "requestId": "..." }`

- **问题描述**：S4 仅有 `POST` 订阅端点，无对应 `DELETE` 取消订阅端点，与 S3 的对称设计不一致
- **所在位置**：§1.4 S4 端点表（行 350）
- **严重程度**：一般
- **改进建议**：新增 `DELETE /api/v1/fleet/performance-warning-subscription/{subscriptionId}` 端点或通过 PUT 更新订阅状态
