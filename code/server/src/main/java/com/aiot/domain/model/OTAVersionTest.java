package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OTAVersion 固件版本测试")
class OTAVersionTest {

    @Nested
    @DisplayName("of() 工厂方法")
    class OfMethod {

        @Test
        @DisplayName("正常创建")
        void shouldCreateSuccessfully() {
            OTAVersion version = OTAVersion.of("2.0.0", "ModelX-ModelY", "sha256:abc123");
            assertEquals("2.0.0", version.getVersionNumber());
            assertEquals("ModelX-ModelY", version.getVehicleModelRange());
            assertEquals("sha256:abc123", version.getPackageDigest());
        }

        @Test
        @DisplayName("vehicleModelRange 和 packageDigest 可为 null")
        void shouldAllowNullOptionalFields() {
            OTAVersion version = OTAVersion.of("1.0.0", null, null);
            assertEquals("1.0.0", version.getVersionNumber());
            assertNull(version.getVehicleModelRange());
            assertNull(version.getPackageDigest());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("versionNumber 为空抛出 BusinessException (MODEL_024)")
        void shouldThrowWhenVersionNumberIsBlank(String versionNumber) {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    OTAVersion.of(versionNumber, "ModelX", "digest"));
            assertEquals("MODEL_024", ex.getErrorCode());
            assertEquals("OTA_VERSION_VALIDATE", ex.getErrorScope());
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同属性相等")
        void shouldBeEqual() {
            OTAVersion a = OTAVersion.of("2.0.0", "ModelX", "abc123");
            OTAVersion b = OTAVersion.of("2.0.0", "ModelX", "abc123");
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同版本号不等")
        void shouldNotBeEqualDifferentVersion() {
            OTAVersion a = OTAVersion.of("2.0.0", "ModelX", "abc123");
            OTAVersion b = OTAVersion.of("3.0.0", "ModelX", "abc123");
            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("不同摘要不等")
        void shouldNotBeEqualDifferentDigest() {
            OTAVersion a = OTAVersion.of("2.0.0", "ModelX", "abc123");
            OTAVersion b = OTAVersion.of("2.0.0", "ModelX", "def456");
            assertNotEquals(a, b);
        }
    }
}
