根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

第 3 轮诊断报告识别出 7 个问题，质询报告确认诊断结论（LOCATED），具体如下：

### 事实错误与逻辑矛盾（4 个）

1. **outbox 广播表策略下的跨分片事务原子性假设未验证**（§3.5.2）— 严重程度：一般。设计断言广播表写入与分片表写入在同一个本地事务中由数据库本地事务保证原子性，但该前提在 ShardingSphere-JDBC LOCAL 模式下的实际行为未经官方文档或实验验证。改进建议：(a) 标注 ShardingSphere-JDBC 目标版本号并说明已验证广播表写入与分片表写入的本地事务原子性；(b) 若未经验证，给出后备方案——outbox 固定于 shard-0（接受最终一致性，补充重试补偿 + SLO 标注）；(c) 或引入 XA 分布式事务作为备选保障路径。

2. **fleet_dashboard_projection 即时增量聚合的跨分片 JOIN 可行性存疑**（§3.2.3 P2 vs §3.5.2）— 严重程度：一般。路径 A 描述实时 JOIN 分布在不同物理数据库的表，ShardingSphere 是否支持跨分片 JOIN 且性能满足 ≤3s SLO 未经验证。改进建议：(a) 将即时聚合数据源从 `safety_alert_event` + `trip` 直接查询改为从 `alert_projection`（同在 shard-0）聚合，在 `alert_projection` 中补充 `fleet_id` 冗余字段；(b) 或标注依赖 ShardingSphere SQL Federation 引擎并说明已验证。

3. **CQRS 投影表写路由机制缺失**（§3.5.2 vs §3.2.3）— 严重程度：中等。`alert_projection` 和 `fleet_dashboard_projection` 配置为"全局只读表固定于 shard-0"，但未说明投影同步器通过 ShardingSphere-JDBC 写入时如何确保数据精确路由至 shard-0。改进建议：(a) 使用 HintManager 强制路由至 shard-0；(b) 为投影表配置独立 datasource 直连 shard-0；(c) 或将投影表作为广播表写入。

4. **文档标题版本号与文件名不一致**（文档第 1 行）— 严重程度：轻微。文件名表明是 v3（`a_v3_copy_from_v2.md`），但标题标注为 v2。改进建议：将标题修正为 `（v3）`。

### 关键遗漏与深度不足（3 个）

5. **@ElementCollection 冻结技术实现中 Hibernate detach()+merge() 行为风险未充分评估**（§3.1.3）— 严重程度：中等。将集合字段置 null 后执行 detach()+merge() 可能导致 flush 时触发面向集合表的 DELETE 语句，意外删除已写入的溢出行数据。改进建议：(a) 改用 StatelessSession 绕过一级缓存和脏检查仅执行 UPDATE；(b) 标注为技术风险项需在编码阶段验证 Hibernate 实际行为；(c) 维护 `snapshotCollectionFrozen` 标志让仓储层跳过级联保存。

6. **VehicleStateBuffer/PhysiologicalDataBuffer 的 ArrayDeque 固定容量实现方式不明确**（§3.4.1、§3.4.2）— 严重程度：轻微。JDK 标准 `ArrayDeque` 为无界队列，设计未说明如何实现"固定容量"语义。改进建议：(a) 使用 `CircularFifoQueue`（Apache Commons Collections 4）；(b) 或在每次 add 前检查容量并手动淘汰最旧元素；(c) 补充窗口溢出时的行为描述。

7. **EdgeSessionContext destroy() 与碰撞检测的时序安全间隙未定义**（§3.4.2a vs §3.7.4）— 严重程度：轻微。`VehicleIgnitionOffEvent` 触发时立即执行 `destroy()` 清空缓冲，但碰撞失能可能在熄火同时或之后才被检测到，导致无法获取事故前窗口数据。改进建议：(a) `destroy()` 延迟至判定引擎确认无待处理安全判定后执行；(b) 或熄火后先等待安全观察窗口（如 5s）再执行销毁。

### 整体评估

产出逐子项响应了需求文档 §一至§七 的 27 个子项，前序 24 个历史问题已全部修复。当前版本无严重级别问题，存在 2 个中等、3 个一般、2 个轻微问题。建议优先标注 ShardingSphere 相关待验证项、补充投影表写路由机制、标注 @ElementCollection 冻结方案为技术风险项。

## 历史迭代回顾

- **已解决的问题**：第 1 轮全部 12 个问题已修复（通过率 100%），第 2 轮全部 12 个问题已修复（通过率 100%），总通过率 24/24。经逐条对照验证，v3 产出中所有历史问题均已完成修复，无遗留。
- **持续存在的问题**：无。本轮 7 个问题均为第 3 轮新识别，尚未在历史迭代中出现过。
- **新发现的问题**：上述 7 个问题均为本轮新发现。需重点关注的问题：问题 3（CQRS 投影表写路由，中等）、问题 5（@ElementCollection 冻结风险，中等）、问题 1 和 2（ShardingSphere 行为未验证，一般）。

## 上一轮产出路径

/home/jasper/AIoT/redeliberations/202606290937_infrastructure_ood/a_v3_copy_from_v2.md

## 用户需求

/home/jasper/AIoT/redeliberations/202606290937_infrastructure_ood/requirement.md
