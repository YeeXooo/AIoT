package com.aiot.infra.persistence;

import com.aiot.infra.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@DisplayName("复杂查询与投影综合测试")
class ComplexQueryAndLockTest {

    @Autowired private TripJpaRepository tripRepo;
    @Autowired private AlertEventJpaRepository alertRepo;
    @Autowired private VehicleJpaRepository vehicleRepo;
    @Autowired private DriverJpaRepository driverRepo;
    @Autowired private AlertProjectionJpaRepository projectionRepo;
    @Autowired private FleetDashboardProjectionJpaRepository dashboardRepo;
    @Autowired private GuardianshipJpaRepository guardianshipRepo;
    @Autowired private SystemAccountJpaRepository accountRepo;
    @Autowired private TestEntityManager em;

    private static final String FLEET_ID = "fleet-test";
    private static final String D_ID = "d-ctest";
    private static final String V_ID = "v-ctest";
    private static final String T_ID = "t-ctest";

    @BeforeEach
    void seed() {
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId(D_ID); d.setName("测试司机"); d.setPhone("13900000001");
        driverRepo.save(d);

        VehicleJpaEntity v = new VehicleJpaEntity();
        v.setVehicleId(V_ID); v.setLicensePlate("京T00001"); v.setVin("VINTEST"); v.setTerminalSn("SN_TEST");
        v.setFleetId(FLEET_ID);
        vehicleRepo.save(v);

        TripJpaEntity t = new TripJpaEntity();
        t.setTripId(T_ID); t.setDriverId(D_ID); t.setVehicleId(V_ID);
        t.setStartedAt(LocalDateTime.now().minusHours(2));
        t.setEndedAt(LocalDateTime.now().minusMinutes(30));
        t.setHardBrakingCount(3); t.setHardAccelerationCount(2); t.setScoreValue(70);
        tripRepo.save(t);
    }

    @Test @DisplayName("批量写入告警事件并过滤查询")
    void batchAlertWithFilter() {
        for (int i = 0; i < 10; i++) {
            AlertEventJpaEntity a = new AlertEventJpaEntity();
            a.setAlertId("a-batch-" + i);
            a.setTripId(T_ID);
            a.setDriverId(D_ID);
            a.setVehicleId(V_ID);
            a.setAlertType(i < 4 ? "FATIGUE" : i < 7 ? "DISTRACTION" : "ROAD_RAGE");
            a.setRiskLevel(i < 2 ? "L3_CRITICAL" : "L2_WARNING");
            a.setOccurredAt(LocalDateTime.now().minusMinutes(10 - i));
            alertRepo.save(a);
        }
        em.flush();
        em.clear();
        assertEquals(10, alertRepo.findByDriverId(D_ID).size());
        assertEquals(5, alertRepo.findFiltered(null, "L3_CRITICAL", null).size());
        assertEquals(6, alertRepo.findFiltered(null, null, "FATIGUE").size());
    }

    @Test @DisplayName("告警投影写入与查询")
    void alertProjectionInsertAndQuery() {
        AlertProjectionEntity p = new AlertProjectionEntity();
        p.setAlertId("p-001");
        p.setDriverId(D_ID);
        p.setVehicleId(V_ID);
        p.setFleetId(FLEET_ID);
        p.setAlertType("FATIGUE");
        p.setRiskLevel("L3_CRITICAL");
        p.setOccurredAt(LocalDateTime.now());
        projectionRepo.save(p);
        em.flush();
        em.clear();

        List<AlertProjectionEntity> fleetAlerts = projectionRepo.findByFleetId(FLEET_ID);
        assertEquals(1, fleetAlerts.size());
    }

    @Test @DisplayName("车队看板投影聚合滚动")
    void fleetDashboardAggregation() {
        FleetDashboardProjectionEntity dp = new FleetDashboardProjectionEntity();
        dp.setFleetId(FLEET_ID); dp.setRiskLevel("L2_WARNING");
        dp.setAlertType("FATIGUE"); dp.setAlertCount(5); dp.setDriverCount(3);
        dashboardRepo.save(dp);
        em.flush();
        em.clear();

        List<FleetDashboardProjectionEntity> byFleet = dashboardRepo.findByFleetId(FLEET_ID);
        assertEquals(1, byFleet.size());
        assertEquals(5, byFleet.get(0).getAlertCount());
    }

    @Test @DisplayName("监护关系写入与查询")
    void guardianshipLifecycle() {
        SystemAccountJpaEntity acc = new SystemAccountJpaEntity();
        acc.setAccountId("acc-guard"); acc.setPhone("15000000001"); acc.setRole("FAMILY");
        accountRepo.save(acc);

        GuardianshipEntity g = new GuardianshipEntity();
        g.setDriverId(D_ID); g.setAccountId("acc-guard");
        g.setGrantedAt(LocalDateTime.now());
        g.setPermissions("{\"MEDIA_CALL\":true,\"STATUS_MONITORING\":true}");
        g.setGrantReason("REGULAR_60S");
        guardianshipRepo.save(g);
        em.flush();
        em.clear();

        List<GuardianshipEntity> byDriver = guardianshipRepo.findByDriverId(D_ID);
        assertEquals(1, byDriver.size());
        assertEquals("acc-guard", byDriver.get(0).getAccountId());
    }
}
