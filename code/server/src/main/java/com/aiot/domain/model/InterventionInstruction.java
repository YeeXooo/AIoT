package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import java.util.Collections;
import java.util.Map;

/**
 * 干预指令（VO-12）
 * 分级干预的统一载体，包含指令类型、目标设备、参数映射与优先级
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class InterventionInstruction {
    private final InterventionInstructionType type;
    private final String targetDevice;
    private final Map<String, Object> parameters;
    private final int priority;

    private InterventionInstruction(InterventionInstructionType type, String targetDevice,
                                    Map<String, Object> parameters, int priority) {
        if (type == null) {
            throw new BusinessException(
                    "MODEL_030",
                    "干预指令类型不能为空",
                    "INTERVENTION_INSTRUCTION_VALIDATE"
            );
        }
        this.type = type;
        this.targetDevice = targetDevice;
        this.parameters = parameters == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(parameters);
        this.priority = priority;
    }

    public static InterventionInstruction of(InterventionInstructionType type, String targetDevice,
                                             Map<String, Object> parameters, int priority) {
        return new InterventionInstruction(type, targetDevice, parameters, priority);
    }

    protected InterventionInstruction() {
        this.type = null;
        this.targetDevice = "";
        this.parameters = Collections.emptyMap();
        this.priority = 0;
    }
}