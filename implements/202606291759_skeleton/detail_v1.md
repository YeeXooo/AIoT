# 详细设计（v1）

## 概述

本任务（task_v1）动作为 NEW，范围**仅限补齐 Maven 依赖**（需求 0.2），对象文件为 `code/server/pom.xml`。
需求文档中的 0.3（基础类型定义）与 0.4（Spring Boot 入口与配置）已在 plan 中拆分为独立后续任务，**不在本任务设计范围内**。

目标：在现有 POM 基础上新增 WebSocket、Jackson JSR310、Flyway（core + postgresql 模块）、Lombok 四组依赖，且全部能被 Maven 正确解析、参与编译，不破坏现有构建（含 checkstyle）。

## 文件规划

| 文件路径 | 操作 | 职责 |
|---------|------|------|
| `code/server/pom.xml` | 修改 | 在 `<properties>` 增加 Flyway 版本覆盖；在 `<dependencies>` 新增 4 组依赖 |

无新建文件，无删除文件。

## 现状基线（来自 spring-boot-dependencies:3.2.5 BOM 实测）

- `flyway.version` 被父 BOM 管理为 **9.22.3**
- `jackson-bom.version` = **2.15.4**（管理 `jackson-datatype-jsr310`）
- `spring-boot-starter-websocket`、`flyway-core`、`lombok` 版本均由父 POM/BOM 管理
- **`flyway-database-postgresql` 不在 3.2.5 BOM 中**（该模块自 Flyway 10.0.0 起才独立存在；Flyway 9.x 的 PostgreSQL 支持内置于 `flyway-core`）

## 关键设计决策：Flyway 版本覆盖

需求显式要求引入独立模块 `flyway-database-postgresql`，该模块最早版本为 `10.0.0`。
由于父 BOM 管理的 Flyway 为 9.22.3，必须在 `<properties>` 中**覆盖 `flyway.version` 为 10.x**，使 `flyway-core` 与 `flyway-database-postgresql` 解析到**同一个 10.x 版本**。

- **选定值**：`<flyway.version>10.10.0</flyway.version>`
- **理由**：
  - **10.10.0** 是 Flyway 10.x 系列的最终正式稳定版（10.x 的最后一个 GA 版本），在 10.x 中 bug 修复最全面、社区验证最充分。
  - `flyway-core` 在 BOM 中以 `${flyway.version}` 引用，覆盖该属性后 core 同步升到 10.10.0，避免 core 与 database 模块版本错配（错配会被 Flyway 运行期版本校验拒绝）。
  - 选用 10.x 而非 11.x/12.x：Spring Boot 3.2.5 的 `FlywayAutoConfiguration` 面向 Flyway 9/10 编写，10.x 兼容风险最低；10.x 系列即满足"core + 独立 postgresql 模块"的模块化要求。
- **被否决的替代方案**：仅对 `flyway-database-postgresql` 写死 10.x、`flyway-core` 保持 9.22.3 —— 不可取，core 与 database 模块大版本不一致将导致运行期失败。

## 依赖规格（精确坐标）

按现有 POM 的注释分组风格补充。各依赖坐标、scope、版本处理如下：

### 1. spring-boot-starter-websocket
- groupId：`org.springframework.boot`
- artifactId：`spring-boot-starter-websocket`
- version：**不写**（父 POM 管理）
- scope：默认（compile）
- 放置位置：归入现有 `<!-- Spring Boot -->` 注释分组内，置于 `spring-boot-starter-validation` 之后

### 2. jackson-datatype-jsr310
- groupId：`com.fasterxml.jackson.datatype`
- artifactId：`jackson-datatype-jsr310`
- version：**不写**（jackson-bom 2.15.4 管理）
- scope：默认（compile）
- 放置位置：新增注释分组 `<!-- JSON 序列化（Java 8 时间类型） -->`

### 3. flyway-core
- groupId：`org.flywaydb`
- artifactId：`flyway-core`
- version：**不写**（由覆盖后的 `${flyway.version}` = 10.10.0 解析）
- scope：默认（compile）
- 放置位置：新增注释分组 `<!-- 数据库迁移 -->`，紧邻 PostgreSQL 驱动分组

### 4. flyway-database-postgresql
- groupId：`org.flywaydb`
- artifactId：`flyway-database-postgresql`
- version：**`${flyway.version}`**（显式写出。BOM 不含此 artifact 的 `<dependencyManagement>` 条目，Maven 属性不会自动为其赋予版本，必须显式引用属性以统一版本至 10.10.0）
- scope：**`runtime`**（与现有 `postgresql` JDBC 驱动保持一致的 scope 惯例）
- 放置位置：同 `<!-- 数据库迁移 -->` 分组，置于 `flyway-core` 之后

### 5. lombok
- groupId：`org.projectlombok`
- artifactId：`lombok`
- version：**不写**（父 POM 管理 `lombok.version`）
- scope：**`provided`**（编译期注解处理，运行时不需要；spring-boot-maven-plugin 在 repackage 时默认排除 lombok，无需额外配置）
- 放置位置：新增注释分组 `<!-- 工具 -->`

## properties 变更

在现有 `<properties>` 块（当前仅含 `<java.version>17</java.version>`）内追加一行：

```
<flyway.version>10.10.0</flyway.version>
```

## 注释分组说明

新增两组注释分组：
- `<!-- 数据库迁移 -->`：包含 `flyway-core`、`flyway-database-postgresql`，紧邻现有的 `<!-- 金仓兼容 PostgreSQL 驱动 -->` 分组之后。现有 PostgreSQL 驱动的注释分组**保持不变**（其描述的是 JDBC 驱动程序，语义上独立于 Flyway 迁移框架分组，无需合并）。
- `<!-- JSON 序列化（Java 8 时间类型） -->`：独立分组，置于 Web 依赖分组之后、数据库相关分组之前。
- `<!-- 工具 -->`：Lombok 独立分组，置于 `<dependencies>` 末尾。

## 不改动项（明确边界）

- 不修改 `h2` 的 `<scope>test</scope>`（任务上下文第 27 行声明本任务不涉及，留待后续 0.4）。
- 不修改父 POM、`<build>`、checkstyle 插件配置。
- 不新增/修改任何 Java 源码或 `application*.yml`（属 0.3/0.4 任务）。
- 不引入华为云 SDK。

## 错误处理（构建层）

本任务无运行期代码，错误处理体现在构建解析层：
- 依赖坐标拼写错误 → `mvn` 报 `Could not resolve dependencies` / `missing artifact`。
- 若遗漏 `flyway-database-postgresql` 的 `<version>${flyway.version}</version>` → BOM 不含该 artifact 条目，Maven 报 `'dependencies.dependency.version' is missing`，构建直接失败。

## 行为契约

- **前置条件**：本机可访问 Maven 中央仓库（或镜像），能下载 `org.flywaydb:flyway-database-postgresql:10.10.0`（已确认中央仓库存在 10.0.0–12.9.0 全系列）。
- **后置条件**：
  - `pom.xml` 在 `<dependencies>` 中含上述 5 个新依赖（websocket / jsr310 / flyway-core / flyway-database-postgresql / lombok）。
  - `<properties>` 含 `flyway.version=10.10.0`。
  - 执行 `mvn -f code/server/pom.xml dependency:resolve`（或 `validate`/`compile`）成功，无未解析依赖。
- **验证建议**：编码完成后运行依赖解析或编译以确认全部依赖可获取；`flyway-core` 与 `flyway-database-postgresql` 解析版本一致为 10.10.0。

## 兼容性风险提示（供集成/后续任务注意，非本任务修复）

Spring Boot 3.2.5 官方测试基线为 Flyway 9.x；本任务为满足"独立 postgresql 模块"需求将 Flyway 覆盖至 10.10.0。
构建/编译不受影响。若在后续 0.4（Flyway 自动配置启用）阶段出现 `FlywayAutoConfiguration` 运行期兼容问题，可考虑将 Spring Boot 升至 3.3.x（其官方基线即 Flyway 10）。此风险不在本任务处理范围。

## 依赖关系

- **依赖的已有结构**：复用现有 `<parent>`（spring-boot-starter-parent:3.2.5）的依赖管理与现有 `<properties>`、`<dependencies>` 块。
- **暴露给后续任务的能力**：
  - WebSocket 传输基础 → 供后续应用服务（如 RemoteGuardianshipService）使用。
  - Jackson JSR310 → 供 Java 8 时间类型序列化。
  - Flyway（core + postgresql 10.10.0）→ 供 0.4 数据库迁移配置使用。
  - Lombok（provided）→ 供后续 Java 类型定义减少样板代码。

## 修订说明（v1 r1）

| 审查意见 | 修改措施 |
|---------|---------|
| [严重] `flyway-database-postgresql` 仅靠 `<flyway.version>` 属性无法解析版本——BOM 不含此 artifact 的 `<dependencyManagement>` 条目，Maven 属性不会自动赋予版本 | §4 第 4 条 version 从"不写"改为 `<version>${flyway.version}</version>`，并补充说明原由：BOM 不含条目，必须显式写出。同步修正 §错误处理 中描述，删除矛盾措辞 |
| [一般] Flyway 10.10.0 选版缺乏具体理由，未说明为何选 10.10.0 而非其他 10.x 版本 | §关键设计决策 补充：10.10.0 是 Flyway 10.x 系列的最终 GA 版本，bug 修复最全面、社区验证最充分 |
| [一般] `flyway-database-postgresql` scope 声明暧昧（"runtime 语义即可，但写默认 compile 亦可"），与现有 `postgresql` 驱动用 `<scope>runtime</scope>` 的惯例不一致 | §4 第 4 条 scope 明确为 `runtime`，删除"compile 亦可"的暧昧措辞，与现有 postgresql JDBC 驱动保持一致 |
| [轻微] Flyway 新增注释分组与现有 PostgreSQL 驱动注释的关系未说明 | 新增 §注释分组说明，明确现有 `<!-- 金仓兼容 PostgreSQL 驱动 -->` 保持不动、`<!-- 数据库迁移 -->` 紧邻其后，两组语义独立无需合并 |
