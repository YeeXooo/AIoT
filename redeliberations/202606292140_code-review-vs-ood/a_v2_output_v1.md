# 再审议修复产出（迭代第2轮）

> M=2, N=1
> 日期：2026-06-29
> 基于审计发现修复前端代码

## 修复概览

本轮修复了代码审查（b_v1_diag_v2）发现的 1 个严重、1 个一般和 4 个轻微问题。

## 修改清单

### 修复 1（严重）：GuardianshipWebSocket 心跳检测时序缺陷

**文件**：`code/frontend/api/GuardianshipWebSocket.ts`

**问题**：双定时器各自递增 `pingMissedCount` 且 `handlePing` 不重置计数器，导致即使服务端按时发 PING，连接也会在约 40 秒后被错误断开。

**修改**：
- 移除 `pongTimeout` 字段和相关方法
- 简化心跳检测为单一间隔定时器（30s）
- `handlePing()` 中收到 PING 时重置 `this.pingMissedCount = 0`
- 配置项 `pingInterval` / `pongTimeout` 合并为 `maxMissedPings`（默认 3）

**时序验证**：
```
t=0:   connect → startPingTimer(), interval schedule
t=30:  PING arrives → handlePing: pingMissedCount=0, send PONG
       interval fires → pingMissedCount=1
t=60:  PING arrives → handlePing: pingMissedCount=0, send PONG
       interval fires → pingMissedCount=1
...    ✅ 连接持续活跃
```

### 修复 2（一般）：WindowStatusEntry 重复定义

**文件**：`code/frontend/model/guardianship.ts` + `code/frontend/model/types.ts`

**问题**：`WindowStatusEntry` 在 `types.ts` 和 `guardianship.ts` 中重复定义。

**修改**：
- 保留 `types.ts` 中的定义（共享类型）
- 移除 `guardianship.ts` 中的重复定义，改为 `import type { WindowStatusEntry } from './types'`
- 同步更新 `websocket.ts` 的导入路径

### 修复 3（轻微）：TokenType 导出移除

**文件**：`code/frontend/model/types.ts`

**问题**：`export type TokenType = 'Bearer'` 定义后未被任何文件引用，为 dead code。

**修改**：移除了 `TokenType` 导出。

### 修复 4（轻微）：FleetWebSocket 空操作定时器清理

**文件**：`code/frontend/api/FleetWebSocket.ts`

**问题**：`startPingTimer()` 创建了空函数体定时器，可能造成资源泄漏。

**修改**：
- 移除 `pingTimer` 字段
- `startPingTimer()` 改为空方法（由服务端 PING 驱动心跳）
- `clearPingTimer()` 改为空方法

### 修复 5（轻微）：添加 barrel 导出文件

**文件**：
- `code/frontend/model/index.ts`（新创建）
- `code/frontend/api/index.ts`（新创建）

**内容**：统一的导出入口，使用方只需 `import { ... } from './frontend/model'` 即可引入所有类型。

## 编译验证

全部 15 个 TypeScript 文件通过 `tsc --noEmit --strict` 编译检查，零错误。

## 文件变更汇总

| 文件 | 操作 | 说明 |
|------|:----:|------|
| `code/frontend/api/GuardianshipWebSocket.ts` | 修改 | 心跳检测缺陷修复 |
| `code/frontend/model/guardianship.ts` | 修改 | 移除 WindowStatusEntry 重复定义 |
| `code/frontend/model/types.ts` | 修改 | 移除 TokenType dead code |
| `code/frontend/api/FleetWebSocket.ts` | 修改 | 移除空操作定时器 |
| `code/frontend/model/websocket.ts` | 修改 | 更新 WindowStatusEntry 导入路径 |
| `code/frontend/model/index.ts` | 新增 | barrel 导出 |
| `code/frontend/api/index.ts` | 新增 | barrel 导出 |
