/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：wifi_connect.h
 * 功能描述：WiFi STA连接头文件 (参考: 25_smart_home)
 */

#ifndef __WIFI_CONNECT_H__
#define __WIFI_CONNECT_H__

#include "soc_osal.h"
#include "errcode.h"

errcode_t wifi_connect(void);
void wifi_set_ssid_pwd(const char *ssid, const char *pwd);

#endif /* __WIFI_CONNECT_H__ */
