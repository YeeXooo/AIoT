根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

基于组件B诊断报告（b_v10_diag_v1.md，质询结果为LOCATED）的8项问题：

### 严重问题

1. **缺少JWT登录/Token签发端点，认证链路不完整**：§5.1描述了完整JWT认证流程（用户登录→IAM签发JWT→API网关校验→应用服务提取sub/role），但§1 REST API契约中未定义任何JWT签发端点（如POST /api/v1/auth/login）。§1.7仅定义了POST /api/v1/auth/secondary-verify二次验证端点。没有初始Token签发端点，调用方无法获取Bearer Token。需在§1.7或新增§1.8中补充POST /api/v1/auth/login端点定义。

### 中等问题

2. **MQTT GPS字段命名未完全统一，文档内部不一致**：v10修订已将SafetyAlertEvent的GPS字段从gps重命名为gpsLocation（:830），与REST一致。但AlertTriggeredEvent（:1288）、TripStatus（:1126）、OverrideSignal（:1207）的GPS字段仍命名为gps。需二选一：(a)全部统一为gpsLocation；(b)在§2.2开头新增字段命名约定说明块。

3. **跨文档DTO不一致——RequestMediaSessionResponse缺少sparkRTCRoomId和sparkRTCJoinToken**：API OOD §1.3定义了4字段，但docs/ood_application.md §4.3同名DTO仅2字段。该问题自第7轮持续未解决，v10标记为"保留（无需修改本文件）"。需在docs/ood_application.md中补充。

4. **跨文档DTO不一致——RequestMediaSessionRequest和TriggerManualRescueRequest缺少secondaryAuthToken**：API OOD §1.3包含secondaryAuthToken字段，但docs/ood_application.md §4.3中对应DTO缺失。该问题自第4轮持续未解决。需在docs/ood_application.md中补充。

### 一般问题

5. **S3家属权限管理只有查询端点，缺少主动管理入口**：需求要求"家属权限查询/管理"，产出仅提供GET查询端点，权限授予与撤销完全由系统侧自动化流程驱动。家属缺少主动撤销权限或请求续期的入口。需二选一：(a)补充DELETE端点；(b)在文档中说明设计理由。

6. **跨文档StatusColor枚举值不一致**：领域层定义GREEN/YELLOW/RED三值，API OOD使用三值并标注一致性，但docs/ood_application.md仍含ORANGE四值。第8轮已发现但根源未解决。需在docs/ood_application.md中移除ORANGE。

7. **跨文档DTO字段缺失**：(a)AlertSummary.gpsLocation（API OOD §1.1/§4.1已定义，应用层ODD缺失，第5轮发现）；(b)QueryTrajectoryResponse.dataConsistency（API OOD §1.4已定义，应用层ODD缺失，第3轮发现）。需在docs/ood_application.md中补充。

8. **§3.2豁免触发条件表述不精确**：当前表述为"S1判定COLLISION_DISABILITY或LIFE_DETECTION类型告警持续异常≥60s"。但LIFE_DETECTION由LifeDetectionService在熄火落锁后60秒判定窗口内一次性产出LifeDetectedEvent，不存在"持续异常≥60s"的概念。需修改为引用具体领域事件（LifeDetectedEvent/EmergencyActivatedEvent），移除"持续异常≥60s"的计时描述。

## 历史迭代回顾

### 已解决的问题
- S3缺失家属权限查询REST端点（v1）→ 已在v2补充GET端点
- MQTT Payload JSON Schema覆盖不完整（v1）→ 已在v2补充14个Payload
- 新增API端点未映射到应用层方法（v2）→ 已在v5在docs/ood_application.md中落地
- S3→S5手动救援ID体系断裂（v3）→ 已在v4补充rescueReportId及流转说明
- S5 createRescueReport方法缺失（v4-v9，持续6轮）→ 已在v10落地
- v9双重修订块结构混乱（v9）→ 已在v10合并
- Markdown代码块格式错误（v8）→ 已在v9修正
- S4 看板数据订阅模型缺少Request示例（v8）→ 已在v9补充

### 持续存在的问题（需重点解决）
- **GPS字段命名不一致**（v9→v10）：v9发现MQTT SafetyAlertEvent的gps与REST QueryAlertHistoryResponse的gpsLocation不一致，v10部分修复（SafetyAlertEvent已改为gpsLocation），但AlertTriggeredEvent、TripStatus、OverrideSignal仍使用gps，全文档未真正统一。
- **RequestMediaSessionResponse缺少sparkRTCRoomId/sparkRTCJoinToken**（v7→v10）：连续4轮标记为"保留（无需修改本文件）"，根源在docs/ood_application.md未同步更新。
- **RequestMediaSessionRequest/TriggerManualRescueRequest缺少secondaryAuthToken**（v4→v10）：连续7轮标记为"保留（无需修改本文件）"，根源在docs/ood_application.md未同步更新。
- **StatusColor枚举ORANGE值**（v6→v10）：跨层枚举不一致，连续5轮未解决。
- **AlertSummary.gpsLocation与应用层ODD不一致**（v5→v10）：连续6轮标记为"保留"。
- **QueryTrajectoryResponse.dataConsistency与应用层ODD不一致**（v3→v10）：连续8轮标记为"保留"。

### 新发现的问题
- JWT登录端点缺失（本轮首发现）：认证链路完整性缺陷，无人能获取Bearer Token。
- S3家属权限主动管理入口缺失（本轮首发现）：家属无法主动撤销监护权限或请求续期。
- §3.2豁免触发条件表述不精确（本轮首发现）：领域事件触发型判定被错误表述为持续监测型。

## 上一轮产出路径
/home/jasper/AIoT/redeliberations/202606291304_api_ood/a_v10_copy_from_v9.md

## 用户需求
/home/jasper/AIoT/redeliberations/202606291304_api_ood/requirement.md
