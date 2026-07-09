/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：app_main.h
 * 功能描述：主头文件 — 华为云IoT/WiFi/任务栈配置
 *
 * 使用前修改:
 *   1. HUAWEI_DEVICE_ID/HUAWEI_PASSWORD: 华为云IoTDA设备三元组
 *   2. WIFI_SSID/WIFI_PWD: 2.4G WiFi热点
 *   3. 任务栈大小可按实际内存调整
 */

#ifndef __APP_MAIN_H__
#define __APP_MAIN_H__

#include "soc_osal.h"
#include "app_init.h"
#include "cmsis_os2.h"

/* ==================== 华为云IoTDA配置 ==================== */
/* 设备接入地址: IoTDA控制台 → 设备详情 → 接入地址(域名) */
#define HUAWEI_IOT_HOST     "99d7c8973d.st1.iotda-device.cn-north-4.myhuaweicloud.com"
#define HUAWEI_IOT_PORT     8883  /* MQTTS — MQTT over TLS */

/* 设备三元组: IoTDA控制台 → 设备详情 */
#define HUAWEI_DEVICE_ID    "6a44f1047f2e6c302f80df85_vehicle_safety_0_0_2026070316"
#define HUAWEI_USERNAME     "6a44f1047f2e6c302f80df85_vehicle_safety"
#define HUAWEI_PASSWORD     "c5d7642387610d0006781815a9b1f4177bbd1782955004397daa578445cec7bc"

/* ==================== WiFi配置 ==================== */
/* 仅支持2.4GHz, 不支持5GHz(WS63硬件限制) */
#define WIFI_SSID           "123"
#define WIFI_PWD            "1234567890"

/* ==================== LiteOS任务配置 ==================== */
#define TASK_STACK_SENSOR   0x1000   /* 4KB  — 传感器采集任务 */
#define TASK_STACK_ENGINE   0x1000   /* 4KB  — 判定引擎任务 */
#define TASK_STACK_MQTT     0x2000   /* 8KB  — MQTT通信任务(需较大栈, MQTT库内部malloc) */
#define TASK_PRIO_NORMAL    15       /* 普通优先级(0最高) */
#define TASK_DELAY_MS       50       /* 引擎+传感器tick间隔(20Hz) */

/* ==================== 传感器仿真模式 (混合: DHT11+LDR走真实硬件) ==================== */
/* 取消注释以启用: IMU/GPS/电池用假数据, DHT11温湿度+LDR光照仍走真实传感器 */
#define ENABLE_SENSOR_SIMULATION

#endif /* __APP_MAIN_H__ */
