package com.aiot.domain.risk;

import com.aiot.domain.shared.Result;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.event.RiskDeterminedEvent;
import com.aiot.domain.event.AlertType;
import com.aiot.domain.event.RiskLevel;
import com.aiot.domain.port.PhysiologicalDataBuffer;
import com.aiot.domain.model.PhysiologicalSnapshot;
import com.aiot.domain.model.Driver;
import com.aiot.domain.repository.DriverRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class FatigueDeterminationService {

    private final DriverRepository driverRepo;
    private final PhysiologicalDataBuffer physioBuffer;
    private final DomainEventPublisher eventPublisher;

    public FatigueDeterminationService(DriverRepository driverRepo,
                                        PhysiologicalDataBuffer physioBuffer,
                                        DomainEventPublisher eventPublisher) {
        this.driverRepo = driverRepo;
        this.physioBuffer = physioBuffer;
        this.eventPublisher = eventPublisher;
    }

    public Result<RiskDeterminedEvent, AppError> determineFatigue(TripId tripId,
            DriverId driverId, PhysiologicalSnapshot snapshot) {
        // 第一期打桩：疲劳指数 > 0.7 触发 WARNING
        if (snapshot.fatigueIndex() != null && snapshot.fatigueIndex() > 0.7) {
            RiskDeterminedEvent event = new RiskDeterminedEvent(
                tripId, RiskLevel.WARNING, AlertType.FATIGUE,
                Instant.now(), "Fatigue index exceeded 0.7");
            eventPublisher.publish(event);
            return Result.ok(event);
        }
        return Result.ok(null);
    }
}
