/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：servo.c
 * 功能描述：SG90舵机驱动 — GPIO1 PWM, 20ms周期, 0.5-2.5ms脉宽
 */

#include "servo.h"
#include "pinctrl.h"
#include "pwm.h"
#include "soc_osal.h"
#include "stdio.h"

#define SERVO_CHANNEL  2    /* PWM2 */
#define SERVO_PIN      2    /* GPIO_02 */

int servo_init(void)
{
    uapi_pin_set_mode(SERVO_PIN, 1); /* PWM模式 */
    uapi_pwm_init();
    printf("Servo init OK: PWM%d GPIO%d\n", SERVO_CHANNEL, SERVO_PIN);
    return 0;
}

void servo_set_angle(int angle)
{
    /* 0°=500us 90°=1500us 180°=2500us 周期20000us=50Hz */
    uint32_t pulse = 500 + (angle * 2000 / 180);
    pwm_config_t cfg = {
        .low_time = 20000 - pulse,
        .high_time = pulse,
        .offset_time = 0, .cycles = 100, .repeat = 1
    };
    uapi_pwm_open(SERVO_CHANNEL, &cfg);
    uapi_pwm_start(SERVO_CHANNEL);
}
