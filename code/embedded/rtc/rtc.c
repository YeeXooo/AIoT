/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：rtc.c
 * 功能描述：INS5699S RTC驱动 (I2C1, addr=0x51)
 */

#include "rtc.h"
#include "i2c.h"
#include "stdio.h"

#define RTC_ADDR    0x51
#define RTC_I2C_BUS 1
#define RTC_REG_SEC 0x00

static uint8_t bcd2dec(uint8_t bcd) { return ((bcd >> 4) * 10) + (bcd & 0x0F); }
static uint8_t dec2bcd(uint8_t dec) { return ((dec / 10) << 4) | (dec % 10); }

static uint8_t rtc_read_reg(uint8_t reg)
{
    uint8_t val = 0;
    i2c_data_t data = { .send_buf = &reg, .send_len = 1, .receive_buf = &val, .receive_len = 1 };
    uapi_i2c_master_writeread(RTC_I2C_BUS, RTC_ADDR, &data);
    return val;
}

static void rtc_write_reg(uint8_t reg, uint8_t val)
{
    uint8_t buf[] = {reg, val};
    i2c_data_t data = { .send_buf = buf, .send_len = 2 };
    uapi_i2c_master_write(RTC_I2C_BUS, RTC_ADDR, &data);
}

int rtc_init(void)
{
    printf("INS5699S RTC init OK\n");
    return 0;
}

int rtc_get_time(rtc_time_t *t)
{
    t->sec  = bcd2dec(rtc_read_reg(0x00) & 0x7F);
    t->min  = bcd2dec(rtc_read_reg(0x01) & 0x7F);
    t->hour = bcd2dec(rtc_read_reg(0x02) & 0x3F);
    t->week = bcd2dec(rtc_read_reg(0x03) & 0x07);
    t->day  = bcd2dec(rtc_read_reg(0x04) & 0x3F);
    t->month= bcd2dec(rtc_read_reg(0x05) & 0x1F);
    t->year = bcd2dec(rtc_read_reg(0x06));
    return 0;
}

int rtc_set_time(rtc_time_t *t)
{
    rtc_write_reg(0x00, dec2bcd(t->sec));
    rtc_write_reg(0x01, dec2bcd(t->min));
    rtc_write_reg(0x02, dec2bcd(t->hour));
    rtc_write_reg(0x03, dec2bcd(t->week));
    rtc_write_reg(0x04, dec2bcd(t->day));
    rtc_write_reg(0x05, dec2bcd(t->month));
    rtc_write_reg(0x06, dec2bcd(t->year));
    return 0;
}
