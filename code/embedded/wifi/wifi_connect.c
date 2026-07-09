/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：wifi_connect.c
 * 功能描述：WiFi STA连接 — 扫描→连接→DHCP获取IP
 *
 * 连接流程:
 *   1. 注册WiFi事件回调(扫描状态+连接状态)
 *   2. 等待wifi初始化完成
 *   3. 创建STA接口
 *   4. 扫描可用AP → 匹配SSID → 输入密码 → 连接
 *   5. DHCP获取IP地址
 *   6. 连接成功 → MQTT开始通信
 *
 * 注意: 仅支持2.4GHz(WS63硬件限制), 不支持5GHz
 * 参考: 25_smart_home/wifi/wifi_connect.c (完全一致的实现)
 */

#include "wifi_connect.h"
#include "lwip/netifapi.h"
#include "wifi_hotspot.h"
#include "wifi_hotspot_config.h"
#include "soc_osal.h"
#include "app_init.h"
#include "cmsis_os2.h"
#include "stdio.h"
#include "string.h"
#include "../app_main.h"  /* WIFI_SSID / WIFI_PWD 宏 */

#define WIFI_SCAN_AP_LIMIT             64
#define WIFI_CONN_STATUS_MAX_GET_TIMES 5
#define DHCP_BOUND_STATUS_MAX_GET_TIMES 20
#define WIFI_STA_IP_MAX_GET_TIMES      5

static char g_ssid[33] = "";
static char g_pwd[65]  = "";

void wifi_set_ssid_pwd(const char *ssid, const char *pwd)
{
    strcpy(g_ssid, ssid);
    strcpy(g_pwd, pwd);
}

/* 从扫描结果中匹配目标SSID */
static errcode_t example_get_match_network(const char *expected_ssid,
                                            const char *key,
                                            wifi_sta_config_stru *expected_bss)
{
    uint32_t num = WIFI_SCAN_AP_LIMIT;
    uint32_t scan_len = sizeof(wifi_scan_info_stru) * WIFI_SCAN_AP_LIMIT;
    wifi_scan_info_stru *result = osal_kmalloc(scan_len, OSAL_GFP_ATOMIC);
    if (result == NULL) return ERRCODE_MALLOC;

    memset_s(result, scan_len, 0, scan_len);
    if (wifi_sta_get_scan_info(result, &num) != ERRCODE_SUCC) {
        osal_kfree(result); return ERRCODE_FAIL;
    }
    uint32_t i;
    for (i = 0; i < num; i++) {
        if (strlen(expected_ssid) == strlen(result[i].ssid) &&
            memcmp(expected_ssid, result[i].ssid, strlen(expected_ssid)) == 0) break;
    }
    if (i >= num) { osal_kfree(result); return ERRCODE_FAIL; }

    memcpy_s(expected_bss->ssid, WIFI_MAX_SSID_LEN, result[i].ssid, WIFI_MAX_SSID_LEN);
    memcpy_s(expected_bss->bssid, WIFI_MAC_LEN, result[i].bssid, WIFI_MAC_LEN);
    expected_bss->security_type = result[i].security_type;
    memcpy_s(expected_bss->pre_shared_key, WIFI_MAX_KEY_LEN, key, strlen(key));
    expected_bss->ip_type = DHCP;
    osal_kfree(result);
    return ERRCODE_SUCC;
}

static void wifi_scan_state_changed(int state, int size) {
    unused(state); unused(size);
}
static void wifi_connection_changed(int state, const wifi_linked_info_stru *info, int reason_code) {
    unused(reason_code);
    if (state == WIFI_STATE_AVALIABLE) printf("[WiFi]:%s RSSI:%d\r\n", info->ssid, info->rssi);
}

errcode_t wifi_connect(void)
{
    char ifname[WIFI_IFNAME_MAX_SIZE + 1] = "wlan0";
    wifi_sta_config_stru expected_bss = {0};
    const char *ssid = g_ssid[0] ? g_ssid : WIFI_SSID;
    const char *pwd  = g_pwd[0]  ? g_pwd  : WIFI_PWD;
    struct netif *netif_p = NULL;
    wifi_linked_info_stru wifi_status;
    uint8_t index;

    /* 注册事件回调 */
    wifi_event_stru cb = { .wifi_event_scan_state_changed = wifi_scan_state_changed,
                           .wifi_event_connection_changed = wifi_connection_changed };
    if (wifi_register_event_cb(&cb) != 0) return ERRCODE_FAIL;
    while (wifi_is_wifi_inited() == 0) osDelay(10);
    if (wifi_sta_enable() != ERRCODE_SUCC) return ERRCODE_FAIL;

    /* 扫描→匹配→连接(循环重试直到成功) */
    do {
        osDelay(100); if (wifi_sta_scan() != ERRCODE_SUCC) continue;
        osDelay(300);
        if (example_get_match_network(ssid, pwd, &expected_bss) != ERRCODE_SUCC) continue;
        if (wifi_sta_connect(&expected_bss) != ERRCODE_SUCC) continue;
        for (index = 0; index < WIFI_CONN_STATUS_MAX_GET_TIMES; index++) {
            osDelay(50); memset_s(&wifi_status, sizeof(wifi_status), 0, sizeof(wifi_status));
            if (wifi_sta_get_ap_info(&wifi_status) != ERRCODE_SUCC) continue;
            if (wifi_status.conn_state == WIFI_CONNECTED) break;
        }
        if (wifi_status.conn_state == WIFI_CONNECTED) break;
    } while (1);

    /* DHCP获取IP */
    netif_p = netifapi_netif_find(ifname);
    if (netif_p == NULL) return ERRCODE_FAIL;
    if (netifapi_dhcp_start(netif_p) != ERR_OK) return ERRCODE_FAIL;
    uint8_t i;
    for (i = 0; i < DHCP_BOUND_STATUS_MAX_GET_TIMES; i++) {
        osDelay(50); if (netifapi_dhcp_is_bound(netif_p) == ERR_OK) break;
    }
    for (i = 0; i < WIFI_STA_IP_MAX_GET_TIMES; i++) {
        osDelay(1);
        if (netif_p->ip_addr.u_addr.ip4.addr != 0) {
            printf("STA IP %u.%u.%u.%u connect success.\r\n",
                   (netif_p->ip_addr.u_addr.ip4.addr & 0xff),
                   (netif_p->ip_addr.u_addr.ip4.addr >> 8) & 0xff,
                   (netif_p->ip_addr.u_addr.ip4.addr >> 16) & 0xff,
                   (netif_p->ip_addr.u_addr.ip4.addr >> 24) & 0xff);
            return ERRCODE_SUCC;
        }
    }
    return ERRCODE_FAIL;
}
