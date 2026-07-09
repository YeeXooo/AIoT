/*
 * MAX30102 心率血氧驱动 (参考 drive-1/MAX30102 + Maxim官方算法)
 * I2C1, 7bit地址 0x57
 */

#ifndef __HEARTRATE_H__
#define __HEARTRATE_H__

#include "common_def.h"

typedef struct {
    int    heart_rate;
    int    spo2;
    int    resting_hr;
    int    valid;
} heartrate_data_t;

int  heartrate_init(void);
int  heartrate_read(heartrate_data_t *hr);
void heartrate_set_resting_hr(int hr);

#endif
