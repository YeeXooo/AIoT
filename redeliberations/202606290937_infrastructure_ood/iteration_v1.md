# 再审议判定报告（v1）

## 判定结果

RETRY

## 判定理由

组件B诊断报告（b_v1_diag_v2.md）共识别出 12 项问题，其中严重等级 2 项（问题 1：`road_rage_voice_record` 表分片键缺失；问题 2：分片策略取模运算与一致性哈希/动态扩容目标矛盾）、中等等级 5 项（问题 3：EmergencyRescueService 部署标注错误；问题 4：SafetyAlertEvent 主表冗余复合索引；问题 5：EdgeSessionContext 管理机制缺失；问题 6：边缘侧仓储实现仅描述 1/5；问题 11：outbox 表缺少 `last_attempt_at` 字段）、轻微等级 5 项（问题 7–10、问题 12）。组件B质询报告（b_v1_challenge_v2.md）对诊断报告进行 LOCATED 确认，未发现诊断报告存在事实错误或逻辑矛盾，审查结论有效。

组件B内部循环实际轮次 2 小于最大轮次 12，质询结论为 LOCATED（审查被确认），非因循环耗尽而终止。

根据判定标准：审查报告包含严重和一般（中等）等级的问题，满足 RETRY 条件。

## 需要解决的问题

- **问题描述**：`road_rage_voice_record` 表分片键 `vehicle_id` 不存在于表结构中
- **所在位置**：§3.1.5（分库分表策略）与 §3.1.1（表结构）的矛盾
- **严重程度**：严重
- **改进建议**：为该表增加冗余 `vehicle_id` VARCHAR(64) NOT NULL 列并补充到 §3.1.1 表结构中；或改为按 `driver_id` 哈希分片

- **问题描述**：分片策略使用取模运算 `hash(vehicle_id) % 4` 而非一致性哈希，与 §3.1.5 的动态扩容目标和需求 §五.2 要求矛盾
- **所在位置**：§3.5.2
- **严重程度**：严重
- **改进建议**：将路由算法改为 ShardingSphere-JDBC 一致性哈希分片算法，或明确声明固定 4 片不扩容并给出全量重分布迁移方案

- **问题描述**：EmergencyRescueService 被标注为边缘部署，但其职责依赖云端网络连通性
- **所在位置**：§3.3.6
- **严重程度**：一般
- **改进建议**：将标注改为"EmergencyRescueService（云端）"

- **问题描述**：SafetyAlertEvent 主表上建有 `(driver_id, occurred_at DESC, alert_type, risk_level)` 和 `(vehicle_id, occurred_at DESC)` 两条复合索引，但读查询已全部路由至 CQRS 投影表，这些索引冗余且拖累写性能
- **所在位置**：§3.1.2
- **严重程度**：一般
- **改进建议**：主表仅保留 `(trip_id)` FK 索引，将两条读侧索引限定于 `alert_projection` 投影表

- **问题描述**：EdgeSessionContext 在基础设施层未定义实例化方式、生命周期管理机制和进程重启恢复策略
- **所在位置**：§3.4.1、§3.4.2
- **严重程度**：一般
- **改进建议**：补充 EdgeSessionContext 的 Spring 集成方式、创建/销毁触发机制、与 VehicleStateBuffer/PhysiologicalDataBuffer 的绑定关系及重启恢复策略

- **问题描述**：边缘侧仅描述了 RoadRageVoiceRecordRepository 的 SQLite 实现，TripRepository、DriverRepository、VehicleRepository、SystemAccountRepository 的边缘实现策略均未描述
- **所在位置**：§3.2.2
- **严重程度**：一般
- **改进建议**：新增"边缘侧仓储实现"小节，说明各仓储在 SQLite 环境下的实现策略

- **问题描述**：outbox 表缺少指数退避重试所需的 `last_attempt_at` 字段，OutboxRelayer 无法判断各事件的重试等待期是否已过
- **所在位置**：§3.3.2 与 §3.3.5 的矛盾
- **严重程度**：一般
- **改进建议**：增加 `last_attempt_at TIMESTAMP NULL` 字段或 `next_retry_at TIMESTAMP NOT NULL` 字段

- **问题描述**：MQTT Topic `iot/family/...` 的用途与家属 APP 不连接 MQTT 的说明不一致
- **所在位置**：§3.5.1
- **严重程度**：轻微
- **改进建议**：明确这两个 Topic 的用途（内部路由还是可删除）

- **问题描述**：需求 §七.5 要求的部署架构图缺失，§3.7.2 仅覆盖 MQTT 信道拓扑
- **所在位置**：需求 §七.5 vs 设计 §3.7
- **严重程度**：轻微
- **改进建议**：增加 Mermaid flowchart 或 ASCII 文本图覆盖所有组件的网络连接关系

- **问题描述**：VO-16 DrivingBehaviorCounters 被错误归入策略 B（@ElementCollection），实际为策略 A（@Embedded）
- **所在位置**：§3.1.3
- **严重程度**：轻微
- **改进建议**：将 VO-16 移至策略 A 表格中

- **问题描述**：聚合根表主键生成策略未说明（应用层 UUID、JPA @GeneratedValue 还是金仓 UUID 函数）
- **所在位置**：§3.1.1
- **严重程度**：轻微
- **改进建议**：统一说明聚合根表主键采用应用层生成 UUID，理由为分布式无冲突生成

- **问题描述**：边缘侧 SQLite 环境下乐观锁实现方案未说明（无法使用 JPA @Version）
- **所在位置**：§3.1.4
- **严重程度**：轻微
- **改进建议**：补充边缘侧 JDBC 层手动构造带版本号条件的 UPDATE 语句实现乐观锁
