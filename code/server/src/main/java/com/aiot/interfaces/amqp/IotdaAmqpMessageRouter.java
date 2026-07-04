package com.aiot.interfaces.amqp;

import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.BehaviorCountersUpdated;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.PhysiologicalDataUpdated;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.event.SafetyAlertDetectedEvent;
import com.aiot.domain.event.SensorDataCollected;
import com.aiot.domain.event.VehicleStateUpdated;
import com.aiot.domain.model.DrivingBehaviorCounters;
import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.SensorReading;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.model.VehicleStateSnapshot;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import com.aiot.domain.shared.TripId;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IotdaAmqpMessageRouter {

    private static final Logger log = LoggerFactory.getLogger(IotdaAmqpMessageRouter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final double PERCLOS_FATIGUE_THRESHOLD = 0.3;
    private static final int YAWN_DISTRACTION_THRESHOLD = 5;
    private static final int BATTERY_LOW_MV = 3300;

    private final DomainEventPublisher eventPublisher;
    private final VehicleRepository vehicleRepository;
    private final TripRepository tripRepository;
    private final ConcurrentMap<String, Integer> previousBrakeCounts;

    public IotdaAmqpMessageRouter(DomainEventPublisher eventPublisher,
                                   VehicleRepository vehicleRepository,
                                   TripRepository tripRepository) {
        this.eventPublisher = eventPublisher;
        this.vehicleRepository = vehicleRepository;
        this.tripRepository = tripRepository;
        this.previousBrakeCounts = new ConcurrentHashMap<>();
    }

    public void route(String jsonBody) {
        try {
            IotdaAmqpEnvelope envelope = MAPPER.readValue(jsonBody, IotdaAmqpEnvelope.class);
            process(envelope);
        } catch (Exception e) {
            log.error("AMQP 消息路由失败: {}", e.getMessage(), e);
        }
    }

    private TripId resolveTripId(String nodeId, String deviceId) {
        Optional<Vehicle> vehicle = vehicleRepository.findByTerminalSn(nodeId);
        if (vehicle.isPresent()) {
            List<Trip> activeTrips = tripRepository.findActiveTrips();
            Optional<Trip> matchingTrip = activeTrips.stream()
                    .filter(t -> t.vehicleId().id().equals(vehicle.get().vehicleId().id()))
                    .findFirst();
            if (matchingTrip.isPresent()) {
                log.info("设备映射成功: nodeId={} → vehicleId={} → tripId={}",
                        nodeId, vehicle.get().vehicleId().id(), matchingTrip.get().tripId().id());
                return matchingTrip.get().tripId();
            }
            log.warn("设备已匹配车辆但无活跃行程: nodeId={}, vehicleId={}",
                    nodeId, vehicle.get().vehicleId().id());
        }
        log.debug("设备未匹配到车辆: nodeId={}, 使用 deviceId 作为 fallback", nodeId);
        return new TripId(deviceId);
    }

    private void process(IotdaAmqpEnvelope envelope) {
        IotdaHeader header = envelope.notifyData().header();
        String deviceId = header.deviceId();
        String nodeId = header.nodeId();

        TripId tripId = resolveTripId(nodeId, deviceId);
        Instant timestamp = Instant.now();

        IotdaBody body = envelope.notifyData().body();
        if (body.services() == null || body.services().isEmpty()) {
            log.warn("AMQP 消息无 service 数据: deviceId={}", deviceId);
            return;
        }

        IotdaServiceData service = body.services().get(0);
        if (!"VehicleSafety".equals(service.serviceId())) {
            log.debug("忽略非 VehicleSafety service: {}", service.serviceId());
            return;
        }

        Map<String, Object> props = service.properties();
        if (props == null || props.isEmpty()) {
            log.warn("VehicleSafety service 无属性数据: deviceId={}", deviceId);
            return;
        }

        log.info("AMQP 路由处理: deviceId={}, properties count={}", deviceId, props.size());

        List<SensorReading> readings = extractSensorReadings(props, timestamp, tripId);
        if (!readings.isEmpty()) {
            eventPublisher.publish(new SensorDataCollected(tripId, deviceId, readings));
            log.debug("发布 SensorDataCollected: {} readings", readings.size());
        }

        extractPhysiological(props, timestamp).ifPresent(snapshot -> {
            eventPublisher.publish(new PhysiologicalDataUpdated(tripId, deviceId, snapshot));
            log.debug("发布 PhysiologicalDataUpdated: hr={}, spo2={}", snapshot.heartRate(), snapshot.bloodOxygen());
        });

        extractVehicleState(props, timestamp).ifPresent(state -> {
            eventPublisher.publish(new VehicleStateUpdated(tripId, deviceId, state));
            log.debug("发布 VehicleStateUpdated: lat={}, lon={}", state.latitude(), state.longitude());
        });

        extractBehaviorCounters(props).ifPresent(counters ->
            eventPublisher.publish(new BehaviorCountersUpdated(tripId, deviceId, counters))
        );

        extractSafetyAlerts(props, timestamp, tripId, deviceId).forEach(eventPublisher::publish);

        extractPcLevelAlert(props, timestamp, tripId, deviceId).ifPresent(eventPublisher::publish);

        log.info("AMQP 消息处理完成: deviceId={}", deviceId);
    }

    private List<SensorReading> extractSensorReadings(Map<String, Object> props, Instant ts, TripId tripId) {
        List<SensorReading> readings = new ArrayList<>();

        Map<String, Double> dmsValues = new HashMap<>();
        addValue(props, dmsValues, "perclos", "PERCLOS");
        addValue(props, dmsValues, "yawn", "YAWN");
        addValue(props, dmsValues, "phone", "PHONE");
        if (!dmsValues.isEmpty()) {
            readings.add(new SensorReading(SensorReading.SensorType.DMS_CAMERA, ts, tripId, dmsValues));
        }

        Map<String, Double> accelValues = new HashMap<>();
        addValue(props, accelValues, "ax", "AX");
        addValue(props, accelValues, "ay", "AY");
        addValue(props, accelValues, "az", "AZ");
        addValue(props, accelValues, "gx", "GX");
        addValue(props, accelValues, "gy", "GY");
        addValue(props, accelValues, "gz", "GZ");
        if (!accelValues.isEmpty()) {
            readings.add(new SensorReading(SensorReading.SensorType.ACCELEROMETER, ts, tripId, accelValues));
        }

        Map<String, Double> envValues = new HashMap<>();
        addValue(props, envValues, "temp", "TEMP");
        addValue(props, envValues, "humi", "HUMI");
        addValue(props, envValues, "lux", "LUX");
        if (!envValues.isEmpty()) {
            readings.add(new SensorReading(SensorReading.SensorType.ENVIRONMENT, ts, tripId, envValues));
        }

        Map<String, Double> radarValues = new HashMap<>();
        addValue(props, radarValues, "radar_human", "HUMAN");
        addValue(props, radarValues, "radar_range_lo", "RANGE_LO");
        addValue(props, radarValues, "radar_range_hi", "RANGE_HI");
        if (!radarValues.isEmpty()) {
            readings.add(new SensorReading(SensorReading.SensorType.MILLIMETER_WAVE_RADAR, ts, tripId, radarValues));
        }

        return readings;
    }

    private Optional<PhysiologicalSnapshot> extractPhysiological(Map<String, Object> props, Instant ts) {
        Integer hr = toInt(props.get("hr"));
        Double spo2 = toDouble(props.get("spo2"));

        if (hr == null && spo2 == null) {
            return Optional.empty();
        }

        Integer restingHr = toInt(props.get("resting_hr"));
        return Optional.of(new PhysiologicalSnapshot(ts, hr, spo2, null, null, null, null, null, null, restingHr));
    }

    private Optional<VehicleStateSnapshot> extractVehicleState(Map<String, Object> props, Instant ts) {
        Double lat = toDouble(props.get("lat"));
        Double lon = toDouble(props.get("lon"));

        if (lat == null && lon == null) {
            return Optional.empty();
        }

        Integer gpsFix = toInt(props.get("gps_fix"));
        return Optional.of(new VehicleStateSnapshot(ts, null, null, null, null, null, lat, lon, gpsFix));
    }

    private Optional<DrivingBehaviorCounters> extractBehaviorCounters(Map<String, Object> props) {
        int brake = toIntOrZero(props.get("hard_brake"));
        int accel = toIntOrZero(props.get("hard_accel"));
        int sharpTurn = toIntOrZero(props.get("sharp_turn"));
        int fatigue = perclosToFatigueCount(props);
        int distraction = toIntOrZero(props.get("yawn"));

        return Optional.of(DrivingBehaviorCounters.of(brake, accel, fatigue, distraction, 0, sharpTurn));
    }

    private List<SafetyAlertDetectedEvent> extractSafetyAlerts(
            Map<String, Object> props, Instant ts, TripId tripId, String deviceId) {
        List<SafetyAlertDetectedEvent> alerts = new ArrayList<>();
        Double lat = toDouble(props.get("lat"));
        Double lon = toDouble(props.get("lon"));

        int risk = toIntOrZero(props.get("risk"));
        if (risk >= 1 && risk <= 3) {
            RiskLevel level = switch (risk) {
                case 1 -> RiskLevel.L1_HINT;
                case 2 -> RiskLevel.L2_WARNING;
                default -> RiskLevel.L3_CRITICAL;
            };
            alerts.add(new SafetyAlertDetectedEvent(tripId, deviceId, AlertType.SYSTEM_RISK, level,
                    lat != null ? lat : 0.0, lon != null ? lon : 0.0, ts,
                    "IoTDA system risk level " + risk));
        }

        double perclos = toDoubleOrZero(props.get("perclos"));
        if (perclos > PERCLOS_FATIGUE_THRESHOLD) {
            alerts.add(new SafetyAlertDetectedEvent(tripId, deviceId, AlertType.FATIGUE, RiskLevel.L2_WARNING,
                    lat != null ? lat : 0.0, lon != null ? lon : 0.0, ts,
                    "PERCLOS exceeds threshold: " + perclos));
        }

        int yawn = toIntOrZero(props.get("yawn"));
        if (yawn > YAWN_DISTRACTION_THRESHOLD) {
            alerts.add(new SafetyAlertDetectedEvent(tripId, deviceId, AlertType.DISTRACTION, RiskLevel.L2_WARNING,
                    lat != null ? lat : 0.0, lon != null ? lon : 0.0, ts,
                    "Yawn count exceeds threshold: " + yawn));
        }

        int brake = toIntOrZero(props.get("hard_brake"));
        Integer prevBrake = previousBrakeCounts.get(deviceId);
        if (prevBrake != null && brake > prevBrake) {
            alerts.add(new SafetyAlertDetectedEvent(tripId, deviceId, AlertType.SUDDEN_BRAKING, RiskLevel.L2_WARNING,
                    lat != null ? lat : 0.0, lon != null ? lon : 0.0, ts,
                    "Hard brake detected: current=" + brake + ", previous=" + prevBrake));
        }
        previousBrakeCounts.put(deviceId, brake);

        int batteryMv = toIntOrZero(props.get("battery_mv"));
        if (batteryMv > 0 && batteryMv < BATTERY_LOW_MV) {
            alerts.add(new SafetyAlertDetectedEvent(tripId, deviceId, AlertType.LOW_BATTERY, RiskLevel.L2_WARNING,
                    lat != null ? lat : 0.0, lon != null ? lon : 0.0, ts,
                    "Battery low voltage: " + batteryMv + "mV"));
        }

        return alerts;
    }

    private Optional<SafetyAlertDetectedEvent> extractPcLevelAlert(
            Map<String, Object> props, Instant ts, TripId tripId, String deviceId) {
        int pcLvl = toIntOrZero(props.get("pc_lvl"));
        if (pcLvl <= 0) {
            return Optional.empty();
        }

        RiskLevel level = pcLvl == 2 ? RiskLevel.L2_WARNING : RiskLevel.L1_HINT;
        Double lat = toDouble(props.get("lat"));
        Double lon = toDouble(props.get("lon"));
        return Optional.of(new SafetyAlertDetectedEvent(tripId, deviceId, AlertType.FATIGUE, level,
                lat != null ? lat : 0.0, lon != null ? lon : 0.0, ts,
                "PC pre-judged fatigue level " + pcLvl));
    }

    private int perclosToFatigueCount(Map<String, Object> props) {
        double perclos = toDoubleOrZero(props.get("perclos"));
        return perclos > PERCLOS_FATIGUE_THRESHOLD ? (int) (perclos * 10) : 0;
    }

    private static void addValue(Map<String, Object> props, Map<String, Double> target, String propKey, String valueKey) {
        Double v = toDouble(props.get(propKey));
        if (v != null) {
            target.put(valueKey, v);
        }
    }

    private static Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int toIntOrZero(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double toDoubleOrZero(Object value) {
        Double v = toDouble(value);
        return v != null ? v : 0.0;
    }

    private static Integer toInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
