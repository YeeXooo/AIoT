/*
 * CW2015电池电量计驱动 (I2C1, addr=0x62)
 * 参考: DUT_Car/cw2015.c
 *
 * CW2015是一款I2C接口的锂电池电量计芯片
 * 寄存器:
 *   0x02-0x03: 电压(高字节+低字节, 单位: mV/LSB)
 *   0x04-0x05: 电量百分比
 * 简化实现: 读电压寄存器 → 换算mV → 低于3.3V标记低电量
 */

#include "battery.h"
#include "i2c.h"
#include "soc_osal.h"
#include "stdio.h"

#define CW2015_ADDR      0x62
#define CW2015_I2C_BUS   1
#define REG_VCELL_H      0x02
#define REG_VCELL_L      0x03
#define REG_SOC_H        0x04
#define REG_SOC_L        0x05

#define LOW_VOLTAGE_MV   3300   /* 低于3.3V触发低电量告警 */

int battery_init(void)
{
    printf("CW2015 battery fuel gauge init OK\n");
    return 0;
}

int battery_read(battery_data_t *bat)
{
    uint8_t reg_h = REG_VCELL_H, reg_l = REG_VCELL_L;
    uint8_t vh = 0, vl = 0;
    uint8_t soc_h = 0, soc_l = 0;

    /* 读电压 */
    i2c_data_t d1 = { .send_buf = &reg_h, .send_len = 1, .receive_buf = &vh, .receive_len = 1 };
    uapi_i2c_master_writeread(CW2015_I2C_BUS, CW2015_ADDR, &d1);
    i2c_data_t d2 = { .send_buf = &reg_l, .send_len = 1, .receive_buf = &vl, .receive_len = 1 };
    uapi_i2c_master_writeread(CW2015_I2C_BUS, CW2015_ADDR, &d2);
    bat->voltage_mv = ((uint16_t)vh << 8) | vl;

    /* 读电量百分比 */
    uint8_t r_soc_h = REG_SOC_H, r_soc_l = REG_SOC_L;
    i2c_data_t d3 = { .send_buf = &r_soc_h, .send_len = 1, .receive_buf = &soc_h, .receive_len = 1 };
    uapi_i2c_master_writeread(CW2015_I2C_BUS, CW2015_ADDR, &d3);
    i2c_data_t d4 = { .send_buf = &r_soc_l, .send_len = 1, .receive_buf = &soc_l, .receive_len = 1 };
    uapi_i2c_master_writeread(CW2015_I2C_BUS, CW2015_ADDR, &d4);
    bat->percent = soc_h;

    bat->low_power = (bat->voltage_mv < LOW_VOLTAGE_MV) ? 1 : 0;
    return 0;
}
