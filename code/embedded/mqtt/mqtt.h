/*
 * MQTT华为云IoTDA客户端头文件
 * 上报格式: 25_smart_home 风格 — 一个大JSON包含全部属性
 */

#ifndef __MQTT_H__
#define __MQTT_H__

#include <stdint.h>
#include "soc_osal.h"
#include "MQTTClient.h"
#include "../gps/gps.h"
#include "../dms/dms.h"
#include "../bmx055/bmx055.h"
#include "../battery/battery.h"
#include "../engine/engine.h"

#define MQTT_REPORT_TOPIC   "$oc/devices/%s/sys/properties/report"
#define MQTT_CMD_TOPIC      "$oc/devices/%s/sys/commands/#"
#define MQTT_RESP_TOPIC     "$oc/devices/%s/sys/commands/response/request_id=%s"

typedef void (*mqtt_cmd_cb_t)(const char *service_id, const char *cmd, const char *paras);

/* 配置 */
void mqtt_set_device_id(const char *id);
void mqtt_set_credentials(const char *user, const char *pass);
void mqtt_set_server(const char *host, int port);
void mqtt_set_cmd_callback(mqtt_cmd_cb_t cb);

/* 连接与任务 */
int  mqtt_connect(void);
void mqtt_task_start(void);

/* ===== 上报接口 ===== */

/* 全量上报: 传感器+引擎+电池数据合并为一个大JSON (建议10s周期) */
int mqtt_report_all(float temp, float humi, uint16_t lux,
                    const gps_data_t *gps, const imu_data_t *imu,
                    const battery_data_t *bat,
                    const dms_data_t *dms,
                    int risk, const driving_score_t *ds);

/* 告警即时上报: 单独上报, 用于BR-02/06等L3事件 */
int mqtt_report_alert(int level, int type, const char *detail);

/* 底层发布(供命令响应用) */
int mqtt_publish(const char *topic, const char *payload);

#endif /* __MQTT_H__ */
