/*
 * DHT11 温湿度传感器驱动
 *
 * 修改记录:
 *   2025-07-04  v2 代码重构: 清晰超时/重试/错误处理
 *   2025-07-04  v3 修复崩溃: osal_udelay→软件延时 (osal_udelay在task上下文触发LoadAccessFault)
 */

#include "pinctrl.h"
#include "common_def.h"
#include "soc_osal.h"
#include "osal_wait.h"
#include "app_init.h"
#include "gpio.h"
#include "adc.h"
#include "adc_porting.h"
#include "stdio.h"
#include "hal_gpio.h"

#include "dht11.h"

#define DHT11_MAX_RETRY  3       /* 单次读取最多重试 */
#define DHT11_TIMEOUT    200     /* 超时计数(≈200us) */

static pin_t dht11_pin;
static int   g_dht11_init_ok = 0;

/* ═══════════════════════════════════════════════════════════════════
 * 微秒延时 — 软件空循环 (不使用 osal_udelay, 已验证会崩溃)
 * WS63 @160MHz, 每次内循环~4 cycle, 40次≈1us
 * ═══════════════════════════════════════════════════════════════════ */
static void dht11_udelay(uint16_t us)
{
    for (volatile uint16_t i = 0; i < us; i++) {
        volatile uint16_t j = 40;
        while (j--) { }
    }
}

/* ── 从 DHT11 读取 8bit ── */
static uint8_t dht11_read_byte(void)
{
    uint8_t  i, temp = 0;
    uint16_t timeout;

    for (i = 0; i < 8; i++) {
        /* 等待 50us 低电平结束 */
        timeout = 0;
        while (uapi_gpio_get_val(dht11_pin) == GPIO_LEVEL_LOW) {
            if (++timeout > DHT11_TIMEOUT) return (uint8_t)ERRCODE_FAIL;
            dht11_udelay(1);
        }

        /* 延时 35us 后采样: "0"=26~28us已结束, "1"=70us还在高 */
        dht11_udelay(35);

        if (uapi_gpio_get_val(dht11_pin) == GPIO_LEVEL_HIGH) {
            timeout = 0;
            while (uapi_gpio_get_val(dht11_pin) == GPIO_LEVEL_HIGH) {
                if (++timeout > DHT11_TIMEOUT) return (uint8_t)ERRCODE_FAIL;
                dht11_udelay(1);
            }
            temp |= (uint8_t)(0x01 << (7 - i));  /* MSB 先行 */
        }
    }
    return temp;
}

/* ── 单次读取 (不加锁, 由调用者加锁) ── */
static errcode_t dht11_read_once(DHT11_Data_TypeDef *data)
{
    uint8_t  temp;
    uint16_t humi_temp;
    uint16_t timeout;

    /* 1. 主机起始信号: 拉低 18ms → 拉高 */
    uapi_gpio_set_dir(dht11_pin, GPIO_DIRECTION_OUTPUT);
    uapi_gpio_set_val(dht11_pin, GPIO_LEVEL_LOW);
    osal_mdelay(18);
    uapi_gpio_set_val(dht11_pin, GPIO_LEVEL_HIGH);

    /* 2. 切换到输入, 等待 DHT11 响应 */
    uapi_gpio_set_dir(dht11_pin, GPIO_DIRECTION_INPUT);
    uapi_pin_set_pull(dht11_pin, PIN_PULL_TYPE_STRONG_UP);

    /* 2a. 等待 DHT11 拉低 (响应: 80us 低) */
    timeout = 0;
    while (uapi_gpio_get_val(dht11_pin) == GPIO_LEVEL_HIGH) {
        if (++timeout > DHT11_TIMEOUT) return ERRCODE_FAIL;
        dht11_udelay(1);
    }

    /* 2b. 等待 DHT11 拉高 (响应: 80us 高) */
    timeout = 0;
    while (uapi_gpio_get_val(dht11_pin) == GPIO_LEVEL_LOW) {
        if (++timeout > DHT11_TIMEOUT) return ERRCODE_FAIL;
        dht11_udelay(1);
    }

    /* 2c. 等待数据传输起始低电平 */
    timeout = 0;
    while (uapi_gpio_get_val(dht11_pin) == GPIO_LEVEL_HIGH) {
        if (++timeout > DHT11_TIMEOUT) return ERRCODE_FAIL;
        dht11_udelay(1);
    }

    /* 3. 接收 40bit */
    data->humi_high8bit = dht11_read_byte();
    data->humi_low8bit  = dht11_read_byte();
    data->temp_high8bit = dht11_read_byte();
    data->temp_low8bit  = dht11_read_byte();
    data->check_sum     = dht11_read_byte();

    /* 4. 释放总线 */
    uapi_gpio_set_dir(dht11_pin, GPIO_DIRECTION_OUTPUT);
    uapi_gpio_set_val(dht11_pin, GPIO_LEVEL_HIGH);

    /* 5. 校验 */
    temp = data->humi_high8bit + data->humi_low8bit
         + data->temp_high8bit + data->temp_low8bit;
    if (temp != data->check_sum) return ERRCODE_FAIL;

    /* 6. 计算物理量 */
    humi_temp = data->humi_high8bit * 100 + data->humi_low8bit;
    data->humidity    = (float)humi_temp / 100;
    humi_temp = data->temp_high8bit * 100 + data->temp_low8bit;
    data->temperature = (float)humi_temp / 100;

    return ERRCODE_SUCC;
}

/* ═══════════════════════════════════════════════════════════════════
 * DHT11 初始化
 * ═══════════════════════════════════════════════════════════════════ */
void dht11_init(void)
{
    dht11_pin = GPIO_04;
    uapi_pin_set_mode(dht11_pin, PIN_MODE_2);
    uapi_gpio_set_dir(dht11_pin, GPIO_DIRECTION_OUTPUT);
    uapi_pin_set_pull(dht11_pin, PIN_PULL_TYPE_UP);
    uapi_gpio_set_val(dht11_pin, GPIO_LEVEL_HIGH);
    g_dht11_init_ok = 1;
    printf("[DHT11] Init OK: GPIO4\n");
}

/* ═══════════════════════════════════════════════════════════════════
 * DHT11 读取 (带重试)
 * ═══════════════════════════════════════════════════════════════════ */
errcode_t dht11_read_data(DHT11_Data_TypeDef *DHT11_Data)
{
    errcode_t ret = ERRCODE_FAIL;

    if (!g_dht11_init_ok || !DHT11_Data) return ERRCODE_FAIL;

    osal_kthread_lock();

    for (int attempt = 0; attempt < DHT11_MAX_RETRY; attempt++) {
        ret = dht11_read_once(DHT11_Data);
        if (ret == ERRCODE_SUCC) break;
        dht11_udelay(500);
    }

    osal_kthread_unlock();

    if (ret != ERRCODE_SUCC) {
        printf("[DHT11] Read failed after %d retries\n", DHT11_MAX_RETRY);
    }
    return ret;
}
