/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：beep.c
 * 功能描述：蜂鸣器驱动 — GPIO模式
 */

#include "beep.h"
#include "pinctrl.h"
#include "gpio.h"
#include "soc_osal.h"
#include "stdio.h"

static pin_t g_beep_pin = CONFIG_BEEP_PIN;

int beep_init(void)
{
    uapi_pin_set_mode(g_beep_pin, PIN_MODE_0);
    uapi_gpio_set_dir(g_beep_pin, GPIO_DIRECTION_OUTPUT);
    uapi_gpio_set_val(g_beep_pin, GPIO_LEVEL_HIGH);
    printf("Beep init OK: PIN=%d\n", g_beep_pin);
    return 0;
}

void beep_on(void)  { uapi_gpio_set_val(g_beep_pin, GPIO_LEVEL_LOW); }
void beep_off(void) { uapi_gpio_set_val(g_beep_pin, GPIO_LEVEL_HIGH); }

void beep_short(void)
{
    beep_on();
    osal_msleep(200);
    beep_off();
}

void beep_alarm(void)
{
    int i;
    for (i = 0; i < 5; i++) {
        beep_on();
        osal_msleep(100);
        beep_off();
        osal_msleep(100);
    }
}
