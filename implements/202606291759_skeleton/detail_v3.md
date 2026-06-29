# 详细设计（v3）

## 概述

实现统一结果承载类型 `Result<T, E>`，为领域层全部服务方法契约（DS-02~DS-16 等）提供统一的"成功/失败"二值抽象。类型采用 Java 17 `sealed interface` + `record`，无外部依赖，不引入尚未实现的自定义类型。

## 文件规划

| 文件路径 | 操作 | 职责 |
|---------|------|------|
| `code/server/src/main/java/com/aiot/domain/shared/Result.java` | 新建 | 定义 `Result<T, E>` 封闭接口及其两个允许子类型 `Ok<T, E>` 和 `Err<T, E>`，含工厂方法与查询/解包方法 |

## 类型定义

### Result&lt;T, E&gt;

**形态**：`sealed interface`（泛型接口）
**包路径**：`package com.aiot.domain.shared;`
**职责**：统一领域层方法返回值的成功/失败二值抽象，permits `Ok` 与 `Err` 两个直接子类型
**类型签名定义**：

```java
package com.aiot.domain.shared;

public sealed interface Result<T, E> permits Result.Ok, Result.Err {
```

**公开接口**：

| 方法签名 | 契约 |
|---------|------|
| `boolean isOk()` | 当 `this` 为 `Ok` 实例时返回 `true`；否则 `false` |
| `boolean isErr()` | 当 `this` 为 `Err` 实例时返回 `true`；否则 `false` |
| `T unwrap()` | `this` 为 `Ok` 时返回其 `value`；`this` 为 `Err` 时抛出 `ResultUnwrapException` |
| `E unwrapErr()` | `this` 为 `Err` 时返回其 `error`；`this` 为 `Ok` 时抛出 `ResultUnwrapException` |

**构造方式**：不直接由外部构造，通过静态工厂方法获取实例：
- `Result.ok(T value)` → 返回 `new Ok<>(value)`
- `Result.err(E error)` → 返回 `new Err<>(error)`

**类型关系**：
- `Result<T, E>` 作为 sealed interface，其 permits 子句限定只有 `Ok` 与 `Err` 两种直接实现
- 实现方（`Ok`, `Err`）作为 `Result` 的静态嵌套 record 类

---

### Ok&lt;T, E&gt;

**形态**：`record`（嵌套在 `Result` 接口内部）
**包路径**：`com.aiot.domain.shared.Result.Ok`
**职责**：承载成功分支的值 `value: T`

**类型签名定义**：

```java
record Ok<T, E>(T value) implements Result<T, E> {
}
```

**公开接口**（继承自 `Result<T, E>` 并覆写契约）：
- `isOk()` → `true`
- `isErr()` → `false`
- `unwrap()` → `this.value`
- `unwrapErr()` → 抛出 `ResultUnwrapException`

**构造方式**：通过 `Result.ok(T value)` 工厂方法创建，禁止直接实例化。record 构造器生成的规范构造器自动赋值 `value` 字段。

**类型关系**：`implements Result<T, E>`，保持 `T` 和 `E` 两个类型参数（即使自身只使用 `T`，使类型系统在全路径上可跟踪错误类型）。

---

### Err&lt;T, E&gt;

**形态**：`record`（嵌套在 `Result` 接口内部）
**包路径**：`com.aiot.domain.shared.Result.Err`
**职责**：承载失败分支的错误值 `error: E`

**类型签名定义**：

```java
record Err<T, E>(E error) implements Result<T, E> {
}
```

**公开接口**（继承自 `Result<T, E>` 并覆写契约）：
- `isOk()` → `false`
- `isErr()` → `true`
- `unwrap()` → 抛出 `ResultUnwrapException`
- `unwrapErr()` → `this.error`

**构造方式**：通过 `Result.err(E error)` 工厂方法创建，禁止直接实例化。

**类型关系**：`implements Result<T, E>`，保持 `T` 和 `E` 两个类型参数（即使自身只使用 `E`）。

---

### ResultUnwrapException

**形态**：`static class`（嵌套在 `Result` 接口内部）
**包路径**：`com.aiot.domain.shared.Result.ResultUnwrapException`
**职责**：`unwrap()` 在 `Err` 分支、`unwrapErr()` 在 `Ok` 分支调用时抛出的非受检异常

**类型签名定义**：

```java
final class ResultUnwrapException extends RuntimeException {
    ResultUnwrapException(String message) {
        super(message);
    }
}
```

**构造方式**：包级私有构造器，仅由 `Ok`/`Err` 的两个 unwrap 方法内部构造。

**类型关系**：`extends RuntimeException`（非受检异常，调用方无需在方法签名中声明 throws，与当前项目领域服务方法签名风格一致——仓储等均使用 unchecked exception）。

---

### 静态工厂方法

| 方法签名 | 返回类型 | 契约 |
|---------|---------|------|
| `static <T, E> Result<T, E> ok(T value)` | `Result<T, E>`（实际为 `Ok<T, E>`） | 创建携带成功值 `value` 的 `Ok` 实例。`value` 允许为 `null`——`null` 作为合法的成功值传递（如 `Result.ok(null)` 表示成功但无数据返回，与 `Option.None` 不同——前者明确成功、后者表达"没有结果"） |
| `static <T, E> Result<T, E> err(E error)` | `Result<T, E>`（实际为 `Err<T, E>`） | 创建携带错误值 `error` 的 `Err` 实例。`error` 不允许为 `null`——调用 `Result.err(null)` 时抛出 `NullPointerException`，保证 `unwrapErr()` 调用方永远收到非 `null` 的错误对象 |

工厂方法为 `Result<T, E>` 接口的静态方法，以 `static <T, E>` 声明独立的类型参数以使调用方可省略显式类型参数（类型推导由参数完成：`Result.ok(someString)` 自动推导为 `Result<String, SomeError>`）。

## 错误处理

- `ResultUnwrapException` 为 `RuntimeException`（非受检），调用方不须在方法签名中声明 `throws`，从代码书写上与项目 OOD 文档中的 `Result<T, E>` 用法一致（所有领域服务签名仅写 `Result<Foo, FooError>` 不写 throws）。
- 不在 `Result` 内部定义其他错误枚举或错误基类——类型参数 `E` 保持开放，由调用方自行指定错误类型（如模块级 `DeterminationError`、`QueryError`、`AppError` 等），`Result` 不依赖任何自定义错误类型。

## 行为契约

### 构造语义

1. `Result.ok(value)` 始终返回成功的 `Ok` 实例，`isOk() == true`。
2. `Result.err(error)` 始终返回失败的 `Err` 实例，`isErr() == true`；传入 `null` 即刻抛出 `NullPointerException`。

### 解包语义

| 实例类型 | `isOk()` | `isErr()` | `unwrap()` | `unwrapErr()` |
|---------|----------|-----------|-----------|-------------|
| `Ok(value)` | `true` | `false` | 返回 `value` | 抛出 `ResultUnwrapException`，消息含 `"called unwrapErr() on Ok"` |
| `Err(error)` | `false` | `true` | 抛出 `ResultUnwrapException`，消息含 `"called unwrap() on Err"` | 返回 `error`（始终非 `null`） |

### 不变式

- `isOk() == !isErr()` — 任何 `Result` 实例在任意时刻必须满足此双向互斥不变式。
- 对于通过 `Result.err(error)` 创建的 `Err` 实例，`error != null` 恒成立。
- 解包方法在语义错误的分支上**不得返回 `null` 掩盖错误**，必须抛出异常以明确通知调用方逻辑错误。

### 模式匹配（Java 17 增强 `instanceof`）

调用方可使用以下模式安全消费：

```java
if (result instanceof Result.Ok<T, E> ok) {
    T value = ok.value();
} else if (result instanceof Result.Err<T, E> err) {
    E error = err.error();
}
```

或简化为：

```java
if (result instanceof Result.Ok<T, E>(var value)) {
    // use value
} else if (result instanceof Result.Err<T, E>(var error)) {
    // use error
}
```

## 依赖关系

**依赖的已有类型**：无——`Result<T, E>` 仅依赖 `java.lang.RuntimeException`（JDK 标准库）。

**引入的第三方库**：无——不依赖 Spring、Lombok 或任何外部库。

**暴露给后续任务的公开接口**：
- `com.aiot.domain.shared.Result` — 所有领域服务（DS-02~DS-16）方法签名中以 `Result<T, E>` 作为统一返回类型
- `com.aiot.domain.shared.Result.Ok` / `com.aiot.domain.shared.Result.Err` — 调用方模式匹配消费
- `com.aiot.domain.shared.Result.ResultUnwrapException` — 调用方在需要精确捕获解包失败场景时使用

**后续任务依赖关系**：`AggregateId`、`AppError`（同属 0.3 任务）与本类型互相独立，无编译期依赖。`Result<T, E>` 的两个类型参数均为泛型，不引用任何项目自定义类型。

## 代码风格约束

- 遵循 `checkstyle.xml` 规则：不得使用 star import（`import ... *`），必须使用大括号包裹所有控制流（`NeedBraces`），`WhitespaceAround` 要求操作符两侧空格。
- 构造器参数与字段命名：record 组件的规范命名（`value`、`error`），无需额外 getter 前缀。
- record 内不覆写 `equals()` / `hashCode()` / `toString()` —— Java 编译器自动生成值语义实现，满足不可变值对象需求。
