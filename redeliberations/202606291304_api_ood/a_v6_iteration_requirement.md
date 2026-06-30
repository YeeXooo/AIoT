根据以下审查结果，迭代上一轮的产出，形成新版的文件，从而更好地满足用户需求。

## 当前审查结果

以下问题选自组件B诊断报告（b_v5_diag_v2.md），审查结论经质询确认为 **LOCATED**。

### 严重问题

1. **接口契约缺失 — createRescueReport 方法未形式化定义（多轮迭代未根本解决）**
   - 位置：a_v5_copy_from_v4.md §1.3 行207–216；对比 docs/ood_application.md §3.5 行397–402
   - 严重程度：严重
   - 改进建议：在 docs/ood_application.md §3.5 `IEmergencyRescueService` 接口方法表中补充 `createRescueReport` 方法行（含输入 `CreateRescueReportRequest`、输出 `CreateRescueReportResponse`、事务属性、异常处理）。API OOD 中"⚠ 接口契约待补"注解应在该方法落地后移除。

### 中等问题

2. **跨层 DTO 不一致 — secondaryAuthToken 字段缺失**
   - 位置：a_v5_copy_from_v4.md §1.3 行154、行193；对比 docs/ood_application.md §4.3 行576–579、行595–598
   - 严重程度：中等
   - 改进建议：在 docs/ood_application.md §4.3 `RequestMediaSessionRequest` 和 `TriggerManualRescueRequest` DTO 中补充 `secondaryAuthToken: String` 字段。

3. **跨层 DTO 不一致 — AlertSummary 缺失 gpsLocation 字段**
   - 位置：a_v5_copy_from_v4.md §1.1 行51；对比 docs/ood_application.md §4.1 行511–517
   - 严重程度：中等
   - 改进建议：在 docs/ood_application.md §4.1 `AlertSummary` DTO 中补充 `gpsLocation: Optional<GeoPoint>` 字段。

4. **ArkTS DTO 类型定义不完整 — AlertType 和 RiskLevel 枚举取值不全**
   - 位置：a_v5_copy_from_v4.md §4.1 行1462–1463、行1485
   - 严重程度：中等
   - 改进建议：在 §4.1 ArkTS DTO 定义前新增独立的类型声明并统一引用：
     ```typescript
     type AlertType = 'FATIGUE' | 'DISTRACTION' | 'ROAD_RAGE' | 'LIFE_DETECTION' | 'COLLISION_DISABILITY' | 'PERFORMANCE_WARNING'
     type RiskLevel = 'L1_HINT' | 'L2_WARNING' | 'L3_CRITICAL'
     ```
     移除各接口中 AlertType 和 RiskLevel 的行内重复注释，统一引用独立类型定义。

5. **S5/S6 端点表缺失"查询参数"列**
   - 位置：a_v5_copy_from_v4.md §1.5 行467–472、§1.6 行563–569
   - 严重程度：中等
   - 改进建议：将 S5 和 S6 端点表扩展为 8 列格式（补充"查询参数"列），将表外散文描述的查询参数纳入表格对应行。

6. **S2 端点表缺失错误响应文档**
   - 位置：a_v5_copy_from_v4.md §1.2 行71–113
   - 严重程度：中等
   - 改进建议：在 §1.2 末尾补充错误响应小节，至少包含：无效参数（400）、行程不存在时返回空集合而非 404、服务不可用（503）。

### 一般问题

7. **跨层 DTO 不一致 — QueryTrajectoryResponse 缺失 dataConsistency 字段**
   - 位置：a_v5_copy_from_v4.md §1.4 行385；对比 docs/ood_application.md §4.4 行765–768
   - 严重程度：一般
   - 改进建议：在 docs/ood_application.md §4.4 `QueryTrajectoryResponse` 中补充 `dataConsistency: DataConsistency` 字段，并定义 `DataConsistency` 枚举（`CONSISTENT | INCONSISTENT`）。

### 轻微问题

8. **前端对接清单遗漏 endMediaSession 端点**
   - 位置：a_v5_copy_from_v4.md §4.1 行1436–1448，对比 §1.3 行127
   - 严重程度：轻微
   - 改进建议：在 §4.1 REST API 调用列表中补充"终止音视频会话"行（DELETE `/api/v1/guardianship/media-session/{sessionHandle}`），响应码 204 无响应体。

9. **S5 内部接口可见性不足（文档可读性改进）**
   - 位置：a_v5_copy_from_v4.md §1.5 行463–556
   - 严重程度：轻微
   - 改进建议：在 §1.5 S5 端点表后添加一段说明，明确 S5 供 S3 内部编排调用的方法（待应用层 OOD 补全 `createRescueReport` 后，此处补充交叉引用）。

10. **MQTT 主题模板 `${commandId}` 与其余 `{variable}` 语法不一致**
    - 位置：a_v5_copy_from_v4.md §2.1 行702
    - 严重程度：轻微
    - 改进建议：将行702的 `${commandId}` 统一为 `{commandId}`。

11. **S4 错误响应码覆盖不完整**
    - 位置：a_v5_copy_from_v4.md §1.4 行323–460
    - 严重程度：轻微
    - 改进建议：在 §1.4 末尾补充错误响应小节，至少包含：400（参数无效）、401（未经认证）、404（资源不存在）、504（报告生成超时，已有）。

## 历史迭代回顾

### 已解决的问题（出现在历史反馈但当前不再提及）
- **SparkRTC 会话时长豁免机制未定义**（迭代第4轮问题4）：已在 v6 中补充完整
- **S3→S5 救援记录的 ID 体系断裂**（迭代第3轮问题1）：已在 v4 中解决
- **新增 API 端点未映射到应用层方法**（迭代第3轮问题2，第2轮问题1）：已在 v5 中落地
- **familyAccountId 横向越权风险**（迭代第3轮问题3）：已补充安全门控规则
- **MQTT Topic cmd/media/join/down 定义缺失**（迭代第3轮问题4）：已补充
- **S4 QueryTrajectoryResponse 缺失 dataConsistency 字段示例**（迭代第3轮问题5）：JSON 示例已补充（但应用层 DTO 仍未补齐，见当前问题11）
- **§4.1 遗漏 requestMediaSession 端点**（迭代第3轮问题6）：已补充

### 持续存在的问题（多轮反馈中反复出现，需重点解决）
- **createRescueReport 接口契约缺失**：迭代第4轮 → 当前问题1，虽已加"⚠ 接口契约待补"注解但根本问题未解除
- **secondaryAuthToken 跨层不一致**：迭代第4轮问题2&3 → 当前问题2，API OOD 侧已定义但应用层 OOD DTO 始终未补齐
- **dataConsistency 跨层不一致**：迭代第3轮问题5（JSON示例缺失）→ 当前问题11（应用层 DTO 缺失），JSON 示例已补充但应用层 DTO 遗漏

### 新发现的问题（本轮新识别）
- AlertSummary 缺失 gpsLocation 字段（问题3）
- ArkTS 类型定义不全（问题4）
- S5/S6 端点表缺查询参数列（问题7）
- S2 端点表缺失错误响应文档（问题8）
- endMediaSession 端点遗漏（问题5）
- S5 内部接口可见性建议（问题6）
- MQTT `${commandId}` 语法不一致（问题9）
- S4 错误响应码不完整（问题10）

## 上一轮产出路径
/home/jasper/AIoT/redeliberations/202606291304_api_ood/a_v5_copy_from_v4.md

## 用户需求
/home/jasper/AIoT/redeliberations/202606291304_api_ood/requirement.md
