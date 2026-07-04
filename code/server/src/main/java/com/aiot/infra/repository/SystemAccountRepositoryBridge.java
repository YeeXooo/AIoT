package com.aiot.infra.repository;

import com.aiot.domain.model.AccountRole;
import com.aiot.domain.model.SystemAccount;
import com.aiot.domain.repository.SystemAccountRepository;
import com.aiot.domain.shared.AccountId;
import com.aiot.infra.persistence.SystemAccountJpaEntity;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@org.springframework.stereotype.Repository
public class SystemAccountRepositoryBridge implements SystemAccountRepository {

    private final SystemAccountJpaRepository jpaRepository;

    public SystemAccountRepositoryBridge(SystemAccountJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(SystemAccount account) {
        SystemAccountJpaEntity entity = jpaRepository.findById(account.accountId().id())
                .orElse(new SystemAccountJpaEntity());
        entity.setAccountId(account.accountId().id());
        entity.setPhone(account.phone());
        entity.setRole(account.role().name());
        jpaRepository.save(entity);
    }

    @Override
    public Optional<SystemAccount> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<SystemAccount> findByPhone(String phone) {
        return jpaRepository.findByPhone(phone).map(this::toDomain);
    }

    @Override
    public List<SystemAccount> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String id) {
        jpaRepository.deleteById(id);
    }

    private SystemAccount toDomain(SystemAccountJpaEntity entity) {
        return SystemAccount.reconstitute(
                new AccountId(entity.getAccountId()),
                entity.getPhone(),
                AccountRole.valueOf(entity.getRole()),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
