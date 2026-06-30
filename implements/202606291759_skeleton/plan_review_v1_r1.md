# 计划审查报告（v1 r1）

## 审查结果
REJECTED

## 发现

- **[严重]** 计划覆盖范围严重不足。`plan.md` 标题声称涵盖完整的"项目骨架（0.2 + 0.3 + 0.4）"，但实际只有 R1（Maven 依赖补齐），完全遗漏了 0.3（基础类型定义）和 0.4（Spring Boot 入口与配置）的任务拆分。例如 `Result<T,E>`、`AggregateId`、`AppError` 密封接口、具体 ID 类型、`application.yml` 配置修改等均无对应任务项。计划与其描述不一致，导致后续环节无法获知完整工作范围。

- **[一般]** 计划未沿用 `task_v1.md` 中已明确的 Lombok `<scope>provided</scope>` 约束，R1 描述中只列出依赖名称而未提及 scope，增加了编码环节出错的可能。

- **[一般]** 计划未识别现有 `application.yml` 与需求之间的差异：当前配置 `ddl-auto: create-drop` / `show-sql: false`，而需求要求 `ddl-auto: validate` / `show-sql: true`；当前缺少 `server.port: 8080` 和 Flyway 配置；H2 当前为 `test` scope 但开发环境需在 default profile 可用。这些差异在计划中未被标注为规划事项。

- **[一般]** `AiotApplication.java` 已存在（含 `@SpringBootApplication` 和 main），0.4 需求是否仅为确认已有文件、还是需要修改/扩展，计划中未做任何说明。

## 修改要求（仅 REJECTED 时）

1. **计划覆盖范围**：补充 0.3（基础类型定义）和 0.4（Spring Boot 入口与配置）的任务拆分。每个子任务应明确：具体产出文件、涉及的 Java 类型或配置项、依赖的前置条件。例如 R2 应按类型列出 `Result<T,E>`、`AggregateId`、各 `*Id` 记录类、`AppError` 的 variant 列表及其文件路径 `com/aiot/domain/shared/`。

2. **Lombok scope**：在 Maven 依赖计划项中明确标注 Lombok 的 `<scope>provided</scope>`。

3. **配置差异**：在 0.4 相关计划项中列出 `application.yml` 当前值与目标值的差异项（ddl-auto、show-sql、server.port、Flyway、H2 scope），确保编码环节不会遗漏。

4. **AiotApplication 状态**：说明 `AiotApplication.java` 是否已满足需求、是否需要改动，消除歧义。
