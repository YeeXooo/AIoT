package com.aiot.infra.repository;

import com.aiot.domain.model.Trip;
import com.aiot.domain.repository.TripRepository;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.TripId;
import com.aiot.domain.shared.VehicleId;
import com.aiot.infra.persistence.TripJpaEntity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@org.springframework.stereotype.Repository
public class TripRepositoryBridge implements TripRepository {

    private final TripJpaRepository jpaRepository;

    public TripRepositoryBridge(TripJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Trip trip) {
        TripJpaEntity entity = new TripJpaEntity();
        entity.setTripId(trip.tripId().id());
        entity.setDriverId(trip.driverId().id());
        entity.setVehicleId(trip.vehicleId().id());
        entity.setStartedAt(trip.startedAt());
        entity.setEndedAt(trip.endedAt().orElse(null));
        entity.setHardBrakingCount(trip.drivingBehaviorCounters().getSuddenBrakingCount());
        entity.setHardAccelerationCount(trip.drivingBehaviorCounters().getSuddenAccelerationCount());
        entity.setScoreValue(trip.tripScore().map(score -> score.getValue()).orElse(null));
        jpaRepository.save(entity);
    }

    @Override
    public Optional<Trip> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Trip> findByDriverId(String driverId) {
        return jpaRepository.findByDriverId(driverId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Trip> findActiveTrips() {
        return jpaRepository.findActiveTrips().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Trip> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        jpaRepository.deleteById(id);
    }

    private Trip toDomain(TripJpaEntity entity) {
        return Trip.reconstitute(
                new TripId(entity.getTripId()),
                new DriverId(entity.getDriverId()),
                new VehicleId(entity.getVehicleId()),
                entity.getStartedAt(),
                entity.getEndedAt(),
                entity.getHardBrakingCount(),
                entity.getHardAccelerationCount(),
                entity.getScoreValue(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
