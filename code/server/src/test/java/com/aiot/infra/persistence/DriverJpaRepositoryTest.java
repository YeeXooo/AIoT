package com.aiot.infra.persistence;

import com.aiot.infra.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@DisplayName("DriverJpaRepository CRUD")
class DriverJpaRepositoryTest {

    @Autowired private DriverJpaRepository repository;
    @Autowired private TestEntityManager em;

    private static DriverJpaEntity driver(String id, String name, String phone) {
        DriverJpaEntity d = new DriverJpaEntity();
        d.setDriverId(id);
        d.setName(name);
        d.setPhone(phone);
        d.setComprehensiveScore(100);
        return d;
    }

    @Nested @DisplayName("基础 CRUD")
    class Crud {
        @Test void saveAndFindById() {
            repository.save(driver("d-001", "张三", "13800000001"));
            DriverJpaEntity found = repository.findById("d-001").orElseThrow();
            assertEquals("张三", found.getName());
            assertEquals("13800000001", found.getPhone());
            assertEquals(100, found.getComprehensiveScore());
            assertNotNull(found.getCreatedAt());
            assertNotNull(found.getUpdatedAt());
            assertNotNull(found.getVersion());
            assertEquals(0, found.getVersion());
        }

        @Test void saveAutoGeneratesTimestamps() {
            DriverJpaEntity saved = repository.save(driver("d-002", "李四", "13800000002"));
            assertNotNull(saved.getCreatedAt());
            assertNotNull(saved.getUpdatedAt());
            assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        }

        @Test void saveChangesIncrementVersion() {
            DriverJpaEntity saved = repository.save(driver("d-v01", "版本测试", "13000000001"));
            em.flush();
            em.clear(); // detach so re-fetch gets a fresh entity
            DriverJpaEntity reloaded = repository.findById("d-v01").orElseThrow();
            reloaded.setComprehensiveScore(85);
            repository.save(reloaded);
            em.flush();
            em.clear();
            DriverJpaEntity updated = repository.findById("d-v01").orElseThrow();
            assertEquals(1, updated.getVersion());
        }

        @Test void findAll() {
            long before = repository.count();
            repository.save(driver("d-a", "A", "111"));
            repository.save(driver("d-b", "B", "222"));
            assertEquals(before + 2, repository.findAll().size());
        }

        @Test void delete() {
            repository.save(driver("d-del", "待删除", "999"));
            repository.deleteById("d-del");
            assertTrue(repository.findById("d-del").isEmpty());
        }

        @Test void saveNullNameThrows() {
            DriverJpaEntity d = new DriverJpaEntity();
            d.setDriverId("d-bad");
            assertThrows(DataIntegrityViolationException.class, () -> {
                repository.saveAndFlush(d);
            });
        }
    }

    @Nested @DisplayName("查询方法")
    class Queries {
        @Test void findByNameLike() {
            repository.save(driver("d-q1", "张小明", "101"));
            repository.save(driver("d-q2", "李小明", "102"));
            repository.save(driver("d-q3", "王大壮", "103"));
            List<DriverJpaEntity> result = repository.findByNameLike("小明");
            assertEquals(2, result.size());
        }

        @Test void findByNameLikeEmpty() {
            List<DriverJpaEntity> result = repository.findByNameLike("不存在");
            assertTrue(result.isEmpty());
        }
    }

    @Nested @DisplayName("乐观锁")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    class OptimisticLock {
        @Test void concurrentUpdateThrowsException() {
            repository.save(driver("d-lock", "并发", "138"));
            // simulate two concurrent reads from separate transactions
            repository.flush();

            DriverJpaEntity e1 = repository.findById("d-lock").orElseThrow();
            DriverJpaEntity e2 = repository.findById("d-lock").orElseThrow();

            e1.setComprehensiveScore(90);
            repository.save(e1);
            repository.flush();

            e2.setComprehensiveScore(80);
            try {
                repository.save(e2);
                repository.flush();
                fail("expected ObjectOptimisticLockingFailureException");
            } catch (Exception ex) {
                assertTrue(ex.getClass().getSimpleName().contains("OptimisticLock"));
            }
        }
    }
}
