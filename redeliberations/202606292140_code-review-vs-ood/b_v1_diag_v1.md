# 质量审查报告（b_v1_diag_v1）

## 审查概况

- **产出**：前端 DTO 模型（13 个 TypeScript 文件）
- **审查者**：质量审查 agent
- **审查方法**：逐文件对照 `docs/ood_interface.md` 接口契约，检查字段完整性、类型正确性、跨文件引用、代码质量

## 审查结果汇总

- **严重问题**：0
- **一般问题**：1
- **轻微问题**：4
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
- **GuardianshipWebSocket.ts** — 信令协议消息类型完整覆盖

---

## 一、任务完备性

**[通过]** 12 个 REST 端点和 6+10 个 WebSocket 消息类型的 DTO 定义及 API 客户端实现完整覆盖。

**[通过]** 认证链路完整：login → access token → secondaryVerify(获取 secondaryAuthToken) → 高敏操作；refresh(轮换策略) 均覆盖。

**[问题-轻微]** API 客户端层缺少 index.ts barrel 导出文件。当前各 API 类需逐个 import，缺少统一导出入口。使用方需要写 5 行 import 语句才能引入全部 API 模块。

---

## 二、质量达标性

### 问题 1（一般）

- **问题**：`WindowStatusEntry` 接口重复定义
- **位置**：`code/frontend/model/types.ts:128-134` 和 `code/frontend/model/guardianship.ts:84-90`
- **影响**：两份定义结构完全一致，但分处两处。后续修改时若只更新一处，会导致跨文件类型不一致。TypeScript 模块隔离不会导致运行时错误，但长期维护有不同步风险。
- **改进建议**：统一至 `types.ts` 一处定义（因其为跨域共享类型），`guardianship.ts` 通过 `import type { WindowStatusEntry } from './types'` 引用。

### 问题 2（轻微）

- **问题**：`SecondaryVerifyRequest.otp` 字段始终可选，未体现 OTP/BIOMETRIC 模式下的条件必需关系
- **位置**：`code/frontend/model/auth.ts:67`
- **影响**：method=OTP 时 `otp` 应当为必填，method=BIOMETRIC 时 `otp` 可填生物凭证。当前类型定义为 `otp?: string`（始终可选），未约束此条件关系。类型安全弱化。
- **改进建议**：使用 discriminated union 区分两种验证方式，或添加 JSDoc 明确条件必填语义。

### 问题 3（轻微）

- **问题**：`TokenType` 类型导出但未被使用
- **位置**：`code/frontend/model/types.ts:118-119`
- **影响**：`export type TokenType = 'Bearer'` 定义后，没有任何文件 import 它。属于 dead code。
- **改进建议**：移除该导出，或在实际用到 `'Bearer'` 字面量的地方（`ApiClient.ts:103`）引用它。
  - 当前 `ApiClient.ts:103` 写死 `Bearer ` 字符串前缀，若改为引用 `TokenType` 可提升一致性。

### 问题 4（轻微）

- **问题**：`FleetWebSocket.startPingTimer()` 创建了空操作定时器
- **位置**：`code/frontend/api/FleetWebSocket.ts:160-165`
- **影响**：方法创建了一个 `setInterval` 每 30s 执行空函数体，既不清理旧定时器，也不处理超时逻辑。同步续 `connect()` 时重复调用 `startPingTimer()` 会创建多个空定时器实例——虽然 `clearPingTimer()` 可在断开时清理，但活跃连接期间每次 `connect()` 都会创建一个新定时器而旧定时器未清理（旧定时器在 `handleClose()` 时才会被 `clearPingTimer()` 清理）。这是一个资源泄漏。
- **改进建议**：要么移除该定时器（由服务端 PING 驱动，客户端无需独立定时器），要么在 `startPingTimer()` 开头调用 `clearPingTimer()` 确保只存在一个实例。

---

## 三、正确性

**[通过]** 所有 REST 端点 HTTP 方法、路径模板与 `docs/ood_interface.md` 一致。

**[通过]** 所有 WebSocket 消息 type 字符串（上行 6 种、下行 10 种）与设计文档一致。

**[通过]** 枚举值与文档一致，未发现事实错误或逻辑矛盾。

---

## 修改要求（一般问题）

### 问题 1：WindowStatusEntry 重复定义

- **问题**：`WindowStatusEntry` 在 `types.ts` 和 `guardianship.ts` 中重复定义
- **原因**：两份定义若后续不同步，会导致跨文件类型不一致
- **建议方向**：统一至 `types.ts`，`guardianship.ts` 导入引用

### 问题 2（建议处理）：FleetWebSocket 空定时器

- **问题**：`startPingTimer()` 创建空操作定时器，可能造成资源泄漏
- **原因**：定时器在 `connect()` 时创建，但不会立即清理前一个实例
- **建议方向**：移除空定时器或添加 `clearPingTimer()` 防护

---

## 综合结论

产出整体质量良好，接口契约完整覆盖 OOD 文档要求。发现 1 个一般问题（WindowStatusEntry 重复定义）和 4 个轻微问题，不影响功能正确性和可直接集成使用。建议在下轮迭代中修复重复定义问题。

