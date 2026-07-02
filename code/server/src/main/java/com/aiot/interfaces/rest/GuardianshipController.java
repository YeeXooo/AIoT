package com.aiot.interfaces.rest;

import com.aiot.application.GuardianshipApplicationService;
import com.aiot.infra.persistence.GuardianshipEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/guardianship")
public class GuardianshipController {

    private final GuardianshipApplicationService service;

    public GuardianshipController(GuardianshipApplicationService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public List<GuardianshipEntity> list(@RequestParam(required = false) String driverId,
                                          @RequestParam(required = false) String accountId) {
        if (driverId != null) { return service.findByDriver(driverId); }
        if (accountId != null) { return service.findAll(); }
        return service.findAll();
    }

    @PostMapping
    public GuardianshipEntity create(@RequestBody GuardianshipEntity entity) {
        return service.create(entity);
    }

    @DeleteMapping("/{driverId}/{accountId}")
    public ResponseEntity<Void> revoke(@PathVariable String driverId,
                                        @PathVariable String accountId) {
        service.revoke(driverId, accountId);
        return ResponseEntity.noContent().build();
    }
}
