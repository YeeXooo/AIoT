package com.aiot.integration;

import com.aiot.infra.persistence.*;
import com.aiot.infra.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("金仓 + JPA Repository 集成测试")
class KingbaseJpaRepositoryIntegrationTest extends KingbaseIntegrationTestBase {

    @Autowired private DriverJpaRepository driverRepo;
    @Autowired private VehicleJpaRepository vehicleRepo;
    @Autowired private TripJpaRepository tripRepo;
    @Autowired private AlertEventJpaRepository alertRepo;
    @Autowired private SystemAccountJpaRepository accountRepo;
    @Autowired private DriverHealthProfileJpaRepository healthRepo;
    @Autowired private GuardianshipJpaRepository guardianshipRepo;
    @Autowired private PhysiologicalSnapshotJpaRepository snapshotRepo;
    @Autowired private AlertProjectionJpaRepository alertProjectionRepo;
    @Autowired private FleetDashboardProjectionJpaRepository dashboardRepo;
    @Autowired private TrajectoryProjectionJpaRepository trajectoryRepo;

    @Test @DisplayName("种子数据可见 — JPA count() 可看到 Flyway 插入的全部 12 张表数据")
    void flywaySeedDataVisible() {
        assertEquals(5, driverRepo.count(), "应看到 5 名驾驶员");
        assertEquals(5, vehicleRepo.count(), "应看到 5 辆车");
        assertEquals(7, tripRepo.count(), "应看到 7 次行程");
        assertEquals(5, alertRepo.count(), "应看到 5 条告警");
        assertEquals(3, accountRepo.count(), "应看到 3 个账户");
    }

    @Test @DisplayName("V4 种子数据可见 — 健康档案 / 监护 / 快照 / 看板 / 轨迹")
    void flywayV4SeedDataVisible() {
        assertEquals(2, healthRepo.count(), "应看到 2 个健康档案");
        assertEquals(2, guardianshipRepo.count(), "应看到 2 条监护关系");
        assertEquals(7, snapshotRepo.count(), "应看到 7 条生理快照");
        assertEquals(5, alertProjectionRepo.count(), "应看到 5 条告警投影");
        assertEquals(5, dashboardRepo.count(), "应看到 5 条看板投影");
        assertEquals(5, trajectoryRepo.count(), "应看到 5 条轨迹投影");
    }

    @Test @DisplayName("JPA findById — 可查到种子驾驶员完整字段")
    void findSeedDriverById() {
        String driverId = "d001-d2e3-4abc-9f01-123456789abc";
        Optional<DriverJpaEntity> found = driverRepo.findById(driverId);
        assertTrue(found.isPresent(), "应能通过 JPA 查询到种子驾驶员 d001");
        assertEquals("张伟", found.get().getName());
        assertEquals("13800000001", found.get().getPhone());
        assertEquals(88, found.get().getComprehensiveScore());
        assertNotNull(found.get().getCreatedAt());
        assertNotNull(found.get().getUpdatedAt());
        assertNotNull(found.get().getVersion());
    }

    @Test @DisplayName("JDBC LIKE — 金仓原生 LIKE 正常工作（验证 escape 问题是 Hibernate 层面）")
    void jdbcLikeWorks() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_driver WHERE name LIKE ?", Integer.class, "%李%");
        assertEquals(1, count, "JDBC LIKE 应返回 1（李强）");
    }

    @Test @DisplayName("JPA CRUD — 不使用 @Transactional，手动清理")
    void crudWithoutTransactional() {
        String testId = TEST_PREFIX + "crud-driver";
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId(testId);
        d.setName("CRUD测试");
        d.setPhone("19900000001");
        d.setComprehensiveScore(80);
        driverRepo.save(d);

        Optional<DriverJpaEntity> found = driverRepo.findById(testId);
        assertTrue(found.isPresent(), "保存后应能查到");
        assertEquals("CRUD测试", found.get().getName());
        assertEquals(80, found.get().getComprehensiveScore());

        found.get().setComprehensiveScore(85);
        driverRepo.save(found.get());

        var updated = driverRepo.findById(testId);
        assertTrue(updated.isPresent());
        assertEquals(85, updated.get().getComprehensiveScore());

        driverRepo.deleteById(testId);
        assertTrue(driverRepo.findById(testId).isEmpty(), "删除后应查不到");
    }

    @Test @DisplayName("JPA Save → 自动填充 createdAt / updatedAt / version")
    void saveAutoPopulatesTimestampsAndVersion() {
        String testId = TEST_PREFIX + "auto-fields";
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId(testId);
        d.setName("自动填充测试");
        d.setPhone("19900000002");
        d.setComprehensiveScore(70);

        driverRepo.save(d);

        var found = driverRepo.findById(testId).orElseThrow();
        assertNotNull(found.getCreatedAt(), "createdAt 应自动填充");
        assertNotNull(found.getUpdatedAt(), "updatedAt 应自动填充");
        assertEquals(0, found.getVersion(), "新实体的 version 应为 0");
    }

    @Test @DisplayName("JPA Update → updatedAt 刷新, version 递增")
    void updateRefreshesTimestampAndVersion() {
        String testId = TEST_PREFIX + "update-fields";
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId(testId);
        d.setName("更新测试");
        d.setPhone("19900000003");
        d.setComprehensiveScore(60);
        driverRepo.save(d);

        var saved = driverRepo.findById(testId).orElseThrow();
        LocalDateTime originalUpdatedAt = saved.getUpdatedAt();
        int originalVersion = saved.getVersion();

        saved.setComprehensiveScore(65);
        driverRepo.save(saved);

        var updated = driverRepo.findById(testId).orElseThrow();
        assertTrue(updated.getUpdatedAt().isAfter(originalUpdatedAt),
                "updatedAt 应在更新后刷新");
        assertEquals(originalVersion + 1, updated.getVersion(),
                "version 应在每次更新时递增");
    }

    @Test @DisplayName("乐观锁冲突 — 并发修改过期版本抛异常")
    void optimisticLockConflict() {
        String testId = TEST_PREFIX + "lock-conflict";
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId(testId);
        d.setName("锁冲突测试");
        d.setPhone("19900000004");
        d.setComprehensiveScore(50);
        driverRepo.saveAndFlush(d);

        var copyA = driverRepo.findById(testId).orElseThrow();
        var copyB = driverRepo.findById(testId).orElseThrow();
        assertEquals(0, copyA.getVersion());
        assertEquals(0, copyB.getVersion());

        copyA.setComprehensiveScore(55);
        driverRepo.saveAndFlush(copyA);

        copyB.setComprehensiveScore(60);
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> driverRepo.saveAndFlush(copyB),
                "过期版本 0 提交应触发乐观锁冲突");
    }

    @Test @DisplayName("关联表操作 — 带 FK 的 Trip 持久化")
    void tripCrudWithForeignKeys() {
        String tripId = TEST_PREFIX + "trip-fk";
        String refDriverId = "d001-d2e3-4abc-9f01-123456789abc";
        String refVehicleId = "v001-c7e5-4ghi-a011-678901234hij";

        TripJpaEntity t = new TripJpaEntity();
        t.setTripId(tripId);
        t.setDriverId(refDriverId);
        t.setVehicleId(refVehicleId);
        t.setStartedAt(LocalDateTime.now().minusHours(1));
        t.setHardBrakingCount(0);
        t.setHardAccelerationCount(0);
        tripRepo.save(t);

        var found = tripRepo.findById(tripId);
        assertTrue(found.isPresent(), "带 FK 的行程应可持久化");
        assertEquals(refDriverId, found.get().getDriverId());
        assertEquals(refVehicleId, found.get().getVehicleId());
    }

    @Test @DisplayName("显式事务中 JPA 可看到种子数据")
    void explicitTransactionSeesFlywayData() {
        String driverId = "d002-f3a1-5bcd-8e02-234567890bcd";
        var found = newTransaction().execute(status -> {
            var d = driverRepo.findById(driverId);
            assertTrue(d.isPresent(), "显式事务中 JPA 应能看到 Flyway 种子数据");
            assertEquals("李强", d.get().getName());
            return d.get();
        });
        assertNotNull(found);
        assertEquals("李强", found.getName());
    }
}
