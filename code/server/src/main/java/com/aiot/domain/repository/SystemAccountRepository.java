package com.aiot.domain.repository;

import com.aiot.domain.model.SystemAccount;

import java.util.List;
import java.util.Optional;

public interface SystemAccountRepository {
    void save(SystemAccount account);
    Optional<SystemAccount> findById(String id);
    Optional<SystemAccount> findByPhone(String phone);
    List<SystemAccount> findAll();
    void delete(String id);
}
