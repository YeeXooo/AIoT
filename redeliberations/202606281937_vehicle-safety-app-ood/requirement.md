# 智能物联——基于多传感器融合的车载安全监测系统 — 应用层 OOD 设计

## 背景

本项目是一个「智能物联——基于多传感器融合的车载安全监测系统」软件项目。系统包含多维感知、AI 风险判定引擎（边缘—云协同）、闭环干预与反馈、远程监护（家属 APP）、车队运营管理（大屏/报表）、应急救援联动、OTA 固件升级管理等功能域。

## 技术栈

- 前端（家属 APP / 车队大屏）：ArkTS（HarmonyOS）
- 后端（云端服务 + 边缘车载端服务）：统一 Java Spring Boot，部署于华为云
- 设备接入：华为云 IoTDA（MQTT 协议）
- 存储：GaussDB/RDS
- 推送：SMN
- 音视频对讲：SparkRTC

## 需求文档

已批准的完整需求文档：D:\软件测试\requirements\202606242158_vehicle-safety-monitoring\req_v4.md

## 第一阶段产出（领域层 OOD）

领域层 OOD 设计产出位于：
D:\软件测试\redeliberations\202606281504_vehicle-safety-ood\a_v10_design_v1.md

其中包含实体（Entity）、值对象（Value Object）、聚合根（Aggregate Root）、领域服务（Domain Service）、领域事件（Domain Event）的完整定义，覆盖了需求文档第四~五节的全部业务规则（BR-01~BR-08）和核心业务对象。

## 第二阶段任务 — 应用层 OOD 设计

承接第一阶段领域层 OOD 产出，为本系统做应用层 OOD 设计。产出内容包括：

### 1. 各功能域的应用服务定义

为以下六个功能域分别定义应用服务（Application Service）及其接口契约：

| 序号 | 应用服务 | 职责概述 |
|------|---------|---------|
| S1 | RiskMonitoringService | AI 风险判定引擎，疲劳/分心/异常驾驶行为实时监测与风险评分 |
| S2 | InterventionService | 闭环干预与反馈，分级告警（车内声光/语音/安全带预紧/紧急制动辅助） |
| S3 | RemoteGuardianshipService | 远程监护，家属 APP 侧实时位置、车内视频、告警推送、音视频对讲 |
| S4 | FleetManagementService | 车队运营管理，大屏态势感知、车辆轨迹、统计报表 |
| S5 | EmergencyRescueService | 应急救援联动，碰撞失能自动 SOS、位置上报、救援调度 |
| S6 | OTAManagementService | OTA 固件升级管理，升级包分发、升级策略、回滚机制 |

### 2. 服务间协作关系

描述上述六个应用服务之间的调用/事件依赖关系图。

### 3. 核心时序图

至少覆盖以下三条关键路径的时序图（包含边缘端—云端—APP/大屏 全链路）：

- **路径1**：疲劳判定 → 告警 → 干预链路（RiskMonitoringService → InterventionService → RemoteGuardianshipService）
- **路径2**：活体遗留 → 报警链路（RiskMonitoringService → InterventionService → RemoteGuardianshipService → EmergencyRescueService）
- **路径3**：碰撞失能 → SOS + 家属自动激活链路（RiskMonitoringService → EmergencyRescueService → RemoteGuardianshipService → InterventionService）

### 输出格式要求

- 每个应用服务：接口方法签名 + 参数/返回值定义 + 异常处理策略
- 服务协作图：以组件图 + 说明文字形式
- 时序图：以 Mermaid sequenceDiagram 语法描述，覆盖全链路消息交互
- 不要实现，仅设计
