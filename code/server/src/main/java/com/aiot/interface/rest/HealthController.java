package com.aiot.interfaces.rest;

import com.aiot.infra.persistence.DriverHealthProfileEntity;
import com.aiot.infra.repository.DriverHealthProfileJpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final DriverHealthProfileJpaRepository profileRepo;

    public HealthController(DriverHealthProfileJpaRepository profileRepo) {
        this.profileRepo = profileRepo;
    }

    @GetMapping("/{driverId}")
    public ResponseEntity<DriverHealthProfileEntity> get(@PathVariable String driverId) {
        return profileRepo.findById(driverId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{driverId}")
    public DriverHealthProfileEntity update(@PathVariable String driverId,
                                             @RequestBody DriverHealthProfileEntity profile) {
        profile.setDriverId(driverId);
        return profileRepo.save(profile);
    }
}
