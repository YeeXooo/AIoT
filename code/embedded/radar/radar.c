/*
 * 版权所有：AIoT车载安全监测系统
 * 文件名称：radar.c
 * 功能描述：WS63E内置2.4GHz毫米波雷达驱动 — BR-02活体遗留检测核心传感器
 *
 * 工作原理:
 *   发射2.4GHz FMCW波形 → 接收反射信号 → 分析相位/频率变化
 *   可检测: 人体存在(is_human_presence) + 距离(lower_boundary/upper_boundary)
 *   检测范围: 0~6米, 穿透衣物/座椅, 不受光线影响
 *
 * 配置参数(可调):
 *   loop=8:       单次雷达工作TRx波形数(越大灵敏度越高, 但功耗↑)
 *   period=5000ms: 雷达工作间隔(5秒扫描一次)
 *   wave=2:        波形选择(0=320M/40M CTA, 1=160M/20M CW, 2=默认)
 *   算法阈值:      d_th_1m=32(1米内检测阈值) d_th_2m=27(2米内)
 *
 * 参考: fbb_ws63-master/HiHope/demo/radar_led/radar_sta_sample.c
 */

#include "radar.h"
#include "soc_osal.h"
#include "stdio.h"

#define RADAR_PERIOD     5000   /* 扫描间隔5s */
#define RADAR_LOOP       8      /* TRx波形数 */
#define RADAR_WAVE       2      /* 波形选择 */

/* 应用层回调(由engine注册) */
static void (*g_radar_cb)(radar_result_t *) = NULL;

/* 雷达检测结果中断回调 — 由WS63雷达硬件触发 */
static void radar_result_handler(radar_result_t *res)
{
    printf("[RADAR] human:%d range:%u-%u\n",
           res->is_human_presence, res->lower_boundary, res->upper_boundary);
    if (g_radar_cb) g_radar_cb(res);  /* 转发给engine */
}

int radar_init(void)
{
    /* 1. 调试参数: 扫描次数/波形数/波形类型/维测方式/周期 */
    radar_dbg_para_t dbg_para = {
        .times = 0, .loop = RADAR_LOOP, .ant = 0,
        .wave = RADAR_WAVE, .dbg_type = 1, .period = RADAR_PERIOD
    };
    uapi_radar_set_debug_para(&dbg_para);

    /* 2. 算法参数: 高度/场景/材质/融合追踪/融合AI */
    radar_sel_para_t sel_para = {
        .height = 0, .scenario = 0, .material = 2,
        .fusion_track = 1, .fusion_ai = 1
    };
    uapi_radar_select_alg_para(&sel_para);

    /* 3. 检测阈值(可调 — 影响灵敏度/误报率):
     *    d_th_1m=32: 1米内检测阈值(值越小越灵敏)
     *    p_th=30:    相位阈值
     *    t_th_1m=13: 1米内时间阈值
     *    a_th=70:    幅度阈值
     */
    radar_alg_para_t alg_para = {
        .d_th_1m = 32, .d_th_2m = 27, .p_th = 30,
        .t_th_1m = 13, .t_th_2m = 26,
        .b_th_ratio = 50, .b_th_cnt = 15, .a_th = 70
    };
    uapi_radar_set_alg_para(&alg_para, 0);

    /* 4. 注册结果回调 — 每次扫描完成后自动调用 */
    uapi_radar_register_result_cb(radar_result_handler);

    printf("WS63E Radar init OK\n");
    return 0;
}

/* 启动雷达扫描(校准+循环检测) */
int radar_start_scan(void)
{
    uapi_radar_set_status(4);  /* 开始射频隔离度校准(RADAR_STATUS_CALI_ISO) */
    printf("Radar scan started\n");
    return 0;
}

/* 获取最新检测结果(非阻塞 — 由回调更新) */
int radar_get_result(radar_result_t *res)
{
    unused(res);  /* 结果通过回调异步更新, 此函数用于保持接口一致性 */
    return 0;
}

/* 注册应用层回调(engine在init时调用) */
void radar_register_callback(void (*cb)(radar_result_t *))
{
    g_radar_cb = cb;
}
