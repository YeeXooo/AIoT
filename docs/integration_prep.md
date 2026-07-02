# 前后端联调准备文档

> 目标：将前端（HarmonyOS ArkTS）与后端（Java Spring Boot）成功连通，完成全链路端到端联调。

---

## 一、系统架构速览

```
┌─────────────────────────────────────────────────────────────┐
│  HarmonyOS 前端 (ArkTS)                                     │
│  ┌──────────┬──────────┬──────────┬──────────┐             │
│  │ Dashboard│ Guardians│  Fleet   │ Profile  │             │
│  │   Tab    │ hip Tab  │   Tab    │   Tab    │             │
│  └────┬─────┴────┬─────┴────┬─────┴────┬─────┘             │
│       │          │          │          │                    │
│  ┌────┴──────────┴──────────┴──────────┴────┐               │
│  │        ViewModels + SessionStore          │               │
│  └────┬──────────┬──────────┬───────────────┘               │
│       │          │          │                                │
│  ┌────┴────┐ ┌───┴────┐ ┌──┴──────────┐                    │
│  │ ApiClient│ │Grd WS  │ │ Fleet WS    │                    │
│  │ (HTTP)  │ │Client  │ │ Client      │                    │
│  └────┬────┘ └───┬────┘ └──┬──────────┘                    │
│       │          │          │                                │
└───────┼──────────┼──────────┼────────────────────────────────┘
        │ HTTPS    │ WSS      │ WSS
        ▼          ▼          ▼
┌─────────────────────────────────────────────────────────────┐
│  Spring Boot 后端 :8080                                     │
│  ┌──────────┬──────────┬──────────┬─────────────────┐      │
│  │ REST     │Auth Guard│ Fleet WS │ Guardianship WS │      │
│  │Controller│ + JWT   │ Handler  │    Handler       │      │
│  └────┬─────┴────┬─────┴────┬─────┴────┬────────────┘      │
│       │          │          │          │                    │
│  ┌────┴──────────┴──────────┴──────────┴────┐               │
│  │      Application Services (6 services)    │               │
│  └────┬──────────┬──────────┬───────────────┘               │
│       │          │          │                                │
│  ┌────┴────┐ ┌───┴────┐ ┌──┴──────────┐                    │
│  │   JPA   │ │ Event   ││  Domain     │                    │
│  │Entities │ │  Bus    ││  Services   │                    │
│  └────┬────┘ └─────────┘ └─────────────┘                    │
└───────┼──────────────────────────────────────────────────────┘
        │
   ┌────┴────┐
   │ 金仓 PG │  (dev) / H2 (ci)
   └─────────┘
```

**通信通道**：
| 通道 | 协议 | 端口 | 用途 |
|------|------|:--:|------|
| REST API | HTTPS | 8080 | 登录、查询、操作 |
| 监护 WebSocket | WSS | 8080 | 家属端实时状态推送 |
| 车队 WebSocket | WSS | 8080 | 车队大屏告警推送 |

---

## 二、后端启动检查清单

### 2.1 环境依赖

| 依赖 | 版本/说明 | 状态 |
|------|----------|:--:|
| Java | JDK 17 | 需确认 |
| Maven | 3.8+ | 需确认 |
| 金仓 PostgreSQL | Docker 部署（端口 54321）| 见 `docs/kingbase_setup.md` |
| H2 (CI) | 内嵌，无需额外安装 | 默认即可 |

### 2.2 数据库准备

**开发环境（dev profile）**：
```bash
# 1. 启动金仓 PostgreSQL Docker（参照 docs/kingbase_setup.md）
docker run -d --name kingbase \
  -p 54321:54321 \
  -e DB_USER=aiot -e DB_PASSWORD=aiot123 -e DB_NAME=aiot \
  registry.cn-beijing.aliyuncs.com/aiot/kingbase:latest

# 2. 检查连接
psql -h localhost -p 54321 -U aiot -d aiot
```

**CI 环境（ci profile）**：无需外部数据库，H2 内存库 + Flyway 自动迁移 + 种子数据。

### 2.3 启动后端

```bash
cd /home/jasper/AIoT/code/server

# dev 模式（连接金仓）
mvn spring-boot:run

# ci 模式（H2 内存库，适合本地开发/联调）
mvn spring-boot:run -Dspring-boot.run.profiles=ci
```

**启动后验证**：
```bash
# 健康检查（如果配置了 actuator）
curl -k https://localhost:8080/actuator/health

# 登录测试
curl -k -X POST https://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"authMethod":"PASSWORD","credential":"family001","secret":"pass123"}'
```

### 2.4 种子数据

Flyway V1–V4 迁移包含以下种子数据（见后端资源文件）：

| 实体 | 数据 |
|------|------|
| 驾驶员 | 5 人（张明 d1、李强 d2、王磊 d3、赵鹏 d4、孙伟 d5） |
| 车辆 | 5 辆（京A·88562、京B·33217、京C·77501、京D·55990、京E·12008） |
| 行程 | 7 条 |
| 告警 | 5 条 |
| 账户 | 3 个（family001/FAMILY、manager001/MANAGER、rescue001/RESCUE） |

**测试账号密码均为 `pass123`**（具体以 Flyway 迁移脚本为准）。

### 2.5 关键配置项

| 配置 | 位置 | 默认值 |
|------|------|------|
| 服务端口 | `application.yml` → `server.port` | 8080 |
| JWT 密钥 | KMS / 应用配置 | 见 `infra/security` |
| WebSocket 心跳间隔 | `application.yml` → `aiot.websocket.heartbeat-interval-sec` | 30s |
| 家属 WS 端点 | `application.yml` → `aiot.websocket.guardianship-endpoint` | `/ws/guardianship` |
| 车队 WS 端点 | `application.yml` → `aiot.websocket.fleet-endpoint` | `/ws/fleet` |
| DMS 感知模式 | `application.yml` → `aiot.perception.dms.mode` | `mock` |
| MQTT 开关 | `application.yml` → `aiot.mqtt.enabled` | `false` |

---

## 三、前端配置检查清单

### 3.1 三个需要修改的 URL

前端 API/WebSocket 基础 URL 目前为默认值，联调前需根据后端实际地址修改：

| 配置 | 文件 | 位置 | 当前默认值 |
|------|------|------|------|
| HTTP 基础 URL | `api/ApiClient.ts` | `constructor` L62 | `/api/v1`（相对路径） |
| 监护 WS 基础 URL | `api/GuardianshipWebSocket.ts` | `constructor` L72 | `wss://api.example.com/ws/guardianship` |
| 车队 WS 基础 URL | `api/FleetWebSocket.ts` | `constructor` L62 | `wss://api.example.com/ws/fleet` |

**联调时按以下规则修改**：

```
后端地址 = 开发机 IP:8080（如 192.168.1.100:8080）

HTTP  baseUrl = "https://192.168.1.100:8080/api/v1"
WS    baseUrl = "wss://192.168.1.100:8080/ws/guardianship"
WS    baseUrl = "wss://192.168.1.100:8080/ws/fleet"
```

> **注意**：HTTP 和 WS 的 baseUrl 构建方式不同：
> - `ApiClient`：只存 scheme+host+path，`buildUrl` 方法自动拼接。设为 `https://host:8080/api/v1`。
> - `WebSocket`：`connect()` 在 baseUrl 后追加 `?token=...`，因此设为完整的 `wss://host:8080/ws/guardianship`（不带 token 参数）。

### 3.2 TLS 证书

开发环境后端使用自签名证书时，前端需信任该证书：

1. **HarmonyOS DevEco Studio 模拟器**：在 `build-profile.json5` 中配置允许自签名证书（或通过应用级网络安全配置）
2. **真机调试**：将 CA 证书安装到设备可信凭据存储

临时方案（仅调试）：在 `ApiClient` 中设置 `usingProtocol: http.HttpProtocol.HTTP1_1` 且不使用 HTTPS，改为 HTTP + 非 TLS WebSocket（`ws://`）。

### 3.3 构建与运行

```bash
# 在 DevEco Studio 中打开项目
# File → Open → /home/jasper/AIoT/code/frontend

# 或命令行构建（需安装 HarmonyOS SDK + hvigor）
cd /home/jasper/AIoT/code/frontend
hvigorw assembleHap
```

---

## 四、端点映射总表

### 4.1 前端 API 调用 → 后端控制器

| 前端调用 | HTTP | 后端路径 | 后端控制器 |
|---------|:--:|------|------|
| `authApi.login(body)` | POST | `/api/v1/auth/login` | `AccountController` |
| `authApi.refresh(body)` | POST | `/api/v1/auth/refresh` | `AccountController` |
| `authApi.secondaryVerify(body)` | POST | `/api/v1/auth/secondary-verify` | `AccountController` |
| `driverApi.getRiskStatus(id)` | GET | `/api/v1/drivers/{id}/risk-status` | `DriverController` |
| `driverApi.queryAlertHistory(id)` | GET | `/api/v1/drivers/{id}/alerts` | `SafetyController` |
| `guardianshipApi.requestMediaSession(b)` | POST | `/api/v1/guardianship/media-session` | `GuardianshipController` |
| `guardianshipApi.endMediaSession(h)` | DELETE | `/api/v1/guardianship/media-session/{h}` | `GuardianshipController` |
| `guardianshipApi.updateNotificationPreference(b)` | PUT | `/api/v1/guardianship/notification-preference` | `GuardianshipController` |
| `guardianshipApi.triggerManualRescue(b)` | POST | `/api/v1/guardianship/manual-rescue` | `GuardianshipController` |
| `guardianshipApi.controlVehicleWindow(b)` | POST | `/api/v1/guardianship/window-control` | `GuardianshipController` |
| `guardianshipApi.queryWindowStatus(vid)` | GET | `/api/v1/vehicles/{vid}/windows` | `GuardianshipController` |
| `guardianshipApi.queryGuardianshipPermissions(did)` | GET | `/api/v1/guardianship/{did}/permissions` | `GuardianshipController` |
| `guardianshipApi.issueSparkRTCToken(b)` | POST | `/api/v1/sparkrtc/token` | `GuardianshipController` |
| `fleetApi.getFatigueDistribution(fid)` | GET | `/api/v1/fleet/{fid}/fatigue-distribution` | `ProjectionController` |
| `fleetApi.getOfflineVehicles(fid)` | GET | `/api/v1/fleet/{fid}/offline-vehicles` | `ProjectionController` |
| `fleetApi.queryTrajectory(fid)` | GET | `/api/v1/fleet/{fid}/trajectory` | `ProjectionController` |
| `fleetApi.drillDownHighRisk(fid)` | GET | `/api/v1/fleet/{fid}/high-risk-drivers` | `ProjectionController` |
| `fleetApi.generateReport(b)` | POST | `/api/v1/fleet/reports` | `ProjectionController` |
| `fleetApi.downloadReport(rid,f)` | GET | `/api/v1/fleet/reports/{rid}/download` | `StorageController` |
| `fleetApi.subscribePerformanceWarning(b)` | POST | `/api/v1/fleet/performance-warning-subscription` | `ProjectionController` |
| `fleetApi.unsubscribePerformanceWarning(sid)` | DELETE | `/api/v1/fleet/performance-warning-subscription/{sid}` | `ProjectionController` |

### 4.2 前端 WebSocket → 后端 Handler

| 连接 | 前端 WS 客户端 | 后端 Handler | 端点 |
|------|------|------|------|
| 家属端 | `GuardianshipWebSocket` | `GuardianshipWebSocketHandler` | `/ws/guardianship` |
| 车队端 | `FleetWebSocket` | `FleetWebSocketHandler` | `/ws/fleet` |

---

## 五、认证与登录流程

```
  前端                   后端                 SessionStore / ApiClient
  ────                   ────                 ──────────────────────

  LoginPage
    │
    ├─ (1) POST /api/v1/auth/login ──────► AccountController
    │      {authMethod, credential, secret}      │
    │                                      验证凭证 + 签发JWT
    │     ◄──── LoginResponse ─────────────      │
    │      {accessToken, refreshToken,           │
    │       accountId, role, expiresIn}           │
    │                                             │
    ├─ (2) sessionStore.saveSession(token,id,role)
    │      ├── 写入 Preferences 持久化           │
    │      └── apiClient.setAccessToken(token) ──► 后续请求自动带 Bearer
    │                                             │
    ├─ (3) router.replaceUrl('pages/MainPage')
    │
  MainPage / ViewModels
    │
    ├─ (4) 所有 API 调用自动携带 Authorization: Bearer <token>
    │
    ├─ (5) WS 连接同样带 token：
    │      wsManager.connect()
    │        └── apiClient.getAccessToken()
    │             └── new WebSocket('wss://.../ws/guardianship?token=<JWT>')
    │
    └─ (6) Token 即将过期 → authApi.refresh({refreshToken})
           └── 更新 apiClient + Preferences
```

### 联调验证步骤

```bash
# 1. 登录获取 token
TOKEN=$(curl -k -s -X POST https://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"authMethod":"PASSWORD","credential":"family001","secret":"pass123"}' \
  | jq -r '.accessToken')

# 2. 用 token 调用受保护端点
curl -k -s https://localhost:8080/api/v1/drivers/d1/risk-status \
  -H "Authorization: Bearer $TOKEN" | jq .

# 3. 预期响应结构
# {
#   "hasActiveTrip": true/false,
#   "activeAlerts": [...],
#   "derivedStatusColor": "GREEN"|"YELLOW"|"RED"
# }
```

---

## 六、WebSocket 联调

### 6.1 家属 WebSocket 握手

```
客户端 → wss://host:8080/ws/guardianship?token=<JWT>
         ↓
后端校验 JWT (签名+过期+role=FAMILY)
         ↓
通过 → 升级为 WebSocket → 下发 {type:"connection_established", payload:{connectionId,accountId}}
失败 → 返回 401
```

### 6.2 用 wscat 测试 WS

```bash
# 安装 wscat
npm install -g wscat

# 连接（注：自签名证书需 -n 跳过验证）
wscat -n -c "wss://localhost:8080/ws/guardianship?token=$TOKEN"

# 发送订阅
> {"type":"subscribe_status","payload":{"driverId":"d1"}}

# 预期收到
< {"type":"subscribe_status_ack","payload":{"subscriptionId":"...","initialSnapshot":{...}}}
< {"type":"driver_status_snapshot","payload":{...}}   ← 1Hz 持续推送
< {"type":"ping","payload":{"serverTime":"2026-..."}} ← 30s 间隔
```

### 6.3 车队 WebSocket 握手

```bash
# 用管理员账户的 token
ADMIN_TOKEN=$(curl -k -s -X POST https://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"authMethod":"PASSWORD","credential":"manager001","secret":"pass123"}' \
  | jq -r '.accessToken')

wscat -n -c "wss://localhost:8080/ws/fleet?token=$ADMIN_TOKEN"

# 预期收到
< {"type":"ping","payload":{"serverTime":"2026-..."}}
# 有 L3 告警时：
< {"type":"l3_alert","payload":{"fleetId":"...","driverId":"...",...}}
```

---

## 七、分功能联调步骤

### 7.1 登录（SessionStore + ApiClient）

**数据流**：
```
LoginPage.ets → doLogin()
  → authApi.login(loginReq)                      // POST /api/v1/auth/login
  → sessionStore.saveSession(token, id, role)    // 持久化 + 注入 apiClient
  → router.replaceUrl('pages/MainPage')          // 跳转主页
```

**检查点**：
- [ ] 用 `family001` / `pass123` 登录成功，跳转主页
- [ ] 用错误密码登录，显示错误提示"登录失败，请检查账号密码"
- [ ] 重启应用 → `SessionStore.restore()` 恢复 token → 直接进主页（无须重新登录）
- [ ] 退出登录 → token 清除 → 回到登录页

### 7.2 监测 Tab（Dashboard）

**数据流**：
```
DashboardTab.ets → aboutToAppear()
  ├── dashboardVM.load()                         // REST 查询
  │     ├── driverApi.getRiskStatus('d1')       // GET /drivers/{id}/risk-status
  │     └── driverApi.queryAlertHistory('d1')   // GET /drivers/{id}/alerts
  │
  └── wsManager.connect() + subscribeDriverStatus('d1')  // WebSocket 实时推送
        └── onDriverStatusSnapshot → 更新 heartRate/spo2/speed/emotionIndex
```

**检查点**：
- [ ] 页面加载后显示驾驶员状态色（绿/黄/红）
- [ ] 告警历史列表正确展示（类型、时间、风险等级）
- [ ] 体征数据区（心率/血氧/情绪/车速）显示初始值
- [ ] WebSocket 连接建立后，体征数据随推送实时更新

### 7.3 监护 Tab（Guardianship）

**数据流**：
```
GuardianshipTab.ets → 选择司机
  ├── loadPerms(driverId)
  │     └── guardianshipApi.queryGuardianshipPermissions(driverId)
  │           → 启用/禁用各操作按钮
  │
  └── 点击操作按钮
        ├── MEDIA_CALL → requestMediaSession() → RTC joinRoom
        ├── RESCUE     → triggerManualRescue()
        ├── WINDOW     → queryWindowStatus()
        └── NOTIFY     → updateNotificationPreference()
```

**检查点**：
- [ ] 选择司机后查询权限 → 根据权限启用/禁用对应按钮
- [ ] 点击"对讲"→ 调用 API 建立媒体会话 → 显示成功/失败 toast
- [ ] 点击"救援"→ 触发手动救援 → 返回 rescueRequestId
- [ ] 点击"车窗"→ 查询车窗状态 → 显示状态信息
- [ ] 点击"通知"→ 更新通知偏好 → 确认成功

### 7.4 车队 Tab（Fleet）

**数据流**：
```
FleetTab.ets → aboutToAppear()
  ├── fleetVM.load()
  │     ├── fleetApi.getFatigueDistribution('f1')
  │     ├── fleetApi.getOfflineVehicles('f1')
  │     └── fleetApi.drillDownHighRisk('f1')
  │
  └── wsManager → onL3Alert / onPerformanceWarning (toast 通知)

  点击"生成报告"
  └── fleetApi.generateReport(body) → 显示 reportId
        └── fleetApi.downloadReport(reportId, 'pdf')
```

**检查点**：
- [ ] 概览区显示总车辆数、脱线数、高危数
- [ ] 疲劳分布柱状图正确渲染（L1/L2/L3 占比）
- [ ] 脱线车辆列表显示车牌、驾驶员、原因
- [ ] 点击"生成报告"→ 返回 reportId → 显示成功 toast
- [ ] WebSocket L3 告警推送 → toast 通知

### 7.5 个人中心 Tab（Profile）

**检查点**：
- [ ] 显示用户头像（首字）、用户名、角色
- [ ] 手机号脱敏显示（138****6789）
- [ ] 点击"设置"→ 展开设置子菜单（推送通知/夜间模式/清除缓存）
- [ ] 点击"关于"→ 弹出对话框显示版本信息
- [ ] 点击"退出登录"→ 清除 session → 跳转登录页

---

## 八、curl 测试命令集

### 登录

```bash
# 家属登录
curl -k -s -X POST https://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"authMethod":"PASSWORD","credential":"family001","secret":"pass123"}' | jq .

# 车队管理员登录
curl -k -s -X POST https://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"authMethod":"PASSWORD","credential":"manager001","secret":"pass123"}' | jq .
```

### S1 风险监测

```bash
TOKEN="<family_token>"

# 驾驶员风险状态
curl -k -s https://localhost:8080/api/v1/drivers/d1/risk-status \
  -H "Authorization: Bearer $TOKEN" | jq .

# 告警历史
curl -k -s "https://localhost:8080/api/v1/drivers/d1/alerts?page=1&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### S3 远程监护

```bash
TOKEN="<family_token>"

# 查询监护权限
curl -k -s https://localhost:8080/api/v1/guardianship/d1/permissions \
  -H "Authorization: Bearer $TOKEN" | jq .

# 请求媒体会话
curl -k -s -X POST https://localhost:8080/api/v1/guardianship/media-session \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"familyAccountId":"<account_id>","driverId":"d1","sessionType":"AUDIO","secondaryAuthToken":""}' | jq .

# 手动救援
curl -k -s -X POST https://localhost:8080/api/v1/guardianship/manual-rescue \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"familyAccountId":"<account_id>","driverId":"d1","secondaryAuthToken":""}' | jq .

# 查询车窗状态
curl -k -s https://localhost:8080/api/v1/vehicles/v1/windows \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### S4 车队管理

```bash
TOKEN="<manager_token>"

# 疲劳分布
curl -k -s "https://localhost:8080/api/v1/fleet/f1/fatigue-distribution" \
  -H "Authorization: Bearer $TOKEN" | jq .

# 脱线车辆
curl -k -s "https://localhost:8080/api/v1/fleet/f1/offline-vehicles" \
  -H "Authorization: Bearer $TOKEN" | jq .

# 高风险司机
curl -k -s "https://localhost:8080/api/v1/fleet/f1/high-risk-drivers?riskLevel=L3_CRITICAL" \
  -H "Authorization: Bearer $TOKEN" | jq .

# 生成报告
curl -k -s -X POST https://localhost:8080/api/v1/fleet/reports \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"driverId":"d1","timeRange":{"start":"2026-06-01T00:00:00Z","end":"2026-07-01T00:00:00Z"},"reportType":"MONTHLY"}' | jq .

# 下载报告
curl -k -s -o report.pdf \
  "https://localhost:8080/api/v1/fleet/reports/<reportId>/download?format=pdf" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 九、常见问题与排查

### 9.1 401 Unauthorized

| 原因 | 排查 |
|------|------|
| Token 未设置 | `SessionStore.restore()` 后检查是否调用了 `apiClient.setAccessToken()` |
| Token 过期 | 前端应在过期前调用 `authApi.refresh()`。检查 `expiresIn` 是否合理 |
| 角色不符 | 如用 FAMILY 账号访问 S4 端点，检查 JWT role 声明 |
| 路径错误 | 检查 baseUrl 是否以 `/api/v1` 结尾 |

### 9.2 WebSocket 连接失败

| 原因 | 排查 |
|------|------|
| URL 错误 | 检查 `GuardianshipWebSocket` 构造函数的 `baseUrl` 是否指向正确地址 |
| Token 无效 | WS 握手时 token 通过 query 参数传递，检查 `?token=...` |
| HTTPS/WSS 不一致 | 开发环境自签名证书需在客户端信任 |
| 后端 WS 端点未注册 | 检查 `WebSocketConfig` 是否正确注册了 handler |

### 9.3 响应数据为空或格式不匹配

| 原因 | 排查 |
|------|------|
| 后端返回字段名与前端 DTO 不一致 | 对照 `docs/ood_interface.md` 中的 JSON 示例检查字段名（驼峰 vs 下划线） |
| 后端返回 `Record` 而前端期望类型化对象 | 检查各 API 文件中的 `fromJson` 映射逻辑 |
| 种子数据未加载 | 检查 Flyway 迁移是否执行（`flyway.enabled: true`） |

### 9.4 CORS 错误（浏览器调试时）

如通过浏览器调试前端，后端需配置 CORS：
- 后端 `application.yml` 中检查 CORS 是否开启
- 或通过 DevEco Studio 模拟器/真机调试（不经过浏览器，无 CORS 问题）

### 9.5 ArkTS 编译错误

| 常见错误 | 原因 | 解决 |
|---------|------|------|
| `arkts-no-any-unknown` | 使用了 `any` 或裸 `unknown` | 改用 `Record<string, unknown>` 或具体类型 |
| `arkts-no-untyped-obj-literals` | 对象字面量无类型标注 | 确保为显式 interface 类型的变量赋值 |
| `as T` 禁止 | 对用户接口做了类型断言 | 通过 `fromJson` 构造器从 `Record` 逐字段提取 |

---

## 十、联调步骤总览

```
阶段 1: 环境就绪
  ├── [ ] 启动金仓 PostgreSQL (dev) 或确认 H2 (ci)
  ├── [ ] 启动后端 mvn spring-boot:run -Dspring-boot.run.profiles=ci
  ├── [ ] curl 测试登录接口 (POST /api/v1/auth/login)
  └── [ ] curl 测试受保护接口 (GET /drivers/d1/risk-status)

阶段 2: HTTP 全链路
  ├── [ ] 修改 ApiClient.ts 的 baseUrl（如后端不在同域）
  ├── [ ] 测试登录 → 获取 token → 注入 apiClient → 跳转主页
  ├── [ ] 测试各 Tab 页面的 REST 数据加载（Dashboard/Fleet/Guardianship/Profile）
  └── [ ] 测试所有 CRUD 操作（救援触发、车窗控制、通知偏好、报告生成）

阶段 3: WebSocket 全链路
  ├── [ ] 修改 WebSocket URL（GuardianshipWebSocket / FleetWebSocket）
  ├── [ ] wscat 测试 WS 连接 + 订阅 + 消息收发
  ├── [ ] DashboardTab 接入实时体征推送
  └── [ ] FleetTab 接入 L3 告警推送

阶段 4: 端到端场景
  ├── [ ] 家属登录 → 查看监测数据 → 接收实时推送 → 触发救援
  ├── [ ] 管理员登录 → 查看车队看板 → 生成报告 → 下载 PDF
  ├── [ ] Token 过期 → refresh → 继续正常使用
  └── [ ] 异常场景：网络断开 → WS 重连 → 数据恢复

阶段 5: 验证与回归
  ├── [ ] 运行后端测试 (mvn test)
  ├── [ ] 前端视觉回归（各页面截屏对比）
  └── [ ] 性能基准（首屏加载时间、API 响应时间）
```

---

## 附录 A：前端关键文件索引

| 文件 | 用途 |
|------|------|
| `entry/src/main/ets/api/ApiClient.ts` | HTTP 客户端，baseUrl 在此配置 |
| `entry/src/main/ets/api/AuthApi.ts` | 登录/刷新/二次验证 |
| `entry/src/main/ets/api/DriverApi.ts` | 驾驶员风险状态/告警历史 |
| `entry/src/main/ets/api/GuardianshipApi.ts` | 监护 REST API |
| `entry/src/main/ets/api/FleetApi.ts` | 车队管理 REST API |
| `entry/src/main/ets/api/GuardianshipWebSocket.ts` | 家属 WS，baseUrl 在此 |
| `entry/src/main/ets/api/FleetWebSocket.ts` | 车队 WS，baseUrl 在此 |
| `entry/src/main/ets/websocket/WebSocketManager.ets` | WS 统一管理器，从 apiClient 获取 token |
| `entry/src/main/ets/viewmodel/SessionStore.ts` | 登录态持久化 + token 注入 |
| `entry/src/main/ets/viewmodel/DashboardViewModel.ts` | 监测 Tab 数据加载 |
| `entry/src/main/ets/viewmodel/FleetViewModel.ts` | 车队 Tab 数据加载 |
| `entry/src/main/ets/viewmodel/GuardianshipViewModel.ts` | 监护 Tab 数据/操作 |
| `entry/src/main/ets/pages/LoginPage.ets` | 登录页面 |
| `entry/src/main/ets/pages/tabs/DashboardTab.ets` | 监测 Tab UI + WS 集成 |
| `entry/src/main/ets/pages/tabs/GuardianshipTab.ets` | 监护 Tab UI + API 操作 |
| `entry/src/main/ets/pages/tabs/FleetTab.ets` | 车队 Tab UI + 报告生成 |
| `entry/src/main/ets/pages/tabs/ProfileTab.ets` | 个人中心 Tab UI |
| `entry/src/main/ets/rtc/SparkRTCClient.ets` | RTC 外观层（待 SDK 替换） |

## 附录 B：后端关键文件索引

| 文件 | 用途 |
|------|------|
| `src/main/java/.../AiotApplication.java` | Spring Boot 入口 |
| `src/main/java/.../interfaces/rest/AccountController.java` | 认证 REST 控制器 |
| `src/main/java/.../interfaces/rest/DriverController.java` | 驾驶员 REST 控制器 |
| `src/main/java/.../interfaces/rest/GuardianshipController.java` | 监护 REST 控制器 |
| `src/main/java/.../interfaces/rest/SafetyController.java` | 安全/告警 REST 控制器 |
| `src/main/java/.../interfaces/rest/ProjectionController.java` | 车队看板 REST 控制器 |
| `src/main/java/.../interfaces/rest/StorageController.java` | 文件下载 REST 控制器 |
| `src/main/java/.../interfaces/websocket/GuardianshipWebSocketHandler.java` | 家属 WS Handler |
| `src/main/java/.../interfaces/websocket/FleetWebSocketHandler.java` | 车队 WS Handler |
| `src/main/java/.../interfaces/websocket/WebSocketConfig.java` | WS 端点注册 |
| `src/main/resources/application.yml` | 主配置文件 |
| `src/main/resources/db/migration/` | Flyway SQL 迁移脚本 |
| `docs/ood_interface.md` | API 接口规范（2256 行） |
