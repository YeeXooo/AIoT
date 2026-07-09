/*
 * BMX055九轴IMU — DUT_Car原版12bit解包 + 正确单位转换
 * ACC ±2g: raw = ((msb<<4)|((lsb&0xF0)>>4)), 1LSB=2g/2048, 输出m/s²
 * GYR ±2000°/s: raw = (msb<<8)|lsb, 1LSB=2000/32768, 输出°/s
 * MAG: raw packed 13/15bit, 输出µT
 */

#include "bmx055.h"
#include "i2c.h"
#include "soc_osal.h"
#include "stdio.h"

#define I2C_BUS   1
#define ACC_ADDR  0x18
#define GYR_ADDR  0x68
#define MAG_ADDR  0x10

static void bmx_write(uint8_t dev, uint8_t reg, uint8_t val)
{
    uint8_t buf[] = {reg, val};
    i2c_data_t data = { .send_buf = buf, .send_len = 2 };
    uapi_i2c_master_write(I2C_BUS, dev, &data);
}

static void bmx_read(uint8_t dev, uint8_t reg, uint8_t *dst, uint8_t len)
{
    uint8_t sb = reg;
    i2c_data_t data = { .send_buf = &sb, .send_len = 1, .receive_buf = dst, .receive_len = len };
    uapi_i2c_master_writeread(I2C_BUS, dev, &data);
}

int bmx055_init(void)
{
    osal_msleep(50);
    /* ACC: ±2g, 100Hz */
    bmx_write(ACC_ADDR, 0x0F, 0x03); osal_msleep(10);
    bmx_write(ACC_ADDR, 0x10, 0x0F); osal_msleep(10);
    bmx_write(ACC_ADDR, 0x11, 0x00); osal_msleep(10);
    /* GYR: ±2000°/s */
    bmx_write(GYR_ADDR, 0x0F, 0x00); osal_msleep(10);
    bmx_write(GYR_ADDR, 0x10, 0x07); osal_msleep(10);
    bmx_write(GYR_ADDR, 0x11, 0x00); osal_msleep(10);
    /* MAG: full init */
    uint8_t id;
    bmx_read(MAG_ADDR, 0x4B, &id, 1); osal_msleep(10);
    if (id == 0) { bmx_write(MAG_ADDR, 0x4B, 0x83); osal_msleep(20); }
    bmx_write(MAG_ADDR, 0x4B, 0x01); osal_msleep(10);
    bmx_write(MAG_ADDR, 0x4C, 0x38); osal_msleep(10);
    bmx_write(MAG_ADDR, 0x4E, 0x84); osal_msleep(10);
    bmx_write(MAG_ADDR, 0x51, 0x04); osal_msleep(10);
    bmx_write(MAG_ADDR, 0x52, 0x0F); osal_msleep(10);

    printf("BMX055 IMU init OK (DUT_Car driver)\n");
    return 0;
}

int bmx055_read_all(imu_data_t *imu)
{
    uint8_t buf[8];
    int raw;

    /* === ACC: 12bit packed, ±2g → m/s² === */
    bmx_read(ACC_ADDR, 0x02, buf, 6);
    for (int i = 0; i < 3; i++) {
        uint8_t lsb = buf[2*i], msb = buf[2*i + 1];
        raw = ((msb << 4) | ((lsb & 0xF0) >> 4));   /* DUT_Car 12bit解包 */
        if (raw > 2047) raw -= 4096;                  /* 有符号转换 */
        float g = raw * 2.0f / 2048.0f;               /* g值 */
        float mps2 = g * 9.8f;                        /* → m/s² */
        if (i == 0) imu->ax = mps2;
        else if (i == 1) imu->ay = mps2;
        else imu->az = mps2;
    }

    /* === GYR: 16bit, ±2000°/s → °/s === */
    bmx_read(GYR_ADDR, 0x02, buf, 6);
    for (int i = 0; i < 3; i++) {
        uint8_t lsb = buf[2*i], msb = buf[2*i + 1];
        raw = (msb << 8) | lsb;
        if (raw > 32767) raw -= 65536;
        float dps = raw * 2000.0f / 32768.0f;         /* °/s */
        if (i == 0) imu->gx = dps;
        else if (i == 1) imu->gy = dps;
        else imu->gz = dps;
    }

    /* === MAG: 13/15bit packed → µT === */
    bmx_read(MAG_ADDR, 0x42, buf, 8);
    raw = ((buf[1] << 5) | ((buf[0] & 0xF8) >> 3));
    if (raw > 4095) raw -= 8192;
    imu->mx = raw * 0.3f;
    raw = ((buf[3] << 5) | ((buf[2] & 0xF8) >> 3));
    if (raw > 4095) raw -= 8192;
    imu->my = raw * 0.3f;
    raw = ((buf[5] << 7) | ((buf[4] & 0xFE) >> 1));
    if (raw > 16383) raw -= 32768;
    imu->mz = raw * 0.3f;

    return 0;
}
