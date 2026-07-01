package com.aiot.application;

import com.aiot.domain.model.Driver;
import com.aiot.domain.repository.DriverRepository;

import java.util.List;

public class DriverApplicationService {

    private final DriverRepository driverRepository;

    public DriverApplicationService(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    public List<Driver> list(String name) {
        if (name != null && !name.isEmpty()) {
            return driverRepository.findByNameLike(name);
        }
        return driverRepository.findAll();
    }

    public void add(Driver driver) {
        driverRepository.save(driver);
    }

    public void update(Driver driver) {
        driverRepository.save(driver);
    }

    public void delete(String id) {
        driverRepository.delete(id);
    }
}
