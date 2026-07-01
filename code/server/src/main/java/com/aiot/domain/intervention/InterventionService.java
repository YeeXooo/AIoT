package com.aiot.domain.intervention;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.port.NotificationPort.NotificationPayload;
import com.aiot.domain.port.NotificationPort.NotificationType;
import com.aiot.domain.port.NotificationPort.NotificationPriority;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import com.aiot.domain.shared.VehicleId;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InterventionService {

    private final TripRepository tripRepo;
    private final VehicleRepository vehicleRepo;
    private final NotificationPort notificationPort;

    public InterventionService(TripRepository tripRepo, VehicleRepository vehicleRepo,
                                NotificationPort notificationPort) {
        this.tripRepo = tripRepo;
        this.vehicleRepo = vehicleRepo;
        this.notificationPort = notificationPort;
    }

    public Result<Void, AppError> intervene(TripId tripId, String interventionLevel) {
        Optional<Trip> tripOpt = tripRepo.findById(tripId);
        if (tripOpt.isEmpty()) return Result.err(AppError.notFound("Trip not found: " + tripId));

        // 第一期打桩：下发干预指令
        NotificationPayload payload = new NotificationPayload(
            NotificationType.ALERT, "Intervention " + interventionLevel,
            "Trip " + tripId.id() + " requires intervention", NotificationPriority.HIGH);

        try {
            notificationPort.pushNotification(new AccountId("default"), payload);
        } catch (Exception e) {
            return Result.err(AppError.internal("Notification failed: " + e.getMessage()));
        }

        return Result.ok(null);
    }
}
