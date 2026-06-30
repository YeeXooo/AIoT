# 任务指令（v3）

## 动作
NEW

## 任务描述
实现统一结果承载类型 `Result<T, E>`（需求 0.3 第 1 项），采用 Java 17 `sealed interface` + `record`。

- 文件：`code/server/src/main/java/com/aiot/domain/shared/Result.java`
- 包：`com.aiot.domain.shared`
- 类型构成：
  - `sealed interface Result<T, E>`，permits `Ok` 与 `Err`
  - `record Ok<T, E>(T value) implements Result<T, E>`（承载成功值 `value: T`）
  - `record Err<T, E>(E error) implements Result<T, E>`（承载错误值 `error: E`）
- 工厂方法（静态）：
  - `Result.ok(T value)` → 返回 `Ok`
  - `Result.err(E error)` → 返回 `Err`
- 查询/解包行为（接口方法）：
  - `isOk()` → 成功时 `true`
  - `isErr()` → 失败时 `true`
  - `unwrap()` → 成功返回 `value`；失败应明确失败（如抛异常），不得返回 `null` 掩盖错误
  - `unwrapErr()` → 失败返回 `error`；成功应明确失败

行为契约由设计/编码阶段细化（含解包在错误分支下的失败语义），本任务仅交付该类型本身，不引入对其他自定义类型的依赖。

## 选择理由
R2（0.2 Maven 依赖）已 PASSED；0.4 入口与配置产物（AiotApplication.java、application.yml、application-ci.yml）已存在于基线且 Spring 上下文加载测试通过，视为已满足。剩余唯一未实现部分为 0.3 基础类型定义。`Result<T, E>` 是领域层全部服务方法契约（ood_domain.md §五「Result 语义」、DS-02~DS-16 等）广泛引用的核心结果承载抽象，且为含行为的类型，按粒度规则「复杂行为类型单独一个任务」优先单独交付。AggregateId/具体 ID 类型、AppError 作为后续独立任务，三者相互无依赖。

## 任务上下文
- 需求 0.3 第 1 项（`requirement.md` 19–23 行）：
  - `Ok<T>` record（含 `value: T`）、`Err<E>` record（含 `error: E`）
  - 工厂方法 `Result.ok(T value)` / `Result.err(E error)`
  - `isOk()` / `isErr()` / `unwrap()` / `unwrapErr()`
- `docs/ood_domain.md` §五 及多处服务契约以 `Result<T, E>` 作为返回类型的统一约定（例如 line 512/525/542/558/576/664/679/723/762），是本类型的下游使用模式参考。
- 约束：Java 17，`sealed interface` + `record`；不引入华为云 SDK；不依赖尚未实现的 AppError/ID 类型（E 为泛型参数，保持解耦）。

## 已有代码上下文
- 基线包根为 `com.aiot`（见 `code/server/src/main/java/com/aiot/AiotApplication.java`）；`com.aiot.domain.shared` 包及目录尚不存在，需新建目录 `code/server/src/main/java/com/aiot/domain/shared/`。
- 构建为 Maven（`code/server/pom.xml`），Java 17，已含 Lombok（provided）；测试用 JUnit 5（参见 `code/server/src/test/java/com/aiot/`）。验证统一使用 `mvn -f code/server/pom.xml test`（**非** `cjpm`，本项目无 cjpm.toml）。
- 已有 checkstyle 配置 `code/server/checkstyle.xml` 参与构建，新增源码须符合其规则。
