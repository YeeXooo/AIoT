# 测试报告（v1）

## 概述

本任务为 POM 依赖补齐，被测对象是 `code/server/pom.xml` 的 XML 结构。采用 JUnit 5 + DOM/XPath 解析 POM 文件，验证依赖坐标、版本处理、scope 等行为契约。

## 测试文件

| 文件 | 说明 |
|------|------|
| `code/server/src/test/java/com/aiot/PomXmlTests.java` | POM 结构验证，覆盖全部行为契约 |

## 行为契约覆盖

### 1. properties 块

| 契约 | 测试用例 |
|------|---------|
| `<flyway.version>10.10.0</flyway.version>` 存在 | `Properties.flywayVersion()` — XPath 断言值为 `10.10.0` |
| `<java.version>17</java.version>` 不变 | `Properties.javaVersion()` — 基线回归断言 |

### 2. 新增依赖存在性（5 组）

| 依赖 | 测试用例 |
|------|---------|
| `spring-boot-starter-websocket` | `Dependencies.WebSocket.coordinates()` — XPath 计数断言恰好 1 个 |
| `jackson-datatype-jsr310` | `Dependencies.JacksonJsr310.coordinates()` — 同上 |
| `flyway-core` | `Dependencies.FlywayCore.coordinates()` — 同上 |
| `flyway-database-postgresql` | `Dependencies.FlywayPostgresql.coordinates()` — 同上 |
| `lombok` | `Dependencies.Lombok.coordinates()` — 同上 |

### 3. 版本处理（符合设计决策）

| 契约 | 测试用例 |
|------|---------|
| websocket 无 `<version>`（父 BOM 管理） | `WebSocket.versionNotHardcoded()` — 断言无 version 元素 |
| jsr310 无 `<version>`（jackson-bom 管理） | `JacksonJsr310.versionNotHardcoded()` — 同上 |
| flyway-core 无 `<version>`（`${flyway.version}` 属性解析） | `FlywayCore.versionNotHardcoded()` — 同上 |
| flyway-database-postgresql 显式 `<version>${flyway.version}</version>` | `FlywayPostgresql.versionIsFlywayVersionProperty()` — 断言 version 元素存在且值为 `${flyway.version}` |
| lombok 无 `<version>`（父 POM 管理） | `Lombok.versionNotHardcoded()` — 无 version 元素 |

### 4. scope 约定

| 契约 | 测试用例 |
|------|---------|
| websocket / jsr310 / flyway-core：默认 compile（无 scope 元素） | 各自 `scopeIsCompile()` — 断言 scope 元素为 null |
| flyway-database-postgresql：runtime | `FlywayPostgresql.scopeIsRuntime()` — 断言 scope 文本为 `runtime` |
| lombok：provided | `Lombok.scopeIsProvided()` — 断言 scope 文本为 `provided` |

### 5. 跨依赖一致性

| 契约 | 测试用例 |
|------|---------|
| flyway-core 与 flyway-database-postgresql 均通过同一属性解析版本 | `Invariants.flywayVersionsConsistent()` — core 无显式 version 且 pg 引用 `${flyway.version}` |
| 所有依赖（含基线依赖）无重复 | `Invariants.noDuplicates()` — 遍历 12 组坐标，每组恰好 1 个 |

### 6. 基线不变性

| 契约 | 测试用例 |
|------|---------|
| postgresql 驱动保持 runtime scope | `UnchangedBaseline.postgresqlDriverScopeRuntime()` |
| h2 保持 test scope | `UnchangedBaseline.h2ScopeTest()` |
| spring-boot-starter-test 保持 test scope | `UnchangedBaseline.starterTestScopeTest()` |

## 覆盖维度总结

- **正常路径**：5 组新依赖坐标均正确存在
- **边界条件**：flyway-database-postgresql 的显式版本引用、版本属性一致性、scope 默认/显式边界
- **错误路径**：依赖重复检测（若出现重复则 POM 非法）
- **状态交互**：flyway-core 与 flyway-database-postgresql 版本配套性
- **回归防护**：基线依赖 scope 不受本次修改影响

## 未覆盖项

- `mvn dependency:resolve` 构建层验证：属集成/构建验证范畴，非单元测试职责。实现报告已记录 BUILD SUCCESS。
- `AiotApplicationTests.contextLoads()`：属 Spring 集成测试，不在此次 POM 测试范围内，保持不变。

## 修订说明

首轮，无修订记录。
