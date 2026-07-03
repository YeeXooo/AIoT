/**
 * 车载安全监测系统 — 车队大屏 WebSocket 客户端
 *
 * 基于 docs/ood_interface.md §4.2 车队大屏对接
 * 用于接收 L3 高危告警实时推送和绩效预警推送
 *
 * 心跳策略：服务端每 30s 下发 PING，客户端在 handlePing 中回复 PONG。
 * 客户端侧另启 30s 检测定时器，连续 maxMissedPings 次（默认 3）未收到 PING
 * 即主动 close —— 对 TCP 半开连接（服务端挂起但无 FIN）做兜底检测，
 * 与 GuardianshipWebSocket 心跳策略对齐（问题 3 修复）。
 */

import { getStr, getRecord } from '../common/JsonParser'
import { BaseWebSocket, type BaseWebSocketOptions } from './BaseWebSocket'

// ===================================================================
// 事件回调类型
// ===================================================================

export interface FleetWSEvents {
  onConnected?: () => void
  onDisconnected?: (code: number, reason: string) => void
  onError?: (code: string, message: string) => void
  onL3Alert?: (msg: Record<string, Object>) => void
  onPerformanceWarning?: (msg: Record<string, Object>) => void
  onPing?: (serverTime: string) => void
}

// ===================================================================
// 配置选项
// ===================================================================

export interface FleetWebSocketOptions extends BaseWebSocketOptions {
  /** 心跳检测间隔（ms），默认 30000 */
  pingInterval?: number
  /** 连续未收到 PING 的超时次数，默认 3 */
  maxMissedPings?: number
}

// ===================================================================
// FleetWebSocket
// ===================================================================

export class FleetWebSocket extends BaseWebSocket {
  private events: FleetWSEvents
  private fleetOptions: Required<FleetWebSocketOptions>
  private pingTimer: ReturnType<typeof setInterval> | null = null
  private pingMissedCount = 0

  constructor(
    accessToken: string,
    events: FleetWSEvents = {},
    options: FleetWebSocketOptions = {}
  ) {
    super(accessToken, {
      baseUrl: options.baseUrl ?? 'wss://api.example.com/ws/fleet',
      maxReconnectAttempts: options.maxReconnectAttempts,
      reconnectBaseDelay: options.reconnectBaseDelay,
      autoReconnect: options.autoReconnect,
    })
    this.events = events
    this.fleetOptions = {
      baseUrl: options.baseUrl ?? 'wss://api.example.com/ws/fleet',
      pingInterval: options.pingInterval ?? 30000,
      maxMissedPings: options.maxMissedPings ?? 3,
      maxReconnectAttempts: options.maxReconnectAttempts ?? 5,
      reconnectBaseDelay: options.reconnectBaseDelay ?? 1000,
      autoReconnect: options.autoReconnect ?? true,
    }
  }

  // ---- 基类钩子实现 ----

  protected onConnected(): void {
    this.events.onConnected?.()
  }

  protected onDisconnected(code: number, reason: string): void {
    this.events.onDisconnected?.(code, reason)
  }

  protected onError(): void {
    this.events.onError?.('WS_ERROR', 'WebSocket 连接错误')
  }

  protected dispatchMessage(msg: Record<string, Object>): void {
    const type: string = getStr(msg, 'type')
    const payload: Record<string, Object> = getRecord(msg, 'payload')

    switch (type) {
      case 'l3_alert':
        this.events.onL3Alert?.(payload)
        break

      case 'performance_warning':
        this.events.onPerformanceWarning?.(payload)
        break

      case 'ping':
        this.handlePing(getStr(payload, 'serverTime'))
        break

      default:
        console.warn(`[FleetWS] 未知消息类型: ${type}`)
    }
  }

  /**
   * 心跳定时器（问题 3 修复）：客户端侧 30s 检测，连续 maxMissedPings 次
   * 未收到服务端 PING 即主动 close，对 TCP 半开连接做兜底。
   */
  protected startPingTimer(): void {
    this.clearPingTimer()
    this.pingMissedCount = 0
    this.pingTimer = setInterval(() => {
      this.pingMissedCount++
      if (this.pingMissedCount >= this.fleetOptions.maxMissedPings) {
        this.ws?.close()
      }
    }, this.fleetOptions.pingInterval)
  }

  protected clearPingTimer(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer)
      this.pingTimer = null
    }
  }

  // ---- 私有方法 ----

  private handlePing(serverTime: string): void {
    this.pingMissedCount = 0
    this.events.onPing?.(serverTime)
    this.send({ type: 'pong', payload: {} })
  }
}
