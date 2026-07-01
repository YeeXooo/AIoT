package com.aiot.domain.emergency;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.port.NotificationPort;
import com.aiot.domain.port.NotificationPort.NotificationPayload;
import com.aiot.domain.port.NotificationPort.NotificationType;
import com.aiot.domain.port.NotificationPort.NotificationPriority;
import com.aiot.domain.port.RescueReportPort;
import com.aiot.domain.shared.AccountId;
import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.repository.DriverRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EmergencyRescueService {

    private final TripRepository tripRepo;
    private final DriverRepository driverRepo;
    private final RescueReportPort rescueReportPort;
    private final NotificationPort notificationPort;

    public EmergencyRescueService(TripRepository tripRepo, DriverRepository driverRepo,
                                   RescueReportPort rescueReportPort,
                                   NotificationPort notificationPort) {
        this.tripRepo = tripRepo;
        this.driverRepo = driverRepo;
        this.rescueReportPort = rescueReportPort;
        this.notificationPort = notificationPort;
    }

    public Result<Void, AppError> initiateRescue(TripId tripId) {
        Optional<Trip> tripOpt = tripRepo.findById(tripId);
        if (tripOpt.isEmpty()) return Result.err(AppError.notFound("Trip not found"));

        NotificationPayload payload = new NotificationPayload(
            NotificationType.RESCUE_REPORT, "SOS Rescue",
            "Emergency rescue initiated for trip " + tripId.id(),
            NotificationPriority.URGENT);

        try {
            notificationPort.pushNotification(new AccountId("120-emergency"), payload);
        } catch (Exception e) {
            return Result.err(AppError.internal("Rescue notification failed: " + e.getMessage()));
        }

        return Result.ok(null);
    }
}
