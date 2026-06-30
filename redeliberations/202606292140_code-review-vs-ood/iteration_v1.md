# 再审议判定报告（v1）

## 判定结果

**RETRY**

## 判定理由

组件B的诊断报告（b_v1_diag_v2）最终确认了以下问题：

1. **严重问题 × 1**：GuardianshipWebSocket 心跳检测逻辑存在时序缺陷，双定时器各自递增 `pingMissedCount` 且 `handlePing` 不重置计数器，导致即使服务端按时发送 PING，连接也会在约 40 秒后被错误断开

2. **一般问题 × 1**：WindowStatusEntry 接口在 types.ts 和 guardianship.ts 中重复定义

3. **轻微问题 × 4**：SecondaryVerifyRequest.otp 条件可选、TokenType dead code、FleetWebSocket 空定时器、缺少 barrel 导出

根据判定标准：审查报告包含「严重」或「一般」等级的问题 → 需重新运行组件A进行修复。

B 内部循环 2 轮后 LOCATED（质询确认），诊断结论已被质询方有效验证。

## 需要解决的问题

- **问题描述**：GuardianshipWebSocket 心跳检测双定时器时序缺陷
- **所在位置**：`code/frontend/api/GuardianshipWebSocket.ts:253-271`
- **严重程度**：严重
- **改进建议**：在 `handlePing()` 中添加 `this.pingMissedCount = 0`，移除冗余的 `pongTimeout` 逻辑，简化心跳检测为单一间隔定时器

---

- **问题描述**：WindowStatusEntry 在 types.ts 和 guardianship.ts 中重复定义
- **所在位置**：`code/frontend/model/types.ts:128-134` 和 `code/frontend/model/guardianship.ts:84-90`
- **严重程度**：一般
- **改进建议**：统一至 types.ts，guardianship.ts 通过 import 引用

---

- **问题描述**：SecondaryVerifyRequest.otp 始终可选，未体现条件必需关系
- **所在位置**：`code/frontend/model/auth.ts:67`
- **严重程度**：轻微
- **改进建议**：使用 discriminated union 或 JSDoc 明确条件必填语义

---

- **问题描述**：TokenType 类型导出但未被使用
- **所在位置**：`code/frontend/model/types.ts:118-119`
- **严重程度**：轻微
- **改进建议**：移除该导出，或让 ApiClient.ts 引用

---

- **问题描述**：FleetWebSocket.startPingTimer() 创建空操作定时器
- **所在位置**：`code/frontend/api/FleetWebSocket.ts:160-165`
- **严重程度**：轻微
- **改进建议**：添加 `clearPingTimer()` 防护或直接移除

---

- **问题描述**：API 客户端层缺少 barrel 导出文件
- **所在位置**：`code/frontend/api/`
- **严重程度**：轻微
- **改进建议**：创建 index.ts 统一导出所有 API 类
