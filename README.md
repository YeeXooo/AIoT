# AIoT — 智能物联：基于多传感器融合的车载安全监测系统

系统接收多种车载传感器数据，对驾驶员与车内环境做实时风险判定，判定到风险时执行分级干预与反馈，并将脱敏后的安全状态同步给家属、车队管理方与救援机构，实现"主动干预 + 情感化守护"的安全闭环。

**功能域**：多维感知、AI 风险判定引擎（边缘—云协同）、闭环干预与反馈、远程监护（家属 APP）、车队运营管理（大屏/报表）、应急救援联动、OTA 固件升级管理。

**技术栈**：前端 ArkTS（HarmonyOS），后端 Java Spring Boot，数据库 H2（开发）/ PostgreSQL（生产），IoTDA（MQTT）。

> 课程作业，全部采用本地免费方案，参见 `todo.md` 附录对照表。

## 仓库结构

```
├── README.md                          # 本文件
├── todo.md                            # 实现任务清单
├── prompt.md                          # 需求澄清与 OOD 设计流程说明
├── code/
│   ├── server/                        # 后端 Spring Boot（Maven 单模块）
│   │   └── src/main/java/com/aiot/
│   │       ├── domain/                # 领域层
│   │       ├── application/           # 应用层
│   │       ├── infra/                 # 基础设施层
│   │       └── interfaces/            # 接口层
│   └── frontend/                      # 前端 HarmonyOS ArkTS
│       ├── pages/                     # 页面
│       ├── model/                     # DTO 类型定义
│       ├── api/                       # REST API 客户端
│       ├── websocket/                 # WebSocket 信令
│       ├── rtc/                       # SparkRTC 集成
│       ├── viewmodel/                 # 状态管理
│       └── common/                    # 常量/工具
├── docs/
│   ├── ood_domain.md                  # 领域层 OOD 设计
│   ├── ood_application.md             # 应用层 OOD 设计
│   ├── ood_infrastructure.md          # 基础设施层 OOD 设计
│   ├── ood_interface.md               # 接口/API 层 OOD 设计
│   └── ood_perception_yolo.md         # 视觉感知层（YOLO）OOD 设计分册
├── requirements/
│   ├── 202606242158_vehicle-safety-monitoring/  # 需求迭代与审查
│   │   └── (requirement.md, req_v1~4, review_v1~4)
│   └── 202606242128_vehicle-safety-monitoring/  # 早期需求版本
└── redeliberations/
    ├── 202606281504_vehicle-safety-ood/    # 领域层+应用层 OOD 再审议
    ├── 202606290937_infrastructure_ood/    # 基础设施层 OOD 再审议
    └── 202606291304_api_ood/               # 接口/API 层 OOD 再审议
```

## 分支规范

```
main ───── 设计文档（冻结，受保护）
  └── develop ───── 开发集成分支
        ├── feat/* ── 功能分支
        └── fix/*  ── 修复分支
```

- **main**：存放已完成审定的设计文档，不直接提交代码。受保护。
- **develop**：开发集成分支，所有功能/修复分支合并到此。
- **feat/xxx**：功能分支，从 `develop` 切出，完成后 PR 到 `develop`。
- **fix/xxx**：修复分支，同上。

> 完整工作流（从 Issue 到 PR 合入）见 [CONTRIBUTING.md](CONTRIBUTING.md)。

### 当前进度

| 阶段 | 状态 | 产出 |
|------|:----:|------|
| 第一阶段：领域层 OOD | ✅ | `docs/ood_domain.md` |
| 第二阶段：应用层 OOD | ✅ | `docs/ood_application.md` |
| 第三阶段：基础设施层 OOD | ✅ | `docs/ood_infrastructure.md` |
| 第四阶段：接口/API 层 OOD | ✅ | `docs/ood_interface.md` |
| 实现阶段 | 🔲 | `todo.md`（38 个后端包 + 7 个前端目录已建） |
