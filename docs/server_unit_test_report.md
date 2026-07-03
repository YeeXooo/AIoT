# 服务端单元测试覆盖报告

> 日期：2026-07-03  |  目标：Java Spring Boot 服务端全量单元测试覆盖情况

## 一、总体概览

| 指标 | 数值 |
|------|------|
| 测试用例总数 | **875** |
| 测试文件数 | **76** |
| 测试框架 | JUnit 5 + Mockito + H2 |
| 构建工具 | Maven (maven-surefire-plugin) |
| 运行命令 | `mvn test -B -Dspring.profiles.active=ci` |

## 二、三层测试架构

```
┌─────────────────────────────────────────────────────────┐
│ 接口层测试（MockMvc standalone）                          │
│ 数据源：无（纯 Mock）                                     │
│ 用途：验证 REST Controller / WebSocket Handler 行为        │
│ 文件：14 个                                              │
│ 用例：168                                                │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ 应用层 / 领域层 / 基础设施层测试（Mockito）                  │
│ 数据源：无（纯 Mock）                                     │
│ 用途：验证业务逻辑 / 领域规则 / 适配器行为                   │
│ 文件：45 个                                              │
│ 用例：584                                                │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ 持久层测试（@DataJpaTest + H2）                           │
│ 数据源：H2 内存库 + JPA create-drop                      │
│ 用途：验证 JPA 映射 / 查询 / 乐观锁                        │
│ 文件：8 个                                               │
│ 用例：76                                                 │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ 集成测试（@SpringBootTest + Kingbase）                    │
│ 数据源：KingbaseES / PostgreSQL（dev profile）            │
│ 用途：验证 Flyway Schema + 种子数据 + 端到端查询            │
│ 文件：3 个                                               │
│ 用例：15                                                 │
└─────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────┐
│ 领域模型测试（纯 JUnit）                                   │
│ 数据源：无（纯 Java）                                     │
│ 用途：验证聚合根不变式 / 值对象 / 领域事件                   │
│ 文件：6 个                                               │
│ 用例：32                                                 │
└─────────────────────────────────────────────────────────┘
```

## 三、逐层明细

### 3.1 领域模型测试（6 文件 / 32 用例）

| 测试文件 | 用例数 | 被测类 |
|----------|:------:|--------|
| `domain/model/DriverTest.java` | 13 | Driver 聚合根（创建、重构造、校验） |
| `domain/model/TripTest.java` | 13 | Trip 聚合根（状态机、评分、约束） |
| `domain/model/DomainModelReconstituteTest.java` | 6 | DomainModel 重构造不变式 |
| `infra/cache/ConcurrentHashMapCacheTest.java` | — | ConcurrentHashMapCache 缓存实现 |
| `infra/cache/DefaultCacheManagerTest.java` | — | CacheManager 生命周期 |
| `PomXmlTests.java` | — | pom.xml 依赖/版本校验 |

### 3.2 持久层测试 — @DataJpaTest（8 文件 / 76 用例）

| 测试文件 | 用例数 | 被测 Repository |
|----------|:------:|-----------------|
| `infra/persistence/DriverJpaRepositoryTest.java` | 14 | DriverJpaRepository |
| `infra/persistence/TripJpaRepositoryTest.java` | 14 | TripJpaRepository |
| `infra/persistence/VehicleJpaRepositoryTest.java` | 13 | VehicleJpaRepository |
| `infra/persistence/AlertEventJpaRepositoryTest.java` | 13 | AlertEventJpaRepository |
| `infra/persistence/RoadRageVoiceRecordJpaRepositoryTest.java` | 8 | RoadRageVoiceRecordJpaRepository |
| `infra/persistence/ComplexQueryAndLockTest.java` | 7 | 复杂查询 + 悲观锁 |
| `infra/persistence/OptimisticLockTest.java` | 7 | 乐观锁冲突 |
| `infra/repository/DriverRepositoryBridgeTest.java` | — | Repository Bridge 转换 |

### 3.3 接口层测试（14 文件 / 168 用例）

#### REST Controllers（7 文件 / 71 用例）

所有 Controller 使用 `MockMvcBuilders.standaloneSetup()` 独立测试，不启动 Spring 上下文。

| 测试文件 | 用例数 | 覆盖端点 |
|----------|:------:|----------|
| `interfaces/rest/AccountControllerTest.java` | 8 | GET /list, GET /{phone} |
| `interfaces/rest/HealthControllerTest.java` | 8 | GET /{driverId}, PUT /{driverId} |
| `interfaces/rest/DriverControllerTest.java` | 14 | GET /list, POST /, PUT /, DELETE /{id} |
| `interfaces/rest/StorageControllerTest.java` | 9 | GET /info, GET /list, POST /upload |
| `interfaces/rest/GuardianshipControllerTest.java` | 8 | GET /list, POST /, DELETE / |
| `interfaces/rest/SafetyControllerTest.java` | 11 | GET /trip/list, GET /alert/list, GET /vehicle/list |
| `interfaces/rest/ProjectionControllerTest.java` | 13 | GET /alert, GET /dashboard, GET /trajectory |

#### WebSocket（5 文件 / 97 用例）

| 测试文件 | 用例数 | 被测类 |
|----------|:------:|--------|
| `interfaces/websocket/WebSocketSessionRegistryTest.java` | 24 | 会话注册表（并发安全、订阅限制） |
| `interfaces/websocket/OfflineAlertQueueTest.java` | 12 | 离线告警队列（入队/排空/过期/上限） |
| `interfaces/websocket/MediaSessionManagerTest.java` | 15 | 媒体会话管理（创建/结束/续期/过期清理） |
| `interfaces/websocket/GuardianshipWebSocketHandlerTest.java` | 30 | 监护 WebSocket（认证/订阅/媒体/救援/心跳） |
| `interfaces/websocket/FleetWebSocketHandlerTest.java` | 16 | 车队 WebSocket（认证/角色/广播/离线） |

### 3.4 领域服务测试（10 文件 / 105 用例）

| 测试文件 | 用例数 | 被测类 |
|----------|:------:|--------|
| `domain/fleet/FleetAnalyticsServiceImplTest.java` | 7 | 车队分析服务 |
| `domain/fleet/ReportGenerationServiceImplTest.java` | 8 | 报告生成服务 |
| `domain/fleet/ScoringServiceImplTest.java` | 10 | 评分规则服务 |
| `domain/guardianship/PermissionServiceImplTest.java` | 17 | 权限管理服务 |
| `domain/guardianship/DriverStatusBroadcastServiceImplTest.java` | 4 | 司机状态广播服务 |
| `domain/intervention/InterventionServiceImplTest.java` | 19 | 干预决策服务（纯逻辑） |
| `domain/intervention/EmergencyResponseServiceImplTest.java` | 7 | 应急响应服务 |
| `domain/emergency/PrivacyProtectionServiceImplTest.java` | 13 | 隐私保护服务 |
| `domain/ota/OTAUpdateServiceImplTest.java` | 14 | OTA 更新编排服务 |
| `domain/ota/SensorSelfCheckServiceImplTest.java` | 6 | 传感器自检服务 |

### 3.5 应用服务测试（12 文件 / 200 用例）

| 测试文件 | 用例数 | 被测类 |
|----------|:------:|--------|
| `application/HealthApplicationServiceTest.java` | 6 | 健康档案 CRUD |
| `application/GuardianshipApplicationServiceTest.java` | 9 | 监护关系 CRUD |
| `application/AlertApplicationServiceTest.java` | 8 | 告警列表查询 |
| `application/TripApplicationServiceTest.java` | 15 | 行程/车辆查询 |
| `application/DriverApplicationServiceTest.java` | 10 | 司机 CRUD |
| `application/ProjectionApplicationServiceTest.java` | 15 | 投影/仪表盘/轨迹 |
| `application/guardianship/RemoteGuardianshipServiceImplTest.java` | 36 | 远程监护（订阅/媒体/救援） |
| `application/risk/RiskMonitoringServiceImplTest.java` | 19 | 风险监控（DMS/毫米波/生命检测） |
| `application/fleet/FleetManagementServiceImplTest.java` | 26 | 车队管理（疲劳/离线/报告） |
| `application/intervention/InterventionServiceImplTest.java` | 13 | 干预指令 |
| `application/emergency/EmergencyRescueServiceImplTest.java` | 14 | 紧急救援 |
| `application/ota/OTAManagementServiceImplTest.java` | 30 | OTA 升级管理 |

### 3.6 基础设施测试（22 文件 / 247 用例）

#### 事件总线（4 文件）

| 测试文件 | 用例数 |
|----------|:------:|
| `infra/eventbus/EdgeEventBusTest.java` | 10 |
| `infra/eventbus/CloudEventBusTest.java` | — |
| `infra/eventbus/DeadLetterHandlerTest.java` | — |
| `infra/eventbus/OutboxPersisterTest.java` | — |
| `infra/eventbus/OutboxRelayerTest.java` | — |

#### 适配器（8 文件 / 65 用例）

| 测试文件 | 用例数 | 被测适配器 |
|----------|:------:|-----------|
| `infra/adapter/DrivingBehaviorTrackingAdapterTest.java` | 8 | 驾驶行为追踪 |
| `infra/adapter/MediaSessionAdapterTest.java` | 8 | 媒体会话（SparkRTC） |
| `infra/adapter/CameraOcclusionDetectionAdapterTest.java` | 8 | 摄像头遮挡检测 |
| `infra/adapter/RescueReportAdapterTest.java` | 5 | 救援报告 |
| `infra/adapter/VehicleStateBufferAdapterTest.java` | 12 | 车辆状态缓冲 |
| `infra/adapter/OTADeliveryAdapterTest.java` | 7 | OTA 下发 |
| `infra/adapter/PhysiologicalDataBufferAdapterTest.java` | 13 | 生理数据缓冲 |
| `infra/adapter/NotificationAdapterTest.java` | 6 | 通知推送 |

#### 安全（4 文件 / 59 用例）

| 测试文件 | 用例数 | 被测类 |
|----------|:------:|--------|
| `infra/security/JwtTokenProviderTest.java` | 22 | JWT 令牌（创建/验证/续期/过期） |
| `infra/security/AesGcmEncryptionTest.java` | 14 | AES-GCM 加解密 |
| `infra/security/RateLimitFilterTest.java` | 9 | 限流过滤器 |
| `infra/security/SecurityPropertiesTest.java` | 14 | 安全配置属性 |

#### 存储 / Edge / MQTT（6 文件 / 86 用例）

| 测试文件 | 用例数 | 被测类 |
|----------|:------:|--------|
| `infra/storage/LocalFileStorageServiceTest.java` | 15 | 本地文件存储 |
| `infra/storage/FileExpiryServiceTest.java` | 6 | 文件过期清理 |
| `infra/edge/EdgeCloudSyncServiceTest.java` | 10 | 边缘云同步 |
| `infra/edge/EdgePersistenceServiceTest.java` | 12 | 边缘持久化 |
| `interfaces/mqtt/MqttDeviceGatewayTest.java` | 28 | MQTT 设备网关 |
| `interfaces/mqtt/MqttDeviceAuthProviderTest.java` | 17 | MQTT 设备鉴权 |

### 3.7 集成测试（3 文件 / 15 用例）

| 测试文件 | 用例数 | 说明 |
|----------|:------:|------|
| `integration/KingbaseJdbcIntegrationTest.java` | 10 | JDBC 直连 — Schema/种子数据/CRUD/约束 |
| `integration/KingbaseJpaRepositoryIntegrationTest.java` | — | JPA Repository 金仓集成 |
| `integration/KingbaseApplicationServiceTest.java` | 5 | 应用服务金仓端到端 |
| `AiotApplicationTests.java` | — | Spring 上下文加载冒烟测试 |

## 四、测试约定

### 4.1 Mock 策略

一律使用 `@ExtendWith(MockitoExtension.class)` + `@Mock` + 手动构造注入。**全项目零 `@MockBean`**。

```java
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

### 4.2 Controller 测试

使用 `MockMvcBuilders.standaloneSetup()`，不启动 Spring 上下文，避免与安全过滤器链冲突。

### 4.3 断言

仅使用 JUnit 5 内置断言：`assertEquals`、`assertTrue`、`assertThrows`、`assertNotNull`、`assertFalse`。未使用 AssertJ 或 Hamcrest。

### 4.4 测试组织

- 领域模型测试使用 `@Nested` + `@DisplayName` 分组
- 服务测试按方法拆分，每方法覆盖：happy path / null or empty / 异常传播 / 边界条件

## 五、未覆盖项（有意跳过）

| 组件 | 原因 |
|------|------|
| `KeyStoreKeyManager` | 构造函数需要真实 PKCS12 keystore 文件（`/tmp/aiot-dev-keystore.p12`） |
| `MqttClientManager` | 构造函数内联创建 Eclipse Paho `MqttAsyncClient`，需要真实 MQTT Broker |
| `EdgeMqttClient` | 同上 |
| `MqttDeviceGateway`（IoTDA 路径） | 依赖真实华为 IoTDA 平台连接 |
| `WebSocketConfig` | Spring `WebSocketConfigurer` 配置注册类，逻辑过于简单无需单测 |
| `MqttPayloads` / `MqttTopics` / `WebSocketPayloads` | DTO / 常量类，无行为逻辑 |

## 六、运行方式

```bash
# 全量测试（H2 内存库）
cd code/server
mvn test -B -Dspring.profiles.active=ci

# 单模块测试
mvn test -B -Dspring.profiles.active=ci -pl . -Dtest=DriverControllerTest

# 金仓集成测试（需要 Kingbase Docker 运行中）
mvn test -B -Dspring.profiles.active=dev -Dtest="com.aiot.integration.*"
```

## 七、CI 流水线

GitHub Actions 配置（`.github/workflows/ci.yml`）：

```yaml
backend:
  steps:
    - mvn compile
    - mvn test
    - JUnit test report (dorny/test-reporter@v2)
```

## 八、相关文件

| 文件 | 用途 |
|------|------|
| `code/server/pom.xml` | Maven 依赖（`spring-boot-starter-test`、H2） |
| `code/server/src/main/resources/application-ci.yml` | CI profile（H2 数据源配置） |
| `code/server/src/test/java/com/aiot/` | 测试根目录（76 个文件） |
| `docs/kingbase_integration_test_report.md` | 金仓集成测试专项报告 |
| `.github/workflows/ci.yml` | GitHub Actions CI 配置 |
