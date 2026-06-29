# 再审议判定报告（v2）

## 判定结果

RETRY

## 判定理由

组件B诊断报告共识别8个问题，其中：1个严重问题（问题1：新增API端点未映射到应用层方法）、2个中等问题（问题2：家属手动救援触发端点归属不一致、问题3：queryRescueHistory认证标注矛盾）、5个一般/轻微问题。组件B质询报告标记为LOCATED，确认全部问题属实，证据充分、逻辑自洽、覆盖完备。实际轮次1 < 最大轮次12，判定为非耗尽终止。

根据判定标准，审查报告包含严重和一般等级的问题，满足RETRY条件。

## 需要解决的问题

- **问题描述**：新增API端点未映射到应用层方法——v2新增的3个REST端点（`GET /guardianship/{driverId}/permissions`、`POST /sparkrtc/token`、`DELETE /ota/upgrade-tasks/{taskId}`）在应用层OOD中无对应的方法签名，违反需求§2约束
- **所在位置**：§1.3端点表（行128, 129）、§1.6端点表（行537）；对比`docs/ood_application.md`§3.3、§3.6
- **严重程度**：严重
- **改进建议**：在应用层OOD中为S3补充`queryGuardianshipPermissions`和`issueSparkRTCToken`方法，为S6补充`cancelUpgradeTask`方法；或在API OOD中明确标注对应应用层待新增方法的具体预签名

- **问题描述**：家属手动救援触发端点归属与需求分组不一致——`POST /api/v1/guardianship/manual-rescue`归入S3，但需求`requirement.md:27`将其列入S5 EmergencyRescueService覆盖范围
- **所在位置**：§1.3 S3端点表（行125）；对比`requirement.md:27`、`docs/ood_application.md`§3.3 vs §3.5
- **严重程度**：一般
- **改进建议**：在文档开篇或对应分节处添加说明，解释设计层面基于职责内聚的归口调整；或将端点从§1.3移至§1.5并在应用层S5中补充方法

- **问题描述**：S5 queryRescueHistory端点认证标注与其他S5端点及§5.1角色映射矛盾——§1.5标注`JWT`（无角色限定），但§5.1限定S5全部端点仅`RESCUE`角色可访问
- **所在位置**：§1.5端点表（行440）；对比§5.1角色→权限映射表（行1643）
- **严重程度**：一般
- **改进建议**：将§1.5中`queryRescueHistory`端点的认证列统一标注为`JWT (RESCUE)`

- **问题描述**：SparkRTC Token独立端点的消费者未阐明——`POST /api/v1/sparkrtc/token`端点未说明目标调用方，§4.1和§4.3也未列出该端点
- **所在位置**：§1.3端点表（行129）、§3.2（行1294-1326）；对比§4.1（行1371-1378）
- **严重程度**：轻微
- **改进建议**：补充调用场景说明，若面向家属APP应在§4.1 REST调用列表中补入

- **问题描述**：TriggerRollbackResponse示例未覆盖ROLLING_BACK中间状态——JSON示例仅展示`ROLLED_BACK`，缺失`ROLLING_BACK`状态
- **所在位置**：§1.6 TriggerRollbackResponse JSON示例（行597-602）；对比`docs/ood_application.md`§4.6
- **严重程度**：轻微
- **改进建议**：补充`newStatus`的可能取值及两种状态的含义说明

- **问题描述**：S4 report download端点响应未指定Content-Type——不同format参数应返回不同Content-Type
- **所在位置**：§1.4端点表（行309）
- **严重程度**：轻微
- **改进建议**：补充Content-Type说明及Content-Disposition头

- **问题描述**：MQTT主题模板语法不一致——`${sensorType}`与其余`{variable}`语法不统一
- **所在位置**：§2.1主题路由总表第一行（行649）
- **严重程度**：轻微
- **改进建议**：将`${sensorType}`统一为`{sensorType}`

- **问题描述**：DELETE请求的成功响应码约定不统一——S3返回204，S6返回200+响应体
- **所在位置**：§1.3（行123）、§1.6（行537）
- **严重程度**：轻微
- **改进建议**：在文档开篇统一DELETE响应码策略
