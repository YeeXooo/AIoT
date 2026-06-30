# 再审议判定报告（v3）

## 判定结果

RETRY

## 判定理由

组件B诊断报告识别出 4 个一般等级问题和 3 个轻微等级问题。一般等级问题包括：(1) outbox 广播表策略下跨分片事务原子性假设未经验证；(2) fleet_dashboard_projection 即时增量聚合的跨分片 JOIN 可行性存疑；(3) CQRS 投影表写路由机制缺失；(4) @ElementCollection 冻结技术实现中 Hibernate detach()+merge() 行为风险未充分评估。组件B质询报告结论为 LOCATED，确认审查结论成立。组件B内部循环实际轮次 3 < 最大轮次 12，提前终止且审查被确认。依据判定标准，审查报告包含一般等级问题，判定为 RETRY。

## 需要解决的问题

- **问题描述**：outbox 广播表策略下的跨分片事务原子性假设未验证——§3.5.2 断言广播表写入与分片表写入在同一个本地事务中由数据库本地事务保证原子性，但该前提在 ShardingSphere-JDBC LOCAL 模式下的实际行为未经官方文档或实验验证，存在事务原子性断裂风险
- **所在位置**：§3.5.2（金仓数据库连接与分片配置）
- **严重程度**：一般
- **改进建议**：标注 ShardingSphere-JDBC 目标版本号并说明已验证广播表写入与分片表写入的本地事务原子性；或给出后备方案（outbox 固定于 shard-0 接受最终一致性 / 引入 XA 分布式事务）

- **问题描述**：fleet_dashboard_projection 即时增量聚合的跨分片 JOIN 可行性存疑——§3.2.3 P2 路径 A 描述实时 JOIN 三个分布在不同物理数据库的表，ShardingSphere 是否支持跨分片 JOIN 且性能满足 ≤3s SLO 未经验证
- **所在位置**：§3.2.3 P2 vs §3.5.2
- **严重程度**：一般
- **改进建议**：将即时聚合数据源改为从 `alert_projection`（同在 shard-0）聚合，补充 `fleet_id` 冗余字段避免跨分片；或明确标注依赖 ShardingSphere SQL Federation 引擎并说明已验证

- **问题描述**：CQRS 投影表写入路由机制缺失——§3.5.2 将 `alert_projection` 和 `fleet_dashboard_projection` 配置为"全局只读表固定于 shard-0"，但未说明投影同步器通过 ShardingSphere-JDBC 写入时如何确保数据精确路由至 shard-0
- **所在位置**：§3.5.2 vs §3.2.3
- **严重程度**：一般
- **改进建议**：明确投影表写入路由方式——使用 HintManager 强制路由至 shard-0 / 配置独立 datasource 直连 shard-0 / 或将投影表作为广播表写入

- **问题描述**：@ElementCollection 冻结技术实现中 Hibernate detach()+merge() 行为风险未充分评估——将集合字段置 null 后执行 detach()+merge() 可能导致 flush 时触发面向集合表的 DELETE 语句，意外删除已写入的溢出行数据
- **所在位置**：§3.1.3 策略 B「集合冻结技术实现」
- **严重程度**：一般
- **改进建议**：改用 StatelessSession 绕过一级缓存和脏检查仅执行 UPDATE；或标注为技术风险项需在编码阶段验证 Hibernate 实际行为；或维护 `snapshotCollectionFrozen` 标志让仓储层跳过级联保存

- **问题描述**：文档一级标题版本号标注为 `v2`，与文件名表明的 v3 版本矛盾
- **所在位置**：文档第 1 行标题
- **严重程度**：轻微
- **改进建议**：将标题修正为 `（v3）`

- **问题描述**：VehicleStateBuffer/PhysiologicalDataBuffer 描述为基于 `ArrayDeque`（固定容量）实现，但 JDK 标准 `ArrayDeque` 为无界队列，未说明如何实现固定容量语义
- **所在位置**：§3.4.1、§3.4.2
- **严重程度**：轻微
- **改进建议**：使用 `CircularFifoQueue` 或在每次 add 前检查容量并手动淘汰最旧元素，补充窗口溢出行为描述

- **问题描述**：EdgeSessionContext destroy() 在 VehicleIgnitionOffEvent 触发时立即执行清空缓冲，但碰撞失能判定可能在此之后才检测到，导致无法获取事故前时间窗口数据
- **所在位置**：§3.4.2a vs §3.7.4
- **严重程度**：轻微
- **改进建议**：将 destroy() 延迟至判定引擎确认无待处理安全判定后执行，或熄火后先等待安全观察窗口再执行销毁
