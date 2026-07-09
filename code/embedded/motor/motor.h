/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：motor.h
 * 功能描述：TB6612电机驱动头文件
 * 参考: 25_smart_home/motor/motor.h
 */

#ifndef __MOTOR_H__
#define __MOTOR_H__

#include "common_def.h"
#include "soc_osal.h"

/* TB6612控制引脚定义 — 与25_smart_home完全一致 */
#define MOTOR_PWMA_PIN   1       /* GPIO_01 - PWM速度控制 */
#define MOTOR_AIN1_PIN   10      /* GPIO_10 - 方向控制1 */
#define MOTOR_AIN2_PIN   11      /* GPIO_11 - 方向控制2 */

/* 电机方向定义 */
typedef enum {
    MOTOR_STOP = 0,
    MOTOR_FORWARD,
    MOTOR_BACKWARD,
    MOTOR_BRAKE
} motor_direction_t;

int motor_init(void);
int motor_set_speed(uint8_t speed, motor_direction_t direction);
void motor_stop(void);
void motor_brake(void);
int motor_forward(uint8_t speed);
int motor_backward(uint8_t speed);

#endif /* __MOTOR_H__ */
