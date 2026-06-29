# 质量审查诊断报告（第 6 轮）

## 审查范围

- 审查产出：`a_v6_copy_from_v5.md`（API/接口层 OOD 设计 v6）
- 用户需求：`requirement.md`
- 历史迭代：已覆盖 5 轮审查，累计识别 30+ 问题
- 审查侧重：需求响应充分度、事实错误/逻辑矛盾、深度和完整性（避免重复验证已确认维度）

## 审查结论

产出在整体结构和五个核心章节（REST API、MQTT、WebSocket/SparkRTC、ArkTS 前端、安全设计）上覆盖了需求所要求的全部主题领域。以下 5 个问题集中于跨层一致性、文档内格式一致性和概念定义的完整性。

---

## 问题清单

### 问题 1：S3 端点表表头缺失"查询参数"列——格式不一致

- **问题描述**：S3 RemoteGuardianshipService 端点表（§1.3 行 129）表头为 7 列：`端点 | 方法 | 路径 | 请求体 | 响应体 | HTTP 状态码 | 认证`，缺少"查询参数"列。而 S1（行 20）、S2（行 79）、S4（行 332）、S5（行 478）、S6（行 574）均使用 8 列表头（含"查询参数"列）。即使 S3 当前所有端点均不使用查询参数，文档格式与其余五节不一致，遵循者无法确定是设计意图还是疏漏。
- **所在位置**：§1.3，行 129（S3 端点表头行）
- **严重程度**：轻微
- **改进建议**：将 S3 端点表头扩展为 8 列（补充"查询参数"列），各端点的查询参数列填 `—`。

### 问题 2：S4 端点表被 blockquote 注释插入导致表格结构断裂

- **问题描述**：S4 端点表中，报告文件下载行（行 339）与绩效预警订阅行（行 345）之间插入了一个 `> **下载响应头说明**：` blockquote 注释块（行 341–344）。这导致 Markdown 表格在此处断裂为两张独立的表——上一张表以报告文件下载行结束，下一张表以绩效预警订阅行作为新表起始。文档解析工具（如将 Markdown 转为 HTML/PDF）可能因此产生渲染偏差，且结构分裂降低可读性。
- **所在位置**：§1.4，行 339–345
- **严重程度**：轻微
- **改进建议**：将下载响应头说明块移至端点表之后（§1.4 段落末尾或独立小节），恢复 S4 端点表为单张连续表格。

### 问题 3：StatusColor 枚举值跨层不一致——应用层引入未定义的 ORANGE 值

- **问题描述**：`GetDriverRiskStatusResponse` 中 `derivedStatusColor` 字段的取值在三个文档间不一致：
  - 领域层 OOD（`docs/ood_domain.md`）VO-15 定义 `StatusColor` 为 `GREEN | YELLOW | RED`（三值）。
  - API OOD（本产出 §1.1 行 37）描述为 `GREEN / YELLOW / RED`（三值），ArkTS 类型（§4.1 行 1471）亦为 `'GREEN' | 'YELLOW' | 'RED'`。
  - 应用层 OOD（`docs/ood_application.md` §4.1 行 498）将 `StatusColor` 描述为 `GREEN / YELLOW / ORANGE / RED`（四值），多了 `ORANGE`。
  领域层和 API 层均不含 `ORANGE`，应用层却列出此值，构成跨层定义矛盾。下游实现者面对三份文档中同名字段的三种取值约定将无法确定应以哪个为准。此外，`ORANGE` 与 `YELLOW`（L2 对应）和 `RED`（L3 对应）在语义上是否重叠也未得到解释。
- **所在位置**：§1.1 行 37（API OOD），对比 `docs/ood_application.md` §4.1 行 498
- **严重程度**：一般
- **改进建议**：在 `docs/ood_application.md` §4.1 `GetDriverRiskStatusResponse` 的 `derivedStatusColor` 字段说明中，移除 `ORANGE` 或明确 `ORANGE` 的语义来源与触发条件。建议统一为三值 `GREEN / YELLOW / RED`，与领域层定义保持一致。

### 问题 4：§3.2 高危失能豁免机制引入了 `LIFE_DETECTION_PROLONGED` 概念但缺乏跨层形式化定义

- **问题描述**：§3.2"高危失能场景会话时长豁免机制"（行 1427）将 `LIFE_DETECTION_PROLONGED`（活体检测持续异常 ≥60s）列为豁免触发条件之一，与 `COLLISION_DISABILITY` 并列。然而：
  - 领域层 OOD 的 AlertType 枚举（VO-02）仅定义 `LIFE_DETECTION`，不存在 `LIFE_DETECTION_PROLONGED` 变体。
  - 领域层的事件类型仅有 `LifeDetectedEvent`，没有"持续异常 ≥60s"对应的独立事件或判定方法。
  - 应用层 OOD（§3.3）中高危激活流程使用 `LifeDetectedEvent` 驱动预判，未涉及"持续 ≥60s"这个时长维度的独立判定。
  `LIFE_DETECTION_PROLONGED` 本质上是一个新概念——它引入了一个时间累积判定的语义（不是单一的检出事件，而是持续异常达阈值），但这一判定逻辑在领域层和应用层中均无对应的形式化定义（判定服务方法、事件类型、状态追踪机制），API 层直接引用该概念将导致下游实现者无法确定其判定依据和数据来源。
- **所在位置**：§3.2，行 1427
- **严重程度**：一般
- **改进建议**：二选一：(a) 在领域层 OOD 或应用层 OOD 中补充对"活体检测持续异常 ≥60s"的判定逻辑形式化定义（如在 LifeDetectionService 中定义 `evaluateProlongedCondition` 方法、产出独立事件或在 API OOD 中说明该条件由 LifeDetectedEvent 的持续时长计算派生）；(b) 在 API OOD 中将 `LIFE_DETECTION_PROLONGED` 替换为对既有概念的引用（如"RiskMonitoringService 基于 LifeDetectionService 判定活体持续异常且累计时长超过 60s"），明确此为判定逻辑描述而非新的事件类型或 AlertType。

### 问题 5：§5.1 标题声明 OAuth2 但正文未展开——标题与内容不匹配

- **问题描述**：需求 §5 要求覆盖"API 认证（JWT/OAuth2）"。§5.1 标题为"API 认证——JWT / OAuth2"（行 1725），但正文（行 1727–1747）仅描述了 JWT 的签发与校验流程（Header/Payload/Signature 结构、签发流程 5 步、角色→权限映射），未涉及 OAuth2 的任何设计要素——如授权服务器端点、grant type（authorization_code / client_credentials）、scope 定义、token 类型（access token / refresh token 与 JWT 的关系）、OAuth2 与 JWT 在本系统中的分层关系（OAuth2 作为认证框架，JWT 作为 token 格式）等。标题暗示文档将覆盖 OAuth2 的设计，但正文内容与这一暗示不匹配。如果设计中不需要 OAuth2（即仅使用 JWT 作为独立认证方案），标题不应包含 OAuth2；如果需要 OAuth2，则当前内容存在关键遗漏。
- **所在位置**：§5.1，行 1725–1747
- **严重程度**：轻微
- **改进建议**：二选一：(a) 若设计不采用 OAuth2，将标题改为"#### 5.1 API 认证——JWT"，移除 OAuth2 引用；(b) 若设计需要 OAuth2，补充 OAuth2 授权服务器角色、支持的 grant type、scope 与 JWT 中 `scope` 字段的映射关系、token endpoint 等设计要点。

---

## 整体评估

产出已覆盖需求要求的五个设计主题，经过 5 轮迭代修复后，大部分历史问题已解决（REST 端点完整性、MQTT Payload 覆盖率、SparkRTC/WebSocket 信令、ArkTS 前端对接模型、安全设计各子项）。当前剩余的 5 个问题均为相对轻微的格式一致性和跨层定义对齐问题，倾向于可快速修复的层面。

未发现严重的事实错误或逻辑矛盾。`createRescueReport` 接口定义缺失为已知持续性问题（已在多轮中记录），本次不重复标记。
