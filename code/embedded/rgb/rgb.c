/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：rgb.c
 * 功能描述：WS2812B RGB灯带驱动 — GPIO模式
 */

#include "rgb.h"
#include "pinctrl.h"
#include "gpio.h"
#include "soc_osal.h"
#include "stdio.h"

static pin_t g_rgb_pin = 5;

int rgb_init(void)
{
    uapi_pin_set_mode(g_rgb_pin, PIN_MODE_0);
    uapi_gpio_set_dir(g_rgb_pin, GPIO_DIRECTION_OUTPUT);
    printf("WS2812B RGB init OK: GPIO%d\n", g_rgb_pin);
    return 0;
}

void rgb_set_color(uint8_t r, uint8_t g, uint8_t b)
{
    unused(r); unused(g); unused(b);
    /* WS2812B 时序通过bit-banging GPIO实现，此处为简化桩 */
    printf("RGB set: R=%d G=%d B=%d\n", r, g, b);
}

void rgb_set_hmi(int risk_level)
{
    switch (risk_level) {
        case 0: rgb_set_color(0, 255, 0);   break;
        case 1: rgb_set_color(255, 165, 0); break;
        case 2: rgb_set_color(255, 0, 0);   break;
        case 3: rgb_set_color(255, 0, 0);   break;
        default: rgb_set_color(0, 0, 0);     break;
    }
}
