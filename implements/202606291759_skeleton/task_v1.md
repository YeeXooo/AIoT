# 任务指令（v1）

## 动作
NEW

## 任务描述
在 `code/server/pom.xml` 中新增以下 Maven 依赖：
- `spring-boot-starter-websocket`
- `jackson-datatype-jsr310`（Java 8 时间序列化）
- `flyway-core` + `flyway-database-postgresql`（数据库迁移）
- `org.projectlombok:lombok`（可选，提供 `@Getter` / `@AllArgsConstructor` 等便利注解）

现有依赖已有：spring-boot-starter、spring-boot-starter-data-jpa、spring-boot-starter-web、spring-boot-starter-validation、postgresql、h2（test scope）。不引入华为云 SDK。

## 选择理由
构建系统是所有 Java 代码的底层依赖。WebSocket 是后续应用服务（如 S3 RemoteGuardianshipService）的传输基础，Flyway 是数据库迁移基础设施，Jackson JSR310 是 Java 8 时间类型的序列化必需，Lombok 可减少样板代码。这些依赖需要在任何 Java 类型定义之前完成。

## 任务上下文
- Spring Boot 版本：3.2.5（由 parent POM 管理，无需指定版本）
- Java 版本：17
- Flyway 需同时引入 `flyway-core` 和 `flyway-database-postgresql`
- Lombok 的 scope 为 `provided`（编译期注解处理，运行时无需）

## 已有代码上下文
- `code/server/pom.xml` 已存在，含 Spring Boot parent POM、基础依赖和 maven-checkstyle-plugin
- `code/server/src/main/java/com/aiot/AiotApplication.java` 已存在（@SpringBootApplication）
- H2 当前 scope 为 `test`，后续需要调整为 `runtime` 以支持开发环境，但本任务不涉及

## 修订说明（v1 r1）

| 审查意见 | 修改措施 |
|---------|---------|
| 计划覆盖范围严重不足，遗漏 0.3（基础类型定义）和 0.4（Spring Boot 入口与配置）拆分 | 在 plan.md 追加 R1 NEW "基础类型定义（0.3）"和 R1 NEW "Spring Boot 入口与配置（0.4）"，明确各任务产出文件、类型清单和配置差异项 |
| 计划未标注 Lombok `<scope>provided</scope>` | plan.md R1 描述中已补注 `<scope>provided</scope>`；本任务上下文（第22行）原本已明确 |
| 计划未识别 application.yml 当前值与目标值差异 | 在 0.4 任务中逐条列出差异：`ddl-auto: create-drop→validate`、`show-sql: false→true`、缺 `server.port: 8080`、缺 Flyway 配置、H2 scope 需调整 |
| AiotApplication.java 状态未说明 | 在 0.4 任务中明确标注：已含 `@SpringBootApplication` + main，**已满足需求无需改动** |
