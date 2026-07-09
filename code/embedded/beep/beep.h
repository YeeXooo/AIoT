/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：beep.h
 * 功能描述：蜂鸣器驱动头文件
 */

#ifndef __BEEP_H__
#define __BEEP_H__

#include "common_def.h"

int beep_init(void);
void beep_on(void);
void beep_off(void);
void beep_short(void);
void beep_alarm(void);

#endif /* __BEEP_H__ */
