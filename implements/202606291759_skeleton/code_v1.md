# 实现报告（v1）

## 概述

按详细设计（detail_v1.md）补齐 `code/server/pom.xml` 的 Maven 依赖。在 `<properties>` 中覆盖 Flyway 版本为 10.10.0，并在 `<dependencies>` 中新增 5 个依赖：`spring-boot-starter-websocket`、`jackson-datatype-jsr310`、`flyway-core`、`flyway-database-postgresql`、`lombok`，按设计的注释分组组织。

## 文件变更清单
| 操作 | 文件路径 | 说明 |
|------|---------|------|
| 修改 | code/server/pom.xml | `<properties>` 新增 `<flyway.version>10.10.0</flyway.version>`；新增 websocket（归入 Spring Boot 分组）、`<!-- JSON 序列化（Java 8 时间类型） -->` 分组、`<!-- 数据库迁移 -->` 分组（flyway-core + flyway-database-postgresql）、`<!-- 工具 -->` 分组（lombok） |

依赖排列顺序：Spring Boot 分组（含 websocket）→ JSON 序列化分组 → 金仓兼容 PostgreSQL 驱动分组（不变）→ 数据库迁移分组 → 测试分组（不变）→ 工具分组。各坐标、scope、version 处理严格依设计：

- `spring-boot-starter-websocket`：无 version，默认 scope。
- `jackson-datatype-jsr310`：无 version（jackson-bom 管理），默认 scope。
- `flyway-core`：无 version（由覆盖后 `${flyway.version}` 解析），默认 scope。
- `flyway-database-postgresql`：显式 `<version>${flyway.version}</version>`，`<scope>runtime</scope>`。
- `lombok`：无 version（父 POM 管理），`<scope>provided</scope>`。

## 编译验证

执行 `mvn dependency:resolve`（项目 `code/server`），结果 **BUILD SUCCESS**（EXIT=0），全部依赖可解析，无未解析项。关键依赖解析版本：

- `org.springframework.boot:spring-boot-starter-websocket:3.2.5:compile`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.4:compile`
- `org.flywaydb:flyway-core:10.10.0:compile`
- `org.flywaydb:flyway-database-postgresql:10.10.0:runtime`
- `org.projectlombok:lombok:1.18.32:provided`

`flyway-core` 与 `flyway-database-postgresql` 解析版本一致为 10.10.0，符合行为契约后置条件。

## 设计偏差说明

无偏差。
