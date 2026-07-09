/*
 * SU-03T语音模块 — UART通信完全参考 19-voice/voice/voice.c
 * 26特有: 心率/血氧/电池/评分/风险 查询 + 静音控制
 *
 * 修改记录:
 *   2025-07-04  v2 过滤 0x00/0xFF 噪声, 添加诊断计数器
 */

#include "pinctrl.h"
#include "uart.h"
#include "debug.h"
#include "soc_osal.h"
#include "app_init.h"
#include "string.h"
#include "cmsis_os2.h"
#include "math.h"
#include "gpio.h"
#include "voice.h"

/* 26传感器 */
#include "../led/led.h"
#include "../beep/beep.h"
#include "../rgb/rgb.h"
#include "../dht11/dht11.h"
#include "../battery/battery.h"
#include "../engine/engine.h"


/* ==================== 19-voice 配置 ==================== */
#define DELAY_TIME_MS        10
#define UART_RECV_SIZE       14
#define UART_INT_MODE        0       /* 19-voice默认轮询模式 */
#define UART_TASK_STACK_SIZE 0x1000
#define UART_TASK_PRIO       28

/* ==================== 全局变量 (19-voice 命名风格) ==================== */
uint8_t uart_recv[UART_RECV_SIZE] = {0};
uint8_t usart_su_TXpacket[14] = {0};

#if (UART_INT_MODE)
static uint8_t uart_rx_flag = 0;
#endif

uart_buffer_config_t g_app_uart_buffer_config = {
    .rx_buffer = uart_recv,
    .rx_buffer_size = UART_RECV_SIZE
};

static int g_muted = 0;
static int g_noise_count = 0;       /* 噪声字节计数 */
static int g_noise_reported = 0;    /* 是否已报告噪声问题 */

/* ==================== UART GPIO 初始化 (19-voice 原版) ==================== */
void uart_gpio_init(void)
{
    uapi_pin_set_mode(GPIO_08, PIN_MODE_1); /* GPIO08 -> UART2 TX */
    uapi_pin_set_mode(GPIO_07, PIN_MODE_1); /* GPIO07 -> UART2 RX */

    uapi_gpio_set_dir(GPIO_08, GPIO_DIRECTION_OUTPUT);
    uapi_gpio_set_dir(GPIO_07, GPIO_DIRECTION_INPUT);

    /* RX 引脚上拉, 防止悬空时收到噪声 */
    uapi_pin_set_pull(GPIO_07, PIN_PULL_TYPE_UP);
}

/* ==================== UART 控制器配置 (19-voice 原版) ==================== */
void uart_init_config(void)
{
    uart_attr_t attr = {
        .baud_rate = 115200,
        .data_bits = UART_DATA_BIT_8,
        .stop_bits = UART_STOP_BIT_1,
        .parity = UART_PARITY_NONE
    };

    uart_pin_config_t pin_config = {
        .tx_pin = S_MGPIO0,   /* GPIO08 UART2 TX */
        .rx_pin = S_MGPIO1,   /* GPIO07 UART2 RX */
        .cts_pin = PIN_NONE,
        .rts_pin = PIN_NONE
    };

    uapi_uart_deinit(UART_BUS_2);
    int ret = uapi_uart_init(UART_BUS_2, &pin_config, &attr, NULL, &g_app_uart_buffer_config);
    if (ret != 0) {
        printf("[VOICE] UART2 init FAILED! ret=0x%02X\n", ret);
    } else {
        printf("[VOICE] UART2 init OK: GPIO7(RX) GPIO8(TX) @115200\n");
    }
}

/* ==================== 语音指令解析 (19-voice switch 风格) ==================== */
void voice_analysis(uint8_t *info)
{
    uint8_t cmd = info[0];

    /* ── 噪声过滤: 0x00/0xFF 是悬空或干扰, 不是有效指令 ── */
    if (cmd == 0x00 || cmd == 0xFF) {
        g_noise_count++;
        if (!g_noise_reported && g_noise_count >= 50) {
            printf("[VOICE] WARNING: %d noise bytes (0x00/0xFF) received. "
                   "Check SU-03T module power/wiring.\n", g_noise_count);
            g_noise_reported = 1;
        }
        return;
    }

    /* 收到有效指令, 复位噪声计数器 */
    g_noise_count = 0;
    g_noise_reported = 0;

    printf("[VOICE] Cmd=0x%02X\n", cmd);
    memset(usart_su_TXpacket, 0, 14);

    switch (cmd) {

    /* ---- 控制命令 (无应答) ---- */
    case VOICE_CMD_LIGHT_ON:    /* 0x01 打开氛围灯 */
        printf("[VOICE] → Turn on lamp\n");
        rgb_set_color(0, 255, 0);
        break;

    case VOICE_CMD_LIGHT_OFF:   /* 0x02 关闭氛围灯 */
        printf("[VOICE] → Turn off lamp\n");
        rgb_set_color(0, 0, 0);
        break;

    case VOICE_CMD_MUTE_ALERT:  /* 0x03 静音 */
        printf("[VOICE] → Mute alert\n");
        g_muted = 1;
        beep_off();
        break;

    case VOICE_CMD_UNMUTE_ALERT: /* 0x04 取消静音 */
        printf("[VOICE] → Unmute alert\n");
        g_muted = 0;
        break;

    /* ---- 查询命令 (应答帧: AA 55 <msgno> <params> 55 AA) ---- */
    case VOICE_CMD_QUERY_TEMP: { /* 0x05 车内温度 */
        printf("[VOICE] → Query temperature\n");
        DHT11_Data_TypeDef dht_data;
        if (dht11_read_data(&dht_data) == ERRCODE_SUCC) {
            usart_su_TXpacket[0] = 0xAA;
            usart_su_TXpacket[1] = 0x55;
            usart_su_TXpacket[2] = VOICE_MSG_TEMP;          /* msgno=1 */
            usart_su_TXpacket[3] = dht_data.temp_high8bit;  /* 整数 */
            usart_su_TXpacket[4] = 0x00;
            usart_su_TXpacket[5] = 0x00;
            usart_su_TXpacket[6] = 0x00;
            usart_su_TXpacket[7] = dht_data.temp_low8bit;   /* 小数 */
            usart_su_TXpacket[8] = 0x00;
            usart_su_TXpacket[9] = 0x00;
            usart_su_TXpacket[10] = 0x00;
            usart_su_TXpacket[11] = 0x55;
            usart_su_TXpacket[12] = 0xAA;
            uapi_uart_write(UART_BUS_2, usart_su_TXpacket, 13, 0);
            printf("[VOICE] Sent temp: %d.%d C\n",
                   dht_data.temp_high8bit, dht_data.temp_low8bit);
        } else {
            printf("[VOICE] DHT11 read failed, cannot answer temp query\n");
        }
        break;
    }
    case VOICE_CMD_QUERY_HUMI: { /* 0x06 车内湿度 */
        printf("[VOICE] → Query humidity\n");
        DHT11_Data_TypeDef dht_data;
        if (dht11_read_data(&dht_data) == ERRCODE_SUCC) {
            usart_su_TXpacket[0] = 0xAA;
            usart_su_TXpacket[1] = 0x55;
            usart_su_TXpacket[2] = VOICE_MSG_HUMI;
            usart_su_TXpacket[3] = dht_data.humi_high8bit;
            usart_su_TXpacket[4] = 0x00;
            usart_su_TXpacket[5] = 0x00;
            usart_su_TXpacket[6] = 0x00;
            usart_su_TXpacket[7] = dht_data.humi_low8bit;
            usart_su_TXpacket[8] = 0x00;
            usart_su_TXpacket[9] = 0x00;
            usart_su_TXpacket[10] = 0x00;
            usart_su_TXpacket[11] = 0x55;
            usart_su_TXpacket[12] = 0xAA;
            uapi_uart_write(UART_BUS_2, usart_su_TXpacket, 13, 0);
            printf("[VOICE] Sent humi: %d.%d %%\n",
                   dht_data.humi_high8bit, dht_data.humi_low8bit);
        } else {
            printf("[VOICE] DHT11 read failed, cannot answer humi query\n");
        }
        break;
    }
    case VOICE_CMD_QUERY_HR:   /* 0x07 心率 — MAX30102已删除 */
        printf("[VOICE] → Query heart rate (unavailable)\n");
        break;

    case VOICE_CMD_QUERY_SPO2: /* 0x08 血氧 — MAX30102已删除 */
        printf("[VOICE] → Query SpO2 (unavailable)\n");
        break;
    case VOICE_CMD_QUERY_SCORE: { /* 0x09 驾驶评分 */
        printf("[VOICE] → Query score\n");
        driving_score_t s = engine_get_score();
        usart_su_TXpacket[0] = 0xAA;
        usart_su_TXpacket[1] = 0x55;
        usart_su_TXpacket[2] = VOICE_MSG_SCORE;             /* msgno=5 */
        usart_su_TXpacket[3] = s.trip_score & 0xFF;
        usart_su_TXpacket[4] = (s.trip_score >> 8) & 0xFF;
        usart_su_TXpacket[5] = 0x00;
        usart_su_TXpacket[6] = 0x00;
        usart_su_TXpacket[7] = 0x55;
        usart_su_TXpacket[8] = 0xAA;
        uapi_uart_write(UART_BUS_2, usart_su_TXpacket, 9, 0);
        printf("[VOICE] Sent score: %d/100\n", s.trip_score);
        break;
    }
    case VOICE_CMD_QUERY_BATTERY: { /* 0x0A 电池电量 */
        printf("[VOICE] → Query battery\n");
        battery_data_t bat;
        battery_read(&bat);
        usart_su_TXpacket[0] = 0xAA;
        usart_su_TXpacket[1] = 0x55;
        usart_su_TXpacket[2] = VOICE_MSG_BATTERY;           /* msgno=6 */
        usart_su_TXpacket[3] = bat.voltage_mv & 0xFF;       /* 电压低字节 */
        usart_su_TXpacket[4] = (bat.voltage_mv >> 8) & 0xFF;
        usart_su_TXpacket[5] = 0x00;
        usart_su_TXpacket[6] = 0x00;
        usart_su_TXpacket[7] = bat.percent & 0xFF;          /* 电量低字节 */
        usart_su_TXpacket[8] = (bat.percent >> 8) & 0xFF;
        usart_su_TXpacket[9] = 0x00;
        usart_su_TXpacket[10] = 0x00;
        usart_su_TXpacket[11] = 0x55;
        usart_su_TXpacket[12] = 0xAA;
        uapi_uart_write(UART_BUS_2, usart_su_TXpacket, 13, 0);
        printf("[VOICE] Sent battery: %umV %u%%\n", bat.voltage_mv, bat.percent);
        break;
    }
    case VOICE_CMD_QUERY_RISK: { /* 0x0B 安全状态 */
        printf("[VOICE] → Query risk\n");
        int risk = engine_get_risk();
        usart_su_TXpacket[0] = 0xAA;
        usart_su_TXpacket[1] = 0x55;
        usart_su_TXpacket[2] = VOICE_MSG_RISK;             /* msgno=7 */
        usart_su_TXpacket[3] = risk & 0xFF;
        usart_su_TXpacket[4] = (risk >> 8) & 0xFF;
        usart_su_TXpacket[5] = 0x00;
        usart_su_TXpacket[6] = 0x00;
        usart_su_TXpacket[7] = 0x55;
        usart_su_TXpacket[8] = 0xAA;
        uapi_uart_write(UART_BUS_2, usart_su_TXpacket, 9, 0);
        printf("[VOICE] Sent risk: L%d\n", risk);
        break;
    }

    default:
        printf("[VOICE] Unknown cmd: 0x%02X\n", cmd);
        break;
    }
}

/* ==================== UART中断回调 (19-voice 原版) ==================== */
#if (UART_INT_MODE)
void uart_read_handler(const void *buffer, uint16_t length, bool error)
{
    unused(error);
    if (buffer == NULL || length == 0 || length > UART_RECV_SIZE) {
        return;
    }
    memcpy(uart_recv, buffer, length);
    uart_rx_flag = 1;
}
#endif

/* ==================== 语音任务 (19-voice 原版) ==================== */
void *uart_voice_task(const char *arg)
{
    unused(arg);

#if (UART_INT_MODE)
    if (uapi_uart_register_rx_callback(UART_BUS_2,
                                       UART_RX_CONDITION_MASK_IDLE,
                                       1,
                                       uart_read_handler) != ERRCODE_SUCC) {
        printf("[VOICE] UART rx callback register failed!\n");
    }
#endif

    while (1) {
#if (UART_INT_MODE)
        while (!uart_rx_flag) {
            osal_msleep(DELAY_TIME_MS);
        }
        uart_rx_flag = 0;
        voice_analysis(uart_recv);
        memset(uart_recv, 0, UART_RECV_SIZE);
#else
        /* 轮询模式 (19-voice默认) */
        uint16_t read_len = uapi_uart_read(UART_BUS_2, uart_recv, UART_RECV_SIZE, 100);
        if (read_len > 0) {
            voice_analysis(uart_recv);
            memset(uart_recv, 0, UART_RECV_SIZE);
        }
        osal_msleep(10);
#endif
    }
    return NULL;
}

/* ==================== 26特有: 静音状态查询 ==================== */
int voice_is_muted(void) { return g_muted; }
