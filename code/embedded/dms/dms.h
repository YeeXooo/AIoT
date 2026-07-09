/*
 * DMS驾驶员监测系统 (PC摄像头 → UART → WS63)
 *
 * 架构: PC端运行AI视觉模型(YOLO/MediaPipe)分析面部/姿态
 *   → 通过USB转UART发送结构化数据到WS63
 *   → WS63解析后提供给engine做BR-01疲劳判定
 *
 * 通信协议 (115200bps, 8N1):
 *   格式: "DMS,PERCLOS:0.xx,YAWN:cnt,HAT:0/1,PHONE:0/1,LVL:0-2\n"
 *   PERCLOS:   眼睛闭合百分比(0.00-1.00)
 *   YAWN:      每分钟哈欠次数
 *   HAT:       是否戴帽子(0/1)
 *   PHONE:     是否接打电话(0/1)
 *   LVL:       PC端预判疲劳等级(0=清醒 1=轻度 2=重度)
 */

#ifndef __DMS_H__
#define __DMS_H__

#include "common_def.h"

typedef struct {
    float perclos;        /* 眼睑闭合比例 0-1 */
    int   yawn_count;     /* 哈欠计数 */
    int   phone_use;      /* 接打电话标记 */
    int   hat_on;         /* 戴帽子(影响PERCLOS精度) */
    int   pc_fatigue_lvl; /* PC端预判等级 0-2 */
    int   valid;          /* 数据有效(收到最近1秒内的消息) */
} dms_data_t;

int dms_init(void);
int dms_read(dms_data_t *dms);

#endif /* __DMS_H__ */
