package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 驾驶员综合风险评分（VO-22）
 * 跨行程加权平均的整体风险水平
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class DriverComprehensiveScore {
    private final int value;

    private DriverComprehensiveScore(int value) {
        if (value < 0 || value > 100) {
            throw new BusinessException(
                    "MODEL_046",
                    String.format("驾驶员综合评分必须在 0~100 范围内，当前值：%d", value),
                    "DRIVER_COMPREHENSIVE_SCORE_VALIDATE"
            );
        }
        this.value = value;
    }

    public static DriverComprehensiveScore of(int value) {
        return new DriverComprehensiveScore(value);
    }

    protected DriverComprehensiveScore() {
        this.value = 0;
    }

}