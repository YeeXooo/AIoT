# 设计审查报告（v1 r1）

## 审查结果
REJECTED

## 发现

### [严重] `flyway-database-postgresql` 版本解析方式错误

设计 §4 第 4 条依赖声明 `flyway-database-postgresql` 的 `version` 为"不写"，并声称依赖 `<flyway.version>` 属性覆盖即可解析版本。经查 `spring-boot-dependencies:3.2.5` BOM 的 `<dependencyManagement>`（POM 行 700–729），BOM 仅管理 `flyway-core`、`flyway-database-oracle`、`flyway-firebird`、`flyway-mysql`、`flyway-sqlserver`，**不包含 `flyway-database-postgresql`**。

Maven 属性仅通过 `<dependencyManagement>` 条目间接作用于依赖版本解析；对于不在任何 `<dependencyManagement>` 中的 artifact，光设置 `<flyway.version>` 属性不会为其赋予版本。`flyway-database-postgresql` 缺少显式 `<version>` 将导致 Maven 报 `'dependencies.dependency.version' is missing`，构建失败。

设计自身的错误处理段（§"错误处理"）已正确指出"BOM 无管理版本"，却得出矛盾结论——应写 `<version>${flyway.version}</version>`。

### [一般] Flyway 10.10.0 选版缺乏具体理由

设计选定 `<flyway.version>10.10.0</flyway.version>`，理由是 10.x 兼容 Spring Boot 3.2.5 且满足模块化要求，但 10.x 系列跨度大（10.0.0 至 10.20+），未说明为何选 10.10.0 而非最新的 10.x 稳定分支（如 10.20.x）。建议补充"所选为 10.x 中最新的正式稳定版"或等价说明。

### [一般] `flyway-database-postgresql` scope 暧昧

设计声称 scope "runtime 语义即可，但写默认 compile 亦可，与项目现有风格一致"。但项目现有风格中 `postgresql` JDBC 驱动即用 `<scope>runtime</scope>`（`pom.xml:47`）。`flyway-database-postgresql` 同为数据库运行时模块，应与现有 `postgresql` 一致使用 `runtime`，设计应给出明确决策而非两可。

### [轻微] Flyway 依赖与 PostgreSQL 驱动注释分组关系未说明

设计将 Flyway 依赖放入新增的 `<!-- 数据库迁移 -->` 分组"紧邻 PostgreSQL 驱动分组"，但未说明是否需要将现有 `postgresql` 驱动注释（当前为 `<!-- 金仓兼容 PostgreSQL 驱动 -->`）也归入该分组或与之对齐。不会导致功能问题，但建议明确是否调整现有注释。

## 修改要求

### 对 [严重] 问题的修正方向

`flyway-database-postgresql` 必须写出显式 `<version>`：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
    <version>${flyway.version}</version>
</dependency>
```

同时更新设计 §4 第 4 条和 §"错误处理"相关描述，删除"不写"的错误结论，改为"写 `${flyway.version}`，依赖属性覆盖统一版本"。

### 对 [一般] 问题的修正方向

1. 补充 Flyway 10.10.0 的具体选版标准（是否为 10.x 最新稳定版，或基于其他约束）。
2. 明确 `flyway-database-postgresql` scope 为 `runtime`，与现有 `postgresql` 驱动惯例一致，并删去"compile 亦可"的暧昧措辞。
