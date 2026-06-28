# AIoT — 智能物联：基于多传感器融合的车载安全监测系统

系统接收多种车载传感器数据，对驾驶员与车内环境做实时风险判定，判定到风险时执行分级干预与反馈，并将脱敏后的安全状态同步给家属、车队管理方与救援机构，实现"主动干预 + 情感化守护"的安全闭环。

**功能域**：多维感知、AI 风险判定引擎（边缘—云协同）、闭环干预与反馈、远程监护（家属 APP）、车队运营管理（大屏/报表）、应急救援联动、OTA 固件升级管理。

**技术栈**：前端 ArkTS（HarmonyOS），后端 Java Spring Boot，数据库金仓（兼容 PostgreSQL），华为云 IoTDA（MQTT）、SMN、SparkRTC。

## 仓库结构

```
├── README.md                          # 本文件
├── prompt.md                          # 需求澄清与 OOD 设计流程说明
├── code/                              # 源代码（文档与代码分离）
├── docs/
│   ├── ood_domain.md                  # 领域层 OOD 设计方案（DDD 分层）
│   ├── ood_application.md             # 应用层 OOD 设计方案
│   └── requirements.md                # 需求文档（req_v4）
├── instructions/                      # 流程指令
│   ├── 202606242128.md
│   ├── 202606242158.md
│   ├── 202606281420.md
│   ├── 202606281559.md
│   └── 202606281754.md
├── requirements/
│   └── 202606242158_vehicle-safety-monitoring/  # 需求迭代与审查
│       ├── requirement.md             # 原始需求
│       ├── req_v1.md ~ req_v4.md      # 需求文档（4 轮迭代）
│       └── review_v1.md ~ review_v4.md # 审查报告（4 轮审查，v4 APPROVED）
├── requirements/
│   └── 202606242128_vehicle-safety-monitoring/  # 早期需求版本
│       ├── requirement.md
│       ├── req_v1.md ~ req_v2.md
│       └── review_v1.md ~ review_v2.md
├── issues/
│   ├── 001-基础设施层OOD设计.md
│   └── 002-接口层OOD设计.md
└── redeliberations/
    └── 202606281504_vehicle-safety-ood/  # OOD 再审议（10 轮迭代）
        └── ...
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

### 当前进度

| 阶段 | 状态 | 产出 |
|------|:----:|------|
| 第一阶段：领域层 OOD | ✅ | `docs/ood_domain.md` |
| 第二阶段：应用层 OOD | ✅ | `docs/ood_application.md` |
| 第三阶段：基础设施层 OOD | ⬜ | 待开始 |
| 第四阶段：接口/API 层 OOD | ⬜ | 待开始 |
