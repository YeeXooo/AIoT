# 测试 Seed 数据重复插入冲突问题文档

> 发现日期: 2026-07-04
> 关联 PR: Integration HMI + Perception + Server
> 严重程度: 阻断 — 所有 `@DataJpaTest` 集成测试无法加载 ApplicationContext

---

## 一、问题定位

### 现象

```
Tests run: 913, Failures: 0, Errors: 35, Skipped: 0
```

全部 35 个 Error 为同一根因: **ApplicationContext 加载时 data.sql 与 Flyway migration 主键冲突**。

### 根因堆栈

```
Caused by: org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException:
Unique index or primary key violation:
"PUBLIC.PRIMARY_KEY_30 ON PUBLIC.T_DRIVER(DRIVER_ID) VALUES (...)"
```

---

## 二、冲突分析

### 两条 Seed 数据注入路径

```
@SpringBootTest / @DataJpaTest(classes=AiotApplication.class)
    │
    ├── 路径 A: Flyway Migration
    │   ├── V3__core_tables.sql      → INSERT t_driver, t_vehicle, t_trip,
    │   │                               t_safety_alert_event, t_system_account
    │   └── V4__remaining_tables.sql → INSERT t_driver_health_profile, t_guardianship,
    │                                   t_trip_physiological_snapshot, t_alert_projection,
    │                                   t_fleet_dashboard_projection, t_trajectory_projection
    │
    └── 路径 B: Spring DataSource Initializer (auto-detect)
        └── src/test/resources/data.sql → INSERT 相同主键的行到上述所有表
```

两条路径插入**相同主键的数据**，Spring 先执行 Flyway 迁移，再执行 `data.sql`，第二次 INSERT 触发 H2 主键冲突。

### 重复覆盖的表

| 表 | Flyway 来源 | data.sql 来源 | 数据量 |
|---|---|---|---|
| `t_driver` | V3 | data.sql | 5 rows |
| `t_vehicle` | V3 | data.sql | 5 rows |
| `t_trip` | V3 | data.sql | 7 rows |
| `t_safety_alert_event` | V3 | data.sql | 5 rows |
| `t_system_account` | V3 | data.sql | 3 rows |
| `t_driver_health_profile` | V4 | data.sql | 2 rows |
| `t_guardianship` | V4 | data.sql | 2 rows |
| `t_trip_physiological_snapshot` | V4 | data.sql | 2 rows |
| `t_alert_projection` | V4 | data.sql | 3 rows |
| `t_fleet_dashboard_projection` | V4 | data.sql | 3 rows |
| `t_trajectory_projection` | V4 | data.sql | 3 rows |

### data.sql 与 Flyway INSERT 的列差异

`data.sql` 显式指定了所有列（含 `version`, `created_at`, `updated_at`），Flyway INSERT 省略了带默认值的列。除此之外，数据内容**几乎一致**（仅个别字段如 `alert_msg` 有细微文案差异）。

---

## 三、修复方案评估

### 方案 1: 删除 data.sql，仅依赖 Flyway migration (推荐)

**难度**: 低 (1 文件删除)  
**风险**: 低  
**可行性**: 高

```bash
rm src/test/resources/data.sql
```

**优点**:
- 一劳永逸消除重复
- 生产/测试共享同一套 seed 数据源
- Flyway 提供版本化管理

**缺点**:
- `data.sql` 比 Flyway INSERT 多涵盖 `version`/`created_at`/`updated_at` 字段。H2 表创建时这些字段有默认值 (0 / CURRENT_TIMESTAMP)，删除 `data.sql` 后值略有不同但不影响测试。
- 部分 `alert_msg` 字段 content 有细微差异，需确认测试无硬断言。

**需验证**:
- 运行 `mvn test` 确认所有 47 个 JPA 测试通过
- 检查是否有测试断言具体的 `created_at` 或 `alert_msg` 值

---

### 方案 2: 测试 profile 禁用 Spring DataSource 初始化

**难度**: 低 (1 行配置)  
**可行性**: 高

在 `src/test/resources/application.yml` 中添加:

```yaml
spring:
  sql:
    init:
      mode: never
```

或者在 `@DataJpaTest` 上添加:

```java
@SpringBootTest(properties = "spring.sql.init.mode=never")
```

**优点**: 保留 `data.sql` 文件，改动最小  
**缺点**: 保留冗余数据源，后续开发者可能困惑

---

### 方案 3: 合并 data.sql 到 Flyway migration

**难度**: 中  
**风险**: 中 (需仔细比对所有字段)  
**可行性**: 中高

1. 将 `data.sql` 中多余的 `version`/`created_at`/`updated_at` 列补充到对应 Flyway migration
2. 统一 `alert_msg` 的细微差异
3. 创建 V6 migration（不可修改已有 V3/V4，因 checksum 已固化）
4. 删除 `data.sql`

**优点**: 最干净的方案  
**缺点**: 需要新增 migration 或重建已固化的 V3/V4，工作量较大

---

### 方案 4: 测试 profile 禁用 Flyway

**难度**: 低 (1 行配置)  
**可行性**: 低 (不推荐)

```yaml
spring:
  flyway:
    enabled: false
```

**缺点**: 牺牲 Flyway migration 测试覆盖，可能导致生产 migration 问题不在测试阶段暴露。

---

## 四、推荐修复路径

**推荐方案 1（删除 data.sql）**，具体步骤:

```bash
# 1. 删除冗余的 data.sql
rm src/test/resources/data.sql

# 2. 运行全量测试
mvn test

# 3. 如果有测试因字段差异失败，逐个调整 test assertion
```

预计耗时: **5 分钟**（删除 + 跑测试验证）。

如果个别测试因 `alert_msg` 内容差异失败，将测试中的硬编码字符串改为 Flyway V3/V4 中的对应值即可。

---

## 五、当前影响范围

| 测试模块 | 受影响测试数 | 状态 |
|---------|:---------:|:---:|
| DriverJpaRepositoryTest | 9 tests | ERRORS |
| TripJpaRepositoryTest | 5 tests | ERRORS |
| VehicleJpaRepositoryTest | 4 tests | ERRORS |
| AlertEventJpaRepositoryTest | 5 tests | ERRORS |
| RoadRageVoiceRecordJpaRepositoryTest | 4 tests | ERRORS |
| ComplexQueryAndLockTest | 4 tests | ERRORS |
| OptimisticLockTest | 4 tests | ERRORS |
| DriverTest (domain) | 10 tests | OK (纯单元测试，不加载 Spring 上下文) |
| TripTest (domain) | 12 tests | OK (纯单元测试) |
| AiotApplicationTests | 1 test | OK |

> 35 个受影响的测试全部为 Spring Context 加载失败导致的连锁 skip，非业务逻辑错误。
