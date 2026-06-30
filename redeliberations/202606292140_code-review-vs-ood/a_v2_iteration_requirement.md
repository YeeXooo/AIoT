根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

### 严重问题：GuardianshipWebSocket 心跳检测逻辑存在时序缺陷
- **位置**：`code/frontend/api/GuardianshipWebSocket.ts:253-271`
- **描述**：`startPingTimer()` 中 `setInterval`（30s）和 `pongTimeout`（10s）各自独立递增 `pingMissedCount`，而 `handlePing()` 仅重置 `pongTimeout` 但不重置 `pingMissedCount`。时序推演显示即使服务端完美地每 30s 发送 PING，连接也会在约 40 秒后被错误断开。
  ```
  t=0:   connect → interval(+1 at t=30), pongTimeout(+1 at t=10)
  t=10:  pongTimeout fires → pingMissedCount=1
  t=30:  PING arrives → resetPongTimeout(+1 at t=40)
         interval fires → pingMissedCount=2
  t=40:  pongTimeout fires → pingMissedCount=3 → ws.close()
  ```
- **改进建议**：在 `handlePing()` 末尾添加 `this.pingMissedCount = 0`，移除冗余的 `pongTimeout` 逻辑，简化心跳检测为单一间隔定时器

### 一般问题：WindowStatusEntry 接口重复定义
- **位置**：`code/frontend/model/types.ts:128-134` 和 `code/frontend/model/guardianship.ts:84-90`
- **描述**：两处定义结构完全一致，后续修改若只更新一处会导致跨文件类型不一致
- **改进建议**：统一至 `types.ts` 一处定义，`guardianship.ts` 通过 `import type { WindowStatusEntry } from './types'` 引用

### 轻微问题汇总
1. **SecondaryVerifyRequest.otp 始终可选**（auth.ts:67）— 未体现 OTP/BIOMETRIC 模式下的条件必需关系。建议使用 discriminated union 或 JSDoc 明确条件必填语义
2. **TokenType 类型导出但未被使用**（types.ts:118-119）— dead code。建议移除或让 ApiClient.ts 的 `'Bearer'` 字面量引用此类型
3. **FleetWebSocket.startPingTimer() 空操作定时器**（FleetWebSocket.ts:160-165）— 创建空函数体定时器。建议添加 `clearPingTimer()` 防护或直接移除
4. **API 客户端层缺少 barrel 导出文件**（api/）— 缺少统一导出入口。建议创建 index.ts

## 历史迭代回顾

本轮为首轮迭代后的首次修复，无历史迭代反馈记录。

## 上一轮产出路径
/home/jasper/AIoT/redeliberations/202606292140_code-review-vs-ood/a_v1_imported.md

## 用户需求
/home/jasper/AIoT/redeliberations/202606292140_code-review-vs-ood/requirement.md
