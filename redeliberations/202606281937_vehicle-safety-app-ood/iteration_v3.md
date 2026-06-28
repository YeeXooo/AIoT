# 再审议判定报告（v3）

## 判定结果

RETRY

## 判定理由

组件B质询结果：LOCATED（审查结论被确认，质询通过）。实际轮次 1 < 最大轮次 12，提前终止。

诊断报告共识别 **9 个问题**：
- **严重** 3 个：问题 1（S2 事件订阅与时序图逻辑矛盾）、问题 2（TokenVerifyResult::INVALID 死代码）、问题 3（ReportData 类型完全未定义）
- **一般** 4 个：问题 4（交叉引用章节号断裂）、问题 5（领域服务方法签名覆盖率不一致）、问题 6（verifyRescueToken 操作校验缺失）、问题 7（无活跃行程时边界条件未定义）
- **轻微** 2 个：问题 8（协作图缺少 EventBus 节点）、问题 9（逐条提交事务可见性未说明）

依据判定标准，审查报告包含严重或一般等级问题 → **RETRY**。组件A需修复上述问题后重新产出。

## 需要解决的问题

- **问题描述**：S2 InterventionServiceImpl 协作关系声明订阅 RiskDeterminedEvent / RiskResolvedEvent，但 §8.2 时序图中 S2 直接接收 LifeDetectedEvent 并生成干预指令，LifeDetectedEvent 未出现在 S2 订阅列表中
- **所在位置**：§3.2 L190 vs §8.2 L1133
- **严重程度**：严重
- **改进建议**：二选一修正——（1）在 §3.2 事件订阅列表中补充 LifeDetectedEvent，并说明其触发干预指令的语义；或（2）修改 §8.2 时序图使 S2 通过 AlertTriggeredEvent（经 Alarm）而非 LifeDetectedEvent 获取干预触发信号

- **问题描述**：VerifyRescueTokenResponse 中 TokenVerifyResult::INVALID 变体与 AppError 错误体系统存在设计冗余——所有无效情形均已通过 Err(AppError) 路径返回，INVALID 变体无任何使用场景
- **所在位置**：§4.5 VerifyRescueTokenResponse（L673–674）、TokenVerifyResult 枚举定义（L676）
- **严重程度**：严重
- **改进建议**：移除 TokenVerifyResult::INVALID 变体，仅保留 VALID，与现有 AppError 体系统一

- **问题描述**：ReportData 类型完全未定义——§4.4 GenerateReportResponse 含 reportData: ReportData 字段，但该类型在全文（§4 DTO 定义、§5 跨层类型定义、§5.5 领域层引用类型表）中均未出现
- **所在位置**：§4.4 GenerateReportResponse（L623–627）
- **严重程度**：严重
- **改进建议**：在 §4.4 中补充 ReportData 结构类型定义，至少包含驾驶行为评分 Summary、风险分布 Map、各维度扣分明细等字段；或如已在领域层定义，列入 §5.5 领域层引用类型表并标注引用位置

- **问题描述**：§10 决策 A4 修订理由中引用的旧版章节号（§5.2、§5.3、§4.3）在当前文档结构下已失效，读者无法定位到正确位置
- **所在位置**：§10 决策 A4 修订理由段落（L1256）
- **严重程度**：一般
- **改进建议**：将引用更新为当前文档实际章节号：§5.2 → §8.2、§5.3 → §8.3、§4.3 → §7.3，并对全文进行交叉引用一致性检查

- **问题描述**：应用服务编排中对领域服务方法的调用签名覆盖率不一致——部分给出了具体方法名，多数场景仅以自然语言描述，开发人员需自行推断应调用的方法
- **所在位置**：§3.1 方法契约表（L156、L157）、§3.4 方法契约表（L319）、§3.5 方法契约表（L256 等）
- **严重程度**：一般
- **改进建议**：为每个应用服务方法的说明补充其所调用的领域服务方法签名，格式统一为「委托 {领域服务名}.{方法名}」

- **问题描述**：§3.5 verifyRescueToken 的校验说明未包含对 requestedOperation 是否在凭证 authorizedOperations 集合内的校验，存在权限跨越风险
- **所在位置**：§3.5 接口方法契约 verifyRescueToken 行（L381）、§4.5 VerifyRescueTokenRequest（L668–671）
- **严重程度**：一般
- **改进建议**：增加操作匹配校验，不匹配时返回 AppError.AccessDenied(OperationNotAuthorized)，并在 AccessDenialReason 枚举中新增 OperationNotAuthorized 变体

- **问题描述**：subscribeDriverStatus 在驾驶员无活跃行程时的 initialSnapshot 行为未定义——各字段是返回空值、默认值还是残留数据未说明
- **所在位置**：§3.3 订阅方法说明（L251）、§4.3 SubscribeDriverStatusResponse（L548–549）、§5.2 DriverStatusSnapshot（L770–780）
- **严重程度**：一般
- **改进建议**：补充无活跃行程时 initialSnapshot 的字段取值规范，同步更新 DriverStatusSnapshot 字段类型为 Option<T>
