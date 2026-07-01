package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;

/**
 * 干预指令类型（VO-12 配套枚举）
 * 闭合穷举所有分级干预指令种类
 */
public enum InterventionInstructionType {
    /** 氛围灯调色 */
    AMBIENT_LIGHT_COLOR,
    /** 语音播报 */
    VOICE_BROADCAST,
    /** 座椅震动 */
    SEAT_VIBRATION,
    /** 双闪警示 */
    HAZARD_LIGHTS,
    /** 空调调节 */
    AIR_CONDITIONING,
    /** 音频播放 */
    AUDIO_PLAYBACK,
    /** CAN总线减速请求 */
    CAN_DECELERATION_REQUEST,
    /** 导航建议减速 */
    NAVIGATE_DECELERATION,
    /** 导航引导靠边 */
    NAVIGATE_TO_SHOULDER,
    /** 通用告警 */
    ALERT;

    /**
     * 字符串转干预指令类型枚举，内置业务校验
     * @param typeCode 类型字符串
     * @return 对应枚举值
     * @throws BusinessException 空值/非法值时抛出
     */
    public static InterventionInstructionType of(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new BusinessException(
                    "MODEL_009",
                    "干预指令类型不能为空",
                    "INTERVENTION_TYPE_VALIDATE"
            );
        }
        try {
            return InterventionInstructionType.valueOf(typeCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "MODEL_010",
                    String.format("干预指令类型非法，当前值：%s", typeCode),
                    "INTERVENTION_TYPE_VALIDATE"
            );
        }
    }
<<<<<<< HEAD
=======

>>>>>>> d61a4a60204c7e68e9b5b3ec725a630abc2e642a
}