package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InterventionInstructionType 干预指令类型枚举测试")
class InterventionInstructionTypeTest {

    @Nested
    @DisplayName("of() 字符串转干预指令类型")
    class OfMethod {

        @Test
        @DisplayName("正常转换 - AMBIENT_LIGHT_COLOR")
        void shouldReturnAmbientLightColor() {
            assertEquals(InterventionInstructionType.AMBIENT_LIGHT_COLOR,
                    InterventionInstructionType.of("AMBIENT_LIGHT_COLOR"));
        }

        @Test
        @DisplayName("正常转换 - VOICE_BROADCAST")
        void shouldReturnVoiceBroadcast() {
            assertEquals(InterventionInstructionType.VOICE_BROADCAST,
                    InterventionInstructionType.of("VOICE_BROADCAST"));
        }

        @Test
        @DisplayName("正常转换 - SEAT_VIBRATION")
        void shouldReturnSeatVibration() {
            assertEquals(InterventionInstructionType.SEAT_VIBRATION,
                    InterventionInstructionType.of("SEAT_VIBRATION"));
        }

        @Test
        @DisplayName("正常转换 - HAZARD_LIGHTS")
        void shouldReturnHazardLights() {
            assertEquals(InterventionInstructionType.HAZARD_LIGHTS,
                    InterventionInstructionType.of("HAZARD_LIGHTS"));
        }

        @Test
        @DisplayName("正常转换 - AIR_CONDITIONING")
        void shouldReturnAirConditioning() {
            assertEquals(InterventionInstructionType.AIR_CONDITIONING,
                    InterventionInstructionType.of("AIR_CONDITIONING"));
        }

        @Test
        @DisplayName("正常转换 - AUDIO_PLAYBACK")
        void shouldReturnAudioPlayback() {
            assertEquals(InterventionInstructionType.AUDIO_PLAYBACK,
                    InterventionInstructionType.of("AUDIO_PLAYBACK"));
        }

        @Test
        @DisplayName("正常转换 - CAN_DECELERATION_REQUEST")
        void shouldReturnCanDecelerationRequest() {
            assertEquals(InterventionInstructionType.CAN_DECELERATION_REQUEST,
                    InterventionInstructionType.of("CAN_DECELERATION_REQUEST"));
        }

        @Test
        @DisplayName("正常转换 - NAVIGATE_DECELERATION")
        void shouldReturnNavigateDeceleration() {
            assertEquals(InterventionInstructionType.NAVIGATE_DECELERATION,
                    InterventionInstructionType.of("NAVIGATE_DECELERATION"));
        }

        @Test
        @DisplayName("正常转换 - NAVIGATE_TO_SHOULDER")
        void shouldReturnNavigateToShoulder() {
            assertEquals(InterventionInstructionType.NAVIGATE_TO_SHOULDER,
                    InterventionInstructionType.of("NAVIGATE_TO_SHOULDER"));
        }

        @Test
        @DisplayName("正常转换 - ALERT")
        void shouldReturnAlert() {
            assertEquals(InterventionInstructionType.ALERT,
                    InterventionInstructionType.of("ALERT"));
        }

        @Test
        @DisplayName("忽略大小写转换")
        void shouldConvertCaseInsensitive() {
            assertEquals(InterventionInstructionType.VOICE_BROADCAST,
                    InterventionInstructionType.of("voice_broadcast"));
        }

        @Test
        @DisplayName("自动去除前后空格")
        void shouldTrimWhitespace() {
            assertEquals(InterventionInstructionType.ALERT,
                    InterventionInstructionType.of("  ALERT  "));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("空值或空白字符串抛出 BusinessException (MODEL_009)")
        void shouldThrowWhenNullOrEmpty(String input) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> InterventionInstructionType.of(input));
            assertEquals("MODEL_009", ex.getErrorCode());
            assertEquals("INTERVENTION_TYPE_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("非法值抛出 BusinessException (MODEL_010)")
        void shouldThrowForInvalidValue() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> InterventionInstructionType.of("INVALID_TYPE"));
            assertEquals("MODEL_010", ex.getErrorCode());
            assertEquals("INTERVENTION_TYPE_VALIDATE", ex.getErrorScope());
            assertTrue(ex.getMessage().contains("INVALID_TYPE"));
        }
    }

    @Test
    @DisplayName("枚举值数量验证")
    void shouldHaveTenValues() {
        assertEquals(10, InterventionInstructionType.values().length);
    }
}
