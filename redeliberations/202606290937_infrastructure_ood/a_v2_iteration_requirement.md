根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

组件B诊断报告（b_v1_diag_v2.md）对上一轮产出识别出 12 个问题，质询报告确认诊断为 LOCATED（无质疑）。问题按严重程度分类如下：

### 严重（阻塞实现）

1. **`road_rage_voice_record` 表分片键与表结构不匹配**：§3.1.5 将该表列为按 VehicleId 分片，但 §3.1.1 表结构中不含 `vehicle_id` 列。需为该表增加冗余 `vehicle_id` VARCHAR(64) NOT NULL 列，或改为按 `driver_id` 哈希分片并调整分片范围说明。

2. **分片策略使用取模运算而非一致性哈希，与扩容目标矛盾**：§3.5.2 写为 `hash(vehicle_id) % 4` 并标注"一致性哈希路由"。取模哈希与一致性哈希是不同策略，取模方案在变更分片数量时需全量重分布，与 §3.1.5 的"动态扩容"目标及需求 §五.2 要求矛盾。需将路由算法改为 ShardingSphere-JDBC 一致性哈希分片算法，或明确声明固定 4 片不扩容并给出全量重分布迁移方案。

### 中等

3. **`EmergencyRescueService` 部署位置标注错误**：§3.3.6 将 EmergencyRescueService 标注为"边缘"消费方，但其职责（向救援中心发送精准定位、云端授权开门锁、校验 RescueAuthorizationToken 并调用外部 HTTP/SMN 接口）依赖云端网络连通性，无法在边缘侧执行。需改为"EmergencyRescueService（云端）"。

4. **`SafetyAlertEvent` 主表存在与 CQRS 读模型投影冗余的复合索引**：主表上建有 `(driver_id, occurred_at DESC, alert_type, risk_level)` 和 `(vehicle_id, occurred_at DESC)` 两条复合索引，但设计明确声明读查询全部路由至 `alert_projection` 投影表。两条索引冗余且拖累写性能。需从主表移除这两条索引，仅保留 `(trip_id)` FK 索引，将读侧索引限定于 `alert_projection` 投影表。

5. **`EdgeSessionContext` 被多处引用但基础设施层未定义其管理方式**：§3.4.1–3.4.2 引用 EdgeSessionContext 作为 VehicleStateBuffer/PhysiologicalDataBuffer 的容器，但设计未说明其在 Spring Boot 边缘应用中的实例化方式、生命周期绑定时机（点火/熄火时由哪个组件创建/销毁）、以及边缘侧进程重启后的恢复策略。需补充 EdgeSessionContext 的 Spring 集成方式、创建触发（如 `MonitoringSessionStartedEvent`）、与缓冲区的实例绑定关系、销毁时机和资源清理逻辑。

6. **边缘侧仓储实现仅描述了 1/5，其余 4 个仓储的边缘实现缺失**：需求 §七.1 明确边缘侧需部署本地持久化，Decision 4 指出边缘侧使用 SQLite + JDBC。但 §3.2.2 仅描述了 RoadRageVoiceRecordRepository 的边缘实现，TripRepository、DriverRepository、VehicleRepository、SystemAccountRepository 的 SQLite 实现策略均未覆盖。需增加边缘侧仓储实现小节，至少说明边缘侧 TripRepository 的 CRUD 实现方式（SQLite 不支持 JPA @ElementCollection 和 @Version，需替代方案）。

11. **outbox 表缺少指数退避重试所需的 `last_attempt_at` 字段**：§3.3.5 描述了指数退避重试策略，但 outbox 表（§3.3.2）仅有 `created_at` 和 `retry_count`，没有记录最近一次重试尝试时间的字段。OutboxRelayer 无法判断各事件是否已过退避等待期。需增加 `last_attempt_at TIMESTAMP NULL` 字段（或 `next_retry_at TIMESTAMP NOT NULL`），并相应调整 OutboxRelayer 的轮询查询条件。

### 轻微

7. **MQTT Topic `iot/family/...` 的用途与通信拓扑不一致**：Topic 路由表中定义了 `iot/family/{accountId}/alert` 和 `iot/family/{accountId}/status` 下行 Topic，但 §3.5.1 明确"家属 APP 不直接连接 IoTDA MQTT"。需明确这两个 Topic 的用途——若为内部路由 Topic（IoTDA → 云端应用服务 → WebSocket 转推），说明路由机制；若不需要，则删除。

8. **部署架构图缺失**：需求 §七.5 要求提供 Mermaid flowchart 或文本图描述的部署架构图。当前仅 §3.7.2 提供了 MQTT 信道拓扑图（ASCII），§3.7.1 仅以表格列出组件清单。需增加一张覆盖所有组件之间网络连线关系（含协议标注）的整体部署架构图。

9. **VO-16 DrivingBehaviorCounters 映射归类不准确**：VO-16 被列在策略 B（@ElementCollection）下，但其实际映射描述为嵌入列字段（`hard_braking_count` + `hard_acceleration_count` INTEGER），应归入策略 A（@Embedded）。需将 VO-16 移至策略 A 表格。

10. **各表缺少显式主键生成策略说明**：需求 §一.1 要求说明主键策略。各表均标注了 PK 和类型，但未说明主键是应用层生成 UUID、JPA @GeneratedValue 还是金仓 UUID 函数。需在 §3.1.1 中统一说明聚合根表主键采用应用层生成 UUID，并提供理由（分布式无冲突生成，适合云边独立创建后同步）。

12. **边缘侧 SQLite 环境下乐观锁实现方案未说明**：§3.1.4 详细描述了 JPA @Version 机制，但仅适用于云端。边缘侧使用 JDBC 直连 SQLite，无法使用 @Version。需补充边缘侧 JDBC 层手动构造带版本号条件的 UPDATE 语句（`UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`），受影响行数为 0 时抛出 `OptimisticLockConflict`，保持与云端相同异常语义。

### 诊断报告 §2.3 异常检查表中标注但未提升为正式问题的覆盖缺口

- **分片扩容数据迁移方案缺失**：§3.1.5 仅提及"按数据增长动态扩容"，未给出扩容时的数据迁移方案。
- **DEW 密钥过期/轮转策略未说明**：设计中使用 DEW 管理加密密钥，但未说明密钥的生命周期和轮转策略。

## 历史迭代回顾

分析第 1 轮迭代反馈（iteration_history.md）与当前诊断报告（b_v1_diag_v2.md）的关系：

- **已解决的问题**：无。第 1 轮反馈的 12 个问题在本轮诊断报告中全部重现，无一被解决。
- **持续存在的问题**：全部 12 个问题均为第 1 轮持续至第 2 轮的遗留问题（P1-P6、P7-P12 分别对应迭代历史中的问题 1-6、8-12 及问题 7）。其中：
  - **严重问题（2 个）**持续两轮未修复：P1（分片键缺失）、P2（取模与一致性哈希矛盾）。这两个问题阻塞数据库分片实现，需最优先解决。
  - **中等问题（4 个）**持续未解决：P3（部署标注错误）、P4（主表索引冗余）、P5（EdgeSessionContext 缺失）、P6（边缘仓储不完整）、P11（outbox 缺字段）。
  - **轻微问题（6 个）**持续未解决：P7（Topic 不一致）、P8（架构图缺失）、P9（VO 归类错误）、P10（主键策略缺失）、P12（边缘乐观锁缺失）。
- **新发现的问题**：本轮无全新问题。P11（outbox 缺 last_attempt_at）和 P12（边缘乐观锁）在 v2 诊断中标注为"新增"，但实际在第 1 轮历史中已有对应条目（问题 7 和问题 12），只是本轮诊断中严重程度和分析深度有所提升。

本轮修正目标：逐一解决上述 12 个持续存在的问题，重点优先修复 2 个严重阻塞问题和 4 个中等问题。

## 上一轮产出路径

/home/jasper/AIoT/redeliberations/202606290937_infrastructure_ood/a_v1_design_v2.md

## 用户需求

/home/jasper/AIoT/redeliberations/202606290937_infrastructure_ood/requirement.md
