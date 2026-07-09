/*
 * 边缘判定引擎 — BR规则状态机
 *
 * 已启用:
 *   BR-01 疲劳(DMS) BR-05 评分(IMU) BR-06 碰撞(IMU+GPS) BR-08 故障
 * 已删除:
 *   BR-02 活体(雷达) BR-03 路怒(心率)
 *
 * 设计: tick=50ms → 安全告警 ≤500ms, 全部边缘侧完成不依赖云端
 */

#include "engine.h"
#include "soc_osal.h"
#include "stdio.h"
#include "string.h"
#include "math.h"

static int g_risk_level = RISK_NONE;
static int g_alert_type  = 0;
static driving_score_t g_score = {0};

/* BR-06 碰撞计时器 (200 ticks = 10s) */
static uint32_t g_collision_ticks = 0;
static int g_collision_detected = 0;

/* 最近GPS坐标 (BR-06 SOS上报用) */
static float g_last_lat = 0.0f, g_last_lon = 0.0f;

static uint32_t g_total_ticks = 0;

int engine_init(void)
{
    memset(&g_score, 0, sizeof(g_score));
    g_score.trip_score = 100;
    printf("Engine init OK: BR-01/05/06/08 ready\n");
    return 0;
}

/* ==================== BR-01 疲劳分级判定 (DMS摄像头 → PC视觉 → UART) ==================== */
int engine_check_fatigue(const dms_data_t *dms)
{
    if (!dms || !dms->valid) return RISK_NONE;

    /* PC端已判定为重度 → L3 */
    if (dms->pc_fatigue_lvl >= 2) {
        g_risk_level = RISK_L3_CRIT;
        g_alert_type = ALERT_FATIGUE;
        printf("[ENGINE] BR-01 FATIGUE L3: PC_LVL=%d PERCLOS=%.2f\n",
               dms->pc_fatigue_lvl, dms->perclos);
        return RISK_L3_CRIT;
    }

    /* PERCLOS判定 */
    if (dms->perclos > 0.5f) {
        g_risk_level = RISK_L3_CRIT;
        g_alert_type = ALERT_FATIGUE;
        printf("[ENGINE] BR-01 FATIGUE L3: PERCLOS=%.2f\n", dms->perclos);
        return RISK_L3_CRIT;
    } else if (dms->perclos > 0.3f) {
        g_risk_level = RISK_L2_WARN;
        g_alert_type = ALERT_FATIGUE;
        printf("[ENGINE] BR-01 FATIGUE L2: PERCLOS=%.2f YAWN=%d\n",
               dms->perclos, dms->yawn_count);
        return RISK_L2_WARN;
    } else if (dms->perclos > 0.15f) {
        printf("[ENGINE] BR-01 FATIGUE L1: PERCLOS=%.2f\n", dms->perclos);
        return RISK_L1_NOTICE;
    }

    /* 分心: 接打电话 */
    if (dms->phone_use) {
        g_alert_type = ALERT_DISTRACTION;
        printf("[ENGINE] BR-01 DISTRACTION: phone in use\n");
        return RISK_L2_WARN;
    }

    return RISK_NONE;
}

/* ==================== BR-05 驾驶评分 (IMU) ==================== */
static int g_diag_cnt = 0;
void engine_update_score(const imu_data_t *imu)
{
    if (!imu) return;

    /* 检测急加速/急刹车 (加速度突变 > 4 m/s²) */
    float a_mag = imu->ax * imu->ax + imu->ay * imu->ay + imu->az * imu->az;
    if (a_mag > 16.0f) {  /* 4² = 16 */
        if (imu->ax < -3.0f) {
            g_score.hard_brake_count++;
            if (g_score.trip_score > 0) g_score.trip_score -= 3;
        } else if (imu->ax > 3.0f) {
            g_score.hard_accel_count++;
            if (g_score.trip_score > 0) g_score.trip_score -= 2;
        }
    }

    /* 检测急转弯 (Z轴角速度 > 120 °/s) */
    float gz_abs = imu->gz > 0 ? imu->gz : -imu->gz;
    if (gz_abs > 120.0f) {
        g_score.sharp_turn_count++;
        if (g_score.trip_score > 0) g_score.trip_score -= 2;
    }

    /* 自然恢复: 每1000 tick (+0.1/秒) 慢慢恢复1分, 上限100 */
    if (g_total_ticks % 1000 == 0 && g_score.trip_score < 100) {
        g_score.trip_score++;
    }

    if (++g_diag_cnt >= 100) {
        g_diag_cnt = 0;
        printf("[IMU] ax=%.1f ay=%.1f az=%.1f | gx=%.1f gy=%.1f gz=%.1f | "
               "score=%d brake=%d accel=%d turn=%d\n",
               imu->ax, imu->ay, imu->az,
               imu->gx, imu->gy, imu->gz,
               g_score.trip_score, g_score.hard_brake_count,
               g_score.hard_accel_count, g_score.sharp_turn_count);
    }
}

/* ==================== BR-06 碰撞失能 (IMU+GPS) ==================== */
int engine_check_collision(const imu_data_t *imu)
{
    if (!imu) return RISK_NONE;
    float a_mag = imu->ax * imu->ax + imu->ay * imu->ay + imu->az * imu->az;
    if (a_mag > 6084.0f) {
        if (!g_collision_detected) {
            g_collision_detected = 1; g_collision_ticks = 0;
            printf("[ENGINE] BR-06 COLLISION! a=%.1f m/s2 GPS:%.4f,%.4f\n",
                   sqrt(a_mag), g_last_lat, g_last_lon);
        }
        g_collision_ticks++;
        if (g_collision_ticks > 200) {
            printf("[ENGINE] BR-06 NO RESPONSE 10s → L3 SOS! GPS=%.4f,%.4f\n",
                   g_last_lat, g_last_lon);
            g_risk_level = RISK_L3_CRIT; g_alert_type = ALERT_COLLISION;
            return RISK_L3_CRIT;
        }
    } else { g_collision_detected = 0; g_collision_ticks = 0; }
    return RISK_NONE;
}

/* ==================== BR-08 故障保护 ==================== */
int engine_check_fault(int sensor_ok)
{
    if (!sensor_ok) {
        printf("[ENGINE] BR-08 SENSOR FAULT!\n");
        g_risk_level = RISK_L3_CRIT; g_alert_type = ALERT_FAULT;
        return RISK_L3_CRIT;
    }
    return RISK_NONE;
}

/* ==================== 主tick (50ms, EngineTask调用) ==================== */
void engine_tick(const imu_data_t *imu,
                 const dms_data_t *dms,
                 const gps_data_t *gps,
                 float temp, float humi,
                 uint16_t lux, int vehicle_state)
{
    unused(temp); unused(humi); unused(lux);
    g_total_ticks++;

    g_risk_level = RISK_NONE;
    g_alert_type = 0;

    /* 更新GPS坐标缓存 (BR-06 SOS时用) */
    if (gps && gps->fix > 0) {
        g_last_lat = gps->latitude;
        g_last_lon = gps->longitude;
    }

    /* 按优先级执行: BR-06碰撞 > BR-01疲劳 > BR-08故障 > BR-05评分 */
    engine_check_collision(imu);
    engine_check_fatigue(dms);
    engine_check_fault(vehicle_state);  /* BR-08: vehicle_state 复用为 sensor_ok */
    engine_update_score(imu);
}

int engine_get_risk(void) { return g_risk_level; }
driving_score_t engine_get_score(void) { return g_score; }
