# AIoT 性能测试方案（Leming WebRunner）

> 武汉迎风聚智科技有限公司 **Leming WebRunner V6.1**
> 被测系统：AIoT 车载安全监测后端服务 (Spring Boot 3.2.5, Java 17)

---

## 1. 被测系统概述

| 项目 | 说明 |
|------|------|
| **名称** | AIoT 智能物联车载安全监测系统 |
| **架构** | Spring Boot 3.2.5 REST API + WebSocket + MQTT + gRPC |
| **数据库** | PostgreSQL (dev/prod) / H2 (ci) |
| **认证** | JWT (HMAC-SHA256), 角色: FAMILY / MANAGER / RESCUE |
| **端口** | HTTP 8080, WebSocket /ws/*, gRPC 50051 |
| **压测重点** | REST API 核心接口（HTTP） |

---

## 2. 环境准备

### 2.1 启动被测服务

```bash
# 一键准备（启动服务 + 生成 Token + 预置数据）
bash performance-test/setup.sh all

# 或分步操作：
bash performance-test/setup.sh start   # 启动 Spring Boot 服务 (ci profile, H2 内存库)
bash performance-test/setup.sh tokens  # 生成 50×3 个测试 Token
bash performance-test/setup.sh seed    # 预置 50 条 Driver/Health 数据
```

### 2.2 获取认证 Token

WebRunner 录制脚本时需要 `Authorization: Bearer <token>` 请求头。Token 生成方式：

```bash
# 方式1：使用 setup.sh 生成 CSV 批量 Token
bash performance-test/setup.sh tokens 100   # 每角色 100 个

# 方式2：单独生成一个 Token（供录制时使用）
cd /home/jasper/AIoT
java -cp "/tmp/jwt-runner:code/server/target/classes:..." TokenGenerator \
    /tmp/aiot-ci-keystore.p12 aiot-keystore-change-me \
    aiot-master-key aiot-master-key-pwd \
    <accountId> <FAMILY|MANAGER|RESCUE>
```

Token 有效期：Access Token 1小时，Refresh Token 24小时。长时间压测需定时刷新。

### 2.3 预置测试数据

```bash
bash performance-test/setup.sh seed
```

或在服务运行后通过 API 批量创建：

```bash
TOKEN=$(bash performance-test/setup.sh get_token perf-seed FAMILY)
for i in $(seq 1 100); do
  curl -X POST http://localhost:8080/api/v1/driver \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"driver_$(printf %03d $i)\",\"phone\":\"138$(printf %08d $i)\"}"
done
```

---

## 3. API 参考（WebRunner 脚本录制目标）

### 3.1 接口清单

| # | 方法 | 路径 | 角色要求 | 说明 |
|---|------|------|----------|------|
| 1 | GET | `/api/v1/driver/list?name=` | 已认证 | 司机列表查询 |
| 2 | POST | `/api/v1/driver` | 已认证 | 创建司机 |
| 3 | PUT | `/api/v1/driver` | 已认证 | 更新司机 |
| 4 | DELETE | `/api/v1/driver/{id}` | 已认证 | 删除司机 |
| 5 | GET | `/api/v1/account/list` | 已认证 | 账户列表 |
| 6 | GET | `/api/v1/account/{phone}` | 已认证 | 按手机号查账户 |
| 7 | GET | `/api/v1/guardianship/list?driverId=&accountId=` | FAMILY | 监护关系列表 |
| 8 | POST | `/api/v1/guardianship` | FAMILY | 创建监护关系 |
| 9 | DELETE | `/api/v1/guardianship/{driverId}/{accountId}` | FAMILY | 撤销监护关系 |
| 10 | GET | `/api/v1/health/{driverId}` | 已认证 | 司机健康档案 |
| 11 | PUT | `/api/v1/health/{driverId}` | 已认证 | 更新健康档案 |
| 12 | GET | `/api/v1/safety/trip/list?driverId=&active=` | 已认证 | 行程列表 |
| 13 | GET | `/api/v1/safety/alert/list?driverId=&riskLevel=&alertType=` | 已认证 | 告警列表 |
| 14 | GET | `/api/v1/safety/vehicle/list?fleetId=&keyword=` | 已认证 | 车辆列表 |
| 15 | GET | `/api/v1/projection/alert?fleetId=&riskLevel=&activeOnly=` | MANAGER | 告警投影 |
| 16 | GET | `/api/v1/projection/dashboard?fleetId=` | MANAGER | 仪表盘投影 |
| 17 | GET | `/api/v1/projection/trajectory?tripId=` | MANAGER | 轨迹投影 |
| 18 | GET | `/api/v1/storage/info` | 已认证 | 存储信息 |
| 19 | GET | `/api/v1/storage/list?dir=` | 已认证 | 文件列表 |
| 20 | POST | `/api/v1/storage/upload?dir=&fileName=` | 已认证 | 上传文件 |

### 3.2 请求示例

```http
### 创建司机
POST /api/v1/driver HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{"name":"张伟","phone":"13800000001"}

### 查询司机列表
GET /api/v1/driver/list?name=张 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

### 更新司机
PUT /api/v1/driver HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{"driverId":{"id":"550e8400..."},"name":"张伟_updated","phone":"13800000001"}

### 获取健康档案
GET /api/v1/health/550e8400-e29b-41d4-a716-446655440000 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

### 查询告警列表
GET /api/v1/safety/alert/list?driverId=xxx&riskLevel=HIGH HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...

### 车队仪表盘投影
GET /api/v1/projection/dashboard?fleetId=fleet-001 HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## 4. 压测场景设计

### 场景 1：基础查询压测（基准性能）

**目标**：确定系统在纯查询负载下的吞吐量和响应时间基线

| 参数 | 值 |
|------|-----|
| 虚拟用户数 | 10 → 50 → 100 → 200 → 500 |
| 加压方式 | 阶梯递增（每阶梯持续 3 分钟） |
| 测试接口 | GET `/api/v1/driver/list` (80%), GET `/api/v1/safety/vehicle/list` (20%) |
| Think Time | 0～2s 随机 |
| 数据量 | 1000 条 Driver 记录 |

### 场景 2：混合读写压测（典型业务）

**目标**：模拟真实用户操作：家属查看监护司机状态、更新健康档案

| 参数 | 值 |
|------|-----|
| 虚拟用户数 | 50 → 150 → 300 |
| 加压方式 | 阶梯递增（每阶梯持续 5 分钟） |
| 测试接口 | 见下表 |
| Think Time | 1～5s 随机 |
| 角色 | FAMILY |
| 数据量 | 500 条 Driver + 配对的 Guardian/Health 数据 |

**接口权重分配**：

| 接口 | 方法 | 权重 | 说明 |
|------|------|------|------|
| `/api/v1/driver/list` | GET | 20% | 浏览司机列表 |
| `/api/v1/guardianship/list` | GET | 15% | 查看监护关系 |
| `/api/v1/health/{id}` | GET | 25% | 查看健康档案 |
| `/api/v1/safety/trip/list` | GET | 20% | 查看行程 |
| `/api/v1/safety/alert/list` | GET | 15% | 查看告警 |
| `/api/v1/health/{id}` | PUT | 5% | 更新健康档案 |

### 场景 3：车队管理高并发（MANAGER 角色）

**目标**：模拟车队管理员同时监控多个车队

| 参数 | 值 |
|------|-----|
| 虚拟用户数 | 20 → 50 → 100 |
| 加压方式 | 阶梯递增 |
| 接口 | `/api/v1/projection/dashboard`, `/api/v1/projection/alert`, `/api/v1/safety/vehicle/list` |
| Think Time | 2～10s |
| 角色 | MANAGER |

### 场景 4：数据写入压测（创建操作）

**目标**：测试系统在高频写入下的表现

| 参数 | 值 |
|------|-----|
| 虚拟用户数 | 10 → 30 → 50 |
| 加压方式 | 阶梯递增 |
| 接口 | POST `/api/v1/driver` (70%), POST `/api/v1/guardianship` (30%) |
| Think Time | 0～1s |
| 检查点 | HTTP 200 + 返回完整对象 |

### 场景 5：组合压测（综合业务）

**目标**：混合多种角色和接口，模拟生产环境

| 用户组 | 虚拟用户 | 角色 | 接口 |
|--------|----------|------|------|
| 家属用户 | 60% (300 VU) | FAMILY | Driver 查询、Health 查询、Guardianship CRUD |
| 车队管理 | 30% (150 VU) | MANAGER | Projection 查询、Vehicle 查询 |
| 救援用户 | 10% (50 VU) | RESCUE | Alert 查询、Health 查询 |
| 总并发 | 500 VU | - | 见上述分布 |

### 场景 6：稳定性和长时运行

**目标**：检测内存泄漏、连接池耗尽等长期运行问题

| 参数 | 值 |
|------|-----|
| 虚拟用户数 | 200 |
| 持续时间 | 30 分钟～2 小时 |
| 接口 | 场景 2 的混合读写 |
| 关注指标 | 响应时间趋势、内存使用、GC 频率、错误率 |

---

## 5. WebRunner 操作指南

### 5.1 WebRunner 工作流

```
┌─────────────┐     ┌──────────────┐     ┌───────────────┐     ┌───────────┐
│ 录制脚本     │ ──> │ 编辑/参数化   │ ──> │ 设置场景/比例  │ ──> │ 执行压测   │
│ (Proxy 代理) │     │ (变量/Timer)  │     │ (VU数/时长)    │     │ (实时监控)  │
└─────────────┘     └──────────────┘     └───────────────┘     └───────────┘
```

### 5.2 录制前准备

1. **设置 WebRunner 代理**
   - 将被测服务地址设为 `http://localhost:8080`
   - 代理端口默认（通常 8888 或自定义）

2. **准备认证 Header**
   - 在 WebRunner 的 "全局 Header 管理器" 中添加：
     ```
     Authorization: Bearer <TOKEN>
     ```
   - 使用 `performance-test/setup.sh tokens` 预先生成的 Token

3. **参数化变量**
   - `driverId`：从 CSV 数据文件中随机取值
   - `fleetId`：fleet-001 ~ fleet-010
   - `accountId`：从 tokens.csv 中获取

### 5.3 场景录制步骤

**以场景 2（混合读写）为例**：

1. 启动 WebRunner 录制代理
2. 设置浏览器代理指向 WebRunner
3. 执行以下操作（WebRunner 自动录制 HTTP 请求）：
   - 访问 driver/list → driver/list
   - 访问 guardianship/list → guardianship/list
   - 查看某 driver 的 health profile
   - 查看某 driver 的 trip list
   - 查看 alert list
   - 更新 health profile
4. 停止录制
5. 编辑录制的脚本：
   - 将硬编码的 `driverId` 替换为参数变量 `${driverId}`
   - 添加 Think Time（1～5s 随机）
   - 添加检查点（HTTP 200）
6. 保存脚本

### 5.4 场景配置

WebRunner 场景配置参考：

```
场景名称: AIoT-Mixed-RW-300VU
虚拟用户数: 300
加压模式: 阶梯递增
  - 0s:    50 VU
  - 180s:  150 VU
  - 360s:  300 VU
持续时间: 600s
脚本列表:
  - driver_list.js     权重 20%
  - guardian_list.js   权重 15%
  - health_get.js      权重 25%
  - trip_list.js       权重 20%
  - alert_list.js      权重 15%
  - health_update.js   权重 5%
参数文件: perf_tokens.csv, driver_ids.csv
```

### 5.5 分布式压测（多负载机）

如果单台负载机无法达到目标并发数：

1. 在多台机器上安装 WebRunner Agent
2. 在控制节点上配置 Agent 列表
3. 分配每台 Agent 的虚拟用户数
4. 执行分布式压测

参考 B 站视频教程：`Leming WebRunner 分布式使用教程`

---

## 6. 关注指标

| 类别 | 指标 | 目标（参考） |
|------|------|-------------|
| 响应时间 | 平均响应时间 | < 500ms (查询), < 1000ms (写入) |
| 响应时间 | P95 响应时间 | < 1000ms (查询), < 2000ms (写入) |
| 响应时间 | P99 响应时间 | < 2000ms |
| 吞吐量 | TPS (每秒事务数) | > 200 (场景2), > 500 (场景1) |
| 错误率 | HTTP 非 2xx 占比 | < 1% |
| 并发 | 最大支持 VU 数 | 500+ |
| 资源 | CPU 使用率 | < 80% |
| 资源 | 内存使用 | 无 OOM，GC 正常 |
| 稳定性 | 30分钟无内存泄漏 | 响应时间不持续恶化 |

---

## 7. 文件清单

```
performance-test/
├── README.md              # 本文件
├── setup.sh               # 一键环境准备脚本
├── TokenGenerator.java    # JWT Token 生成器 (Java)
├── gen_tokens.py          # 批量 Token 生成器 (Python)
├── seed_data.py           # 测试数据预置脚本 (Python)
└── tokens.csv             # 生成的 Token 文件 (运行后产生)
```

---

## 8. 故障排查

| 问题 | 解决方案 |
|------|----------|
| 服务启动失败 (端口占用) | `lsof -i:8080` 查找占用进程并 `kill` |
| JWT Token 过期 (HTTP 403) | 重新生成 Token: `bash setup.sh tokens` |
| 数据库无数据 | 运行 `bash setup.sh seed` |
| WebRunner 录制不到请求 | 检查代理设置，确认 WebRunner 的端口和浏览器代理一致 |
| 压测时出现大量超时 | 降低 VU 数，检查 H2 内存库瓶颈（CI profile 不适合大压测） |
| 需要真实 PostgreSQL | 修改 `application-dev.yml` 数据库连接，用 `dev` profile 启动 |
