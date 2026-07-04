package com.aiot.interfaces.rest;

import com.aiot.application.fleet.FleetManagementServiceImpl;
import com.aiot.application.risk.IRiskMonitoringService;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "aiot.data.initialize", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final IRiskMonitoringService riskService;
    private final FleetManagementServiceImpl fleetService;
    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;

    public DataInitializer(IRiskMonitoringService riskService,
                           FleetManagementServiceImpl fleetService,
                           TripRepository tripRepository,
                           VehicleRepository vehicleRepository) {
        this.riskService = riskService;
        this.fleetService = fleetService;
        this.tripRepository = tripRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始注入运行时数据...");

        // 1. 为活跃行程启动监测会话
        List<Trip> activeTrips = tripRepository.findActiveTrips();
        log.info("发现 {} 个活跃行程", activeTrips.size());
        for (Trip trip : activeTrips) {
            Result<IRiskMonitoringService.StartMonitoringResponse, ?> result =
                    riskService.startMonitoringSession(trip.driverId(), trip.vehicleId());
            if (result.isOk()) {
                var resp = result.unwrap();
                log.info("启动监测会话: driver={}, vehicle={}, handle={}",
                        resp.driverId(), resp.vehicleId(), resp.sessionHandle());

                // 注入模拟传感器读数，产生告警
                injectMockSensorReadings(resp.sessionHandle(), trip.tripId());
            }
        }

        // 为无活跃行程的司机也启动独立会话
        List<Vehicle> vehicles = vehicleRepository.findAll();
        for (Vehicle v : vehicles) {
            var started = activeTrips.stream().anyMatch(t -> t.vehicleId().id().equals(v.vehicleId().id()));
            if (!started) {
                riskService.startMonitoringSession(
                        new DriverId(v.vehicleId().id().replace("v", "d")),
                        v.vehicleId());
            }

            // 标记脱线车辆：v004 传感器故障
            if ("v004-f0b8-7jkl-d044-901234567klm".equals(v.vehicleId().id())) {
                v.updateMonitoringOffline(true);
            }
        }

        // 2. 注册司机→车队映射
        List<Trip> allTrips = tripRepository.findAll();
        for (Trip trip : allTrips) {
            vehicleRepository.findById(trip.vehicleId().id()).ifPresent(vehicle ->
                    vehicle.fleetId().ifPresent(fleetId -> {
                        fleetService.registerDriverFleet(trip.driverId().id(), fleetId);
                        log.info("注册司机车队: driver={}, fleet={}", trip.driverId().id(), fleetId);
                    }));
        }

        // 3. 注入轨迹数据
        double[][] mockRoutes = {
                {39.9042, 116.4074}, {39.9080, 116.4100}, {39.9120, 116.4150},
                {39.9150, 116.4180}, {39.9180, 116.4220}, {39.9200, 116.4250}
        };
        for (Vehicle v : vehicles) {
            for (int i = 0; i < mockRoutes.length; i++) {
                fleetService.storeTrajectoryPoint(v.vehicleId().id(),
                        new FleetManagementServiceImpl.InMemoryTrajectoryPoint(
                                System.currentTimeMillis() - (mockRoutes.length - i) * 60000L,
                                mockRoutes[i][0] + Math.random() * 0.01,
                                mockRoutes[i][1] + Math.random() * 0.01,
                                40 + Math.random() * 50));
            }
        }

        // 4. 刷新车队数据时间戳
        fleetService.updateFleetFreshness("fleet-east-1", LocalDateTime.now());
        fleetService.updateFleetFreshness("fleet-west-1", LocalDateTime.now());
        fleetService.updateFleetFreshness("fleet-south-1", LocalDateTime.now());

        log.info("运行时数据注入完成");
    }

    private void injectMockSensorReadings(String sessionHandle, TripId tripId) {
        // 模拟疲劳读数 → 产生 FATIGUE 告警
        sendReading(sessionHandle, tripId, SensorReading.SensorType.DMS_CAMERA,
                Map.of("PERCLOS", 0.85, "yawnFreq", 4.0));

        // 模拟分心读数 → 产生 DISTRACTION 告警
        sendReading(sessionHandle, tripId, SensorReading.SensorType.DMS_CAMERA,
                Map.of("gazeDeviationCumulative", 12.0, "handsOffWheel", 1.0));

        // 模拟麦克风读数 → 产生 ROAD_RAGE 告警
        sendReading(sessionHandle, tripId, SensorReading.SensorType.MICROPHONE,
                Map.of("acousticPressureDB", 90.0, "abusiveKeywordCount", 3.0));
    }

    private void sendReading(String sessionHandle, TripId tripId,
                             SensorReading.SensorType type, Map<String, Double> values) {
        SensorReading reading = new SensorReading(type, Instant.now(), tripId, values);
        riskService.processSensorReading(sessionHandle, reading);
    }
}
