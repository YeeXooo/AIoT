# 实现计划

任务描述：项目骨架（0.2 + 0.3 + 0.4）— Maven 依赖补齐、基础类型定义、Spring Boot 入口与配置
项目根目录：/home/jasper/AIoT

---

## R1 FAILED Maven 依赖补齐
结果：pom.xml 已按设计修改，mvn dependency:resolve BUILD SUCCESS，测试代码（PomXmlTests.java）已编写
测试：PomXmlTests.java（19 个用例未执行）
失败原因：验证步骤使用了 `cjpm test`（仓颉构建工具），但本项目是 Maven/Java 工程，不存在 `cjpm.toml`。应使用 `mvn test` 执行 JUnit 测试。

## R2 RETRY Maven 依赖补齐
任务：同 R1 — 重新验证 Maven 依赖补齐。代码和测试无需修改，仅需用 `mvn test`（或 `mvn -f code/server/pom.xml test`）替代 `cjpm test` 执行测试验证。
选择理由：R1 代码实现正确（mvn dependency:resolve 成功），失败纯因验证工具选错（cjpm vs mvn），修正后应能通过。
修正方向：使用 `mvn -f code/server/pom.xml test` 运行 JUnit 测试；验证通过后标记 PASSED。
上下文：pom.xml 及 PomXmlTests.java 状态同 R1，无需改码。

---

## R2 PASSED Maven 依赖补齐
结果：pom.xml 已补齐 websocket / jackson-jsr310 / flyway-core / flyway-database-postgresql(10.10.0) / lombok 五组依赖；`<properties>` 覆盖 flyway.version=10.10.0。涉及文件 `code/server/pom.xml`。
测试：PomXmlTests.java + AiotApplicationTests.java，`mvn -f code/server/pom.xml test` BUILD SUCCESS，Tests run: 23, Failures: 0。
说明：0.2 完成。0.4（Spring Boot 入口与配置）的产物 AiotApplication.java / application.yml / application-ci.yml 均已存在于基线且 Spring 上下文加载测试通过，视为已满足；剩余唯一未实现部分为 0.3 基础类型定义。

## R3 NEW Result<T,E> 统一结果类型
任务：实现 `com.aiot.domain.shared.Result<T,E>` sealed interface 及 `Ok<T>` / `Err<E>` record，含工厂方法与查询/解包行为。文件 `code/server/src/main/java/com/aiot/domain/shared/Result.java`。
选择理由：0.2 已完成、0.4 已满足，进入 0.3。Result 是被领域层全部服务方法契约广泛引用的核心结果承载抽象（ood_domain.md §五 Result 语义），且为含行为的类型，单独成任务、底层优先。
上下文：需求 0.3.1；ood_domain.md 多处使用 `Result<T, E>` 方法返回模式（如 DS-02~DS-16）。AggregateId/ID 类型与 AppError 作为后续独立任务。
