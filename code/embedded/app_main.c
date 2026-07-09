/*
 * AIoT车载安全监测系统 — 主程序入口
 * 平台: WS63 (RISC-V 32bit) + LiteOS
 *
 * 传感器清单:
 *   GPIO: DHT11@GPIO4(温湿度) ADC: LDR@CH5(光照)
 *   UART2: Voice(SU-03T)@115200(GPIO07/08)
 *   I2C1: OLED@0x3C (GPIO15/SCL GPIO16/SDA)
 *
 * 执行器:
 *   LED: 红=GPIO3 绿=GPIO9 黄=GPIO12  蜂鸣器=GPIO0  舵机=GPIO2
 *   电机: TB6612 GPIO1(PWM) GPIO10(AIN1) GPIO11(AIN2)  WS2812B=GPIO5
 *   (LED绿/黄已从GPIO11/10移开, 避免与TB6612 AIN2/AIN1冲突)
 *
 * 任务架构:
 *   AppInit → 硬件+通信初始化(一次性)
 *   SensorTask → 50ms周期采集全部传感器
 *   EngineTask → 50ms周期判定BR-01/05/06/08
 *   DisplayTask → 1s OLED + 10s MQTT上报
 *   MqttTask → 后台MQTT收发
 *
 * 修改记录:
 *   2025-07-04  删除 MAX30102(心率) 和 毫米波雷达
 */

#include "app_main.h"
#include "lwip/netifapi.h"
#include "wifi_hotspot.h"
#include "wifi_hotspot_config.h"
#include "stdlib.h"
#include "uart.h"
#include "soc_osal.h"
#include "app_init.h"
#include "cmsis_os2.h"
#include "wifi_device.h"
#include "lwip/sockets.h"
#include "pinctrl.h"
#include "gpio.h"
#include "i2c.h"

#include "wifi/wifi_connect.h"
#include "led/led.h"
#include "beep/beep.h"
#include "motor/motor.h"
#include "rgb/rgb.h"
#include "dht11/dht11.h"
#include "adc/ldr.h"
#include "bmx055/bmx055.h"
#include "gps/gps.h"
#include "dms/dms.h"
#include "mqtt/mqtt.h"
#include "engine/engine.h"
#include "voice/voice.h"
#include "battery/battery.h"
#include "rtc/rtc.h"
#include "oled/oled.h"

/* ==================== 全局传感器数据(任务间共享) ==================== */
static DHT11_Data_TypeDef g_dht11;
static imu_data_t  g_imu;
static uint16_t g_ldr  = 0;
static gps_data_t        g_gps;
static dms_data_t        g_dms;
static int g_sensor_ok = 1;
static int g_dht11_valid = 0;    /* DHT11 数据是否有效 */
static int g_risk_peak = 0;  /* 记录10s周期内最高风险等级 */

/* ==================== 传感器仿真数据生成 (仅IMU/GPS/电池, DHT11/LDR走真实硬件) ==================== */
#ifdef ENABLE_SENSOR_SIMULATION
#include <math.h>
static int g_sim_tick = 0;

static void sim_generate_imu_gps(void)
{
    g_sim_tick++;
    float t = g_sim_tick * 0.05f;  /* 50ms per tick → seconds */

    /* ── BMX055 IMU: 模拟车辆行驶 ── */
    float accel_noise = 0.3f * sinf(t * 1.7f);
    g_imu.ax = accel_noise;
    g_imu.ay = 0.2f * cosf(t * 2.1f);
    g_imu.az = 9.8f;

    /* 每隔30秒一次急刹/急加速 (触发BR-05扣分) */
    if (g_sim_tick % 600 == 0)   g_imu.ax = -5.0f;
    if (g_sim_tick % 600 == 100) g_imu.ax = 4.5f;

    /* 角速度 */
    g_imu.gx = 1.5f * sinf(t * 0.8f);
    g_imu.gy = 0.8f * cosf(t * 0.6f);
    g_imu.gz = 10.0f * sinf(t * 0.3f);

    /* 每隔45秒急转弯 */
    if (g_sim_tick % 900 == 50) g_imu.gz = 150.0f;

    /* 每隔60秒碰撞 (BR-06) */
    if (g_sim_tick % 1200 == 0) {
        g_imu.ax = 78.0f;
        printf("[SIM] *** Fake collision event! ***\n");
    }

    /* ── GPS: 模拟沿路线移动 ── */
    static float sim_lat = 41.8000f, sim_lon = 123.4300f;
    sim_lat += 0.0002f * cosf(t * 0.1f);
    sim_lon += 0.0003f * sinf(t * 0.1f);
    g_gps.latitude  = sim_lat;
    g_gps.longitude = sim_lon;
    g_gps.fix       = 1;

    /* ── DMS: 模拟驾驶员状态 (大部分时间正常, 偶尔疲劳/分心) ── */
    g_dms.perclos       = 0.08f + 0.05f * sinf(t * 0.07f);   /* 正常眨眼 */
    g_dms.yawn_count    = 0;
    g_dms.phone_use     = 0;
    g_dms.hat_on        = 0;
    g_dms.pc_fatigue_lvl = 0;
    g_dms.valid         = 1;

    /* 每隔120秒模拟一次轻度疲劳 (PERCLOS=0.35 → BR-01 L2) */
    if (g_sim_tick % 2400 == 100) {
        g_dms.perclos = 0.35f;
        g_dms.yawn_count = 3;
        g_dms.pc_fatigue_lvl = 1;
        printf("[SIM] *** Fake fatigue L2! ***\n");
    }
    /* 每隔300秒模拟一次接打电话 (BR-01 分心) */
    if (g_sim_tick % 6000 == 200) {
        g_dms.phone_use = 1;
        g_dms.perclos = 0.12f;
        printf("[SIM] *** Fake phone distraction! ***\n");
    }

    g_sensor_ok = 1;

    if (g_sim_tick % 200 == 0) {
        printf("[SIM] tick=%d IMU(ax=%.1f,ay=%.1f,az=%.1f gz=%.1f) "
               "GPS(%.4f,%.4f) PERCLOS=%.2f\n",
               g_sim_tick, g_imu.ax, g_imu.ay, g_imu.az, g_imu.gz,
               sim_lat, sim_lon, g_dms.perclos);
    }
}
#endif /* ENABLE_SENSOR_SIMULATION */

/* ==================== 传感器采集任务(50ms周期) ==================== */
static void *sensor_task(const char *arg)
{
    unused(arg);
    osal_msleep(1000);
    printf("[SENSOR] Task started\n");

    int loop_cnt = 0;
#ifndef ENABLE_SENSOR_SIMULATION
    int imu_fail_cnt = 0;
#endif
    int dht11_fail_cnt = 0;

    while (1) {
        /* ── DHT11: 始终走真实硬件 ── */
        if (dht11_read_data(&g_dht11) == ERRCODE_SUCC) {
            g_dht11_valid = 1;
        } else {
            dht11_fail_cnt++;
            g_dht11_valid = 0;
        }

        /* ── LDR: 始终走真实硬件 ── */
        g_ldr = get_adc_value();

#ifdef ENABLE_SENSOR_SIMULATION
        /* ── 仿真: IMU/GPS 假数据 ── */
        sim_generate_imu_gps();
#else
        /* ── 真实: BMX055 IMU ── */
        if (bmx055_read_all(&g_imu) != 0) {
            imu_fail_cnt++;
        } else {
            imu_fail_cnt = 0;
            g_sensor_ok = 1;
        }
        if (imu_fail_cnt >= 100) {
            g_sensor_ok = 0;
        }
        /* GPS/DMS 已禁用 */
        /* gps_read(&g_gps); */
        /* dms_read(&g_dms); */
#endif /* ENABLE_SENSOR_SIMULATION */

        /* 每200次(10秒)输出传感器健康摘要 */
        if (++loop_cnt >= 200) {
            if (dht11_fail_cnt > 0) {
                printf("[SENSOR] Health: DHT11 fail=%d/200 LDR=%d%%\n",
                       dht11_fail_cnt, g_ldr);
            }
            dht11_fail_cnt = 0;
            loop_cnt = 0;
        }
        osal_msleep(TASK_DELAY_MS);
    }
    return NULL;
}

/* ==================== 判定引擎任务(50ms, ≤500ms端到端) ==================== */
static void *engine_task(const char *arg)
{
    unused(arg);
    osal_msleep(2000);
    printf("[ENGINE] Task started\n");
    beep_off();  /* 强制静音, 防止其他组件误触发 */
    int beep_cooldown = 0;
    while (1) {
        engine_tick(&g_imu, &g_dms, &g_gps,
                    g_dht11.temperature, g_dht11.humidity,
                    g_ldr, g_sensor_ok);  /* vehicle_state 复用为 IMU 健康标记 */

        int risk = engine_get_risk();
        if (risk != 0) printf("[ENGINE] risk=%d cooldown=%d\n", risk, beep_cooldown);
        if (risk > g_risk_peak) g_risk_peak = risk;
        if (risk > RISK_NONE) {
            led_set_risk_level(risk);
            rgb_set_hmi(risk);
            if (beep_cooldown <= 0 && !voice_is_muted()) {
                if (risk == RISK_L2_WARN) {
                    beep_short();
                    beep_cooldown = 20;
                } else if (risk == RISK_L3_CRIT) {
                    beep_alarm();
                    beep_cooldown = 40;
                    /* L3立即上报告警 (含GPS坐标) */
                    char detail[128];
                    if (g_gps.fix > 0) {
                        /* 处理负坐标: 南半球/西半球时符号在整数部分 */
                        int lat_i = (int)g_gps.latitude;
                        int lat_f = (int)((g_gps.latitude - lat_i) * 10000);
                        if (lat_f < 0) lat_f = -lat_f;
                        int lon_i = (int)g_gps.longitude;
                        int lon_f = (int)((g_gps.longitude - lon_i) * 10000);
                        if (lon_f < 0) lon_f = -lon_f;
                        sprintf(detail, "SOS:%d.%04d,%d.%04d",
                                lat_i, lat_f, lon_i, lon_f);
                    } else
                        strcpy(detail, "SOS:no GPS fix");
                    mqtt_report_alert(risk, 0, detail);
                }
            }
        } else {
            beep_cooldown = 0;  /* 风险解除, 立即允许下次告警 */
        }
        if (beep_cooldown > 0) beep_cooldown--;
        osal_msleep(TASK_DELAY_MS);
    }
    return NULL;
}

/* ==================== 显示+上报任务(1s周期) ==================== */
static void *display_task(const char *arg)
{
    unused(arg);
    osal_msleep(3000);
    printf("[DISPLAY] Task started\n");
    while (1) {
        driving_score_t s = engine_get_score();
        int risk = g_risk_peak;

        static int cnt = 0;
        if (++cnt >= 10) {
            cnt = 0;
            g_risk_peak = 0;
            /* 读取电池数据 */
            battery_data_t bat;
#ifdef ENABLE_SENSOR_SIMULATION
            /* 混合模式: 电池用模拟值 */
            bat.voltage_mv = 3850 + (g_sim_tick % 200);
            bat.percent    = 82;
            bat.low_power  = 0;
#else
            battery_read(&bat);
#endif
            /* 全量上报 MQTT (含IMU+电池, 对齐华为云物模型26属性) */
            mqtt_report_all(g_dht11.temperature, g_dht11.humidity, g_ldr,
                           &g_gps, &g_imu, &bat, &g_dms, risk, &s);
            printf("[REPORT] JSON sent: T=%d.%d H=%d.%d Risk=%d Score=%d Bat=%umV\n",
                   (int)g_dht11.temperature,
                   (int)((g_dht11.temperature - (int)g_dht11.temperature) * 10),
                   (int)g_dht11.humidity,
                   (int)((g_dht11.humidity - (int)g_dht11.humidity) * 10),
                   risk, s.trip_score, bat.voltage_mv);
            /* OLED 刷新显示 */
            bsp_oled_Clear();
            char line[22];
            int t_i = (int)g_dht11.temperature;
            int t_f = (int)((g_dht11.temperature - t_i) * 10);
            if (t_f < 0) t_f = -t_f;
            int h_i = (int)g_dht11.humidity;
            int h_f = (int)((g_dht11.humidity - h_i) * 10);
            if (h_f < 0) h_f = -h_f;
            sprintf(line, "Score:%-3d Risk:L%d", s.trip_score, risk);
            bsp_oled_Printf(0, 0, line);
            sprintf(line, "T:%d.%dC H:%d.%d%%", t_i, t_f, h_i, h_f);
            bsp_oled_Printf(0, 16, line);
            sprintf(line, "Lux:%-3d%% Bat:%umV", g_ldr, bat.voltage_mv);
            bsp_oled_Printf(0, 32, line);
        }
        osal_msleep(1000);
    }
    return NULL;
}

/* ==================== 硬件初始化 ==================== */
static void hardware_init(void)
{
    /* 执行器 (始终初始化) */
    led_init(); beep_init(); motor_init(); rgb_init();

    /* I2C1: OLED + 传感器共用 */
    uapi_pin_set_mode(GPIO_15, PIN_MODE_2);
    uapi_pin_set_mode(GPIO_16, PIN_MODE_2);
    uapi_pin_set_pull(GPIO_15, PIN_PULL_TYPE_UP);
    uapi_pin_set_pull(GPIO_16, PIN_PULL_TYPE_UP);
    errcode_t i2c_ret = uapi_i2c_master_init(1, 400000, 0x0);
    printf("[HW] I2C1 init ret=0x%X (0=OK)\n", i2c_ret);

    /* 非I2C传感器 (始终初始化) */
    dht11_init();
    adc_init();

#ifdef ENABLE_SENSOR_SIMULATION
    /* 仿真模式: 跳过 BMX055/Battery/RTC (用模拟数据) */
    printf("[HW] *** SIM HYBRID: DHT11+LDR=real, IMU+GPS+Battery=sim ***\n");
#else
    /* I2C传感器 */
    bmx055_init();
    battery_init();
    rtc_init();
#endif

    /* OLED 显示 */
    oled_init();

    /* 引擎 */
    engine_init();

    /* 语音: UART2 GPIO07/08 */
    uart_gpio_init();
    uart_init_config();

    g_sensor_ok = 1;
    printf("[HW] Hardware initialized\n");
}

/* ==================== 通信初始化 ==================== */
static void comm_init(void)
{
    wifi_set_ssid_pwd(WIFI_SSID, WIFI_PWD);
    wifi_connect();
    mqtt_set_server(HUAWEI_IOT_HOST, HUAWEI_IOT_PORT);
    mqtt_set_device_id(HUAWEI_DEVICE_ID);
    mqtt_set_credentials(HUAWEI_USERNAME, HUAWEI_PASSWORD);
    mqtt_task_start();
    printf("[COMM] MQTT connecting to Huawei IoTDA...\n");
}

static void *appmain_start(const char *arg)
{
    unused(arg);
    hardware_init();
    comm_init();
    return NULL;
}

static void app_main(void)
{
    printf("\n========================================\n");
    printf("  AIoT Vehicle Safety Monitoring System\n");
    printf("  WS63 + RISC-V + LiteOS + Huawei Cloud\n");
    printf("  BR-01 DMS BR-05 IMU BR-06 IMU+GPS BR-08 Fault\n");
    printf("========================================\n\n");

    osal_kthread_lock();
    osal_task *t1 = osal_kthread_create((osal_kthread_handler)appmain_start, 0, "AppInit", 0x1000);
    osal_kthread_set_priority(t1, 10);
    osal_task *t2 = osal_kthread_create((osal_kthread_handler)sensor_task, 0, "SensorTask", TASK_STACK_SENSOR);
    osal_kthread_set_priority(t2, TASK_PRIO_NORMAL);
    osal_task *t3 = osal_kthread_create((osal_kthread_handler)engine_task, 0, "EngineTask", TASK_STACK_ENGINE);
    osal_kthread_set_priority(t3, TASK_PRIO_NORMAL);
    osal_task *t4 = osal_kthread_create((osal_kthread_handler)display_task, 0, "DisplayTask", 0x1000);
    osal_kthread_set_priority(t4, TASK_PRIO_NORMAL);
    osal_task *t5 = osal_kthread_create((osal_kthread_handler)uart_voice_task, 0, "UartVoiceTask", 0x1000);
    osal_kthread_set_priority(t5, 28);
    osal_kthread_unlock();
}

app_run(app_main);
