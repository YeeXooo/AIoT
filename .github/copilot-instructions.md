# Copilot Code Review Instructions

> 车载安全监测系统（AIoT）— 基于多传感器融合的车载安全监测系统。
> 后端 Java 17 + Spring Boot，前端 ArkTS (HarmonyOS)，DDD 四层架构。

---

## 架构约束

### 分层规则

```
interfaces → application → domain ← infra
```

- **domain/** — 领域模型、聚合根、值对象、领域事件、仓储接口、端口接口。不依赖任何外层
- **application/** — 应用服务，编排领域对象。只依赖 domain
- **infra/** — 基础设施实现（JPA 映射、仓储实现、事件总线、适配器）。依赖 domain
- **interfaces/** — REST 控制器、MQTT、WebSocket。依赖 application

### 关键规则

1. **禁止跨层依赖** — application 不能直接依赖 infra；interfaces 不能直接依赖 domain
2. **聚合根标识** — 使用 `AggregateId(value, AggregateType)`，禁止裸 String UUID
3. **领域事件** — 全部实现 `DomainEvent` 接口，通过 `DomainEventPublisher` 发布
4. **Result<T,E>** — 应用层返回用 `Result`，不抛业务异常
5. **设计契约优先** — 所有实现必须对齐 `docs/ood_domain.md` / `docs/ood_application.md` / `docs/ood_infrastructure.md` / `docs/ood_interface.md`

---

## Java 后端规则

### 代码风格
- Java 17，使用 record 定义不可变值对象
- 所有 public 方法必须有 Javadoc
- `Objects.requireNonNull` 校验构造参数
- 禁止 `@SuppressWarnings("unchecked")` 除非有注释说明原因
- import 使用单类导入，禁止通配符 `import com.aiot.domain.*`

### 安全检查
- 禁止硬编码密钥、token、密码
- JWT token 不在 URL query 参数中传输（WebSocket 除外：遵循 ood_interface.md 契约）
- 敏感数据（驾驶员个人信息、GPS 坐标）需脱敏日志
- AES-256-GCM 加密敏感持久化字段

### 测试
- 聚合根不变式必须有单元测试
- 仓储必须有 `@DataJpaTest` 集成测试
- CI profile 使用 H2 in-memory，不依赖外部数据库

### 数据库
- Flyway 迁移脚本命名：`V{序号}__{描述}.sql`
- 聚合根表必须含 `version` 乐观锁列
- 统一审计列：`created_at` / `updated_at`

---

## 前端 ArkTS 规则

### 语言限制
- **禁止 `as T`**（T 为用户接口/泛型参数）
- **禁止 `any`** 类型
- JSON.parse 返回 `object`，通过 `Record<string, unknown>` + getter 函数访问字段
- DTO 使用 `fromJson` 构造器模式，用基础类型断言逐字段提取

### 代码风格
- API 客户端方法返回具体 DTO 类型（如 `ApiResponse<LoginResponse>`），而非 `ApiResponse<Record<string, unknown>>`
- WebSocket 客户端继承 `BaseWebSocket` 抽象基类
- 必须实现 `startPingTimer()` / `clearPingTimer()` 心跳检测
- catch 块必须传入 err 对象：`console.error('...', err)`
- setTimeout/setInterval 句柄必须保存为实例字段，`disconnect()` 时清理

### 安全检查
- WebSocket 连接 token 遵循 ood_interface.md 定义的 URL query 方式
- 文件下载需解析 JSON 错误体，处理 401 token 过期

---

## PR 审查重点

### 高优先级
1. **跨层依赖** — 是否存在 application 直接引用 infra 包
2. **设计契约偏差** — 方法签名、API 路径是否与 ood_*.md 一致
3. **安全漏洞** — 密钥硬编码、token 泄露、敏感数据未脱敏
4. **事务边界** — 聚合根变更是否在同一事务内
5. **CI 完整性** — 是否意外删除测试、跳过 lint、降级 CI 配置

### 中优先级
6. **代码重复** — 是否存在可提取的公共逻辑（如 ID 生成、DTO fromJson）
7. **ArkTS 合规** — 是否使用了 `as T` / `any` / 通配符 import
8. **错误处理** — catch 块是否丢弃了 err 对象
9. **资源管理** — timer/WebSocket/连接是否在 disconnect 时清理
10. **类型安全** — API 层是否返回了具体 DTO 类型

### 低优先级
11. **Javadoc 完整性** — public 方法是否有文档
12. **测试覆盖** — 新增聚合根/领域服务是否有对应测试
13. **todo.md 同步** — 完成的 checkbox 是否标记

---

## 禁止事项

- ❌ 降级或删除 CI 步骤
- ❌ 注释掉测试而非修复
- ❌ 在 PR 中包含审议过程文档（`redeliberations/`、`implements/`）
- ❌ 提交空 `.gitkeep` 占位文件到已有代码的目录
- ❌ PR 直接指向 `main` 分支（统一指向 `develop`）
- ❌ 合并提交（保持线性历史，使用 rebase/squash）
