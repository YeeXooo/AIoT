# 对新增前端代码进行代码审查 — 对照 OOD 接口设计文档

## 任务概述

对最近新增的前端 DTO 模型和 API 客户端代码进行全面审查，验证其是否严格符合 `docs/ood_interface.md` 中的接口契约定义。

## 待审查的产出

上一轮审议式执行（deliberations/202606292130_frontend-dto-api/）产出以下文件：

### 数据模型层（code/frontend/model/）

| 文件 | 依据 |
|------|------|
| `code/frontend/model/types.ts` | ood_interface.md §4.1 类型别名 |
| `code/frontend/model/auth.ts` | ood_interface.md §1.7 Auth 认证服务 |
| `code/frontend/model/driver.ts` | ood_interface.md §1.1 RiskMonitoringService |
| `code/frontend/model/guardianship.ts` | ood_interface.md §1.3 RemoteGuardianshipService |
| `code/frontend/model/fleet.ts` | ood_interface.md §1.4 FleetManagementService |
| `code/frontend/model/websocket.ts` | ood_interface.md §3.1 / §4.2 |

### API 客户端层（code/frontend/api/）

| 文件 | 依据 |
|------|------|
| `code/frontend/api/ApiClient.ts` | 通用 HTTP 客户端 |
| `code/frontend/api/AuthApi.ts` | ood_interface.md §1.7 |
| `code/frontend/api/DriverApi.ts` | ood_interface.md §1.1 |
| `code/frontend/api/GuardianshipApi.ts` | ood_interface.md §1.3 |
| `code/frontend/api/FleetApi.ts` | ood_interface.md §1.4 |
| `code/frontend/api/GuardianshipWebSocket.ts` | ood_interface.md §3.1 |
| `code/frontend/api/FleetWebSocket.ts` | ood_interface.md §4.2 |

## 审查要求

### 1. 接口契约一致性
- 每个 DTO 的字段名、类型、可选性是否与 ood_interface.md 中的 JSON 示例 / ArkTS 代码定义一致
- 枚举值是否完整覆盖文档中的全部取值
- API 客户端的路径、HTTP 方法、参数是否与文档一致

### 2. 跨文件引用正确性
- import/export 关系是否正确
- 类型是否在正确的文件中定义（共享类型 vs 域特定类型）

### 3. 代码质量
- TypeScript 类型安全（无 any 类型、正确的泛型使用）
- 错误处理是否合理
- API 客户端超时、重试等机制是否完善

### 4. 完整性检查
- 是否遗漏了文档中定义的任何接口或类型
- WebSocket 消息类型是否完整覆盖上行/下行
- 认证流程（login → token → refresh → secondaryVerify）是否完整

## 参考文档

- `docs/ood_interface.md` — 完整的 API/接口层 OOD 设计文档
- `deliberations/202606292130_frontend-dto-api/output_v1.md` — 上一轮审议产出摘要
