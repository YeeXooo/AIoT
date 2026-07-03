# AIoT 服务端测试报告

> 日期：2026-07-03  |  覆盖率：全量 912 测试 / 0 失败  |  集成测试：68 测试（H2 API + 金仓）

---

## 一、总体概览

| 指标 | 数值 |
|------|------|
| 测试用例总数 | **912** |
| 测试文件数 | **83** |
| 失败数 | **0** |
| 测试框架 | JUnit 5 + Mockito + H2 + TestRestTemplate |
| 构建工具 | Maven + maven-surefire-plugin |
| CI 平台 | GitHub Actions |

## 二、测试分层架构

```
┌──────────────────────────────────────────────────────────────────┐
│  H2 API 全栈集成测试（TestRestTemplate + 真实 HTTP）   38 tests  │
│  数据源：H2 内存库 + JPA create-drop + data.sql 种子              │
│  用途：验证 REST 全链路（HTTP→Controller→Service→JPA→H2）          │
│  Profile：it                                                      │
│  新增于 2026-07-03                                                │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│  金仓集成测试（@SpringBootTest + 真实 PostgreSQL）     30 tests   │
│  数据源：KingbaseES V9 / PostgreSQL 兼容端口 (:54321)             │
│  用途：验证 Flyway Schema + 种子数据 + JPA 映射 + 全栈查询          │
│  Profile：dev                                                     │
│  层级：JDBC 直连 (15) / JPA Repository (10) / AppService (5)      │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│  接口层测试（MockMvc standalone）                     168 tests   │
│  数据源：无（纯 Mock）                                              │
│  用途：验证 REST Controller / WebSocket Handler 行为              │
│  文件：14 个                                                      │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│  应用层 / 领域层 / 基础设施层测试（Mockito）           584 tests   │
│  数据源：无（纯 Mock）                                              │
│  用途：验证业务逻辑 / 领域规则 / 适配器行为                         │
│  文件：53 个                                                      │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│  持久层测试（@DataJpaTest + H2）                       76 tests   │
│  数据源：H2 内存库 + JPA create-drop                              │
│  用途：验证 JPA 映射 / 查询 / 乐观锁                                │
│  文件：8 个                                                       │
└──────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────┐
│  领域模型测试（纯 JUnit）                              32 tests   │
│  数据源：无（纯 Java）                                              │
│  用途：验证聚合根不变式 / 值对象 / 领域事件                         │
│  文件：6 个                                                       │
└──────────────────────────────────────────────────────────────────┘
```

## 三、H2 API 全栈集成测试（新增）

### 3.1 架构设计

```
 TestRestTemplate
       │ HTTP (真实)
       ▼
 Spring Boot (随机端口)
       │
   ┌───┴───────────────┐
   │ Security Filter    │ ← JWT 认证
   │ RateLimitFilter    │
   └───┬───────────────┘
       ▼
   Controller
       ▼
   ApplicationService
       ▼
   JPA Repository → H2 内存库 (create-drop + data.sql)
```

### 3.2 测试 Profile：`application-it.yml`

内部使用 H2 内存库，JPA `create-drop` 自动建表，`data.sql` 加载种子数据，Flyway 禁用。

### 3.3 测试覆盖

| 测试类 | 用例 | 覆盖端点 | 认证验证 |
|--------|:--:|----------|:--:|
| `AccountControllerIntegrationTest` | 5 | GET /list, GET /{phone} | FAMILY / MANAGER |
| `DriverControllerIntegrationTest` | 5 | GET /list, GET /list?name= | FAMILY |
| `SafetyControllerIntegrationTest` | 9 | GET /trip/list, /alert/list, /vehicle/list | FAMILY |
| `HealthControllerIntegrationTest` | 4 | GET /{driverId}, PUT /{driverId} | FAMILY |
| `GuardianshipControllerIntegrationTest` | 6 | GET /list, POST /, DELETE / | FAMILY / MANAGER 互斥 |
| `ProjectionControllerIntegrationTest` | 6 | GET /alert, /dashboard, /trajectory | FAMILY |
| `StorageControllerIntegrationTest` | 3 | GET /info, GET /list | FAMILY |
| **合计** | **38** | | |

### 3.4 基类设施

`ApiIntegrationTestBase` 提供：
- `@SpringBootTest(webEnvironment=RANDOM_PORT)` 自启随机端口 Tomcat
- `JwtTokenProvider` 注入，实时签发 JWT
- `familyHeaders()` / `managerHeaders()` / `familyJsonHeaders()` 认证头辅助
- `DefaultResponseErrorHandler` 覆盖——即使 4xx/5xx 也不抛异常，直接断言状态码

### 3.5 运行命令

```bash
# 仅集成测试
mvn test -Dspring.profiles.active=it -Dtest="com.aiot.it.*IntegrationTest"

# 全量测试（含集成测试）
mvn test -Dspring.profiles.active=ci -Dtest='!com.aiot.AiotApplicationTests'
```

---

## 四、金仓数据库集成测试

### 4.1 测试策略

金仓集成测试采用**不使用 `@Transactional`** 的策略，绕过 Spring Test 框架中 `@Transactional` 与 Flyway 的已知数据不可见问题。使用 `@BeforeAll` + `@AfterEach` + 原生 `JdbcTemplate` 手动清理测试数据。

### 4.2 三层金仓测试

| 层级 | 测试类 | 用例 | 说明 |
|------|--------|:--:|------|
| JDBC 直连 | `KingbaseJdbcIntegrationTest` | 15 | 纯 JDBC 验证 Flyway Schema + 12 张表种子数据 + CRUD + 约束 |
| JPA Repository | `KingbaseJpaRepositoryIntegrationTest` | 10 | Spring Data JPA — CRUD / 乐观锁 / 关联表 FK / 显式事务 |
| 应用服务全栈 | `KingbaseApplicationServiceTest` | 5 | Driver.list / Trip 查询 / Alert 过滤 / 端到端持久化流程 |
| **合计** | | **30** | |

### 4.3 种子数据验证矩阵

| 表 | 预期行数 | JPA count() | JDBC SELECT |
|----|:--:|:--:|:--:|
| t_driver | 5 | ✅ | ✅ |
| t_vehicle | 5 | ✅ | ✅ |
| t_trip | 7 | ✅ | ✅ |
| t_safety_alert_event | 5 | ✅ | ✅ |
| t_system_account | 3 | ✅ | ✅ |
| t_driver_health_profile | 2 | ✅ | ✅ |
| t_guardianship | 2 | ✅ | ✅ |
| t_trip_physiological_snapshot | 7 | ✅ | ✅ |
| t_alert_projection | 5 | ✅ | ✅ |
| t_fleet_dashboard_projection | 5 | ✅ | ✅ |
| t_trajectory_projection | 5 | ✅ | ✅ |

### 4.4 运行命令

```bash
# 需要 Kingbase Docker 运行中（端口 54321）
mvn test -Dspring.profiles.active=dev \
  -Dtest="KingbaseJpaRepositoryIntegrationTest,KingbaseApplicationServiceTest,KingbaseJdbcIntegrationTest"
```

---

## 五、CI 流水线

GitHub Actions（`.github/workflows/ci.yml`）的 `backend` job 通过 `mvn test -Dspring.profiles.active=ci` 自动运行全量 912 测试。

H2 API 集成测试（`it` profile）也被包含在内——测试类通过 `@ActiveProfiles("it")` 覆盖命令行 profile，自动切换为集成测试上下文。

金仓集成测试（`dev` profile）不在 CI 中运行，仅在本地有 Kingbase Docker 时手动执行。

---

## 六、Mock 策略

项目统一使用 `@ExtendWith(MockitoExtension.class)` + `@Mock` + 手动构造注入，全项目**零 `@MockBean` 零 `@SpringBootTest` 泄漏到单测**。

```java
// 标准单测模式
@ExtendWith(MockitoExtension.class)
class FooTest {
    @Mock private Dependency dep;
    private Foo subject;

    @BeforeEach
    void setUp() {
        subject = new Foo(dep);
    }
}
```

Controller 测试使用 `MockMvcBuilders.standaloneSetup()`，不启动 Spring 上下文，避免与安全过滤器链冲突。

---

## 七、测试文件清单（新增）

```
code/server/src/main/resources/application-it.yml          ← IT profile
code/server/src/test/resources/data.sql                     ← 种子数据
code/server/src/test/java/com/aiot/it/
├── ApiIntegrationTestBase.java                             ← 基类
├── AccountControllerIntegrationTest.java                   ← 5 tests
├── DriverControllerIntegrationTest.java                    ← 5 tests
├── SafetyControllerIntegrationTest.java                    ← 9 tests
├── HealthControllerIntegrationTest.java                    ← 4 tests
├── GuardianshipControllerIntegrationTest.java              ← 6 tests
├── ProjectionControllerIntegrationTest.java                ← 6 tests
└── StorageControllerIntegrationTest.java                   ← 3 tests
```

---

## 八、运行参考

| 命令 | 模式 | 用例数 |
|------|------|:--:|
| `mvn test -Dspring.profiles.active=ci -Dtest='!...AiotApplicationTests'` | 全量（CI） | 912 |
| `mvn test -Dspring.profiles.active=it -Dtest="com.aiot.it.*IntegrationTest"` | 仅 H2 API 集成 | 38 |
| `mvn test -Dspring.profiles.active=dev -Dtest="Kingbase*"` | 仅金仓集成 | 30 |
