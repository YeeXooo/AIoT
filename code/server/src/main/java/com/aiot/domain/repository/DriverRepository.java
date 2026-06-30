package com.aiot.domain.repository;

import com.aiot.domain.shared.AggregateId;

import java.util.List;
import java.util.Optional;

public interface DriverRepository {
    DriverRepository save(Driver driver);
    Optional<Driver> findById(AggregateId id);
    List<Driver> findByNameLike(String keyword);
    List<Driver> findAll();
    void delete(AggregateId id);

    interface Driver {
        AggregateId getId();
        String getName();
        String getPhone();
        Integer getComprehensiveScore();
    }
}
