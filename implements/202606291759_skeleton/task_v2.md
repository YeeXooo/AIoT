# 任务指令（v2）

## 动作
RETRY

## 任务描述
Maven 依赖补齐 — 重新验证。代码和测试无需修改（pom.xml 已按设计修改完成，PomXmlTests.java 已编写）。仅需用 `mvn test` 替代 `cjpm test` 运行 JUnit 测试以通过验证。

## 选择理由
R1 代码实现正确（mvn dependency:resolve BUILD SUCCESS，pom.xml 变更符合设计），测试已编写（19 个 JUnit 用例）。失败纯因验证步骤误用了仓颉构建工具 `cjpm test`（本项目是 Maven/Java 工程，不存在 cjpm.toml）。修正验证方法后应能通过。

## 任务上下文
需求 0.2：在 `code/server/pom.xml` 新增 spring-boot-starter-websocket、jackson-datatype-jsr310、flyway-core、flyway-database-postgresql、lombok 依赖，并覆盖 flyway.version=10.10.0。

## 已有代码上下文
- `code/server/pom.xml`：已完成依赖补齐，mvn dependency:resolve BUILD SUCCESS
- `code/server/src/test/java/com/aiot/PomXmlTests.java`：19 个 JUnit 5 测试用例，覆盖 properties、依赖坐标、版本处理、scope、跨依赖一致性、基线不变性
- 项目使用 Maven 构建，非仓颉/cjpm

## RETRY 说明
R1 验证失败：错误 `the file './cjpm.toml' does not exist` / `cjpm test failed`。
原因：验证步骤错误使用了仓颉构建工具 `cjpm test`。本项目是 Maven/Java 17 工程，应使用 `mvn -f code/server/pom.xml test` 运行 JUnit 测试。
修正方向：使用 Maven 执行测试验证，代码与测试均无需修改。
