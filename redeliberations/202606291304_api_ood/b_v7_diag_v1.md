# API/接口层 OOD 质量审查报告（v7 第1轮）

> 审查对象：`a_v7_copy_from_v6.md`（内部标题声明 a_v8 / v8）
> 审查轮次：第7次迭代
> 审查视角：需求响应充分度、事实错误与逻辑矛盾、整体深度与完整性（排除内部审议已验证的技术可行性维度）

---

## 问题清单

### 问题 1（严重·跨层 DTO 不一致）

**问题描述**：API OOD 的 `RequestMediaSessionResponse` 定义了 4 个字段（`sessionHandle`、`sessionToken`、`sparkRTCRoomId`、`sparkRTCJoinToken`），但应用层 OOD（`docs/ood_application.md` §4.3）的同名 DTO 仅定义了 2 个字段（`sessionHandle`、`sessionToken`），缺少 `sparkRTCRoomId` 和 `sparkRTCJoinToken`。前端需要这两个字段才能加入 SparkRTC 房间，应用层 DTO 缺失将导致这两个字段在反序列化时丢失，前端无法获取入房凭证。

这是之前 7 轮迭代中未被识别的跨层不一致问题。搜索 `docs/ood_application.md` 全文无 `sparkRTCRoomId` 或 `sparkRTCJoinToken` 出现。

- **所在位置**：API OOD §1.3 `RequestMediaSessionResponse` JSON 示例（行 166-174）及 §4.1 ArkTS `RequestMediaSessionResp`（行 1487-1492），对比 `docs/ood_application.md` §4.3 `RequestMediaSessionResponse`（行 583-585）
- **严重程度**：严重
- **改进建议**：在 `docs/ood_application.md` §4.3 的 `RequestMediaSessionResponse` DTO 中补充 `sparkRTCRoomId: String` 和 `sparkRTCJoinToken: String` 字段。

---

### 问题 2（一般·需求响应缺陷）

**问题描述**：需求 `requirement.md:43` 要求 API 安全设计覆盖 "JWT/OAuth2"，API OOD §5.1 标题经 v8 修订改为仅 "API 认证——JWT"（原含 OAuth2），v8 修订说明明确 "本设计不采用 OAuth2 协议"。但文档未提供排除 OAuth2 的设计理由或适用性评估——需求中并列列出两方案，读者无法判断 OAuth2 是被评估后排除还是被遗漏。这削弱了需求响应充分度。

- **所在位置**：API OOD §5.1 标题及 v8 修订说明（行 1954），对比 `requirement.md:43`
- **严重程度**：一般
- **改进建议**：在 §5.1 开篇补充一段 OAuth2 排除说明，阐述不作采用的设计理由（如：本项目为内部系统间调用，OAuth2 授权码流程的复杂度与本系统角色模型不匹配；家属 APP/车队大屏为第一方应用，JWT 的 Bearer Token 模式更适配当前架构）。

---

### 问题 3（一般·接口契约不完整）

**问题描述**：§5.2 二次身份验证流程（行 1775）引用 `POST /api/v1/auth/secondary-verify` 端点，但该端点未出现在 §1 REST API 契约的任何端点表中，也未列入 §4.1 家属 APP REST API 调用清单。家属 APP 开发者无法从本文档获得该端点的完整规范（请求体字段、响应体结构、HTTP 状态码、认证要求）。

- **所在位置**：§5.2 二次身份验证 Token 流程（行 1773-1781），对比 §1 REST API 全部端点表及 §4.1 REST API 调用列表
- **严重程度**：一般
- **改进建议**：二选一：(a) 在 §1 新增一个独立小节（如 §1.7 认证服务端点）或归入 §1.3 S3 中，正式定义 `POST /api/v1/auth/secondary-verify` 的请求体/响应体/状态码；(b) 若该端点属于 IAM 基础设施而非应用服务范畴，在引用处明确标注其归属及详细规范的外部参考位置。

---

### 问题 4（一般·ArkTS DTO 类型定义不完整）

**问题描述**：v7 修订（见修订说明 v7 问题 4）为 `AlertType` 和 `RiskLevel` 新增了独立的 TypeScript 类型别名定义，但以下 13 个在 ArkTS 接口中被引用的枚举/字面量类型仍仅以行内注释形式存在（如 `derivedStatusColor: StatusColor  // 'GREEN' | 'YELLOW' | 'RED'`），缺少独立的 `type` 声明。前端开发者复制这些接口定义后需自行补充类型别名，增加对接成本：

- `StatusColor`（行 1471）
- `MediaSessionType`（行 1483）
- `RescueRequestStatus`（行 1511）
- `WindowControlOperation`（行 1518）
- `WindowPosition`（行 1519）
- `WindowState`（行 1530）
- `WindowOperationResult`（行 1532）
- `GuardianshipPermissionType`（行 1545）
- `CareRelationshipStatus`（行 1552）
- `SparkRTCRole`（行 1560）
- `TripStatus`（行 1576）
- `AccessGrantReason`（行 1609）
- `AccessRevokeReason`（行 1614）

- **所在位置**：§4.1 ArkTS 数据模型定义（行 1462-1616）
- **严重程度**：一般
- **改进建议**：参照 v7 对 `AlertType` 和 `RiskLevel` 的处理方式，为上述 13 个类型各新增独立的 `type` 别名声明，统一替换接口中的行内注释为类型引用。

---

### 问题 5（轻微·版本标识不一致）

**问题描述**：文件标题行声明 "a_v8 / v8"（行 1），但文件名是 `a_v7_copy_from_v6.md`，且在迭代记录中处于第 7 轮迭代。文件名、文档内标题、迭代轮次三者版本号不同，可能误导下游消费者选择错误的版本。

- **所在位置**：文件名 `a_v7_copy_from_v6.md` vs 文档标题行（行 1）vs 迭代轮次编号
- **严重程度**：轻微
- **改进建议**：统一版本标识——若当前文档反映 v8 修订内容，将文件名更新为 `a_v8.md` 或 `a_v8_output.md`；若保留 v7 文件名则应修正标题为 "a_v7 / v7"。

---

## 已知持续性问题（历史迭代已识别，本轮未新增发现）

以下问题在历史迭代中已被识别，本报告不作重复评审，仅列明供上下文参考：

| 问题简述 | 首次识别轮次 | 当前状态 |
|---------|:---:|------|
| `IEmergencyRescueService.createRescueReport()` 方法未在应用层接口表形式化定义 | 第4轮 | 待应用层 OOD 补充 |
| `RequestMediaSessionRequest` 缺 `secondaryAuthToken`（应用层 DTO） | 第4轮 | 待应用层 OOD 补充 |
| `TriggerManualRescueRequest` 缺 `secondaryAuthToken`（应用层 DTO） | 第4轮 | 待应用层 OOD 补充 |
| `AlertSummary` 缺 `gpsLocation`（应用层 DTO） | 第5轮 | 待应用层 OOD 补充 |
| `QueryTrajectoryResponse` 缺 `dataConsistency`（应用层 DTO） | 第5轮 | 待应用层 OOD 补充 |
| `StatusColor` 应用层引入 `ORANGE`，与领域层三值 (`GREEN/YELLOW/RED`) 不一致 | 第6轮 | 待应用层 OOD 修正 |

---

## 整体评价

产出在 REST API 契约完整性、MQTT 主题路由覆盖、WebSocket 信令协议和全链路安全设计方面已较为成熟。第 1 轮迭代时识别的 6 个基础问题经多轮修订已全部解决。当前主要质量风险集中在**跨层 DTO 一致性**（问题 1 为新发现的严重不一致）和**ArkTS 对接契约的类型完备性**（问题 4）。持续 7 轮未根本解决的跨层 DTO 问题（`secondaryAuthToken` 缺失、`createRescueReport` 接口契约缺失等）根因均在下游应用层 OOD，建议在下一轮迭代中将修复重心转向 `docs/ood_application.md` 的全面修订。
