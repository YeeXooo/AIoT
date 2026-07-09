/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：bmx055.h
 * 功能描述：BMX055九轴IMU驱动头文件 (加速度+陀螺+磁力)
 */

#ifndef __BMX055_H__
#define __BMX055_H__

#include "common_def.h"

typedef struct {
    float ax, ay, az;  /* m/s² */
    float gx, gy, gz;  /* °/s */
    float mx, my, mz;  /* µT */
} imu_data_t;

int bmx055_init(void);
int bmx055_read_all(imu_data_t *imu);

#endif /* __BMX055_H__ */
