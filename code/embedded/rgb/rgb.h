/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：rgb.h
 * 功能描述：WS2812B RGB灯带头文件 — HMI氛围灯
 */

#ifndef __RGB_H__
#define __RGB_H__

#include "common_def.h"

int rgb_init(void);
void rgb_set_color(uint8_t r, uint8_t g, uint8_t b); /* 0~255 */
void rgb_set_hmi(int risk_level); /* L1绿 L2橙 L3红爆闪 */

#endif /* __RGB_H__ */
