/*
 * MQTT华为云IoTDA客户端
 * JSON上报格式: 25_smart_home 风格 — 所有属性合并为一个大JSON
 */

#include "mqtt.h"
#include "MQTTClient.h"
#include "MQTTClientPersistence.h"
#include "errcode.h"
#include "stdio.h"
#include "string.h"
#include "../wifi/wifi_connect.h"
#include "../bmx055/bmx055.h"
#include "../gps/gps.h"
#include "../battery/battery.h"
#include "../dms/dms.h"

/* ==================== MQTT连接参数 ==================== */
static char g_device_id[64]   = "";
static char g_username[64]    = "";
static char g_password[128]   = "";
static char g_server[128]     = "";
static int  g_port            = 1883;
static char g_cmd_response_id[100] = {0};
static uint8_t g_cmd_flag = 0;
static MQTTClient g_client;
static mqtt_cmd_cb_t g_cmd_cb = NULL;
extern int MQTTClient_init(void);

/* ==================== JSON上报缓冲区 ==================== */
/*
 * 上报JSON格式(25_smart_home风格):
 * {
 *   "services": [{
 *     "service_id": "VehicleSafety",
 *     "properties": {
 *       "temp": xx.x, "humi": xx.x, "lux": xxx,
 *       "ax": x.xx, ... "gz": x.xx,
 *       "lat": xx.xxxx, "lon": xxx.xxxx, "gps_fix": x,
 *       "hr": xxx, "spo2": xx, "resting_hr": xx,
 *       "battery_mv": xxxx, "battery_pct": xx,
 *       "perclos": x.xx, "yawn": x, "phone": x, "pc_lvl": x,
 *       "risk": x, "score": xxx,
 *       "hard_brake": x, "hard_accel": x, "sharp_turn": x
 *     },
 *     "event_time": ""
 *   }]
 * }
 */
/*
 * 上报JSON — 全部属性合为一个对象, 对标华为云 IoTDA 物模型
 *
 * 变量列表 (按sprintf参数顺序):
 *   1  temp        (%.1f °C)      DHT11 温度
 *   2  humi        (%.1f %RH)     DHT11 湿度
 *   3  lux         (%d %%)        LDR 光照
 *   4  lat         (%.4f 度)      GPS 纬度
 *   5  lon         (%.4f 度)      GPS 经度
 *   6  gps_fix     (%d 0/1)       GPS 定位状态
 *   7  ax~az       (%.1f m/s²)    BMX055 加速度
 *   8  gx~gz       (%.1f °/s)     BMX055 角速度
 *   9  perclos     (%.2f 0~1)     DMS 眼睑闭合比例
 *  10  yawn        (%d 次)        DMS 哈欠计数
 *  11  phone       (%d 0/1)       DMS 接打电话标记
 *  12  pc_lvl      (%d 0~2)       DMS PC端预判疲劳等级
 *  13  risk        (%d 0~3)       Engine 风险等级
 *  14  score       (%d 0~100)     Engine 驾驶评分
 *  15  hard_brake  (%d 次)        Engine 急刹计数
 *  16  hard_accel  (%d 次)        Engine 急加速计数
 *  17  sharp_turn  (%d 次)        Engine 急转弯计数
 *  18  battery_mv  (%d mV)        CW2015 电池电压
 *  19  battery_pct (%d %%)        CW2015 电量百分比
 */
/* SDK安全库禁用%%f, 全部用整数格式避免vsnprintf_s报错 */
#define JSON_FORMAT \
    "{\"services\":[{" \
        "\"service_id\":\"VehicleSafety\"," \
        "\"properties\":{" \
            "\"temp\":%d.%d,\"humi\":%d.%d,\"lux\":%d," \
            "\"lat\":%d.%04d,\"lon\":%d.%04d,\"gps_fix\":%d," \
            "\"ax\":%d.%d,\"ay\":%d.%d,\"az\":%d.%d," \
            "\"gx\":%d.%d,\"gy\":%d.%d,\"gz\":%d.%d," \
            "\"perclos\":%d.%02d,\"yawn\":%d,\"phone\":%d,\"pc_lvl\":%d," \
            "\"risk\":%d,\"score\":%d," \
            "\"hard_brake\":%d,\"hard_accel\":%d,\"sharp_turn\":%d," \
            "\"battery_mv\":%d,\"battery_pct\":%d" \
        "}," \
        "\"event_time\":\"\"" \
    "}]}"

static char g_json_tree[1024] = {0};

/* ==================== 参数设置 ==================== */
void mqtt_set_device_id(const char *id)     { strcpy(g_device_id, id); }
void mqtt_set_credentials(const char *u, const char *p) { strcpy(g_username, u); strcpy(g_password, p); }
void mqtt_set_server(const char *h, int p)   { strcpy(g_server, h); g_port = p; }
void mqtt_set_cmd_callback(mqtt_cmd_cb_t cb) { g_cmd_cb = cb; }

/* ==================== MQTT回调 ==================== */
static void on_connection_lost(void *ctx, char *cause)
{
    unused(ctx);
    printf("[MQTT] Connection lost: %s\n", cause);
}

static int on_message_arrived(void *ctx, char *topic, int topic_len, MQTTClient_message *msg)
{
    unused(ctx); unused(topic_len);
    printf("[MQTT] Recv: %s -> %s\n", topic, (char *)msg->payload);
    char *eq = strchr(topic, '=');
    if (eq) { strcpy(g_cmd_response_id, eq + 1); g_cmd_flag = 1; }
    if (g_cmd_cb && strstr((char *)msg->payload, "LED")) {
        g_cmd_cb("LED", (char *)msg->payload, "");
    }
    return 1;
}

static void on_delivery(void *ctx, MQTTClient_deliveryToken dt)
{
    unused(ctx); unused(dt);
}

/* ==================== MQTT连接 ==================== */
int mqtt_connect(void)
{
    MQTTClient_connectOptions opts = MQTTClient_connectOptions_initializer;
    int rc;
    MQTTClient_init();
    rc = MQTTClient_create(&g_client, g_server, g_device_id, MQTTCLIENT_PERSISTENCE_NONE, NULL);
    if (rc != MQTTCLIENT_SUCCESS) { printf("[MQTT] Create failed: %d\n", rc); return -1; }
    opts.keepAliveInterval = 120;
    opts.cleansession = 1;
    opts.username = g_username;
    opts.password = g_password;
    MQTTClient_setCallbacks(g_client, NULL, on_connection_lost, on_message_arrived, on_delivery);
    rc = MQTTClient_connect(g_client, &opts);
    if (rc != MQTTCLIENT_SUCCESS) { printf("[MQTT] Connect failed: %d\n", rc); return -1; }

    char topic[128];
    sprintf(topic, MQTT_CMD_TOPIC, g_device_id);
    MQTTClient_subscribe(g_client, topic, 1);
    printf("[MQTT] Connected to Huawei IoTDA\n");
    return 0;
}

/* ==================== MQTT发布 ==================== */
int mqtt_publish(const char *topic, const char *payload)
{
    MQTTClient_message msg = MQTTClient_message_initializer;
    MQTTClient_deliveryToken token;
    msg.payload = (void *)payload;
    msg.payloadlen = strlen(payload);
    msg.qos = 1;
    msg.retained = 0;
    return MQTTClient_publishMessage(g_client, topic, &msg, &token);
}

/* ==================== 全量属性上报 (华为云IoTDA物模型对齐) ==================== */
int mqtt_report_all(float temp, float humi, uint16_t lux,
                    const gps_data_t *gps, const imu_data_t *imu,
                    const battery_data_t *bat,
                    const dms_data_t *dms,
                    int risk, const driving_score_t *ds)
{
    char topic[128];
    sprintf(topic, MQTT_REPORT_TOPIC, g_device_id);

    memset(g_json_tree, 0, sizeof(g_json_tree));

    /* 温湿度: 整数+1位小数 */
    int t_i = (int)temp, t_f = (int)((temp - t_i) * 10);  if(t_f<0) t_f=-t_f;
    int h_i = (int)humi,  h_f = (int)((humi  - h_i)  * 10);  if(h_f<0) h_f=-h_f;

    /* GPS坐标: 整数+4位小数 */
    int la_i=(int)gps->latitude, la_f=((gps->latitude-la_i)*10000);if(la_f<0)la_f=-la_f;
    int lo_i=(int)gps->longitude,lo_f=((gps->longitude-lo_i)*10000);if(lo_f<0)lo_f=-lo_f;

    /* IMU加速度: 整数+1位小数 (m/s²) */
    int axi=(int)imu->ax, axf=(int)((imu->ax-axi)*10);if(axf<0)axf=-axf;
    int ayi=(int)imu->ay, ayf=(int)((imu->ay-ayi)*10);if(ayf<0)ayf=-ayf;
    int azi=(int)imu->az, azf=(int)((imu->az-azi)*10);if(azf<0)azf=-azf;

    /* IMU角速度: 整数+1位小数 (°/s) */
    int gxi=(int)imu->gx, gxf=(int)((imu->gx-gxi)*10);if(gxf<0)gxf=-gxf;
    int gyi=(int)imu->gy, gyf=(int)((imu->gy-gyi)*10);if(gyf<0)gyf=-gyf;
    int gzi=(int)imu->gz, gzf=(int)((imu->gz-gzi)*10);if(gzf<0)gzf=-gzf;

    /* DMS PERCLOS: 整数+2位小数 */
    int pe_i=(int)dms->perclos,pe_f=((dms->perclos-pe_i)*100);if(pe_f<0)pe_f=-pe_f;

    sprintf(g_json_tree, JSON_FORMAT,
            /* temp, humi, lux */
            t_i, t_f, h_i, h_f, lux,
            /* GPS */
            la_i, la_f, lo_i, lo_f, gps->fix,
            /* IMU accel */
            axi, axf, ayi, ayf, azi, azf,
            /* IMU gyro */
            gxi, gxf, gyi, gyf, gzi, gzf,
            /* DMS */
            pe_i, pe_f, dms->yawn_count, dms->phone_use, dms->pc_fatigue_lvl,
            /* engine */
            risk, ds->trip_score,
            /* score details */
            ds->hard_brake_count, ds->hard_accel_count, ds->sharp_turn_count,
            /* battery */
            (int)bat->voltage_mv, (int)bat->percent);

    printf("[MQTT] Report all properties (%d bytes)\n", (int)strlen(g_json_tree));
    return mqtt_publish(topic, g_json_tree);
}

/* ==================== 告警即时上报 ==================== */
int mqtt_report_alert(int level, int type, const char *detail)
{
    char topic[128], payload[512];
    sprintf(topic, MQTT_REPORT_TOPIC, g_device_id);
    sprintf(payload, "{\"services\":[{\"service_id\":\"Alert\","
            "\"properties\":{\"risk_level\":%d,\"alert_type\":%d,\"detail\":\"%s\"},"
            "\"event_time\":\"\"}]}", level, type, detail);
    printf("[MQTT] ALERT L%d type=%d: %s\n", level, type, detail);
    return mqtt_publish(topic, payload);
}

/* ==================== 命令响应 ==================== */
static void mqtt_response_cmd(void)
{
    char topic[128];
    sprintf(topic, MQTT_RESP_TOPIC, g_device_id, g_cmd_response_id);
    mqtt_publish(topic, "{\"result_code\":0,\"response_name\":\"ACK\","
                       "\"paras\":{\"result\":\"success\"}}");
    g_cmd_flag = 0;
}

/* ==================== MQTT主任务 ==================== */
static void *mqtt_main_task(const char *arg)
{
    unused(arg);
    osal_msleep(2000);
    mqtt_connect();
    while (1) {
        /* yield() 是必须的: 收发TCP数据 + 发PINGREQ心跳 + 处理云端命令
         * 不调 → keepAliveInterval(120s) 超时 → 云端断连 */
        MQTTClient_yield();
        if (g_cmd_flag) mqtt_response_cmd();
        osal_msleep(200);
    }
    return NULL;
}

void mqtt_task_start(void)
{
    osal_task *t = osal_kthread_create((osal_kthread_handler)mqtt_main_task, 0, "MqttTask", 0x2000);
    osal_kthread_set_priority(t, 15);
}
