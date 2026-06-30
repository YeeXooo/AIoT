package com.aiot.domain.repository;

import com.aiot.domain.shared.AggregateId;

import java.util.List;
import java.util.Optional;

public interface SystemAccountRepository {
    SystemAccountRepository save(SystemAccount account);
    Optional<SystemAccount> findById(AggregateId id);
    Optional<SystemAccount> findByPhone(String phone);
    List<SystemAccount> findAll();
    void delete(AggregateId id);

    interface SystemAccount {
        AggregateId getId();
        String getPhone();
        String getRole();
    }
}
