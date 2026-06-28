# AIoT — 智能物联：基于多传感器融合的车载安全监测系统

系统接收多种车载传感器数据，对驾驶员与车内环境做实时风险判定，判定到风险时执行分级干预与反馈，并将脱敏后的安全状态同步给家属、车队管理方与救援机构，实现"主动干预 + 情感化守护"的安全闭环。

**功能域**：多维感知、AI 风险判定引擎（边缘—云协同）、闭环干预与反馈、远程监护（家属 APP）、车队运营管理（大屏/报表）、应急救援联动、OTA 固件升级管理。

**技术栈**：前端 ArkTS（HarmonyOS），后端 Java Spring Boot，华为云 IoTDA（MQTT）、GaussDB/RDS、SMN、SparkRTC。

## 仓库结构

```
├── README.md                          # 本文件
├── prompt.md                          # 需求澄清与 OOD 设计流程说明
├── code/                              # 源代码（文档与代码分离）
├── docs/
│   ├── ood.md                         # 领域层 OOD 设计方案（DDD 分层）
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
└── redeliberations/
    └── 202606281504_vehicle-safety-ood/  # OOD 再审议（10 轮迭代）
        ├── requirement.md
        ├── iteration_v1.md ~ iteration_v10.md   # 迭代记录
        ├── iteration_history.md
        ├── a_v*_design_v*.md                    # 设计方案产出
        ├── a_v*_iteration_requirement.md         # 迭代需求
        ├── a_v*_review_v*.md                     # 审查意见
        ├── b_v*_challenge_v*.md                  # 挑战/质疑
        └── b_v*_diag_v*.md                       # 诊断分析
```
