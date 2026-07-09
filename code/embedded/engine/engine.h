/*
 * 边缘判定引擎头文件
 *
 * 三级风险体系:
 *   L1 提示级(绿→黄) — 轻微异常, 仅仪表盘图标/语音提示
 *   L2 预警级(橙→红) — 明确风险, 变色告警+环境调节+家属推送
 *   L3 高危级(红爆闪) — 危及安全, 强制唤醒+双闪+SOS+救援联动
 *
 * 已启用: BR-01 疲劳(DMS) BR-05 评分(IMU) BR-06 碰撞(IMU+GPS) BR-08 故障
 * 已删除: BR-02 活体(雷达) BR-03 路怒(心率)
 */

#ifndef __ENGINE_H__
#define __ENGINE_H__

#include "common_def.h"
#include "../bmx055/bmx055.h"
#include "../dms/dms.h"
#include "../gps/gps.h"

#define RISK_NONE      0
#define RISK_L1_NOTICE 1
#define RISK_L2_WARN   2
#define RISK_L3_CRIT   3

#define ALERT_FATIGUE      1  /* BR-01 DMS疲劳 */
#define ALERT_DISTRACTION  2  /* BR-01 DMS分心(打电话等) */
#define ALERT_COLLISION    5  /* BR-06 碰撞失能(IMU+GPS) */
#define ALERT_FAULT        6  /* BR-08 传感器故障 */

typedef struct {
    int risk_level;
    int alert_type;
    char detail[128];
} engine_result_t;

typedef struct {
    int hard_brake_count;
    int hard_accel_count;
    int sharp_turn_count;
    int trip_score;
} driving_score_t;

int engine_init(void);

/* engine_tick: 每50ms调用, 传入传感器最新数据 */
void engine_tick(const imu_data_t *imu,
                 const dms_data_t *dms,
                 const gps_data_t *gps,
                 float temp, float humi,
                 uint16_t lux, int vehicle_state);

int engine_get_risk(void);
driving_score_t engine_get_score(void);

/* 各规则独立判定接口 */
int engine_check_fatigue(const dms_data_t *dms);            /* BR-01 */
void engine_update_score(const imu_data_t *imu);            /* BR-05 */
int engine_check_collision(const imu_data_t *imu);          /* BR-06 */
int engine_check_fault(int sensor_ok);                      /* BR-08 */

#endif /* __ENGINE_H__ */
