根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

来源：组件B诊断报告（`b_v5_diag_v2.md`，质询结果：LOCATED）

### 严重问题（3项）

1. **SystemAccountRepository.findByDriver 的 JPQL 在 §3.2.1 与 §3.2.2 中矛盾** — §3.2.1 的 JPQL 写为 `JOIN a.guardianships g`（隐含 SystemAccountEntity 上存在 `guardianships` 字段），而 §3.2.2 的正确 JPQL 为 `JOIN guardianship g ON a.id = g.accountId`。两条 JPQL 对同一查询给出了互相矛盾的实现方式，其中 §3.2.1 的写法无法直接编译通过。所在位置：§3.2.1 vs §3.2.2。改进建议：将 §3.2.1 表中的 JPQL 修正为与 §3.2.2 一致的跨表 JOIN 写法。

2. **文档标题版本号与内容修订层级不一致** — 文档第 1 行标题标注为"（v5）"，但修订说明从 v2 延续至 v10。标题版本号与实际内容成熟度严重不一致。所在位置：文档第 1 行标题 + 修订说明各节。改进建议：将标题修正为与实际修订层级一致的版本号（v10），明确产出版本号与修订说明版本号的坐标体系一致。

3. **修订说明「v5」标题出现两次（重复）** — 第 1385 行和第 1391 行各存在一个"修订说明（v5）"标题，两个 v5 标题之间仅相隔 6 行，未合并为同一小节。所在位置：第 1385-1401 行。改进建议：合并两个 v5 批次为单一"修订说明（v5）"小节。

### 一般问题（13项）

4. **缺失需求 §一.5 要求的概念级 DDL 概要** — 需求明确要求产出"CREATE TABLE DDL 概要"，当前仅以 Markdown 表格列出字段，缺少 DDL 语法级别的结构展示（CREATE TABLE 语法、表级约束 SQL 表达）。所在位置：需求 §一.5 vs 设计 §3.1.1-§3.1.3。改进建议：在 §3.1 末尾新增概念级 DDL 概要小节，以 CREATE TABLE 语法展示 1-2 张代表性表。

5. **DriverHealthProfile 表设计缺少独立查询路径的显式声明** — 需求 §一.3 明确 DriverHealthProfile "不是聚合根但需要独立查询"，当前设计未显式声明独立查询的实现路径。所在位置：需求 §一.3 vs 设计 §3.1.2。改进建议：在 §3.1.2 中增加独立查询路径声明，明确哪些健康指标字段可被独立检索及查询实现方式。

6. **修订说明中使用 REJECTED 标记不符合设计文档规范** — 修订说明 v6（第 1407 行）和 v7（第 1414 行）中各有一条审查意见以 **REJECTED** 为前缀标记，该标记来源于审查质询内部流程，不应以原始标记形式出现在最终设计文档中。所在位置：第 1407 行、第 1414 行。改进建议：将 REJECTED 替换为中性表述（如"补充说明"或"已核实并确认"），修订说明应面向最终读者。

7. **OTA 升级刷写期间安全监测中断的安全风险未被承认** — §3.4.5 描述 OTA 刷写阶段"车载终端暂停所有感知和判定业务"，但未对中断期间发生安全事件如何处理给出任何陈述。所在位置：§3.4.5 第 765 行。改进建议：补充 OTA 刷写前置条件声明——触发刷写前校验车辆状态为驻车熄火，行驶中仅允许接收和暂存升级包，禁止进入刷写阶段。

8. **VehicleStateBuffer.getSnapshots 方法签名与需求中的端口契约不一致** — 需求 §四.1 的端口方法契约为 `getSnapshotWindow(Instant from, Instant to)`，但设计 §3.4.1 描述为 `getSnapshots(tripId, window)`，方法名和参数均不一致。所在位置：需求 §四.1 vs 设计 §3.4.1。改进建议：统一端口方法签名，若变更有理由则记录于修订记录。

9. **guardianship 表广播表策略下的悲观锁替代方案未说明跨分片 SELECT...FOR UPDATE 的执行方式** — guardianship 是广播表，所有行遍布每个分片，但 SELECT...FOR UPDATE 在 ShardingSphere-JDBC 中对广播表的实际行为未说明。所在位置：§3.1.2 第 266 行 vs §3.5.2。改进建议：明确广播表上 SELECT...FOR UPDATE 的实际行为，或改用非数据库层分布式锁（如 Redis SET NX）。

10. **PhysiologicalDataBuffer.getReadings 方法签名与需求中的端口契约不一致** — 需求 §四.2 要求 `getPhysiologicalWindow(Instant, Instant)`，设计 §3.4.2 描述为 `getReadings(tripId, window)`。所在位置：需求 §四.2 vs 设计 §3.4.2。改进建议：统一端口方法签名，与问题 8 一并说明变更理由。

11. **OTADeliveryPort 端口方法契约不够完整** — deliverPackage 的完整方法签名、参数类型、返回值约定、异常声明、进度回调接口均未以接口形式呈现。所在位置：§3.4.5。改进建议：为 OTADeliveryPort 定义完整的方法契约列表，参考 NotificationPort 的格式。

12. **MediaSessionPort 会话超时与并发限制未定义** — 单个会话的最大持续时长、空闲超时、并发会话数限制均遗漏。所在位置：§3.4.8。改进建议：补充会话生命周期的边界约束（最大持续时长、空闲超时、并发限制）。

13. **Outbox 表 / DLQ 表在消息队列长时间不可用时的存储膨胀** — 当 DMS Kafka 长时间不可用时 outbox 表的写入速率和存储膨胀问题未讨论，DLQ 表无自动清理策略。所在位置：§3.3.2 outbox 表结构 + §3.3.5 重试策略。改进建议：补充 outbox 积压监控告警阈值和降级策略，为 DLQ 表增加基于 moved_at 的时间保留窗口清理策略。

14. **边缘侧 SQLite 核心表无容量管理与清理策略** — trip、safety_alert_event、road_rage_voice_record 在数据同步至云端后无清理策略，长期运行可能导致存储空间不足。所在位置：§3.7.4 vs SQLite 核心表。改进建议：补充边缘侧 SQLite 核心表清理策略——已同步记录按 created_at 保留窗口清理，RoadRageVoiceRecord 按 expiry_time 清理。

15. **IoTDA 离线消息缓存 24 小时上限与长期离线车辆的矛盾** — 车辆离线超过 24 小时后的数据弥补策略未定义。所在位置：§3.7.5 + §3.7.4。改进建议：明确边缘侧断网超 24 小时场景同步策略——断网恢复后扫描 SQLite 中未确认记录批量补推。

16. **DEW 密钥服务不可用时的边缘侧加密降级策略不完整** — 缓存密钥的有效期、缓存密钥同时丢失时的降级路径未定义。所在位置：§3.5.5 灾备段。改进建议：补充 DEW 不可用时的分级降级策略（短期使用缓存密钥，长期触发审计告警并用临时随机密钥加密）。

### 轻微问题（7项）

17. **guardianship 表描述中存在格式化 artifact `\n\n`** — 第 266 行末尾，原文包含字面量字符串 `\n\n`，影响 Markdown 渲染。所在位置：第 266 行。改进建议：改为标准 Markdown 空行分隔。

18. **domain_event_dlq 表主键使用 BIGSERIAL 与文档声明的统一主键策略不一致** — 第 7 行声明统一使用应用层 UUID，但 §3.3.3 DLQ 表主键 dlq_id 定义为 BIGSERIAL。所在位置：第 7 行 vs §3.3.3。改进建议：将 dlq_id 改为 VARCHAR(64) UUID，或明确标注不适用主键策略的理由。

19. **看板投影 P2 中 driver_count 的聚合语义与数据源不一致** — 路径 A 使用 alert_projection 为数据源，口径概要使用 safety_alert_event，命名不统一。所在位置：§3.2.3 P2 路径 A vs 聚合计算口径概要。改进建议：统一数据源命名为 alert_projection，或注明二者数据等价。

20. **CameraOcclusionDetectionPort 的遮挡/移除信号的载荷结构未定义** — OcclusionDetectedSignal 和 OcclusionRemovedSignal 的载荷字段未定义。所在位置：§3.4.4。改进建议：补充信号载荷字段概要（至少含时间戳），或标注模拟阶段暂不定义。

21. **NotificationPort 各方法接收的 Command 对象载荷字段未定义** — PushAlertCommand、PushRescueReportCommand、PushPerformanceWarningCommand 的字段未定义。所在位置：§3.4.6。改进建议：为三种 Command 对象补充字段概要表。

22. **边缘侧 EmergencyRescueService 的碰撞判定时间窗口与 ring buffer 容量之间的量化关系未给出** — 两个缓冲的容量独立配置，无机制确保碰撞发生后缓冲均含有碰撞前完整数据。所在位置：§3.4.1、§3.4.2、§3.4.2a。改进建议：补充说明碰撞前数据的可用性由 ring buffer 容量保证，并给出判定延迟超限时的降级处理。

23. **IoTDA 离线消息缓存 24 小时上限**（同问题 15，轻微级子项）

## 历史迭代回顾

### 已解决的问题（在历史反馈中出现，但当前审查中不再提及）
- 第 1 轮问题 1：road_rage_voice_record 分片键缺失 — 已增加 vehicle_id 冗余列
- 第 1 轮问题 2：分片策略取模 → 一致性哈希 — 已修正
- 第 1 轮问题 3：EmergencyRescueService 部署标注错误 — 已修正
- 第 1 轮问题 4：SafetyAlertEvent 主表索引冗余 — 已限定于投影表
- 第 1 轮问题 5：EdgeSessionContext 未定义 — 已补充
- 第 1 轮问题 6：边缘侧仓储实现遗漏 — 已新增 §3.2.2a
- 第 1 轮问题 7：outbox 表缺少 last_attempt_at — 已增加
- 第 1 轮问题 8：MQTT Topic 与家属 APP 非 MQTT 连接矛盾 — 已修正
- 第 1 轮问题 9：部署架构图缺失 — 已补充
- 第 1 轮问题 10：VO-16 归入策略 B — 已移至策略 A
- 第 1 轮问题 11：聚合根表主键策略未说明 — 已统一声明
- 第 1 轮问题 12：边缘侧 SQLite 乐观锁未说明 — 已补充 JDBC 层实现
- 第 2 轮问题 1：fleet_dashboard_projection 缺少主键 — 已定义复合主键
- 第 2 轮问题 2：trajectory_projection 分片策略未明确 — 已明确
- 第 2 轮问题 3：Vehicle 表缺少 last_heartbeat_at — 已增加
- 第 2 轮问题 5：guardianship 权限变更策略矛盾 — 已统一为保留历史记录
- 第 2 轮问题 6：IoTDA 上行消费方式未选定 — 已选定
- 第 2 轮问题 7：fleet_dashboard_projection 缓存策略路径断裂 — 已修正
- 第 2 轮问题 8：@ElementCollection 冻结技术实现未说明 — 已补充
- 第 2 轮问题 9：NotificationPort 方法契约未呈现 — 已补充
- 第 2 轮问题 10：OTA 边缘侧设计缺失 — 已补充
- 第 2 轮问题 11：RescueReportPort HTTP API 缺接口规范 — 已标注
- 第 2 轮问题 12：DEW 密钥轮转离线车辆未覆盖 — 已补充
- 第 3 轮问题 1：outbox 广播表事务原子性未验证 — 已标注
- 第 3 轮问题 3：CQRS 投影表写入路由缺失 — 已明确
- 第 3 轮问题 5：文档标题版本号 v2 vs v3 — 已在后续迭代修正（但当前轮标题版本号又出现新矛盾）
- 第 3 轮问题 6：ArrayDeque 固定容量语义 — 已修正
- 第 3 轮问题 7：EdgeSessionContext destroy() 碰撞判定窗口 — 已修正
- 第 4 轮问题 1：IoTDA 上行消费与 outbox 写入事务断裂 — 已修正
- 第 4 轮问题 2：语音存证离线解密链断裂 — 已补充
- 第 4 轮问题 5：脱敏校验门控挂载点未明确 — 已补充
- 第 4 轮问题 6：家属 APP 三通道通知去重 — 已补充
- 第 4 轮问题 7：OTA 固件签名公钥来源 — 已补充
- 第 4 轮问题 9：trip_physiological_snapshot 完整列清单 — 已补充

### 持续存在的问题（在多轮反馈中反复出现）
- **guardianship 表金仓兼容性疑问**：第 4 轮问题 8（部分唯一索引金仓兼容性）→ 当前轮问题 9（广播表 SELECT...FOR UPDATE 跨分片行为未说明）。两者均为 guardianship 表在 ShardingSphere-JDBC 环境下的数据库行为验证不足，属于同一根因的延续——需在设计中明确实际行为或给出确切替代方案。
- **文档标题版本号管理混乱**：第 3 轮问题 5（v2 vs v3）→ 当前轮问题 2（v5 vs v10）、问题 3（v5 重复标题）。版本管理持续不规范，需建立明确的版本号与修订说明对应规则。
- **fleet_dashboard_projection 并发数据一致性**：第 4 轮问题 4（读-计算-写竞争窗口）→ 当前轮已补充并发竞争声明，但数据源命名仍不统一（问题 19），需进一步完善。

### 新发现的问题（本轮新识别）
- 所有当前审查结果中的 23 个问题均为本轮新识别（尽管部分与历史问题有延续关系）。重点关注：
  - JPQL 矛盾（问题 1）会直接导致编译失败
  - OTA 安全中断风险（问题 7）是系统性安全问题
  - 方法签名不一致（问题 8、10）会导致接口不匹配
  - outbox/DLQ 存储膨胀（问题 13）和边缘侧 SQLite 清理缺失（问题 14）是长期运行可靠性风险
  - 端口适配器接口完备性不足（问题 11、12、20、21）会影响下游实现者编码

## 上一轮产出路径
/home/jasper/AIoT/redeliberations/202606290937_infrastructure_ood/a_v5_copy_from_v4.md

## 用户需求
/home/jasper/AIoT/redeliberations/202606290937_infrastructure_ood/requirement.md
