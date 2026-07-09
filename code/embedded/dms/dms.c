/*
 * DMS驾驶员监测系统 — 接收PC端AI视觉分析结果
 *
 * 数据流: PC摄像头 → AI模型(MediaPipe/YOLO) → USB串口 → WS63 UART2
 *
 * UART配置: UART2, 115200bps, 8N1
 *   与smart_home语音模块共用UART设计模式(参考voice/voice.c)
 *
 * PC端需配套程序(不在本项目范围内):
 *   示例: python dms_sender.py — 读取摄像头, 运行MediaPipe,
 *   检测面部landmark, 计算PERCLOS/哈欠/分心, 通过串口发送结构化数据
 */

#include "dms.h"
#include "uart.h"
#include "pinctrl.h"
#include "soc_osal.h"
#include "stdio.h"
#include "string.h"
#include "stdlib.h"

/* ⚠️ DMS 与 GPS 共用 UART1 — 同时只能启用一个!
 *   如需同时使用 GPS+DMS, 将其中一个移到 UART0 (需改GPIO) */
#define DMS_UART_BUS    1           /* UART1 (与GPS互斥, GPS当前已禁用) */
#define DMS_BAUDRATE    115200
#define DMS_BUF_SIZE    128

static dms_data_t g_dms_data = {0};
static uint8_t g_dms_buf[DMS_BUF_SIZE];

/* 解析PC发来的DMS数据行
 * 格式: "DMS,PERCLOS:0.25,YAWN:2,HAT:0,PHONE:0,LVL:1\n" */
static void parse_dms_line(const char *line)
{
    char buf[DMS_BUF_SIZE];
    strcpy(buf, line);

    /* 找 "PERCLOS:" */
    char *p = strstr(buf, "PERCLOS:");
    if (p) g_dms_data.perclos = atof(p + 8);

    /* 找 "YAWN:" */
    p = strstr(buf, "YAWN:");
    if (p) g_dms_data.yawn_count = atoi(p + 5);

    /* 找 "PHONE:" */
    p = strstr(buf, "PHONE:");
    if (p) g_dms_data.phone_use = atoi(p + 6);

    /* 找 "HAT:" */
    p = strstr(buf, "HAT:");
    if (p) g_dms_data.hat_on = atoi(p + 4);

    /* 找 "LVL:" */
    p = strstr(buf, "LVL:");
    if (p) g_dms_data.pc_fatigue_lvl = atoi(p + 4);

    g_dms_data.valid = 1;
    printf("[DMS] PERCLOS=%.2f YAWN=%d PHONE=%d PC_LVL=%d\n",
           g_dms_data.perclos, g_dms_data.yawn_count,
           g_dms_data.phone_use, g_dms_data.pc_fatigue_lvl);
}

int dms_init(void)
{
    uart_pin_config_t pin_cfg = {
        .tx_pin = S_MGPIO0,   /* UART2 TX (不使用, PC端只发不收) */
        .rx_pin = S_MGPIO1,   /* UART2 RX */
        .cts_pin = PIN_NONE,
        .rts_pin = PIN_NONE
    };
    uart_attr_t attr = {
        .baud_rate = DMS_BAUDRATE,
        .data_bits = UART_DATA_BIT_8,
        .stop_bits = UART_STOP_BIT_1,
        .parity = UART_PARITY_NONE
    };
    uart_buffer_config_t buf_cfg = {
        .rx_buffer = g_dms_buf,
        .rx_buffer_size = DMS_BUF_SIZE
    };

    uapi_uart_init(DMS_UART_BUS, &pin_cfg, &attr, NULL, &buf_cfg);
    memset(&g_dms_data, 0, sizeof(g_dms_data));
    printf("DMS init OK: UART%d %dbps (waiting for PC vision data...)\n", DMS_UART_BUS, DMS_BAUDRATE);
    return 0;
}

int dms_read(dms_data_t *dms)
{
    uint8_t ch;
    static char line[DMS_BUF_SIZE];
    static int idx = 0;

    /* 非阻塞读取UART, 逐字符拼行 */
    while (uapi_uart_read(DMS_UART_BUS, &ch, 1, 0) == 1) {
        if (ch == '\n' || ch == '\r') {
            if (idx > 5 && strncmp(line, "DMS,", 4) == 0) {
                line[idx] = '\0';
                parse_dms_line(line);
            }
            idx = 0;
        } else if (idx < DMS_BUF_SIZE - 1) {
            line[idx++] = ch;
        }
    }

    /* 超过2秒没收到数据, 标记无效 */
    static uint32_t last_valid_tick = 0;
    static uint32_t total_ticks = 0;
    total_ticks++;
    if (g_dms_data.valid) last_valid_tick = total_ticks;
    if (total_ticks - last_valid_tick > 40) g_dms_data.valid = 0; /* 2s=40ticks */

    memcpy(dms, &g_dms_data, sizeof(dms_data_t));
    return g_dms_data.valid ? 0 : -1;
}
