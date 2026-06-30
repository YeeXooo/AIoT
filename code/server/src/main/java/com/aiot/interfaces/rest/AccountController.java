package com.aiot.interfaces.rest;

import com.aiot.infra.persistence.SystemAccountJpaEntity;
import com.aiot.infra.repository.SystemAccountJpaRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final SystemAccountJpaRepository accountRepo;

    public AccountController(SystemAccountJpaRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    @GetMapping("/list")
    public List<SystemAccountJpaEntity> list() {
        return accountRepo.findAll();
    }

    @GetMapping("/{phone}")
    public SystemAccountJpaEntity findByPhone(@PathVariable String phone) {
        return accountRepo.findByPhone(phone).orElse(null);
    }
}
