/*
 * 电池电量监测 (CW2015, I2C 0x62)
 * 用于BR-08: 低电量时触发故障告警(系统供电不足 → 安全监测可能失效)
 */

#ifndef __BATTERY_H__
#define __BATTERY_H__

#include "common_def.h"

typedef struct {
    uint16_t voltage_mv;   /* 电池电压 mV */
    uint8_t  percent;      /* 电量百分比 0-100 */
    int      low_power;    /* 低电量标记: 电压<3300mV 为1 */
} battery_data_t;

int battery_init(void);
int battery_read(battery_data_t *bat);

#endif /* __BATTERY_H__ */
