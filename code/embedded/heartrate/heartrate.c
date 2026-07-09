/*
 * MAX30102 心率血氧驱动 — 严格参考 drive-1/MAX30102 寄存器配置 + Maxim 官方算法
 *
 * 参考来源:
 *   D:\Harmony\驱动集合\drive-1\MAX30102\max30102.c  — 寄存器初始化序列
 *   D:\Harmony\驱动集合\drive-1\MAX30102\max30102.h  — 寄存器定义
 *   Maxim MAXREFDES117 algorithm.c/h                   — 心率血氧算法
 *
 * I2C 操作模式 (对齐 LTR-553ALS 验证过的模式):
 *   ws63 SDK 的 uapi_i2c_master_writeread() 有兼容问题,
 *   改用 uapi_i2c_master_write() + uapi_i2c_master_read() 两步走
 *
 * 硬件连接: I2C1  GPIO16=SCL  GPIO15=SDA  7-bit地址 0x57 (= 0xAE>>1)
 *
 * 修改记录:
 *   2025-07-03  v1 修复初始化: I2C错误检查 / reset等待 / 去掉阻塞校准
 *   2025-07-03  v2 修复I2C: writeread→write+read / 结构体零初始化
 *   2025-07-04  v3 增强探测: 加长重试间隔 / I2C错误诊断 / 复位前确认器件在线
 */

#include "heartrate.h"
#include "algorithm.h"
#include "i2c.h"
#include "soc_osal.h"
#include "stdio.h"
#include "string.h"

#define MAX30102_I2C_BUS  1

/* ── 寄存器定义 (同 max30102.h) ── */
#define REG_INTR_STATUS_1  0x00
#define REG_INTR_STATUS_2  0x01
#define REG_INTR_ENABLE_1  0x02
#define REG_INTR_ENABLE_2  0x03
#define REG_FIFO_WR_PTR    0x04
#define REG_OVF_COUNTER    0x05
#define REG_FIFO_RD_PTR    0x06
#define REG_FIFO_DATA      0x07
#define REG_FIFO_CONFIG    0x08
#define REG_MODE_CONFIG    0x09
#define REG_SPO2_CONFIG    0x0A
#define REG_LED1_PA        0x0C   /* LED1=IR, LED2=RED (MAX30102 datasheet) */
#define REG_LED2_PA        0x0D
#define REG_PILOT_PA       0x10
#define REG_TEMP_INTR      0x1F
#define REG_TEMP_FRAC      0x20
#define REG_TEMP_CONFIG    0x21
#define REG_REV_ID         0xFE
#define REG_PART_ID        0xFF

/* FS / BUFFER_SIZE 在 algorithm.h 中已定义 */

static int     g_resting_hr    = 70;
static uint8_t g_max30102_addr = 0;
static int     g_inited        = 0;   /* 0=未初始化, 1=成功 */

/* ═══════════════════════════════════════════════════════════════════
 * I2C 底层 — WS63 SDK (对齐 LTR-553ALS 验证过的 write→read 模式)
 *
 * 使用 g_max30102_addr 而非硬编码地址 — 自动适配 0x57/0x58
 * ═══════════════════════════════════════════════════════════════════ */

static errcode_t max30102_write(uint8_t reg, uint8_t val)
{
    uint8_t buf[2] = {reg, val};
    i2c_data_t data = {0};
    data.send_buf = buf;
    data.send_len = 2;
    errcode_t ret = uapi_i2c_master_write(MAX30102_I2C_BUS, g_max30102_addr, &data);
    if (ret != ERRCODE_SUCC) {
        printf("[MAX30102] W err: reg=0x%02X val=0x%02X ret=0x%X\n", reg, val, ret);
    }
    return ret;
}

static errcode_t max30102_read(uint8_t reg, uint8_t *val)
{
    errcode_t ret;
    uint8_t   buf[1] = {0};
    i2c_data_t data = {0};

    data.send_buf = &reg;
    data.send_len = 1;
    ret = uapi_i2c_master_write(MAX30102_I2C_BUS, g_max30102_addr, &data);
    if (ret != ERRCODE_SUCC) {
        printf("[MAX30102] R-addr err: reg=0x%02X ret=0x%X\n", reg, ret);
        return ret;
    }

    data.send_buf    = NULL;
    data.send_len    = 0;
    data.receive_buf = buf;
    data.receive_len = 1;
    ret = uapi_i2c_master_read(MAX30102_I2C_BUS, g_max30102_addr, &data);
    if (ret != ERRCODE_SUCC) {
        printf("[MAX30102] R-data err: reg=0x%02X ret=0x%X\n", reg, ret);
        return ret;
    }

    *val = buf[0];
    return ERRCODE_SUCC;
}

static errcode_t max30102_read_fifo(uint32_t *red, uint32_t *ir)
{
    errcode_t  ret;
    uint8_t    reg = REG_FIFO_DATA;
    uint8_t    buf[6] = {0};
    i2c_data_t data = {0};

    data.send_buf = &reg;
    data.send_len = 1;
    ret = uapi_i2c_master_write(MAX30102_I2C_BUS, g_max30102_addr, &data);
    if (ret != ERRCODE_SUCC) {
        printf("[MAX30102] FIFO addr err ret=0x%X\n", ret);
        return ret;
    }

    data.send_buf    = NULL;
    data.send_len    = 0;
    data.receive_buf = buf;
    data.receive_len = 6;
    ret = uapi_i2c_master_read(MAX30102_I2C_BUS, g_max30102_addr, &data);
    if (ret != ERRCODE_SUCC) {
        printf("[MAX30102] FIFO data err ret=0x%X\n", ret);
        return ret;
    }

    /* 3字节合成18-bit采样值 (同 max30102.c 行358-376) */
    uint32_t un_temp;
    un_temp  = (uint32_t)buf[0] << 16;
    un_temp |= (uint32_t)buf[1] << 8;
    un_temp |= (uint32_t)buf[2];
    *red = un_temp & 0x03FFFF;

    un_temp  = (uint32_t)buf[3] << 16;
    un_temp |= (uint32_t)buf[4] << 8;
    un_temp |= (uint32_t)buf[5];
    *ir  = un_temp & 0x03FFFF;

    return ERRCODE_SUCC;
}

/* ── 固件复位 (同 max30102.c max30102_reset) ── */
static errcode_t max30102_reset(void)
{
    errcode_t ret;
    uint8_t   val;

    /* 确认器件仍然在线 */
    ret = max30102_read(REG_PART_ID, &val);
    if (ret != ERRCODE_SUCC) {
        printf("[MAX30102] Reset abort: device not responding\n");
        return ret;
    }

    ret = max30102_write(REG_MODE_CONFIG, 0x40);
    if (ret != ERRCODE_SUCC) { printf("[MAX30102] Reset#1 fail\n"); return ret; }
    osal_msleep(100);  /* 等待复位完成 */

    /* 复位后验证器件恢复 */
    ret = max30102_read(REG_PART_ID, &val);
    if (ret != ERRCODE_SUCC || val != 0x15) {
        printf("[MAX30102] Reset verify fail: ret=0x%X val=0x%02X\n", ret, val);
        return ERRCODE_FAIL;
    }

    return ERRCODE_SUCC;
}

/* ═══════════════════════════════════════════════════════════════════
 * 初始化 — 自动探测 0x57 / 0x58
 *
 * v3 改进:
 *   - 重试间隔从 50ms → 150ms (给传感器充足的启动时间)
 *   - 每次重试前先读 PART_ID, 再尝试复位 (减少无效 I2C 操作)
 *   - 详细的错误诊断 (区分 NACK vs 超时)
 * ═══════════════════════════════════════════════════════════════════ */
int heartrate_init(void)
{
    errcode_t ret;
    uint8_t   val;
    uint8_t   addrs[] = {0x57, 0x58};  /* ADDR=GND→0x57, ADDR=VCC→0x58 */
    int       found  = 0;
    int       retry;

    printf("[MAX30102] === Probe start (I2C1, GPIO15=SDA GPIO16=SCL) ===\n");

    /* ── 1. 探测地址 (每个地址重试5次, 间隔150ms) ── */
    for (int a = 0; a < 2; a++) {
        g_max30102_addr = addrs[a];
        for (retry = 0; retry < 5; retry++) {
            if (retry > 0) {
                printf("[MAX30102] Retry #%d addr 0x%02X...\n", retry, g_max30102_addr);
                osal_msleep(150);  /* v3: 加长重试间隔 */
            } else {
                printf("[MAX30102] Try addr 0x%02X...\n", g_max30102_addr);
            }

            ret = max30102_read(REG_PART_ID, &val);
            if (ret == ERRCODE_SUCC && val == 0x15) {
                found = 1;
                break;
            }

            /* 诊断: 区分 NACK(0x80001314) 和其他错误 */
            if (ret == 0x80001314) {
                /* I2C NACK — 器件不在总线或地址错误, 继续重试 */
            } else if (ret != ERRCODE_SUCC) {
                printf("[MAX30102] I2C err 0x%X (check SDA/SCL wiring)\n", ret);
            }
            if (val != 0 && val != 0xFF) {
                printf("[MAX30102] Unexpected PART_ID=0x%02X (expect 0x15)\n", val);
            }
        }
        if (found) break;
    }

    if (!found) {
        printf("[MAX30102] *** FATAL: No MAX30102 detected ***\n");
        printf("[MAX30102] *** Check: VCC(3.3V) GND SCL→GPIO16 SDA→GPIO15 ***\n");
        return -1;
    }

    printf("[MAX30102] Found at 0x%02X, PART_ID=0x%02X (expect 0x15)\n",
           g_max30102_addr, val);

    /* ── 2. 固件复位 ── */
    printf("[MAX30102] Reset...\n");
    if (max30102_reset() != ERRCODE_SUCC) {
        printf("[MAX30102] *** FATAL: Reset fail ***\n");
        return -1;
    }
    printf("[MAX30102] Reset OK\n");

    /* ── 3. 寄存器配置 (对齐 max30102.c:268-278) ── */
    printf("[MAX30102] Config regs...\n");
    max30102_write(REG_INTR_ENABLE_1, 0xc0);
    max30102_write(REG_INTR_ENABLE_2, 0x00);
    max30102_write(REG_FIFO_WR_PTR,  0x00);
    max30102_write(REG_OVF_COUNTER,   0x00);
    max30102_write(REG_FIFO_RD_PTR,   0x00);
    max30102_write(REG_FIFO_CONFIG,   0x0f);
    max30102_write(REG_MODE_CONFIG,   0x03);
    max30102_write(REG_SPO2_CONFIG,   0x27);
    max30102_write(REG_LED1_PA,       0x24);
    max30102_write(REG_LED2_PA,       0x24);
    max30102_write(REG_PILOT_PA,      0x7f);

    /* ── 4. 回读验证 ── */
    osal_msleep(50);
    if (max30102_read(REG_MODE_CONFIG, &val) == ERRCODE_SUCC)
        printf("[MAX30102] MODE_CONFIG=0x%02X (expect 0x03)\n", val);
    else
        printf("[MAX30102] WARN: MODE_CONFIG verify fail\n");
    if (max30102_read(REG_SPO2_CONFIG, &val) == ERRCODE_SUCC)
        printf("[MAX30102] SPO2_CONFIG=0x%02X (expect 0x27)\n", val);
    else
        printf("[MAX30102] WARN: SPO2_CONFIG verify fail\n");

    g_inited = 1;
    printf("[MAX30102] ===== Init Complete (addr=0x%02X) =====\n", g_max30102_addr);
    return 0;
}

/* ═══════════════════════════════════════════════════════════════════
 * 读取 — 累积 ≥100 样本后运行 Maxim 算法
 * ═══════════════════════════════════════════════════════════════════ */
int heartrate_read(heartrate_data_t *hr)
{
    static uint32_t ir_buf[BUFFER_SIZE], red_buf[BUFFER_SIZE];
    static int      idx = 0;
    static int      calib_done = 0;
    errcode_t       ret;
    uint8_t         sts;

    if (!hr) return -1;

    hr->heart_rate = g_resting_hr;
    hr->spo2       = 95;
    hr->resting_hr = g_resting_hr;
    hr->valid      = 0;

    /* 未初始化成功 → 静默返回默认值, 不碰 I2C */
    if (!g_inited) return -1;

    /* 读中断状态 */
    ret = max30102_read(REG_INTR_STATUS_1, &sts);
    if (ret != ERRCODE_SUCC) return -1;

    /* PPG_RDY → 读FIFO */
    if (sts & 0x40) {
        uint32_t red, ir;
        ret = max30102_read_fifo(&red, &ir);
        if (ret == ERRCODE_SUCC && idx < BUFFER_SIZE) {
            ir_buf[idx]  = ir;
            red_buf[idx] = red;
            idx++;
        }
    }

    /* 每100样本计算一次 */
    if (idx >= 100) {
        int32_t hr_v = 0, sp_v = 0;
        int8_t  hrv = 0, spvv = 0;
        maxim_heart_rate_and_oxygen_saturation(ir_buf, idx, red_buf,
                                               &sp_v, &spvv, &hr_v, &hrv);
        if (hrv) {
            hr->heart_rate = hr_v;
            hr->valid = 1;
            if (!calib_done) {
                g_resting_hr = hr_v;
                hr->resting_hr = hr_v;
                calib_done = 1;
                printf("[MAX30102] Calibrated HR=%d\n", hr_v);
            }
        }
        if (spvv) { hr->spo2 = sp_v; hr->valid = 1; }
        idx = 0;
    }
    return hr->valid ? 0 : -1;
}

void heartrate_set_resting_hr(int hr_val) { g_resting_hr = hr_val; }
