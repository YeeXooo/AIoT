/**
 * 车载安全监测系统 — 车队大屏 WebSocket 客户端
 *
 * 基于 docs/ood_interface.md §4.2 车队大屏对接
 * 用于接收 L3 高危告警实时推送和绩效预警推送
 */

import { parseJson, getStr, getRecord } from '../common/JsonParser'

// ===================================================================
// 事件回调类型
// ===================================================================

export interface FleetWSEvents {
  /** 连接建立成功 */
  onConnected?: () => void
  /** 连接断开 */
  onDisconnected?: (code: number, reason: string) => void
  /** 连接错误 */
  onError?: (code: string, message: string) => void
  /** L3 高危告警推送 */
  onL3Alert?: (msg: Record<string, unknown>) => void
  /** 绩效预警推送 */
  onPerformanceWarning?: (msg: Record<string, unknown>) => void
  /** 心跳 */
  onPing?: (serverTime: string) => void
}

// ===================================================================
// 配置选项
// ===================================================================

export interface FleetWebSocketOptions {
  /** WebSocket 基础 URL，默认 wss://api.example.com/ws/fleet */
  baseUrl?: string
  /** 心跳间隔（ms），默认 30000 */
  pingInterval?: number
  /** 最大重连次数，默认 5 */
  maxReconnectAttempts?: number
  /** 重连初始退避（ms），默认 1000 */
  reconnectBaseDelay?: number
  /** 是否自动重连，默认 true */
  autoReconnect?: boolean
}

// ===================================================================
// FleetWebSocket
// ===================================================================

export class FleetWebSocket {
  private ws: WebSocket | null = null
  private accessToken: string
  private events: FleetWSEvents
  private options: Required<FleetWebSocketOptions>
  private reconnectAttempts = 0
  private destroyed = false

  constructor(
    accessToken: string,
    events: FleetWSEvents = {},
    options: FleetWebSocketOptions = {}
  ) {
    this.accessToken = accessToken
    this.events = events
    this.options = {
      baseUrl: options.baseUrl ?? 'wss://api.example.com/ws/fleet',
      pingInterval: options.pingInterval ?? 30000,
      maxReconnectAttempts: options.maxReconnectAttempts ?? 5,
      reconnectBaseDelay: options.reconnectBaseDelay ?? 1000,
      autoReconnect: options.autoReconnect ?? true,
    }
  }

  /** 建立 WebSocket 连接 */
  connect(): void {
    if (this.ws?.readyState === WebSocket.OPEN) return
    this.destroyed = false

    const url = `${this.options.baseUrl}?token=${encodeURIComponent(this.accessToken)}`
    this.ws = new WebSocket(url)
    this.ws.onopen = () => this.handleOpen()
    this.ws.onmessage = (event: MessageEvent) => this.handleMessage(event)
    this.ws.onclose = (event: CloseEvent) => this.handleClose(event.code, event.reason)
    this.ws.onerror = () => this.handleError()
  }

  /** 断开 WebSocket 连接 */
  disconnect(): void {
    this.destroyed = true
    this.clearPingTimer()
    this.reconnectAttempts = 0
    this.ws?.close()
    this.ws = null
  }

  /** 更新 JWT token */
  updateToken(token: string): void {
    this.accessToken = token
  }

  // ---- 内部方法 ----

  private send(data: unknown): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }

  private handleOpen(): void {
    this.reconnectAttempts = 0
    this.startPingTimer()
    this.events.onConnected?.()
  }

  private handleClose(code: number, reason: string): void {
    this.clearPingTimer()
    this.events.onDisconnected?.(code, reason)

    if (!this.destroyed && this.options.autoReconnect) {
      this.scheduleReconnect()
    }
  }

  private handleError(): void {
    this.events.onError?.('WS_ERROR', 'WebSocket 连接错误')
  }

  private handleMessage(event: MessageEvent): void {
    try {
      const msg: Record<string, unknown> = parseJson(event.data as string)
      const type: string = getStr(msg, 'type')
      const payload: Record<string, unknown> = getRecord(msg, 'payload')

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
    } catch (err) {
      console.error('[FleetWS] 消息解析失败:')
    }
  }

  private handlePing(serverTime: string): void {
    this.events.onPing?.(serverTime)
    this.send({ type: 'pong', payload: {} })
  }

  private startPingTimer(): void {
    // 由服务端 PING 驱动心跳检测，客户端无需独立定时器
    // 服务端每 30s 发送 PING，客户端在 handlePing 中回复 PONG
  }

  private clearPingTimer(): void {
    // 无定时器需要清理（心跳由服务端 PING 驱动）
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts) return

    const delay = this.options.reconnectBaseDelay * Math.pow(2, this.reconnectAttempts)
    this.reconnectAttempts++

    setTimeout(() => {
      if (!this.destroyed) this.connect()
    }, delay)
  }
}
