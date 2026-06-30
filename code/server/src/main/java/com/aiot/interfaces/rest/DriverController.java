package com.aiot.interfaces.rest;

import com.aiot.infra.persistence.DriverJpaEntity;
import com.aiot.infra.repository.DriverJpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/driver")
public class DriverController {

    private final DriverJpaRepository driverRepo;

    public DriverController(DriverJpaRepository driverRepo) {
        this.driverRepo = driverRepo;
    }

    @GetMapping("/list")
    public List<DriverJpaEntity> list(@RequestParam(required = false) String name) {
        if (name != null && !name.isEmpty()) {
            return driverRepo.findByNameLike(name);
        }
        return driverRepo.findAll();
    }

    @PostMapping
    public DriverJpaEntity add(@RequestBody DriverJpaEntity driver) {
        if (driver.getDriverId() == null || driver.getDriverId().isEmpty()) {
            driver.setDriverId(UUID.randomUUID().toString());
        }
        return driverRepo.save(driver);
    }

    @PutMapping
    public DriverJpaEntity update(@RequestBody DriverJpaEntity driver) {
        return driverRepo.save(driver);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        driverRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
