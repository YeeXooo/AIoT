# 质量审查报告（b_v1_diag_v2）

## 审查概况

- **产出**：前端 DTO 模型（13 个 TypeScript 文件）
- **审查者**：质量审查 agent
- **审查方法**：逐文件对照 `docs/ood_interface.md` 接口契约，检查字段完整性、类型正确性、跨文件引用、代码质量

## 审查结果汇总

- **严重问题**：1（v2 新增：心跳检测时序逻辑缺陷）
- **一般问题**：1（重复定义）
- **轻微问题**：4（可选类型、dead code、空定时器、缺少 barrel 导出）
- **无显著问题**：下文声明已过审查的文件

### 已通过审查的模块

以下模块经逐字段校验，接口契约、字段完整性、类型引用均与 `docs/ood_interface.md` 一致：

- **types.ts** — 15 个类型别名 + GeoPoint + WindowStatusEntry，枚举值完整
- **driver.ts** — GetDriverRiskStatusResponse / QueryAlertHistoryResponse 字段完整
- **auth.ts** — Login/RefreshToken/SecondaryVerify 三组 DTO，字段类型完整
- **fleet.ts** — 全部 11 个接口类型，字段覆盖完整
- **websocket.ts** — WebSocket 上行 6 类、下行 10 类消息全部覆盖
- **GuardianshipApi.ts** — 8 个端点方法签名与 REST 契约一致
- **AuthApi.ts** — 3 个认证端点正确
- **DriverApi.ts** — 2 个驾驶员端点正确
- **FleetApi.ts** — 8 个车队管理端点正确

---

## 一、任务完备性

**[通过]** 12 个 REST 端点和 6+10 个 WebSocket 消息类型的 DTO 定义及 API 客户端实现完整覆盖。

**[通过]** 认证链路完整：login → access token → secondaryVerify(获取 secondaryAuthToken) → 高敏操作；refresh(轮换策略) 均覆盖。

**[问题-轻微]** API 客户端层缺少 index.ts barrel 导出文件。当前各 API 类需逐个 import，缺少统一导出入口。

---

## 二、质量达标性

### 问题 1（严重）— v2 新增

- **问题**：`GuardianshipWebSocket` 心跳检测逻辑存在时序缺陷，正常连接会在约 40 秒后被错误断开
- **位置**：`code/frontend/api/GuardianshipWebSocket.ts:253-271`
- **表现**：`startPingTimer()` 中 `setInterval`（每 30s）和 `pongTimeout`（10s）各自独立递增 `pingMissedCount`，而 `handlePing()` 仅重置 `pongTimeout` 但不重置 `pingMissedCount`。时序推演：
  ```
  t=0:   connect → interval(+1 at t=30), pongTimeout(+1 at t=10)
  t=10:  pongTimeout fires → pingMissedCount=1
  t=30:  PING arrives → resetPongTimeout(+1 at t=40)
         interval fires → pingMissedCount=2
  t=40:  pongTimeout fires → pingMissedCount=3 → ws.close()
  ```
  即使服务端完美地每 30s 发送 PING，连接也在 40s 后被错误断开。
- **根因**：正确的实现应是「收到 PING 时重置 `pingMissedCount = 0`」，仅靠一个间隔定时器检测心跳丢失
- **改进建议**：在 `handlePing()` 末尾添加 `this.pingMissedCount = 0`，移除冗余的 `pongTimeout` 逻辑，简化心跳检测为单一间隔定时器

### 问题 2（一般）

- **问题**：`WindowStatusEntry` 接口重复定义
- **位置**：`code/frontend/model/types.ts:128-134` 和 `code/frontend/model/guardianship.ts:84-90`
- **影响**：两份定义分处两处，长期维护有不同步风险
- **改进建议**：统一至 `types.ts` 一处定义，`guardianship.ts` 通过 import 引用

### 问题 3（轻微）

- **问题**：`SecondaryVerifyRequest.otp` 字段始终可选，未体现 OTP/BIOMETRIC 模式下的条件必需关系
- **位置**：`code/frontend/model/auth.ts:67`
- **改进建议**：使用 discriminated union 或 JSDoc 明确条件必填语义

### 问题 4（轻微）

- **问题**：`TokenType` 类型导出但未被使用
- **位置**：`code/frontend/model/types.ts:118-119`
- **改进建议**：移除该导出，或让 `ApiClient.ts:103` 的 `'Bearer'` 字面量引用此类型

### 问题 5（轻微）

- **问题**：`FleetWebSocket.startPingTimer()` 创建了空操作定时器
- **位置**：`code/frontend/api/FleetWebSocket.ts:160-165`
- **影响**：每 30s 执行空函数体，`clearPingTimer()` 虽可在断开时清理，但活跃连接期间每次 `connect()` 都会创建新实例
- **改进建议**：在 `startPingTimer()` 开头调用 `clearPingTimer()` 确保只存在一个实例，或直接移除

---

## 三、正确性

**[问题-严重]** `GuardianshipWebSocket` 心跳检测逻辑存在功能性缺陷（见问题 1），这是正确性维度中发现的实质性问题。

**[通过]** 其余所有 REST 端点 HTTP 方法、路径模板、WebSocket 消息 type 字符串、枚举值与 `docs/ood_interface.md` 一致。

---

## 修改要求

### 严重问题：GuardianshipWebSocket 心跳检测逻辑缺陷

- **问题**：双定时器各自递增 `pingMissedCount` + `handlePing` 不重置计数器，导致正常连接约 40s 后断开
- **原因**：时序逻辑未正确实现 OOD 文档中的心跳协议
- **建议方向**：在 `handlePing()` 末尾添加 `this.pingMissedCount = 0`，移除冗余的 `pongTimeout` 机制

### 一般问题：WindowStatusEntry 重复定义

- **问题**：`types.ts` 和 `guardianship.ts` 中重复定义
- **建议方向**：统一至 `types.ts`

### 建议处理：FleetWebSocket 空定时器

- **问题**：`startPingTimer()` 创建空操作定时器
- **建议方向**：移除或添加 `clearPingTimer()` 防护

---

## 综合结论

产出整体质量良好，接口契约完整覆盖 OOD 文档要求。v2 新增发现 1 个严重问题（心跳检测时序缺陷），修正了 v1 在该维度的遗漏。建议在下一轮组件 A 迭代中重点修复心跳检测逻辑，同时处理重复定义等次要问题。

## 修订说明（v2）

| 质询意见 | 回应 |
|---------|------|
| 诊断报告遗漏了 GuardianshipWebSocket 心跳检测逻辑缺陷 | **接受并修订** — v2 报告在「正确性」维度新增问题 1（严重），详细描述时序缺陷及改进建议。已验证该问题确实存在（时序推演路径：t=0→t=10→t=30→t=40→断开） |
| 诊断报告将 FleetWebSocket 空定时器标记为轻微但未评估其影响范围 | **保留原级别** — 该定时器确实执行空函数体，但 `clearPingTimer()` 在断开时能回收资源，实际泄漏有限，维持「轻微」评级 |
