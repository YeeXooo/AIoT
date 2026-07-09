/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：radar.h
 * 功能描述：WS63E毫米波雷达驱动头文件 — 人体存在/距离检测
 */

#ifndef __RADAR_H__
#define __RADAR_H__

#include "common_def.h"
#include "radar_service.h"

int radar_init(void);
int radar_start_scan(void);
int radar_get_result(radar_result_t *res); /* is_human_presence / boundary */
void radar_register_callback(void (*cb)(radar_result_t *));

#endif /* __RADAR_H__ */
