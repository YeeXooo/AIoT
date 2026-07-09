/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：pwm_lamp.c
 * 功能描述：PWM驱动实现 — GPIO1, 24MHz→1kHz, 用于TB6612电机调速
 * 参考: 25_smart_home/pwm/pwm_lamp.c
 */

#include "stdio.h"
#include "pwm_lamp.h"

static unsigned char g_pwm_initialized = 0;

static errcode_t calculate_pwm_timing(uint32_t freq, uint8_t duty,
                                       uint32_t *low_time, uint32_t *high_time)
{
    if (freq == 0) return ERRCODE_FAIL;
    uint32_t total_cycles = PWM_BASE_CLOCK_HZ / freq;
    if (total_cycles < 2) total_cycles = 2;
    if (total_cycles > 0xFFFF) total_cycles = 0xFFFF;

    uint32_t high_cycles = (total_cycles * duty) / 100;
    uint32_t low_cycles = total_cycles - high_cycles;
    if (high_cycles == 0) high_cycles = 1;
    if (low_cycles == 0) low_cycles = 1;

    *high_time = high_cycles;
    *low_time = low_cycles;
    return ERRCODE_SUCC;
}

errcode_t pwm_init_module(void)
{
    uapi_pin_set_mode(PWM_PIN, PWM_PIN_MODE);
    uapi_pwm_deinit();
    errcode_t ret = uapi_pwm_init();
    if (ret != ERRCODE_SUCC) return ret;
    g_pwm_initialized = 1;
    return ERRCODE_SUCC;
}

errcode_t pwm_setup_output(uint32_t freq, uint8_t duty)
{
    uint32_t low_time, high_time;
    errcode_t ret = calculate_pwm_timing(freq, duty, &low_time, &high_time);
    if (ret != ERRCODE_SUCC) return ret;

    pwm_config_t cfg = {
        .low_time = low_time, .high_time = high_time,
        .offset_time = 0, .cycles = 0, .repeat = 1
    };

    if (g_pwm_initialized) uapi_pwm_close(PWM_CHANNEL);

    ret = uapi_pwm_open(PWM_CHANNEL, &cfg);
    if (ret != ERRCODE_SUCC) return ret;

    ret = uapi_pwm_start(PWM_CHANNEL);
    if (ret != ERRCODE_SUCC) { uapi_pwm_close(PWM_CHANNEL); return ret; }

    g_pwm_initialized = 1;
    return ERRCODE_SUCC;
}

void pwm_cleanup(void)
{
    uapi_pwm_close(PWM_CHANNEL);
    uapi_pwm_deinit();
    g_pwm_initialized = 0;
}
