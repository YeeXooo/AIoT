package com.aiot.application;

import com.aiot.infra.persistence.GuardianshipEntity;
import com.aiot.infra.repository.GuardianshipJpaRepository;
import org.junit.jupiter.api.BeforeEach;
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
class GuardianshipApplicationServiceTest {

    @Mock
    private GuardianshipJpaRepository repo;

    private GuardianshipApplicationService service;

    @BeforeEach
    void setUp() {
        service = new GuardianshipApplicationService(repo);
    }

    @Test
    void findByDriverShouldReturnActiveGuardianships() {
        var entity1 = new GuardianshipEntity();
        entity1.setDriverId("driver-1");
        entity1.setAccountId("account-1");
        var entity2 = new GuardianshipEntity();
        entity2.setDriverId("driver-1");
        entity2.setAccountId("account-2");
        when(repo.findActiveByDriver("driver-1")).thenReturn(List.of(entity1, entity2));

        var result = service.findByDriver("driver-1");

        assertEquals(2, result.size());
        assertTrue(result.contains(entity1));
        assertTrue(result.contains(entity2));
        verify(repo, times(1)).findActiveByDriver("driver-1");
    }

    @Test
    void findByDriverShouldReturnEmptyListWhenNoActiveGuardianships() {
        when(repo.findActiveByDriver("unknown")).thenReturn(List.of());

        var result = service.findByDriver("unknown");

        assertTrue(result.isEmpty());
        verify(repo, times(1)).findActiveByDriver("unknown");
    }

    @Test
    void findByDriverShouldReturnEmptyForNullDriverId() {
        when(repo.findActiveByDriver(null)).thenReturn(List.of());

        var result = service.findByDriver(null);

        assertTrue(result.isEmpty());
        verify(repo, times(1)).findActiveByDriver(null);
    }

    @Test
    void findAllShouldReturnAllGuardianships() {
        var entity1 = new GuardianshipEntity();
        entity1.setDriverId("driver-1");
        entity1.setAccountId("account-1");
        var entity2 = new GuardianshipEntity();
        entity2.setDriverId("driver-2");
        entity2.setAccountId("account-3");
        when(repo.findAll()).thenReturn(List.of(entity1, entity2));

        var result = service.findAll();

        assertEquals(2, result.size());
        verify(repo, times(1)).findAll();
    }

    @Test
    void findAllShouldReturnEmptyList() {
        when(repo.findAll()).thenReturn(List.of());

        var result = service.findAll();

        assertTrue(result.isEmpty());
        verify(repo, times(1)).findAll();
    }

    @Test
    void createShouldSetGrantedAtAndSave() {
        var entity = new GuardianshipEntity();
        entity.setDriverId("driver-1");
        entity.setAccountId("account-1");
        entity.setPermissions("READ");
        entity.setGrantReason("Family");
        when(repo.save(any(GuardianshipEntity.class))).thenReturn(entity);

        var result = service.create(entity);

        assertNotNull(result);
        assertEquals("driver-1", result.getDriverId());
        assertEquals("account-1", result.getAccountId());

        var captor = ArgumentCaptor.forClass(GuardianshipEntity.class);
        verify(repo, times(1)).save(captor.capture());
        assertNotNull(captor.getValue().getGrantedAt());
    }

    @Test
    void revokeShouldSetRevokedAtWhenActiveGuardianshipFound() {
        var entity = new GuardianshipEntity();
        entity.setDriverId("driver-1");
        entity.setAccountId("account-1");
        when(repo.findActive("driver-1", "account-1")).thenReturn(Optional.of(entity));

        service.revoke("driver-1", "account-1");

        var captor = ArgumentCaptor.forClass(GuardianshipEntity.class);
        verify(repo, times(1)).save(captor.capture());
        assertNotNull(captor.getValue().getRevokedAt());
        verify(repo, times(1)).findActive("driver-1", "account-1");
    }

    @Test
    void revokeShouldDoNothingWhenNoActiveGuardianshipFound() {
        when(repo.findActive("driver-1", "account-1")).thenReturn(Optional.empty());

        service.revoke("driver-1", "account-1");

        verify(repo, times(1)).findActive("driver-1", "account-1");
        verify(repo, never()).save(any(GuardianshipEntity.class));
    }

    @Test
    void revokeShouldDoNothingForNullDriverId() {
        when(repo.findActive(null, "account-1")).thenReturn(Optional.empty());

        service.revoke(null, "account-1");

        verify(repo, never()).save(any(GuardianshipEntity.class));
    }
}
