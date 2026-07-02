package com.aiot.interfaces.rest;

import com.aiot.infra.persistence.SystemAccountJpaEntity;
import com.aiot.infra.repository.SystemAccountJpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final SystemAccountJpaRepository accountRepository;

    public AccountController(SystemAccountJpaRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/list")
    public List<SystemAccountJpaEntity> list() {
        return accountRepository.findAll();
    }

    @GetMapping("/{phone}")
    public SystemAccountJpaEntity findByPhone(@PathVariable String phone) {
        return accountRepository.findByPhone(phone).orElse(null);
    }
}
