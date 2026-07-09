
#include "oled.h"

static i2c_bus_t oled_i2c_bus;
errcode_t oled_init(void)
{
    oled_i2c_bus = CONFIG_OLED_I2C_BUS;

    /* I2C1 已在 hardware_init 中初始化 (400kHz), 此处仅初始化 SSD1306 */
    ssd1306_Init(oled_i2c_bus);
    printf("[OLED] SSD1306 init OK (I2C1, 0x3C)\n");
    return ERRCODE_SUCC;
}
