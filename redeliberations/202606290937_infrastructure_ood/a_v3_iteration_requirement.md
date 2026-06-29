根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

组件B诊断报告（b_v2_diag_v2.md）对上一轮产出（a_v2_design_v6.md）进行了全面审查，质询结果确认为 **LOCATED**。诊断报告通过需求-设计追溯矩阵确认 34 个子要点均有对应设计章节覆盖，各组件就绪度评估中 17 个组件可直接编码、10 个组件需补充决策。合计发现 **22 个质量问题**（1 严重 + 11 一般 + 10 轻微），分类如下：

### 严重问题（阻塞项）

1. **fleet_dashboard_projection 投影表缺主键/唯一约束**：`§3.2.3` 投影表 P2 字段定义缺主键，导致 `INSERT ... ON CONFLICT DO UPDATE` 操作无法执行。建议以 `(fleet_id, risk_level, alert_type)` 作为复合唯一约束。

### 一般问题（需补充决策）

2. **trajectory_projection 分片策略未明确**：`§3.2.3` P3 未纳入 `§3.1.5` 分片清单，PK `BIGSERIAL` 在跨分片下冲突。建议按 `vehicle_id` 分片，PK 改为应用层 UUID。

3. **心跳监控缺 last_heartbeat_at 字段**：`§3.1.1` Vehicle 表无最后心跳时间戳。建议增加列并给出心跳监控定时任务的更新 SQL 和负责组件。

4. **Outbox 投递器轮询 SQL 逻辑缺陷**：`§3.3.2`/`§3.3.5`/`§4` 场景 2 三处描述的退避区间计算矛盾——per-event 变量无法在单条 SQL 中计算。建议选定方案 A（SQL 固定上界 60s + Java 层逐事件计算退避）或方案 B（增加 `next_retry_at` 预计算列）并同步三处描述。

5. **guardianship 权限变更策略矛盾**：`§3.1.2`/`§3.1.4`/`§6.3` 中 DELETE 与 REVOKED 状态标记矛盾。建议统一为保留历史记录方案（PK 改为 `(driver_id, account_id, granted_at)` 或加自增主键），或明确删除历史依赖事件审计。

6. **IoTDA 上行消息消费机制未选定方案**：`§3.5.1` 使用了"或"字未选定消费方式。建议选定 IoTDA 数据转发至 DMS Kafka 方案，说明消息体格式和消费方式。

7. **fleet_dashboard_projection 缓存策略路径断裂**：`§3.2.3` P2 中事件驱动缓存失效仅删 Redis key 不更新投影表，"即时重算"回退到陈旧数据。建议明确路径 A（即时增量聚合 + 同步更新投影表）或路径 B（接受 ≤5min 延迟约束）。

8. **@ElementCollection 冻结技术实现未说明**：`§3.1.3` 集合冻结后 JPA 脏检查可能触发级联 DELETE+INSERT 覆盖溢出数据。建议说明冻结实现方式（不可变包装 / 清空引用 / 防御性标记）。

9. **NotificationPort 端口方法契约未呈现**：`§3.4.6` 缺少方法签名列表。建议列出告警推送、SOS 救援推送、绩效预警推送、家属常态快照推送方法的契约概要。

10. **OTA 升级车载终端侧设计缺失**：`§3.4.5` 仅覆盖云端下发，边缘侧分片接收校验、组装刷写、回滚策略均未涉及。建议新增边缘侧 OTA 客户端小节。

11. **RescueReportPort HTTP API 备选路径缺接口规范**：`§3.4.7` 缺少 endpoint/格式/认证/可重试状态码。建议定义接口规范概要或标注为外部依赖待确认。

12. **DEW 密钥轮转离线车辆场景未覆盖**：`§3.5.5` 车辆离线期间密钥轮转未处理。建议补充分离线排队补推或宽限期方案。

### 轻微问题

13. **trip_physiological_snapshot 绑定表机制未说明**：`§3.1.5` 分片范围 vs `§3.1.3` 表结构——子表未含 `vehicle_id` 列，依赖 ShardingSphere 绑定表机制但未说明。

14. **TripRepository JPQL 未处理行程进行中场景**：`§3.2.2` 查询条件 `endedAt <= :to` 排除未结束行程。建议改为 `(endedAt IS NULL OR endedAt <= :to)`。

15. **DriverRepository @Modifying @Query 乐观锁异常转换链路未说明**：`§3.2.1` 受影响行数 0 时需手动抛异常但文档未说明。

16. **传感器自检重试间隔 0ms**：`§3.2.4` 即时重试在并发场景下可能再次冲突。建议改为 10ms 或说明仅边缘侧单线程使用。

17. **SMN 消息模板变量列表未定义**：`§3.5.3` 三类模板各需哪些变量未列出。

18. **SparkRTC Token 续期机制未描述**：`§3.4.8` 续期触发者、时机、失败行为均未说明。

19. **三张 CQRS 投影表分片归属未定义**：`§3.5.2` 分片清单中缺 alert_projection/fleet_dashboard_projection/trajectory_projection。

20. **看板刷新聚合 SQL 无具体概要**：`§3.2.3` P2 仅概念描述，driver_count 计算口径和 heatmap_data 组装逻辑未说明。

21. **家属 WebSocket 心跳/重连机制缺失**：`§6.2` vs `§3.7.5` 缺少 ping/pong 参数和重连策略。

22. **边缘存储降级优先级分类不清晰**：`§3.7.4` 将生理快照与告警事件混在同一优先级轴比较，建议区分 MQTT 积压和磁盘不足两种场景。

## 历史迭代回顾

### 已解决的问题（第 1 轮 12 项均已解决）

第 1 轮反馈的 12 个问题（分片键矛盾、取模路由、EmergencyRescueService 部署位置、SafetyAlertEvent 冗余索引、EdgeSessionContext 缺失、边缘仓储策略缺失、outbox 缺 last_attempt_at、MQTT Topic 不一致、部署图缺失、VO-16 归类错误、主键策略未说明、边缘乐观锁未说明）均已在本轮诊断中不再提及，确认为已修复。

### 持续存在的问题（第 2 轮 12 项全部持续）

第 2 轮反馈的 12 个问题（对应本次诊断问题 1–8 和 13–16）经过一轮迭代后**全部仍然存在**，需重点解决：
- **问题 1（严重）**：fleet_dashboard_projection 缺主键——已跨 2 轮未解决，本轮列为最高优先级修复项
- **问题 2–8、13–16**：共 11 个一般问题反复出现在两轮反馈中（如 trajectory_projection 分片、心跳字段缺失、outbox SQL 矛盾、guardianship 表述矛盾等），需本轮系统性解决

### 新发现的问题（本轮新增 10 项）

本轮新增问题 9–12、17–22（共 10 项），集中在以下维度：
- 数据持久化细节：trip_physiological_snapshot 绑定表（问题 9）、TripRepository JPQL 边界（问题 10）、@Modifying 异常转换（问题 11）、重试间隔（问题 12）
- 端口适配器与云服务细节：SMN 模板变量（问题 17）、SparkRTC Token 续期（问题 18）、CQRS 投影表分片（问题 19）、看板 SQL（问题 20）
- 部署拓扑边缘场景：WebSocket 心跳（问题 21）、边缘存储降级（问题 22）

## 上一轮产出路径

/home/jasper/AIoT/redeliberations/202606290937_infrastructure_ood/a_v2_design_v6.md

## 用户需求

/home/jasper/AIoT/redeliberations/202606290937_infrastructure_ood/requirement.md
