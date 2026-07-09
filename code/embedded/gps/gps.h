/*
 * GPS驱动 (UART2 + GPIO07/08 + 中断收帧)
 * 参考: 19-GPS, NMEA0183协议, 解析$GPGLL/$GNGLL/$GPGGA
 * UART2, 9600bps, 8N1, 空闲中断模式
 */

#ifndef __GPS_H__
#define __GPS_H__

#include "common_def.h"
#include "uart.h"

/* GPS解析结果 */
typedef struct {
    float  latitude;
    float  longitude;
    float  speed_kmh;
    int    fix;
    int    hour, min, sec;
    int    satellites;
} gps_data_t;

/* 全局变量(中断回调更新, 其他任务读取) */
extern float g_gps_latitude;
extern float g_gps_longitude;
extern uint8_t g_gps_fix_valid;

void gps_uart_gpio_init(void);
void gps_uart_init_config(void);
void *uart_gps_task(const char *arg);
int  gps_read(gps_data_t *gps);
int  gps_has_fix(void);

#endif /* __GPS_H__ */
