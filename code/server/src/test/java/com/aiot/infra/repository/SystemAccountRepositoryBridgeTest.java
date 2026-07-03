package com.aiot.infra.repository;

import com.aiot.domain.model.SystemAccount;
import com.aiot.domain.model.AccountRole;
import com.aiot.domain.shared.AccountId;
import com.aiot.infra.persistence.SystemAccountJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemAccountRepositoryBridge 仓储桥接")
class SystemAccountRepositoryBridgeTest {

    @Mock
    private SystemAccountJpaRepository jpaRepository;

    private SystemAccountRepositoryBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new SystemAccountRepositoryBridge(jpaRepository);
    }

    @Test
    @DisplayName("save 将 SystemAccount 正确映射为 JPA 实体")
    void saveMapsToJpaEntity() {
        SystemAccount account = SystemAccount.create("13800138000", AccountRole.FAMILY);

        bridge.save(account);

        ArgumentCaptor<SystemAccountJpaEntity> captor = ArgumentCaptor.forClass(SystemAccountJpaEntity.class);
        verify(jpaRepository).save(captor.capture());

        SystemAccountJpaEntity entity = captor.getValue();
        assertEquals(account.accountId().id(), entity.getAccountId());
        assertEquals("13800138000", entity.getPhone());
        assertEquals("FAMILY", entity.getRole());
    }

    @Test
    @DisplayName("findById 正确还原领域对象（含角色枚举）")
    void findByIdReconstitutesWithRole() {
        SystemAccountJpaEntity entity = new SystemAccountJpaEntity();
        entity.setAccountId("acc-001");
        entity.setPhone("13900139000");
        entity.setRole("MANAGER");

        when(jpaRepository.findById("acc-001")).thenReturn(Optional.of(entity));

        Optional<SystemAccount> result = bridge.findById("acc-001");

        assertTrue(result.isPresent());
        assertEquals("acc-001", result.get().accountId().id());
        assertEquals(AccountRole.MANAGER, result.get().role());
    }

    @Test
    @DisplayName("findByPhone 正确处理存在/不存在两种情况")
    void findByPhoneHandlesBothCases() {
        when(jpaRepository.findByPhone("13800138000")).thenReturn(Optional.empty());

        Optional<SystemAccount> missing = bridge.findByPhone("13800138000");
        assertTrue(missing.isEmpty());

        SystemAccountJpaEntity entity = new SystemAccountJpaEntity();
        entity.setAccountId("acc-002");
        entity.setPhone("13700137000");
        entity.setRole("FAMILY");
        when(jpaRepository.findByPhone("13700137000")).thenReturn(Optional.of(entity));

        Optional<SystemAccount> found = bridge.findByPhone("13700137000");
        assertTrue(found.isPresent());
        assertEquals("acc-002", found.get().accountId().id());
    }

    @Test
    @DisplayName("findAll 返回所有账户")
    void findAllReturnsAll() {
        SystemAccountJpaEntity e1 = new SystemAccountJpaEntity();
        e1.setAccountId("a1"); e1.setPhone("111"); e1.setRole("FAMILY");
        SystemAccountJpaEntity e2 = new SystemAccountJpaEntity();
        e2.setAccountId("a2"); e2.setPhone("222"); e2.setRole("MANAGER");
        when(jpaRepository.findAll()).thenReturn(List.of(e1, e2));

        List<SystemAccount> result = bridge.findAll();

        assertEquals(2, result.size());
        assertEquals(AccountRole.FAMILY, result.get(0).role());
        assertEquals(AccountRole.MANAGER, result.get(1).role());
    }

    @Test
    @DisplayName("delete 委托 JPA 删除")
    void deleteDelegates() {
        bridge.delete("to-delete");
        verify(jpaRepository).deleteById("to-delete");
    }
}
