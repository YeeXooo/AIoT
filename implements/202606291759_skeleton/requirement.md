# 任务：项目骨架（0.2 + 0.3 + 0.4）

实现项目前置骨架，包括 Maven 依赖补齐、基础类型定义、Spring Boot 入口与配置。

## 0.2 补齐 Maven 依赖（`code/server/pom.xml`）

在现有 POM 基础上新增以下依赖：
- `spring-boot-starter-websocket`
- `jackson-datatype-jsr310`（Java 8 时间序列化）
- `flyway-core` + `flyway-database-postgresql`（数据库迁移）
- 可选：Lombok（`org.projectlombok:lombok`）

现有依赖已有：spring-boot-starter、spring-boot-starter-data-jpa、spring-boot-starter-web、spring-boot-starter-validation、postgresql、h2。不引入华为云 SDK。

## 0.3 基础类型定义（`com.aiot.domain.shared`）

创建以下 Java 类型（使用 Java 17 `record` 和 `sealed interface`）：

1. **`Result<T, E>`** — 统一结果承载类型：
   - `Ok<T>` record（含 `value: T`）
   - `Err<E>` record（含 `error: E`）
   - 工厂方法 `Result.ok(T value)` / `Result.err(E error)`
   - `isOk()` / `isErr()` / `unwrap()` / `unwrapErr()`

2. **`AggregateId`** — 聚合根标识基类：
   - record，含 `id: String`（UUID v4）
   - 工厂方法 `AggregateId.generate()`
   - 构造方法接受字符串用于反序列化

3. 具体标识类型（均 extends/implements 合适的方式）：
   - `DriverId`、`TripId`、`VehicleId`、`AccountId`、`RescueReportId`、`UpgradeTaskId`、`AlertId`、`GuardianshipId`

4. **`AppError`** — 应用层错误（参考 `docs/ood_application.md` §5）：
   - sealed interface `AppError`
   - 至少包含以下 variant：
     - `NotFound(String resource, String id)`
     - `AccessDenied(String reason)`
     - `InvalidState(String message)`
     - `ValidationFailed(String message)`
     - `IoTDAChannelFailure(String message)`
     - `UpgradeTaskNotCancellable(String taskId, String stage)`
     - `UpgradeInProgress(String vehicleId)`
     - `UpgradeAlreadyFinished(String vehicleId, String status)`

## 0.4 Spring Boot 入口与配置

1. **`AiOTApplication.java`**（`com.aiot`）：
   - `@SpringBootApplication`
   - main 方法

2. **`application.yml`**（`src/main/resources/`）：
   - H2 开发环境数据源
   - JPA 配置（ddl-auto: validate，show-sql: true）
   - Flyway 基础配置
   - server.port: 8080

3. **`application-ci.yml`**（已存在，检查是否需要更新）

## 项目根目录

`/home/jasper/AIoT`

## 参考文档

- `docs/ood_domain.md` — 领域层设计（聚合根、值对象、标识类型）
- `docs/ood_application.md` §5 — AppError 定义
- `docs/ood_infrastructure.md` — 基础设施层设计
- `todo.md` — 任务清单
