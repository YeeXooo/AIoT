package com.aiot.infra.persistence;

import com.aiot.infra.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@DisplayName("乐观锁并发冲突测试")
class OptimisticLockTest {

    @Autowired private TripJpaRepository tripRepo;
    @Autowired private VehicleJpaRepository vehicleRepo;
    @Autowired private DriverJpaRepository driverRepo;
    @Autowired private EntityManager entityManager;

    @Test @DisplayName("通过 entityManager.lock 触发乐观锁异常")
    void tripOptimisticLockViaLock() {
        seed();
        entityManager.flush();
        entityManager.clear();

        TripJpaEntity e1 = tripRepo.findById("t-olock").orElseThrow();
        entityManager.clear();

        // 用另一个连接模拟并发 UPDATE 增加 version
        entityManager.createNativeQuery(
                "UPDATE t_trip SET version = version + 1 WHERE trip_id = ?1")
                .setParameter(1, "t-olock")
                .executeUpdate();

        // lock 应检测到版本冲突
        assertThrows(Exception.class, () -> {
            entityManager.lock(e1, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        });
    }

    @Test @DisplayName("乐观锁保护下 version 随更新递增")
    void versionIncrementsOnUpdate() {
        seed();
        entityManager.flush();
        entityManager.clear();

        TripJpaEntity e1 = tripRepo.findById("t-olock").orElseThrow();
        assertEquals(0, e1.getVersion());
        e1.setHardBrakingCount(100);
        TripJpaEntity saved = tripRepo.save(e1);
        entityManager.flush();
        entityManager.clear();

        TripJpaEntity latest = tripRepo.findById("t-olock").orElseThrow();
        assertEquals(1, latest.getVersion());
        assertEquals(100, latest.getHardBrakingCount());
    }

    @Test @DisplayName("version 字段在多轮更新中持续递增")
    void versionIncrementsTwice() {
        seed();
        entityManager.flush();
        entityManager.clear();

        TripJpaEntity e = tripRepo.findById("t-olock").orElseThrow();
        e.setHardBrakingCount(10);
        tripRepo.save(e);
        entityManager.flush();
        entityManager.clear();

        TripJpaEntity e2 = tripRepo.findById("t-olock").orElseThrow();
        assertEquals(1, e2.getVersion());
        e2.setHardBrakingCount(20);
        tripRepo.save(e2);
        entityManager.flush();
        entityManager.clear();

        TripJpaEntity e3 = tripRepo.findById("t-olock").orElseThrow();
        assertEquals(2, e3.getVersion());
    }

    @Test @DisplayName("H2 内嵌数据库 Hibernate DDL 自动为 @Version 列创建")
    void versionColumnExistsInSchema() {
        seed();
        Object result = entityManager.createNativeQuery(
                "SELECT version FROM t_trip WHERE trip_id = ?1")
                .setParameter(1, "t-olock")
                .getSingleResult();
        assertNotNull(result);
        assertEquals(0, ((Number) result).intValue());
    }

    private void seed() {
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId("d-olock"); d.setName("锁测试"); d.setPhone("137");
        driverRepo.save(d);

        VehicleJpaEntity v = new VehicleJpaEntity();
        v.setVehicleId("v-olock"); v.setLicensePlate("京L00001"); v.setVin("VINLOCK"); v.setTerminalSn("SNLOCK");
        vehicleRepo.save(v);

        TripJpaEntity t = new TripJpaEntity();
        t.setTripId("t-olock"); t.setDriverId("d-olock"); t.setVehicleId("v-olock");
        t.setStartedAt(LocalDateTime.now().minusHours(1));
        t.setHardBrakingCount(0); t.setHardAccelerationCount(0);
        tripRepo.save(t);
    }
}
