# 验证报告（v2）

## 结果
PASSED

## 统计
- 通过：23
- 失败：0

## 测试执行日志

```
[[1;34mINFO[m] Scanning for projects...
[[1;34mINFO[m] 
[[1;34mINFO[m] [1m------------------------< [0;36mcom.aiot:aiot-server[0;1m >------------------------[m
[[1;34mINFO[m] [1mBuilding AIoT Server 0.1.0-SNAPSHOT[m
[[1;34mINFO[m] [1m--------------------------------[ jar ]---------------------------------[m
[[1;34mINFO[m] 
[[1;34mINFO[m] [1m--- [0;32mmaven-resources-plugin:3.3.1:resources[m [1m(default-resources)[m @ [36maiot-server[0;1m ---[m
[[1;34mINFO[m] Copying 2 resources from src/main/resources to target/classes
[[1;34mINFO[m] Copying 0 resource from src/main/resources to target/classes
[[1;34mINFO[m] 
[[1;34mINFO[m] [1m--- [0;32mmaven-compiler-plugin:3.11.0:compile[m [1m(default-compile)[m @ [36maiot-server[0;1m ---[m
[[1;34mINFO[m] Nothing to compile - all classes are up to date
[[1;34mINFO[m] 
[[1;34mINFO[m] [1m--- [0;32mmaven-resources-plugin:3.3.1:testResources[m [1m(default-testResources)[m @ [36maiot-server[0;1m ---[m
[[1;34mINFO[m] skip non existing resourceDirectory /home/jasper/AIoT/code/server/src/test/resources
[[1;34mINFO[m] 
[[1;34mINFO[m] [1m--- [0;32mmaven-compiler-plugin:3.11.0:testCompile[m [1m(default-testCompile)[m @ [36maiot-server[0;1m ---[m
[[1;34mINFO[m] Nothing to compile - all classes are up to date
[[1;34mINFO[m] 
[[1;34mINFO[m] [1m--- [0;32mmaven-surefire-plugin:3.1.2:test[m [1m(default-test)[m @ [36maiot-server[0;1m ---[m
[[1;34mINFO[m] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[[1;34mINFO[m] 
[[1;34mINFO[m] -------------------------------------------------------
[[1;34mINFO[m]  T E S T S
[[1;34mINFO[m] -------------------------------------------------------
[[1;34mINFO[m] Running com.aiot.[1mAiotApplicationTests[m
18:38:08.305 [main] INFO org.springframework.test.context.support.AnnotationConfigContextLoaderUtils -- Could not detect default configuration classes for test class [com.aiot.AiotApplicationTests]: AiotApplicationTests does not declare any static, non-private, non-final, nested classes annotated with @Configuration.
18:38:08.484 [main] INFO org.springframework.boot.test.context.SpringBootTestContextBootstrapper -- Found @SpringBootConfiguration com.aiot.AiotApplication for test class com.aiot.AiotApplicationTests

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.5)

2026-06-29T18:38:09.293+08:00  INFO 592059 --- [           main] com.aiot.AiotApplicationTests            : Starting AiotApplicationTests using Java 21.0.11 with PID 592059 (started by jasper in /home/jasper/AIoT/code/server)
2026-06-29T18:38:09.297+08:00  INFO 592059 --- [           main] com.aiot.AiotApplicationTests            : The following 1 profile is active: "ci"
2026-06-29T18:38:10.704+08:00  INFO 592059 --- [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-06-29T18:38:10.759+08:00  INFO 592059 --- [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 36 ms. Found 0 JPA repository interfaces.
2026-06-29T18:38:12.179+08:00  INFO 592059 --- [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-06-29T18:38:12.621+08:00  INFO 592059 --- [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection conn0: url=jdbc:h2:mem:aiot user=SA
2026-06-29T18:38:12.623+08:00  INFO 592059 --- [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-06-29T18:38:12.707+08:00  INFO 592059 --- [           main] org.flywaydb.core.FlywayExecutor         : Database: jdbc:h2:mem:aiot (H2 2.2)
2026-06-29T18:38:12.731+08:00  WARN 592059 --- [           main] o.f.c.internal.database.base.Database    : Flyway upgrade recommended: H2 2.2.224 is newer than this version of Flyway and support has not been tested. The latest supported version of H2 is 2.2.220.
2026-06-29T18:38:12.758+08:00  INFO 592059 --- [           main] o.f.c.i.s.JdbcTableSchemaHistory         : Schema history table "PUBLIC"."flyway_schema_history" does not exist yet
2026-06-29T18:38:12.761+08:00  INFO 592059 --- [           main] o.f.core.internal.command.DbValidate     : Successfully validated 0 migrations (execution time 00:00.011s)
2026-06-29T18:38:12.761+08:00  WARN 592059 --- [           main] o.f.core.internal.command.DbValidate     : No migrations found. Are your locations set up correctly?
2026-06-29T18:38:12.776+08:00  INFO 592059 --- [           main] o.f.c.i.s.JdbcTableSchemaHistory         : Creating Schema History table "PUBLIC"."flyway_schema_history" ...
2026-06-29T18:38:12.874+08:00  INFO 592059 --- [           main] o.f.core.internal.command.DbMigrate      : Current version of schema "PUBLIC": << Empty Schema >>
2026-06-29T18:38:12.880+08:00  INFO 592059 --- [           main] o.f.core.internal.command.DbMigrate      : Schema "PUBLIC" is up to date. No migration necessary.
2026-06-29T18:38:13.064+08:00  INFO 592059 --- [           main] o.hibernate.jpa.internal.util.LogHelper  : HHH000204: Processing PersistenceUnitInfo [name: default]
2026-06-29T18:38:13.163+08:00  INFO 592059 --- [           main] org.hibernate.Version                    : HHH000412: Hibernate ORM core version 6.4.4.Final
2026-06-29T18:38:13.206+08:00  INFO 592059 --- [           main] o.h.c.internal.RegionFactoryInitiator    : HHH000026: Second-level cache disabled
2026-06-29T18:38:13.554+08:00  INFO 592059 --- [           main] o.s.o.j.p.SpringPersistenceUnitInfo      : No LoadTimeWeaver setup: ignoring JPA class transformer
2026-06-29T18:38:14.102+08:00  INFO 592059 --- [           main] o.h.e.t.j.p.i.JtaPlatformInitiator       : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-06-29T18:38:14.111+08:00  INFO 592059 --- [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-06-29T18:38:14.385+08:00  WARN 592059 --- [           main] JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-06-29T18:38:15.438+08:00  INFO 592059 --- [           main] com.aiot.AiotApplicationTests            : Started AiotApplicationTests in 6.739 seconds (process running for 8.566)
OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
WARNING: A Java agent has been loaded dynamically (/home/jasper/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.13/byte-buddy-agent-1.14.13.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
[[1;34mINFO[m] [1;32mTests run: [0;1;32m1[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.625 s -- in com.aiot.[1mAiotApplicationTests[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests$UnchangedBaseline[m
[[1;34mINFO[m] [1;32mTests run: [0;1;32m3[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.074 s -- in com.aiot.[1mPomXmlTests$UnchangedBaseline[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests$Dependencies[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests$Dependencies$Invariants[m
[[1;34mINFO[m] [1;32mTests run: [0;1;32m2[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.047 s -- in com.aiot.[1mPomXmlTests$Dependencies$Invariants[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests$Dependencies$Lombok[m
[[1;34mINFO[m] [1;32mTests run: [0;1;32m3[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.023 s -- in com.aiot.[1mPomXmlTests$Dependencies$Lombok[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests$Dependencies$FlywayPostgresql[m
[[1;34mINFO[m] [1;32mTests run: [0;1;32m3[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in com.aiot.[1mPomXmlTests$Dependencies$FlywayPostgresql[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests$Dependencies$FlywayCore[m
[[1;34mINFO[m] [1;32mTests run: [0;1;32m3[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.018 s -- in com.aiot.[1mPomXmlTests$Dependencies$FlywayCore[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests$Dependencies$JacksonJsr310[m
[[1;34mINFO[m] [1;32mTests run: [0;1;32m3[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.017 s -- in com.aiot.[1mPomXmlTests$Dependencies$JacksonJsr310[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests$Dependencies$WebSocket[m
[[1;34mINFO[m] [1;32mTests run: [0;1;32m3[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s -- in com.aiot.[1mPomXmlTests$Dependencies$WebSocket[m
[[1;34mINFO[m] [1mTests run: [0;1m0[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.165 s -- in com.aiot.[1mPomXmlTests$Dependencies[m
[[1;34mINFO[m] Running com.aiot.[1mPomXmlTests$Properties[m
[[1;34mINFO[m] [1;32mTests run: [0;1;32m2[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.007 s -- in com.aiot.[1mPomXmlTests$Properties[m
[[1;34mINFO[m] [1mTests run: [0;1m0[m, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.356 s -- in com.aiot.[1mPomXmlTests[m
[[1;34mINFO[m] 
[[1;34mINFO[m] Results:
[[1;34mINFO[m] 
[[1;34mINFO[m] [1;32mTests run: 23, Failures: 0, Errors: 0, Skipped: 0[m
[[1;34mINFO[m] 
[[1;34mINFO[m] [1m------------------------------------------------------------------------[m
[[1;34mINFO[m] [1;32mBUILD SUCCESS[m
[[1;34mINFO[m] [1m------------------------------------------------------------------------[m
[[1;34mINFO[m] Total time:  12.908 s
[[1;34mINFO[m] Finished at: 2026-06-29T18:38:17+08:00
[[1;34mINFO[m] [1m------------------------------------------------------------------------[m
```
