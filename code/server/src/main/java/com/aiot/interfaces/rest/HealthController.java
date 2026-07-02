package com.aiot.interfaces.rest;

import com.aiot.application.HealthApplicationService;
import com.aiot.infra.persistence.DriverHealthProfileEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final HealthApplicationService service;

    public HealthController(HealthApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{driverId}")
    public DriverHealthProfileEntity get(@PathVariable String driverId) {
        return service.get(driverId).orElse(null);
    }

    @PutMapping("/{driverId}")
    public DriverHealthProfileEntity update(@PathVariable String driverId,
                                             @RequestBody DriverHealthProfileEntity profile) {
        profile.setDriverId(driverId);
        return service.save(profile);
    }
}
