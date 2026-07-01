package com.aiot.domain.repository;

import com.aiot.domain.model.Driver;

import java.util.List;
import java.util.Optional;

public interface DriverRepository {
    void save(Driver driver);
    Optional<Driver> findById(String id);
    List<Driver> findByNameLike(String keyword);
    List<Driver> findAll();
    void delete(String id);
}
