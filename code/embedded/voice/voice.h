/*
 * SU-03T 语音模块 — 19-voice 通信模式
 *
 * smartpi.cn 添加顺序 → 命令码:
 *   第1~4个(控制): 0x01~0x04
 *   第5~11个(查询): 0x05~0x0B
 * 应答帧: AA 55 <msgno> <params LE> 55 AA
 */

#ifndef VOICE_H
#define VOICE_H

/* ==================== SU-03T → WS63 命令码 (smartpi.cn 串口输入添加顺序) ==================== */
#define VOICE_CMD_LIGHT_ON      0x01
#define VOICE_CMD_LIGHT_OFF     0x02
#define VOICE_CMD_MUTE_ALERT    0x03
#define VOICE_CMD_UNMUTE_ALERT  0x04
#define VOICE_CMD_QUERY_TEMP    0x05
#define VOICE_CMD_QUERY_HUMI    0x06
#define VOICE_CMD_QUERY_HR      0x07
#define VOICE_CMD_QUERY_SPO2    0x08
#define VOICE_CMD_QUERY_SCORE   0x09
#define VOICE_CMD_QUERY_BATTERY 0x0A
#define VOICE_CMD_QUERY_RISK    0x0B

/* ==================== WS63 → SU-03T 应答消息编号 (smartpi.cn 串口输出 msgno) ==================== */
#define VOICE_MSG_TEMP      1
#define VOICE_MSG_HUMI      2
#define VOICE_MSG_HR        3
#define VOICE_MSG_SPO2      4
#define VOICE_MSG_SCORE     5
#define VOICE_MSG_BATTERY   6
#define VOICE_MSG_RISK      7

/* ==================== 函数声明 (19-voice 风格) ==================== */
void uart_gpio_init(void);
void uart_init_config(void);
void voice_analysis(uint8_t *info);
void *uart_voice_task(const char *arg);
int  voice_is_muted(void);

#endif /* VOICE_H */
