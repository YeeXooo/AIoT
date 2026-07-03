package com.aiot.infra.repository;

import com.aiot.domain.model.Driver;
import com.aiot.domain.model.DriverComprehensiveScore;
import com.aiot.domain.shared.DriverId;
import com.aiot.infra.persistence.DriverJpaEntity;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DriverRepositoryBridge 仓储桥接")
class DriverRepositoryBridgeTest {

    @Mock
    private DriverJpaRepository jpaRepository;

    private DriverRepositoryBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new DriverRepositoryBridge(jpaRepository);
    }

    @Test
    @DisplayName("save 将领域对象正确映射为 JPA 实体并持久化")
    void saveMapsDomainToJpaAndPersists() {
        Driver driver = Driver.create("张三", "13800138000");
        driver.updateComprehensiveScore(DriverComprehensiveScore.of(85));

        bridge.save(driver);

        ArgumentCaptor<DriverJpaEntity> captor = ArgumentCaptor.forClass(DriverJpaEntity.class);
        verify(jpaRepository).save(captor.capture());

        DriverJpaEntity entity = captor.getValue();
        assertEquals(driver.driverId().id(), entity.getDriverId());
        assertEquals("张三", entity.getName());
        assertEquals("13800138000", entity.getPhone());
        assertEquals(85, entity.getComprehensiveScore());
        assertNull(entity.getVersion());
    }

    @Test
    @DisplayName("findById 将 JPA 实体正确还原为领域对象")
    void findByIdReconstitutesDomainObject() {
        DriverJpaEntity entity = new DriverJpaEntity();
        entity.setDriverId("d-001");
        entity.setName("李四");
        entity.setPhone("13900139000");
        entity.setComprehensiveScore(92);

        when(jpaRepository.findById("d-001")).thenReturn(Optional.of(entity));

        Optional<Driver> result = bridge.findById("d-001");

        assertTrue(result.isPresent());
        Driver d = result.get();
        assertEquals("d-001", d.driverId().id());
        assertEquals("李四", d.name());
        assertEquals("13900139000", d.phone());
        assertEquals(92, d.comprehensiveScore().get().getValue());
    }

    @Test
    @DisplayName("findById 对不存在的 ID 返回 Optional.empty")
    void findByIdReturnsEmptyForMissingId() {
        when(jpaRepository.findById("missing")).thenReturn(Optional.empty());

        Optional<Driver> result = bridge.findById("missing");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findAll 返回所有持久化的领域对象")
    void findAllReturnsAll() {
        DriverJpaEntity e1 = createEntity("d-1", "王五", "13700137000", 70);
        DriverJpaEntity e2 = createEntity("d-2", "赵六", "13600136000", 88);

        when(jpaRepository.findAll()).thenReturn(List.of(e1, e2));

        List<Driver> result = bridge.findAll();

        assertEquals(2, result.size());
        assertEquals("王五", result.get(0).name());
        assertEquals("赵六", result.get(1).name());
    }

    @Test
    @DisplayName("findByNameLike 正确按名称模糊查询")
    void findByNameLikeDelegatesToJpa() {
        DriverJpaEntity entity = createEntity("d-3", "张三丰", "13500135000", 65);
        when(jpaRepository.findByNameLike("张")).thenReturn(List.of(entity));

        List<Driver> result = bridge.findByNameLike("张");

        assertEquals(1, result.size());
        assertEquals("张三丰", result.get(0).name());
        verify(jpaRepository).findByNameLike("张");
    }

    @Test
    @DisplayName("delete 正确委托 JPA 删除")
    void deleteDelegatesToJpa() {
        bridge.delete("d-to-delete");

        verify(jpaRepository).deleteById("d-to-delete");
    }

    @Test
    @DisplayName("save 正确处理 comprehensiveScore 为 null 的情况")
    void saveHandlesNullComprehensiveScore() {
        Driver driver = Driver.create("无评分", "13000130000");

        bridge.save(driver);

        ArgumentCaptor<DriverJpaEntity> captor = ArgumentCaptor.forClass(DriverJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertNull(captor.getValue().getComprehensiveScore());
    }

    private static DriverJpaEntity createEntity(String id, String name, String phone, Integer score) {
        DriverJpaEntity e = new DriverJpaEntity();
        e.setDriverId(id);
        e.setName(name);
        e.setPhone(phone);
        e.setComprehensiveScore(score);
        return e;
    }
}
