package com.aiot.interfaces.rest;

import com.aiot.application.DriverApplicationService;
import com.aiot.domain.model.Driver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/driver")
public class DriverController {

    private final DriverApplicationService driverService;

    public DriverController(DriverApplicationService driverService) {
        this.driverService = driverService;
    }

    @GetMapping("/list")
    public List<Driver> list(@RequestParam(required = false) String name) {
        return driverService.list(name);
    }

    @PostMapping
    public Driver add(@RequestBody Driver driver) {
        if (driver.driverId() == null || driver.driverId().id() == null || driver.driverId().id().isEmpty()) {
            driver = Driver.create(driver.name(), driver.phone());
        }
        driverService.add(driver);
        return driver;
    }

    @PutMapping
    public Driver update(@RequestBody Driver driver) {
        driverService.update(driver);
        return driver;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        driverService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
