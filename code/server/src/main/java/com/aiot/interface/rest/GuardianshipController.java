package com.aiot.interfaces.rest;

import com.aiot.infra.persistence.GuardianshipEntity;
import com.aiot.infra.repository.GuardianshipJpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/guardianship")
public class GuardianshipController {

    private final GuardianshipJpaRepository guardianshipRepo;

    public GuardianshipController(GuardianshipJpaRepository guardianshipRepo) {
        this.guardianshipRepo = guardianshipRepo;
    }

    @GetMapping("/list")
    public List<GuardianshipEntity> list(@RequestParam(required = false) String driverId,
                                          @RequestParam(required = false) String accountId) {
        if (driverId != null) return guardianshipRepo.findActiveByDriver(driverId);
        if (accountId != null) return guardianshipRepo.findByAccountId(accountId);
        return guardianshipRepo.findAll();
    }

    @PostMapping
    public GuardianshipEntity create(@RequestBody GuardianshipEntity entity) {
        entity.setGrantedAt(LocalDateTime.now());
        return guardianshipRepo.save(entity);
    }

    @DeleteMapping("/{driverId}/{accountId}")
    public ResponseEntity<Void> revoke(@PathVariable String driverId,
                                        @PathVariable String accountId) {
        guardianshipRepo.findActive(driverId, accountId).ifPresent(g -> {
            g.setRevokedAt(LocalDateTime.now());
            guardianshipRepo.save(g);
        });
        return ResponseEntity.noContent().build();
    }
}
