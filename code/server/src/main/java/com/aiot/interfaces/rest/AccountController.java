package com.aiot.interfaces.rest;

import com.aiot.domain.model.SystemAccount;
import com.aiot.domain.repository.SystemAccountRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final SystemAccountRepository accountRepository;

    public AccountController(SystemAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping("/list")
    public List<SystemAccount> list() {
        return accountRepository.findAll();
    }

    @GetMapping("/{phone}")
    public SystemAccount findByPhone(@PathVariable String phone) {
        return accountRepository.findByPhone(phone).orElse(null);
    }
}
