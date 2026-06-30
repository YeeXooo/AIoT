# 测试审查报告（v2 r1）

## 审查结果
APPROVED

## 发现

本任务行为契约（detail_v2.md §行为契约/§依赖规格）范围仅限 `pom.xml` 依赖补齐，对应的交付测试为 `code/server/src/test/java/com/aiot/PomXmlTests.java`。已对其逐条核对契约并实际运行验证。

### 覆盖度核对（与契约逐条映射）
- **spring-boot-starter-websocket**：坐标唯一存在、无 `<version>`（父 BOM 管理）、无 `<scope>`（默认 compile）——三条全覆盖（契约 §1）。
- **jackson-datatype-jsr310**：坐标唯一、无 version、无 scope——全覆盖（契约 §2）。
- **flyway-core**：坐标唯一、无 version（经 `${flyway.version}` 解析）、无 scope——全覆盖（契约 §3）。
- **flyway-database-postgresql**：坐标唯一、显式 `<version>${flyway.version}</version>`、`<scope>runtime</scope>`——全覆盖（契约 §4，含 r1 修订的关键点）。
- **lombok**：坐标唯一、无 version、`<scope>provided</scope>`——全覆盖（契约 §5）。
- **properties**：`flyway.version=10.10.0`、`java.version=17` 精确校验（契约 §properties 变更）。
- **不改动边界**：postgresql 驱动 `runtime`、h2 `test`、spring-boot-starter-test `test` 均有守护断言（契约 §不改动项）。
- **跨依赖不变量**：`flywayVersionsConsistent` 验证 core/database 同源于 `${flyway.version}`；`noDuplicates` 校验 12 项坐标各出现且仅出现一次——有效防止重复引入与版本错配（契约 §关键设计决策）。

### 断言有效性
- 断言均为非空泛断言：精确值比较（如 `assertEquals("10.10.0", ...)`、`"runtime"`、`"provided"`、`"${flyway.version}"`）、节点计数 `assertEquals(1, ...)`、scope 存在/缺失（`assertNotNull`/`assertNull`）方向正确，能在 POM 写错时真实失败。
- 命名空间处理正确：POM 含默认命名空间 `xmlns="http://maven.apache.org/POM/4.0.0"`，测试采用非命名空间感知解析（`DocumentBuilderFactory` 默认）+ 无前缀 XPath，二者匹配。已就含点号元素名（`flyway.version`/`java.version`）与 dependency 谓词 XPath 做独立实证，均能正确取值，不存在静默返回空导致假通过的风险。

### 实证运行
- `mvn -f code/server/pom.xml test -Dtest=PomXmlTests`：**Tests run: 22, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS**。
- 全量 `mvn -f code/server/pom.xml test`（契约 §验证说明指定的验证命令）：**Tests run: 23, Failures: 0, Errors: 0, Skipped: 0，BUILD SUCCESS**（含 AiotApplicationTests 上下文加载烟雾测试，Flyway 10.10.0 + H2 仅产生版本提示 WARN，非错误）。

### 轻微（不影响正确性，不构成驳回）
- **[轻微]** `PomXmlTests.java` — 测试仅对 POM 文本结构做断言，未直接断言"依赖可被仓库解析"。但 Maven 在 test 阶段前已完成依赖解析，契约 §后置条件中 `mvn dependency:resolve` 成功这一项由构建本身保证（构建能跑到测试即说明解析成功），故对 POM 做结构化单元测试的范围划分是恰当的，无需额外补充网络解析断言。
- **[轻微]** `PomXmlTests.java:29` — `Paths.get("pom.xml")` 依赖工作目录为模块根。Surefire 默认以模块 basedir 为 CWD，实践可靠；若将来从 reactor 根目录以非常规方式调用需留意。
- **[轻微]** `AiotApplicationTests.java` — 该上下文加载测试属后续 0.4 任务契约，非本任务交付物；当前实际通过，仅作信息记录，不计入本任务判定。

## 结论
交付测试覆盖完整、断言有效、运行可靠，无严重、无一般问题。
