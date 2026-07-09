/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：motor.c
 * 功能描述：TB6612直流电机驱动(参考: 25_smart_home/motor/motor.c)
 *
 * 硬件连接:
 *   PWMA  → GPIO1  (PWM速度控制, 24MHz→1kHz, 占空比0-100%)
 *   AIN1  → GPIO10 (方向控制1: HIGH=正转, LOW=反转/停止)
 *   AIN2  → GPIO11 (方向控制2: HIGH=反转, LOW=正转/停止)
 *
 * TB6612真值表:
 *   AIN1=LOW  AIN2=LOW   → 停止(惯性滑行)
 *   AIN1=HIGH AIN2=LOW   → 正转(CW)
 *   AIN1=LOW  AIN2=HIGH  → 反转(CCW)
 *   AIN1=HIGH AIN2=HIGH  → 刹车(短路制动)
 */

#include "motor.h"
#include "pinctrl.h"
#include "gpio.h"
#include "stdio.h"
#include "pwm_lamp.h"  /* PWM调速模块 */

static unsigned char g_motor_initialized = 0;

/* 设置TB6612方向控制引脚 */
static void set_motor_direction(motor_direction_t direction)
{
    switch (direction) {
        case MOTOR_FORWARD:
            uapi_gpio_set_val(MOTOR_AIN1_PIN, GPIO_LEVEL_HIGH);
            uapi_gpio_set_val(MOTOR_AIN2_PIN, GPIO_LEVEL_LOW);
            break;
        case MOTOR_BACKWARD:
            uapi_gpio_set_val(MOTOR_AIN1_PIN, GPIO_LEVEL_LOW);
            uapi_gpio_set_val(MOTOR_AIN2_PIN, GPIO_LEVEL_HIGH);
            break;
        case MOTOR_BRAKE:
            uapi_gpio_set_val(MOTOR_AIN1_PIN, GPIO_LEVEL_HIGH);
            uapi_gpio_set_val(MOTOR_AIN2_PIN, GPIO_LEVEL_HIGH);
            break;
        case MOTOR_STOP:
        default:
            uapi_gpio_set_val(MOTOR_AIN1_PIN, GPIO_LEVEL_LOW);
            uapi_gpio_set_val(MOTOR_AIN2_PIN, GPIO_LEVEL_LOW);
            break;
    }
}

int motor_init(void)
{
    if (g_motor_initialized) return 0;

    printf("Initializing TB6612 motor driver...\r\n");

    /* 初始化PWM模块(GPIO1, 24MHz→1kHz) */
    if (pwm_init_module() != ERRCODE_SUCC) {
        printf("Failed to initialize PWM for motor\r\n");
        return -1;
    }

    /* 配置方向控制引脚为GPIO输出 */
    uapi_pin_set_mode(MOTOR_AIN1_PIN, PIN_MODE_0);
    uapi_pin_set_mode(MOTOR_AIN2_PIN, PIN_MODE_0);
    uapi_gpio_set_dir(MOTOR_AIN1_PIN, GPIO_DIRECTION_OUTPUT);
    uapi_gpio_set_dir(MOTOR_AIN2_PIN, GPIO_DIRECTION_OUTPUT);

    /* 初始: 电机停止 */
    set_motor_direction(MOTOR_STOP);
    g_motor_initialized = 1;
    printf("Motor init OK: AIN1=GPIO%d AIN2=GPIO%d PWMA=GPIO%d\r\n",
           MOTOR_AIN1_PIN, MOTOR_AIN2_PIN, MOTOR_PWMA_PIN);
    return 0;
}

/* 设置速度(0-100%) + 方向 */
int motor_set_speed(uint8_t speed, motor_direction_t direction)
{
    if (!g_motor_initialized || speed > 100) return -1;

    set_motor_direction(direction);

    if (direction == MOTOR_STOP || speed == 0)
        pwm_setup_output(1000, 0);   /* 占空比0% → 停止 */
    else
        pwm_setup_output(1000, speed);
    return 0;
}

void motor_stop(void)               { motor_set_speed(0, MOTOR_STOP); }
void motor_brake(void)              { set_motor_direction(MOTOR_BRAKE); }
int motor_forward(uint8_t speed)    { return motor_set_speed(speed, MOTOR_FORWARD); }
int motor_backward(uint8_t speed)   { return motor_set_speed(speed, MOTOR_BACKWARD); }
