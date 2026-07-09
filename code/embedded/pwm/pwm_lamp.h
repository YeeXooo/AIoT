/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：pwm_lamp.h
 * 功能描述：PWM驱动头文件 (GPIO1, TB6612电机调速)
 * 参考: 25_smart_home/pwm/pwm_lamp.h
 */

#ifndef __PWM_LAMP_H__
#define __PWM_LAMP_H__

#include "common_def.h"
#include "pinctrl.h"
#include "pwm.h"
#include "soc_osal.h"

#define PWM_CHANNEL          1
#define PWM_GROUP_ID         0
#define PWM_PIN              1       /* GPIO_01 */
#define PWM_PIN_MODE         1
#define PWM_BASE_CLOCK_HZ    24000000
#define PWM_DESIRED_FREQ_HZ  1000

errcode_t pwm_init_module(void);
errcode_t pwm_setup_output(uint32_t freq, uint8_t duty);
void pwm_cleanup(void);

#endif /* __PWM_LAMP_H__ */
