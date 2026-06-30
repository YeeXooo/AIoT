package com.aiot.infra.repository;

import com.aiot.infra.persistence.TripJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripJpaRepository extends JpaRepository<TripJpaEntity, String> {

    List<TripJpaEntity> findByDriverId(String driverId);

    @Query("SELECT t FROM TripJpaEntity t WHERE t.endedAt IS NULL")
    List<TripJpaEntity> findActiveTrips();
}
