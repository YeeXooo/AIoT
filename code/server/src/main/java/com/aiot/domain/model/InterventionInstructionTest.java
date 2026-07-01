package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InterventionInstruction 干预指令测试")
class InterventionInstructionTest {

    @Nested
    @DisplayName("of() 工厂方法")
    class OfMethod {

        @Test
        @DisplayName("正常创建 - 带参数")
        void shouldCreateWithParameters() {
            Map<String, Object> params = Map.of("volume", 80, "message", "请注意休息");
            InterventionInstruction instruction = InterventionInstruction.of(
                    InterventionInstructionType.VOICE_BROADCAST, "speaker-01", params, 1);
            assertEquals(InterventionInstructionType.VOICE_BROADCAST, instruction.getType());
            assertEquals("speaker-01", instruction.getTargetDevice());
            assertEquals(80, instruction.getParameters().get("volume"));
            assertEquals(1, instruction.getPriority());
        }

        @Test
        @DisplayName("正常创建 - 无参数")
        void shouldCreateWithNullParameters() {
            InterventionInstruction instruction = InterventionInstruction.of(
                    InterventionInstructionType.HAZARD_LIGHTS, "light-01", null, 2);
            assertNotNull(instruction.getParameters());
            assertTrue(instruction.getParameters().isEmpty());
        }

        @Test
        @DisplayName("targetDevice 可为 null")
        void shouldAllowNullTargetDevice() {
            InterventionInstruction instruction = InterventionInstruction.of(
                    InterventionInstructionType.ALERT, null, null, 0);
            assertNull(instruction.getTargetDevice());
        }

        @Test
        @DisplayName("type 为 null 抛出 BusinessException (MODEL_030)")
        void shouldThrowWhenTypeIsNull() {
            BusinessException ex = assertThrows(BusinessException.class, () ->
                    InterventionInstruction.of(null, "device", null, 0));
            assertEquals("MODEL_030", ex.getErrorCode());
            assertEquals("INTERVENTION_INSTRUCTION_VALIDATE", ex.getErrorScope());
        }

        @Test
        @DisplayName("参数映射不可变")
        void parametersShouldBeUnmodifiable() {
            Map<String, Object> params = new HashMap<>();
            params.put("key", "value");
            InterventionInstruction instruction = InterventionInstruction.of(
                    InterventionInstructionType.AMBIENT_LIGHT_COLOR, "light", params, 1);
            assertThrows(UnsupportedOperationException.class, () ->
                    instruction.getParameters().put("newKey", "newValue"));
        }
    }

    @Nested
    @DisplayName("equals/hashCode")
    class EqualsHashCode {

        @Test
        @DisplayName("相同属性相等")
        void shouldBeEqual() {
            Map<String, Object> params = Map.of("volume", 80);
            InterventionInstruction a = InterventionInstruction.of(
                    InterventionInstructionType.VOICE_BROADCAST, "speaker", params, 1);
            InterventionInstruction b = InterventionInstruction.of(
                    InterventionInstructionType.VOICE_BROADCAST, "speaker", params, 1);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("不同类型不等")
        void shouldNotBeEqualDifferentType() {
            InterventionInstruction a = InterventionInstruction.of(
                    InterventionInstructionType.VOICE_BROADCAST, "speaker", null, 1);
            InterventionInstruction b = InterventionInstruction.of(
                    InterventionInstructionType.HAZARD_LIGHTS, "speaker", null, 1);
            assertNotEquals(a, b);
        }
    }
}
