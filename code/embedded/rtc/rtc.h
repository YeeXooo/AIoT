/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：rtc.h
 * 功能描述：INS5699S RTC驱动头文件 (I2C, 0x51)
 */

#ifndef __RTC_H__
#define __RTC_H__

#include "common_def.h"

typedef struct {
    uint8_t sec, min, hour, week, day, month, year;
} rtc_time_t;

int rtc_init(void);
int rtc_get_time(rtc_time_t *t);
int rtc_set_time(rtc_time_t *t);

#endif /* __RTC_H__ */
