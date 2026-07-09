/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：servo.h
 * 功能描述：SG90舵机驱动 — GPIO1 PWM
 */

#ifndef __SERVO_H__
#define __SERVO_H__

#include "common_def.h"

int servo_init(void);
void servo_set_angle(int angle);

#endif /* __SERVO_H__ */
