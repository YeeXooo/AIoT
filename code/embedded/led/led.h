/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：led.h
 * 功能描述：LED驱动头文件 — 红/绿/黄三色告警灯
 */

#ifndef __LED_H__
#define __LED_H__

#include "common_def.h"
#include "soc_osal.h"

int led_init(void);
int led_red_on(void);
int led_red_off(void);
int led_green_on(void);
int led_green_off(void);
int led_yellow_on(void);
int led_yellow_off(void);
void led_all_off(void);

/* HMI分级: L1绿灯 L2黄灯 L3红灯闪烁 */
void led_set_risk_level(int level);

#endif /* __LED_H__ */
