package com.aiot.domain.fleet;

import com.aiot.domain.event.DriverScoreUpdatedEvent;
import com.aiot.domain.event.DomainEventPublisher;
import com.aiot.domain.model.DriverComprehensiveScore;
import com.aiot.domain.repository.DriverRepository;
import org.springframework.stereotype.Service;

@Service
public class DriverScoreUpdateService {

    private final DriverRepository driverRepository;
    private final DomainEventPublisher eventPublisher;

    public DriverScoreUpdateService(DriverRepository driverRepository,
                                     DomainEventPublisher eventPublisher) {
        this.driverRepository = driverRepository;
        this.eventPublisher = eventPublisher;
    }

    public void registerListener() {
        eventPublisher.registerAsyncHandler("DriverScoreUpdatedEvent", event -> {
            if (event instanceof DriverScoreUpdatedEvent scoreEvent) {
                onDriverScoreUpdated(scoreEvent);
            }
        });
    }

    private void onDriverScoreUpdated(DriverScoreUpdatedEvent event) {
        driverRepository.findById(event.driverId().id()).ifPresent(driver -> {
            DriverComprehensiveScore newScore = DriverComprehensiveScore.of(event.newScore());
            driver.updateComprehensiveScore(newScore);
            driverRepository.save(driver);
        });
    }
}
