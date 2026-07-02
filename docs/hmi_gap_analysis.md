# 车机端 HMI 设计总结与差距分析

> 本文档汇总车机端 HMI（In-Vehicle Head Unit）的需求、架构设计、通信方式、UI 功能清单以及与当前代码实现的差距。供后续开发参考。

---

## 一、车机端 HMI 定位

### 1.1 是什么

运行在**车内中控显示屏**上的嵌入式应用，是驾驶员与系统交互的唯一界面。它与当前 `code/frontend/`（HarmonyOS 手机 APP）是 **完全独立的前端项目**。

| 维度 | 车机端 HMI | code/frontend（家属/管理端） |
|------|-----------|--------------------------|
| 部署位置 | 车载嵌入式终端 | 手机 / 平板 / PC |
| 操作系统 | 车载 Linux / Android Automotive / QNX | HarmonyOS |
| UI 框架 | 车载 HMI 框架（如 Kanzi / Qt / AGL） | ArkTS |
| 通信方式 | **进程内调用**（与边缘 Java 服务同进程） | REST + WebSocket（跨网络） |
| 网络依赖 | 断网时核心功能可用 | 依赖云服务 |
| 目标用户 | **驾驶员** | 家属 / 车队管理员 / 救援机构 |

### 1.2 在架构中的位置

```
┌──────────────────────────────────────────────┐
│              车载终端（边缘侧）                  │
│                                              │
│  ┌─────────────┐   进程内调用    ┌───────────┐│
│  │  车机端 HMI   │◄────────────►│ Java 边缘  ││
│  │  (嵌入式)     │               │  服务      ││
│  │              │               │           ││
│  │  · 氛围灯控制 │               │ · Risk…   ││
│  │  · 语音播报   │               │ · Inter…  ││
│  │  · 座椅震动   │               │ · MQTT    ││
│  │  · 后排 PIP   │               │ · gRPC    ││
│  │  · 覆盖信号   │               │           ││
│  └─────────────┘               └─────┬─────┘│
│                                      │MQTT  │
│  ┌─────────────┐    gRPC            │      ││
│  │ Python YOLO  │◄─────────────────►│      ││
│  │ sidecar      │                   │      ││
│  └─────────────┘                    │      ││
└──────────────────────────────────────────────┘
                                       │
                                 IoTDA / 云端
```

### 1.3 设计出处

| 来源 | 关联内容 |
|------|---------|
| `requirements.md` §3.3 | HMI 分级反馈（氛围灯、语音、震动、空调、音乐） |
| `requirements.md` §3.4 | 家属远程对讲启动时的声光提示；驾驶员物理遮挡权 |
| `requirements.md` BR-01/BR-03/BR-06/BR-08 | 各风险等级下的 HMI 响应规则 |
| `ood_domain.md` DS-07, VO-12 | InterventionService + InterventionInstruction（10 种指令类型） |
| `ood_domain.md` VO-21 | OverrideSignal（驾驶员覆盖信号） |
| `ood_application.md` §3.2 | S2 InterventionServiceImpl（干预编排） |
| `ood_interface.md` §4.3 | HMI 本地查询接口完整清单 |
| `communication_architecture.md` §2.7 | 车机 HMI → 边缘服务的进程内调用协议 |

---

## 二、功能需求全景

### 2.1 主动感知与反馈（系统 → HMI）

系统依据风险判定结果，通过 HMI 向驾驶员输出分级干预：

#### 2.1.1 干预指令类型（VO-12 InterventionInstruction）

| 指令类型 | 触发场景 | 参数 | 设计要求 |
|----------|---------|------|---------|
| **AMBIENT_LIGHT_COLOR** | BR-01 轻度疲劳 L2 | color=ORANGE | 氛围灯变橙提醒 |
| | BR-01/L3 升级中 | color=RED | 氛围灯变红 |
| **VOICE_BROADCAST** | BR-01 重度疲劳 L3 | "您已疲劳驾驶，请停车休息" | 语义清晰、音量 ≥85dB |
| | BR-08 传感器故障 L3 | "安全监测系统已失效，请注意驾驶安全" | **3 秒内**触发 |
| | BR-02 活体遗留 L3 | 车外鸣笛（短促） | |
| **SEAT_VIBRATION** | BR-01 重度疲劳 L3 | 高强度震动 | 强制唤醒 |
| **HAZARD_LIGHTS** | BR-02 活体遗留 L3 | 开启双闪 | 车辆安全控制 |
| | BR-06 碰撞失能 L3 | 开启双闪 | |
| **AIR_CONDITIONING** | BR-03 路怒 L2 | 温度−2℃ | 环境调节安抚情绪 |
| **AUDIO_PLAYBACK** | BR-03 路怒 L2 | 播放白噪音/舒缓音乐 | 环境调节安抚情绪 |
| **CAN_DECELERATION_REQUEST** | 渐进干预 L3 升级 | — | CAN 减速请求（本期仅指令级） |
| **NAVIGATE_DECELERATION** | 渐进干预中间阶段 | — | 建议/请求减速 |
| **NAVIGATE_TO_SHOULDER** | 渐进干预最终阶段 | — | 引导靠边 |
| **ALERT** | 分心 L2 | — | 通用告警 |

#### 2.1.2 HMI 分级反馈总览

```
无风险 ───► 正常显示（车速表、导航等）
   │
   ▼
L1 提示级 ─► 本期预留，无触发路径
   │
   ▼
L2 预警级 ─► 氛围灯变橙（疲劳/分心）
   │         语音提醒（分心成立后 0.5s 内）
   │         空调降温 + 白噪音（路怒）
   │
   ▼
L3 高危级 ─► 氛围灯变红 + 语音强制播报 + 座椅震动（重度疲劳）
              双闪 + 短鸣笛（活体遗留）
              双闪 + 车门解锁（碰撞失能）
              渐进干预序列（CAN 减速 → 引导靠边）
              传感器故障语音提示（3s 内）
```

### 2.2 信息展示

#### 2.2.1 常驻信息

| 信息项 | 更新频率 | 来源 |
|--------|---------|------|
| 当前风险状态色（绿/黄/红） | 实时 | `queryInterventionStatus()` |
| 活跃干预指令列表 | 实时 | `queryInterventionStatus()` |
| 传感器健康状态 | 轮询或事件驱动 | Vehicle.SensorStatus |
| 家属接入通知（声光提示） | 事件驱动 | `FamilyAccessGrantedEvent` |
| 后排红外影像画中画（PIP） | 实时视频流 | 直接读取 REAR_IR_CAMERA |

#### 2.2.2 弹出/覆盖信息

| 信息项 | 显示条件 | 持续时间 |
|--------|---------|---------|
| "请停车休息" 强制语音 + 红色全屏 | BR-01 L3 重度疲劳 | 直至风险解除或被覆盖 |
| "安全监测失效" 语音 + 警告图标 | BR-08 传感器故障 | 3s 内触发，持续至故障解除 |
| "远程对讲/视频已接通" 声光提示 | FamilyAccessGrantedEvent | 接入时提示 2s |
| 分心告警 | 分心 L2 成立后 0.5s | 直至视线恢复 |
| 活体遗留报警（双闪 + 鸣笛） | BR-02 L3 判定成立 | 直至被处理或电瓶保护 |

### 2.3 驾驶员主动操作（HMI → 系统）

| 操作 | 触发方式 | 上报接口 | 说明 |
|------|---------|---------|------|
| **覆盖系统干预** | 转动方向盘 / 踩制动 / 踩加速 | `S2.reportOverride(OverrideSignal)` | STEER / BRAKE / ACCELERATE |
| **物理遮挡摄像头** | HMI 按钮点击 | 触发 `CameraOcclusionDetectedEvent` | 一键断开视频流（保留音频或完全挂断） |
| **响应家属对讲请求** | HMI 按钮（接受/拒绝） | 通过权限管理接口 | 驾驶员拥有接受/拒绝权 |

---

## 三、通信接口

### 3.1 进程内调用接口

车机 HMI 与 Java 边缘服务共享同一进程，以下为 HMI 需要调用的接口：

| 功能 | 调用方式 | 接口 | 返回 |
|------|---------|------|------|
| 查询当前干预指令 | 方法调用 | `S2.queryInterventionStatus(tripId)` | `QueryInterventionStatusResponse` |
| 上报覆盖信号 | 方法调用 | `S2.reportOverride(OverrideSignal)` | `ReportOverrideResponse` |
| 查询传感器状态 | 聚合根读取 | Vehicle.SensorStatus | JSONB 传感器健康状态集合 |
| 断开视频流（遮挡） | 触发事件 | 产生 `CameraOcclusionDetectedEvent` | — |
| 查询驾驶员风险状态 | 方法调用 | `S1.getDriverRiskStatus(driverId)` | `GetDriverRiskStatusResponse` |

### 3.2 进程内事件消费

HMI 需要订阅以下领域事件（通过 EdgeEventBus 同步回调）：

| 事件 | 触发条件 | HMI 响应 |
|------|---------|---------|
| `CameraOcclusionDetectedEvent` | 摄像头被物理遮挡 | 显示"摄像头已遮挡"提示 |
| `CameraOcclusionRemovedEvent` | 遮挡解除 | 撤销遮挡提示 |
| `FamilyAccessGrantedEvent` | 家属获得对讲/视频权限 | **声光提示**"对讲/视频已接通" |
| `FamilyAccessRevokedEvent` | 家属权限被撤销 | 提示"远程对讲已结束" |
| `RiskDeterminedEvent` | 新风险判定成立 | 按 AlertType×RiskLevel 映射触发干预 |
| `RiskResolvedEvent` | 风险已解除 | 撤销对应干预指令，恢复常态 HMI |
| `SensorFailureEvent` | 传感器故障 | 3s 内展示"监控失效"提示 |
| `LifeDetectedEvent` | 熄火后被锁车内生命体征 | 双闪 + 鸣笛（由 InterventionService 驱动） |
| `EmergencyActivatedEvent` | 碰撞失能 | 双闪 + 声光告警 |

### 3.3 HMI 与云端的间接通信

HMI 不直接与云端通信。以下云端发起的操作通过边缘 Java 服务中转后再以进程内方式通知 HMI：

| 云端操作 | 中转路径 | HMI 感知 |
|---------|---------|---------|
| 家属发起对讲/视频 | 云端 → MQTT → 边缘 → `FamilyAccessGrantedEvent` | 声光提示 |
| 家属控制车窗 | 云端 → MQTT `cmd/window/down` → 边缘 → CAN 执行 | HMI 不感知或仅展示执行结果 |
| 云端下发 OTA | 云端 → MQTT `cmd/ota/down` → 边缘升级模块 | HMI 显示升级进度 |
| 救援远程解锁 | 云端 → MQTT `cmd/door/unlock/down` → 边缘 | HMI 可显示"救援远程解锁已执行" |

---

## 四、UI/UX 设计要点

### 4.1 可用性要求

| 要求 | 数值 | 来源 |
|------|------|------|
| 告警文字字号 | ≥28px | `requirements.md` §六 易用性 |
| 告警文字对比度 | 高对比度 | 同上 |
| 紧急报警音量 | ≥85dB | 同上 |
| 端到端时延（判定→HMI反馈） | ≤500ms（边缘本地） | `requirements.md` §六 性能 |
| 传感器故障告警时延 | ≤3s | BR-08 |
| 分心告警时延 | 判定成立后 ≤0.5s | BR-分心（§四） |

### 4.2 安全驾驶设计原则

- **不遮挡驾驶信息**：HMI 告警不得完全覆盖车速、导航等关键驾驶信息
- **手套可操作**：交互元素大小 ≥ 15mm×15mm（参考车载 HMI 标准）
- **物理遮挡一键触发**：断开视频的按钮必须始终可触达（≥20mm×20mm，参考 APP 设计规范）
- **夜间模式**：氛围灯亮度根据环境光自动调节，避免夜间眩目
- **渐进式升级不跳跃**：疲劳 L2→L3 采用渐进增强，避免突兀惊吓

### 4.3 屏幕布局建议

```
┌──────────────────────────────────────────┐
│  [传感器状态栏] 摄像头 ●  雷达 ●  生理 ●  │  ← 常驻顶部
├─────────────────────┬────────────────────┤
│                     │                    │
│    主动画区域        │   后排 PIP          │  ← 画中画
│   (车速/导航等)      │  (红外影像)         │
│                     │                    │
│                     │                    │
├─────────────────────┴────────────────────┤
│  ⚠ 风险状态色条（绿/黄/红）               │  ← 氛围灯等效
├──────────────────────────────────────────┤
│  [当前干预提示]  [覆盖干预按钮] [断开视频] │  ← 操作栏
└──────────────────────────────────────────┘
```

---

## 五、当前实现状态

### 5.1 代码现状

**车机端 HMI：零代码，不存在。**

当前代码库中：
- `code/frontend/` — HarmonyOS 手机 APP（家属/管理员混合），**不是车机端**
- `code/server/` — Java 后端（边缘+云端），定义了干预指令、覆盖信号等模型，但无 HMI 直接消费

### 5.2 后端已有的支撑能力

| 能力 | 代码位置 | 状态 |
|------|---------|------|
| InterventionInstruction 值对象 | `domain/model/InterventionInstruction.java` | ✅ 已实现 (enum Type 含全部 10 种) |
| OverrideSignal 值对象 | `domain/model/OverrideSignal.java` + `OverrideType.java` | ✅ 已实现 |
| InterventionService (领域) | `domain/intervention/InterventionServiceImpl.java` | ✅ 已实现 |
| IInterventionService (应用) | `application/intervention/` | ✅ 已实现（interface + impl） |
| EdgeEventBus (同步) | `infra/eventbus/EdgeEventBus.java` | ✅ 已实现 |
| SensorStatus 值对象 | `domain/model/SensorStatus.java` | ✅ 已实现 |
| CameraOcclusionDetectionPort | `domain/port/CameraOcclusionDetectionPort.java` | ✅ 接口已定义 |
| Vehicle 聚合 SensorStatus | `domain/model/Vehicle.java` | ✅ 已实现 |
| REST queryInterventionStatus | 接口设计已定义，**代码中无对应端点** | ❌ 缺失 |
| REST getDriverRiskStatus | 接口设计已定义，**代码中无对应端点** | ❌ 缺失 |

### 5.3 后端缺失的支撑能力

| 缺失项 | 说明 |
|--------|------|
| `S2.queryInterventionStatus()` REST 端点 | HMI 当前只能进程内调用；若需云端查询当前干预状态，缺 REST 端点 |
| `S2.reportOverride()` REST 端点 | 覆盖信号设计为进程内调用；若需从独立 HMI 进程上报，需要 IPC 封装 |
| HMI 消费事件的总线连接 | EdgeEventBus 存在，但缺少将事件路由到 HMI 进程的桥接层（如通过 JNI/Socket/D-Bus） |
| Vehicle.SensorStatus JSONB 实时查询端点 | 设计为聚合根读取，需确认 HMI 是否可直接访问 Vehicle 聚合 |
| 后排红外摄像头视频流路由 | 设计要求"不流经融合判定门面"，需在基础设施层实现直接视频流转发 |

---

## 六、待开发任务清单

### 6.1 HMI 应用开发

- [ ] **H1. 技术选型**：确定车机端 UI 框架（Qt / Kanzi / Android Automotive / AGL）和开发语言
- [ ] **H2. 基础框架**：HMI 进程与 Java 边缘服务的 IPC 通信通道（JNI / Unix Domain Socket / gRPC localhost）
- [ ] **H3. 风险状态色条**：实时消费 `queryInterventionStatus()` 输出，绿/黄/红三色渲染
- [ ] **H4. 氛围灯等效 UI**：按 AMBIENT_LIGHT_COLOR 指令渲染（橙/红）
- [ ] **H5. 语音播报**：按 VOICE_BROADCAST 指令播放 TTS，≥85dB，语义清晰
- [ ] **H6. 座椅震动**：按 SEAT_VIBRATION 指令触发震动马达
- [ ] **H7. 双闪 + 鸣笛**：按 HAZARD_LIGHTS 指令控制 CAN（本期指令级）
- [ ] **H8. 环境调节提示**：按 AIR_CONDITIONING / AUDIO_PLAYBACK 指令展示当前调节状态
- [ ] **H9. 分心告警**：分心成立后 0.5s 内弹出告警提示
- [ ] **H10. 传感器故障提示**：3s 内展示"监测失效"警告
- [ ] **H11. 后排红外 P**IP：实时显示后排红外摄像头画面
- [ ] **H12. 家属接入声光提示**：消费 `FamilyAccessGrantedEvent`，展示"对讲已接通"
- [ ] **H13. 覆盖信号上报**：检测方向盘/踏板操作，生成 OverrideSignal 并上报
- [ ] **H14. 物理遮挡按钮**：HMI 一键触发 `CameraOcclusionDetectedEvent`
- [ ] **H15. OTA 升级进度**：展示升级阶段、百分比、预计剩余时间
- [ ] **H16. 日常驾驶信息共存**：告警不遮挡车速/导航等核心信息
- [ ] **H17. 夜间模式**：根据环境光自动调节亮度和配色

### 6.2 后端接口完善

- [ ] **B1. HMI 进程间通信桥接**：封装 Java 边缘服务的 `queryInterventionStatus` / `reportOverride` / `getDriverRiskStatus` 为 HMI 可调用的 IPC 接口
- [ ] **B2. 事件→HMI 路由**：EdgeEventBus 中需要推给 HMI 的事件（CameraOcclusion*、FamilyAccess*、SensorFailure 等）通过 IPC 桥转发
- [ ] **B3. REST GET `/api/v1/trips/{tripId}/interventions/active`**：实现 ood_interface.md §1.2 定义的端点
- [ ] **B4. REST GET `/api/v1/drivers/{driverId}/risk-status`**：实现 ood_interface.md §1.1 定义的端点
- [ ] **B5. 后排红外视频流媒体转发**：不影响判定主循环的低延迟视频流路由

---

## 七、与其他前端的职责对比

| 功能 | 车机端 HMI | 家属 APP | 车队大屏 |
|------|:---------:|:------:|:------:|
| 查看驾驶员实时风险状态 | ✅ | ✅ | — |
| 接收风险通知/告警 | ✅（本地） | ✅（推送） | ✅（L3 + 绩效） |
| 氛围灯/语音/震动反馈 | ✅ | — | — |
| 后排红外影像 | ✅（画中画） | ✅（视频巡视） | — |
| 驾驶员覆盖（打断干预） | ✅ | — | — |
| 物理遮挡摄像头 | ✅ | — | — |
| 远程对讲/视频 | — | ✅ | — |
| 远程车窗控制 | — | ✅ | — |
| 手动救援触发 | — | ✅ | — |
| 疲劳分布看板 | — | — | ✅ |
| 钻取/报告/OTA 管理 | — | — | ✅ |
| 绩效预警订阅 | — | — | ✅ |

---

## 八、关键设计决策记录

| # | 决策 | 结论 | 来源 |
|----|------|------|------|
| 1 | HMI 通信方式 | 进程内调用（与边缘 Java 服务共享进程），不经过 REST | `ood_interface.md` §4.3 |
| 2 | 干预指令→HMI 映射 | 由 DS-07 产出 InterventionInstruction 集合，HMI 逐条渲染 | `ood_domain.md` VO-12 |
| 3 | 驾驶员覆盖权 | 转向/制动/加速均视为有效覆盖，立即中止干预升级 | `requirements.md` §3.3 |
| 4 | 原始图像不上 HMI 外传 | DMS 帧仅在 Python sidecar 内存中计算，HMI 仅展示后排红外影像（非 DMS） | `ood_perception_yolo.md` §五 |
| 5 | 断网时 HMI 功能 | 安全告警链路（判定→干预→HMI）完全在边缘本地完成，断网不失效 | `requirements.md` §一 |
| 6 | OTA 升级期间 CAN 干预抑制 | 固件刷写和回滚期间仅抑制 CAN 级干预，氛围灯/语音/震动正常执行 | `ood_application.md` §3.6 |

---

## 九、交叉引用

| 文档 | 关联内容 |
|------|---------|
| `requirements.md` | §3.3 闭环干预与反馈、BR-01/BR-03/BR-06/BR-08、§三"驾驶员"角色、§七范围外 |
| `ood_domain.md` | DS-07 InterventionService、VO-12 InterventionInstruction（10 种）、VO-21 OverrideSignal、AR-03 Vehicle.SensorStatus |
| `ood_application.md` | §3.2 S2 InterventionServiceImpl |
| `ood_interface.md` | §4.3 HMI 本地查询接口（共 7 项）、§1.2 intervention REST 端点 |
| `ood_perception_yolo.md` | §五 BR-04 隐私边界（原始帧仅 Python 内存） |
| `communication_architecture.md` | §2.7 车机 HMI→边缘服务（进程内调用协议、5 个调用方向） |
| `code/server/domain/model/InterventionInstruction.java` | 指令类型枚举 + 参数结构（已实现） |
| `code/server/domain/model/OverrideSignal.java` | 覆盖信号值对象（已实现） |
