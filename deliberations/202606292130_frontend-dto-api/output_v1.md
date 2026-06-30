# 前端 DTO 模型 + API 客户端 — 执行产出（v1）

> 轮次：N=1
> 日期：2026-06-29
> 依据：`docs/ood_interface.md` §4.1、§4.2、§1.7

## 产出物概述

根据 `todo.md` 中角色 D 的分工和 `docs/ood_interface.md` 的接口契约，完成了家属 APP 前端 DTO 模型定义和 API 客户端封装。

## 产出文件清单

### 数据模型层（`code/frontend/model/`）

| 文件 | 内容 | 行数 |
|------|------|:----:|
| `types.ts` | 基础类型别名（15 个 string literal union type） | 119 |
| `auth.ts` | 认证服务 DTO（Login/Refresh/SecondaryVerify 共 6 个接口） | 75 |
| `driver.ts` | 驾驶员风险监测 DTO（RiskStatus/AlertHistory + 基础类型） | 46 |
| `guardianship.ts` | 远程监护服务 DTO（MediaSession/Rescue/Window/Permissions/SparkRTC 共 11 个接口） | 133 |
| `fleet.ts` | 车队管理 DTO（Fatigue/OfflineVehicles/Trajectory/Report/Subscription 共 11 个接口） | 167 |
| `websocket.ts` | WebSocket 消息模型（家属 APP + 车队大屏 共 20 个接口） | 184 |

### API 客户端层（`code/frontend/api/`）

| 文件 | 内容 | 行数 |
|------|------|:----:|
| `ApiClient.ts` | 基础 HTTP 客户端（fetch 封装、JWT 携带、统一错误处理、超时控制） | 199 |
| `AuthApi.ts` | 认证 API（login/refresh/secondaryVerify 3 个方法） | 46 |
| `DriverApi.ts` | 驾驶员 API（getRiskStatus/queryAlertHistory 2 个方法） | 40 |
| `GuardianshipApi.ts` | 远程监护 API（8 个方法：mediaSession/endMedia/notification/rescue/window/queryWindow/permissions/sparkrtcToken） | 91 |
| `FleetApi.ts` | 车队管理 API（8 个方法：fatigue/offline/trajectory/highRisk/generateReport/download/subscribe/unsubscribe） | 144 |
| `GuardianshipWebSocket.ts` | 家属 APP WebSocket 客户端（完整信令协议：连接/心跳/订阅/重连/离线补推） | 300 |
| `FleetWebSocket.ts` | 车队大屏 WebSocket 客户端（L3 告警/绩效预警推送） | 185 |

## 设计说明

### 1. 模型文件组织

- **`types.ts`** 作为基础类型入口，包含全部 string literal union type（15 个），被其他所有模型文件引用
- 按业务域拆分文件（auth/driver/guardianship/fleet/websocket），与后端服务划分一致
- 严格遵循 `docs/ood_interface.md` 中定义的接口字段名、可选性、枚举值

### 2. API 客户端组织

- **`ApiClient.ts`** — 基础层，封装 `fetch` API，自动管理 JWT `Authorization` 头，提供统一 `ApiResponse<T>` 包装和错误解析（`errorCode`/`message`/`requestId`）
- 服务模块按业务域拆分，每个 API 类对应一个后端服务（S1/S3/S4/Auth）
- 方法签名使用强类型参数，提供类型安全的调用体验
- WebSocket 客户端独立封装，支持心跳检测（30s PING/10s PONG 超时）、指数退避重连（1s→2s→4s→8s→16s，最多 5 次）、订阅管理

### 3. 命名约定

- DTO 类型使用缩写后缀 `Req`/`Resp`，与 `docs/ood_interface.md` §4.1 约定一致（等价于后端 `Request`/`Response`）
- 枚举类型使用全大写蛇形命名（如 `'L3_CRITICAL'`），与后端枚举值一致

### 4. 未覆盖内容（后续轮次/待办）

- S5 EmergencyRescueService 的 API 客户端（角色限定 RESCUE，前端家属 APP 不直接调用）
- S2 InterventionService 的 API 客户端（仅边缘侧进程内调用，不对外暴露 REST）
- S6 OTAManagementService 的 API 客户端（角色限定 MANAGER，前端当前未覆盖）
- 单元测试
- index.ts barrel 导出文件

## OUTPUT_WRITTEN:/home/jasper/AIoT/code/frontend