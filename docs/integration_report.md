# 前后端联调改造文档

> 时间：2026-07-04  
> 范围：后端 REST 控制器补齐 + WS 修复 + 数据初始化 + 金仓切换

---

## 一、改造前状态

前端 22 个 REST 调用端点与后端 20 个 CRUD 端点**完全不对齐**（0% 匹配）。前端调用 `/auth/login`、`/drivers/{id}/risk-status` 等功能导向路径，后端只有 `/account/list`、`/driver/list` 等实体 CRUD。

## 二、新增文件

| 文件 | 用途 |
|------|------|
| `AuthController.java` | 登录 / 刷新 / 二次验证，BCrypt 密码，支持账号名映射 |
| `DriversController.java` | `/drivers/{id}/risk-status` + `/drivers/{id}/alerts` |
| `FleetController.java` | 8 个车队分析端点 + fleet ID 映射（`f1`→`fleet-east-1`） |
| `SparkRtcController.java` | `POST /sparkrtc/token` |
| `VehiclesController.java` | `GET /vehicles/{vehicleId}/windows` |
| `GlobalExceptionHandler.java` | 统一异常 → HTTP 错误码 |
| `RequestLoggingFilter.java` | 全量 API 请求日志 |
| `DataInitializer.java` | 启动注入运行时数据（监测会话/告警/轨迹/车队映射） |
| `V5__password_hash.sql` | 新增 `password_hash` 列 + 分账号 BCrypt 哈希 |

## 三、修改文件

| 文件 | 改动 |
|------|------|
| `GuardianshipController.java` | 新增 8 个子端点：bind / media-session / notification-preference / manual-rescue / window-control / permissions |
| `WebSocketPayloads.java` | PING / ConnectionEstablished 增加 `payload` 嵌套，匹配前端格式 |
| `GuardianshipWebSocketHandler.java` | `mock_token` 兼容（无 HTTP 登录时的临时方案） |
| `FleetWebSocketHandler.java` | 同上 |
| `WebSecurityConfig.java` | 增加 `/vehicles/**`、`/sparkrtc/**` 角色鉴权 |
| `SystemAccountJpaEntity.java` | 增加 `passwordHash` 字段 |
| `SystemAccountRepositoryBridge.java` | `save()` 从 INSERT 改为 UPSERT（修复通知 500） |
| `application-ci.yml` | 开启 Flyway，关闭 JPA ddl-auto |
| `AuthController.java` | 增加登录日志 |

## 四、前端需改动（已在 repo 修改源码，需 HarmonyOS 重新编译）

| 文件 | 行 | 改动 |
|------|-----|------|
| `ApiClient.ts` | L62 | baseUrl → `http://172.22.103.50:8080/api/v1` |
| `GuardianshipWebSocket.ts` | L62/69 | WS URL → `ws://172.22.103.50:8080/ws/guardianship` |
| `FleetWebSocket.ts` | L56/63 | WS URL → `ws://172.22.103.50:8080/ws/fleet` |

## 五、数据库

| 环境 | profile | 数据库 | 命令 |
|------|---------|--------|------|
| 开发（当前） | `dev` | 金仓 PostgreSQL 12.1 | `java -jar ... --spring.profiles.active=dev` |
| CI 测试 | `ci` | H2 内存 | `--spring.profiles.active=ci` |

金仓连接：`localhost:54321/aiot`，用户 `kingbase`，密码 `kingbase123`

## 六、测试账号

| 账号 | 密码 | 角色 | 映射关系 |
|------|------|------|----------|
| `family001` | `123456` | FAMILY | → phone `13900000001` |
| `family002` | `pass123` | FAMILY | → phone `13900000002` |
| `manager001` | `pass123` | MANAGER | → phone `18800000001` |

## 七、联调结果

| 通道 | 状态 |
|------|:--:|
| HTTP 登录 | ✅ |
| REST 驾驶员 / 告警 | ✅ |
| REST 监护操作 | ✅ |
| REST 车队分析 | ✅ |
| Guardianship WS（家属实时） | ✅（偶发 EOF 断开） |
| Fleet WS（车队实时） | ⚠️ FAMILY 角色被拒（正确行为） |
| 角色隔离（FAMILY ≠ MANAGER） | ✅ |

## 八、已知问题

1. **Fleet WS 带旧 token**：切换账号后 WS token 未更新，需 `wsManager.disconnect()` + `connect()`
2. **Guardianship WS 偶发 EOF**：心跳格式已修复，偶现 HarmonyOS WebSocket 库传输异常
3. **救援/对讲按钮 403**：前端需调 `POST /auth/secondary-verify` 拿 `secondaryAuthToken`（验证码 `123456`）
4. **数据传输层**：如需真实传感器数据，HMI 端需连通 MQTT（当前 `mqtt.enabled: false`）

## 九、服务器启动

```bash
cd /home/jasper/AIoT/code/server
mvn package -DskipTests -q
nohup java -jar target/aiot-server-*.jar --spring.profiles.active=dev --server.port=8080 > /tmp/aiot-server.log 2>&1 &
```
