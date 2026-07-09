/*
 * GPS NMEA0183 驱动 — 19-GPS代码风格, UART1空闲中断收帧
 * 引脚: TX=GPIO03 RX=GPIO02, 9600 8N1
 * 解析: $GPGLL $GNGLL $GPGGA
 */

#include "gps.h"
#include "pinctrl.h"
#include "uart.h"
#include "soc_osal.h"
#include "app_init.h"
#include "cmsis_os2.h"
#include "gpio.h"
#include "string.h"
#include "stdio.h"
#include "stdlib.h"
#include "math.h"

#define GPS_BUF_SIZE    256
#define UART_INT_MODE   1

/* ========== 全局变量(中断回调写入, 其他任务只读) ========== */
float g_gps_latitude = 0.0f;
float g_gps_longitude = 0.0f;
uint8_t g_gps_fix_valid = 0;

static char g_gps_buf[GPS_BUF_SIZE] = {0};
static uint8_t g_gps_buf_idx = 0;
static uint8_t g_gps_ready = 0;
#if (UART_INT_MODE)
static uint8_t g_uart_rx_flag = 0;
#endif

/* ========== GPIO初始化: TX=GPIO03 RX=GPIO02 (UART1) ========== */
void gps_uart_gpio_init(void)
{
    uapi_pin_set_mode(GPIO_03, PIN_MODE_1);
    uapi_pin_set_mode(GPIO_02, PIN_MODE_1);
    uapi_gpio_set_dir(GPIO_03, GPIO_DIRECTION_OUTPUT);
    uapi_gpio_set_dir(GPIO_02, GPIO_DIRECTION_INPUT);
}

/* ========== UART初始化: UART1 9600 8N1 ========== */
void gps_uart_init_config(void)
{
    uart_attr_t attr = {
        .baud_rate = 9600,
        .data_bits = UART_DATA_BIT_8,
        .stop_bits = UART_STOP_BIT_1,
        .parity = UART_PARITY_NONE
    };
    uart_pin_config_t pin_cfg = {
        .tx_pin = S_MGPIO0, .rx_pin = S_MGPIO1,
        .cts_pin = PIN_NONE, .rts_pin = PIN_NONE
    };
    uart_buffer_config_t buf_cfg = {
        .rx_buffer = (uint8_t *)g_gps_buf,
        .rx_buffer_size = GPS_BUF_SIZE
    };

    uapi_uart_deinit(UART_BUS_1);
    int ret = uapi_uart_init(UART_BUS_1, &pin_cfg, &attr, NULL, &buf_cfg);
    if (ret != 0) printf("GPS UART init failed! ret=%02x\n", ret);
}

/* ========== 度分→十进制度 (19-GPS原版) ========== */
static float convert_degmin_to_dec(float degMin)
{
    int deg = (int)(degMin / 100);
    float min = degMin - deg * 100.0f;
    return deg + min / 60.0f;
}

/* ========== 安全atof ========== */
static float safe_atof(char *str)
{
    if (str == NULL || strlen(str) == 0 || *str == ',') return 0.0f;
    return atof(str);
}

/* ========== NMEA解析: $GPGLL / $GNGLL / $GPGGA ========== */
static void parse_gps(char *buffer)
{
    char *token, *saveptr;
    int fi = 0;
    uint8_t has_new = 0;
    float t_lat = 0.0f, t_lon = 0.0f;
    char t_lat_d = 'N', t_lon_d = 'E';
    uint8_t t_fix = 0;

    if (buffer == NULL || strlen(buffer) < 10) return;

    uint8_t isGLL = (strstr(buffer, "$GPGLL") != NULL || strstr(buffer, "$GNGLL") != NULL);
    uint8_t isGGA = (strstr(buffer, "$GPGGA") != NULL);
    uint8_t isGSV = (strstr(buffer, "$GPGSV") != NULL);
    if (isGSV) return;
    if (!isGLL && !isGGA) return;

    token = strtok_r(buffer, ",", &saveptr);
    while (token) {
        if (isGLL) {
            switch (fi) {
                case 1: t_lat = convert_degmin_to_dec(safe_atof(token)); break;
                case 2: if (*token) { t_lat_d = token[0]; if (t_lat_d == 'S') t_lat = -t_lat; } break;
                case 3: t_lon = convert_degmin_to_dec(safe_atof(token)); break;
                case 4: if (*token) { t_lon_d = token[0]; if (t_lon_d == 'W') t_lon = -t_lon; } break;
                case 6: if (*token == 'A') { t_fix = 1; has_new = 1; } break;
            }
        }
        if (isGGA) {
            switch (fi) {
                case 2: t_lat = convert_degmin_to_dec(safe_atof(token)); break;
                case 3: if (*token) { t_lat_d = token[0]; if (t_lat_d == 'S') t_lat = -t_lat; } break;
                case 4: t_lon = convert_degmin_to_dec(safe_atof(token)); break;
                case 5: if (*token) { t_lon_d = token[0]; if (t_lon_d == 'W') t_lon = -t_lon; } break;
                case 6: if (safe_atof(token) > 0) { t_fix = 1; has_new = 1; } break;
            }
        }
        token = strtok_r(NULL, ",", &saveptr);
        fi++;
    }

    if (has_new) {
        g_gps_latitude = t_lat;
        g_gps_longitude = t_lon;
        g_gps_fix_valid = t_fix;
    }
}

/* ========== UART空闲中断回调 (19-GPS原版) ========== */
#if (UART_INT_MODE)
static void gps_uart_rx_cb(const void *buffer, uint16_t length, bool error)
{
    unused(error);
    if (buffer == NULL || length == 0) return;

    uint8_t *p = (uint8_t *)buffer;
    for (uint16_t i = 0; i < length; i++) {
        char c = p[i];
        if ((c >= 32 && c <= 126) || c == '\r' || c == '\n') {
            if (g_gps_buf_idx >= GPS_BUF_SIZE - 1) {
                g_gps_buf_idx = 0;
                memset(g_gps_buf, 0, GPS_BUF_SIZE);
                continue;
            }
            if (c == '\r') c = '\0';
            g_gps_buf[g_gps_buf_idx++] = c;
            if (c == '\n') {
                g_gps_buf[g_gps_buf_idx - 1] = '\0';
                g_gps_ready = 1;
                g_gps_buf_idx = 0;
            }
        }
    }
    static int cb_cnt = 0;
    if (++cb_cnt % 10 == 0) printf("[GPS] rx_cb called %d times, len=%d\n", cb_cnt, length);
    g_uart_rx_flag = 1;
}
#endif

/* ========== GPS采集主任务 (19-GPS原版) ========== */
void *uart_gps_task(const char *arg)
{
    unused(arg);

#if (UART_INT_MODE)
    if (uapi_uart_register_rx_callback(UART_BUS_1,
                                       UART_RX_CONDITION_MASK_IDLE,
                                       1,
                                       gps_uart_rx_cb) != ERRCODE_SUCC)
    {
        printf("GPS UART rx callback register failed!\n");
    }
#endif

    while (1) {
#if (UART_INT_MODE)
        while (!g_uart_rx_flag) osal_msleep(10);
        g_uart_rx_flag = 0;

        if (g_gps_ready == 1) {
            if (g_gps_buf[0] == '$') {
                printf("[GPS] NMEA:%s\n", g_gps_buf);
                parse_gps(g_gps_buf);
            } else {
                printf("[GPS] bad frame:%02x\n", g_gps_buf[0]);
            }
            g_gps_ready = 0;
            memset(g_gps_buf, 0, GPS_BUF_SIZE);
        }
#else
        uint16_t rlen = uapi_uart_read(UART_BUS_1, (uint8_t *)g_gps_buf, GPS_BUF_SIZE, 100);
        if (rlen > 0) {
            if (g_gps_buf[0] == '$') parse_gps(g_gps_buf);
            memset(g_gps_buf, 0, GPS_BUF_SIZE);
        }
        osal_msleep(10);
#endif
    }
    return NULL;
}

/* ========== 对外接口(供app_main调用) ========== */
int gps_read(gps_data_t *gps)
{
    gps->latitude = g_gps_latitude;
    gps->longitude = g_gps_longitude;
    gps->fix = g_gps_fix_valid;
    gps->speed_kmh = 0.0f;
    gps->hour = gps->min = gps->sec = 0;
    return g_gps_fix_valid;
}

int gps_has_fix(void) { return g_gps_fix_valid > 0; }
