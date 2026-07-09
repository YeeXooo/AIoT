/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：led.c
 * 功能描述：三色LED告警灯驱动
 *
 * 引脚: 红=GPIO3  绿=GPIO9  黄=GPIO12  (避开GPIO10/11=电机TB6612)
 * 电平: LOW=亮 HIGH=灭 (共阳接法, 低电平有效)
 *
 * HMI分级输出(led_set_risk_level):
 *   L0 NONE  → 绿灯(系统正常运行)
 *   L1 提示  → 黄灯(轻微异常, 仪表盘提示级别)
 *   L2 预警  → 红灯+黄灯(明确风险, 需要干预)
 *   L3 高危  → 仅红灯(危及安全, 最高优先级)
 */

#include "led.h"
#include "pinctrl.h"
#include "gpio.h"
#include "stdio.h"

/* GPIO引脚定义 — 避开 GPIO10/11(电机TB6612占用) */
#define LED_RED_PIN    3    /* GPIO3  (红) */
#define LED_GREEN_PIN  9    /* GPIO9  (绿, 原GPIO11与电机AIN2冲突) */
#define LED_YELLOW_PIN 12   /* GPIO12 (黄, 原GPIO10与电机AIN1冲突) */

int led_init(void)
{
    /* 设置IO复用为普通GPIO功能, 方向为输出 */
    uapi_pin_set_mode(LED_RED_PIN, PIN_MODE_0);
    uapi_pin_set_mode(LED_GREEN_PIN, PIN_MODE_0);
    uapi_pin_set_mode(LED_YELLOW_PIN, PIN_MODE_0);

    uapi_gpio_set_dir(LED_RED_PIN, GPIO_DIRECTION_OUTPUT);
    uapi_gpio_set_dir(LED_GREEN_PIN, GPIO_DIRECTION_OUTPUT);
    uapi_gpio_set_dir(LED_YELLOW_PIN, GPIO_DIRECTION_OUTPUT);

    /* 初始状态: 全部熄灭 */
    led_all_off();
    printf("LED init OK: R=GPIO%d G=GPIO%d Y=GPIO%d\n", LED_RED_PIN, LED_GREEN_PIN, LED_YELLOW_PIN);
    return 0;
}

/* 低电平点亮(共阳接法) */
int led_red_on(void)    { return uapi_gpio_set_val(LED_RED_PIN, GPIO_LEVEL_LOW); }
int led_red_off(void)   { return uapi_gpio_set_val(LED_RED_PIN, GPIO_LEVEL_HIGH); }
int led_green_on(void)  { return uapi_gpio_set_val(LED_GREEN_PIN, GPIO_LEVEL_LOW); }
int led_green_off(void) { return uapi_gpio_set_val(LED_GREEN_PIN, GPIO_LEVEL_HIGH); }
int led_yellow_on(void)  { return uapi_gpio_set_val(LED_YELLOW_PIN, GPIO_LEVEL_LOW); }
int led_yellow_off(void) { return uapi_gpio_set_val(LED_YELLOW_PIN, GPIO_LEVEL_HIGH); }

void led_all_off(void)
{
    led_red_off();
    led_green_off();
    led_yellow_off();
}

/* HMI分级告警输出 — 先关全部再按等级点亮对应颜色 */
void led_set_risk_level(int level)
{
    led_all_off();
    switch (level) {
        case 0: /* NORMAL — 系统正常, 绿灯常亮 */
            led_green_on();
            break;
        case 1: /* L1 提示 — 轻微异常, 黄灯提醒 */
            led_yellow_on();
            break;
        case 2: /* L2 预警 — 明确风险, 红灯+黄灯 */
            led_red_on();
            led_yellow_on();
            break;
        case 3: /* L3 高危 — 生命危险, 仅红灯(最醒目) */
            led_red_on();
            break;
        default:
            break;
    }
}
