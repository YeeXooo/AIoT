package com.aiot.domain.model;

/**
 * 干预指令类型（VO-12 配套枚举）
 * 闭合穷举所有分级干预指令种类，基础设施层据此翻译为硬件指令
 * NAVIGATE_DECELERATION 与 NAVIGATE_TO_SHOULDER 为两个独立强度阶段
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
    ALERT
}