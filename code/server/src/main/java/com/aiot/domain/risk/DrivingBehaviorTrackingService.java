package com.aiot.domain.risk;

public interface DrivingBehaviorTrackingService {

    void onHardBrakingDetected(String tripId, long timestamp, double magnitude);

    void onHardAccelerationDetected(String tripId, long timestamp, double magnitude);
}
