package com.aiot.domain.shared;

import com.aiot.domain.model.Driver;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.repository.DriverRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PrivacyProtectionService {

    private final DriverRepository driverRepo;

    public PrivacyProtectionService(DriverRepository driverRepo) {
        this.driverRepo = driverRepo;
    }

    public boolean isAuthorizedForSensitiveData(DriverId driverId, String requestScope) {
        Optional<Driver> driver = driverRepo.findById(driverId);
        return driver.isPresent();
    }
}
