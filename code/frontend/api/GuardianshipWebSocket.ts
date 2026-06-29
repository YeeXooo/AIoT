/**
 * 车载安全监测系统 — 家属 APP WebSocket 客户端
 *
 * 基于 docs/ood_interface.md §3.1 家属 APP WebSocket 信令协议
 * 提供实时状态订阅、告警推送、音视频对讲信令等长连接管理
 */

import { parseJson, getStr, getRecord } from '../common/JsonParser'

// ===================================================================
// 事件回调类型（使用 Record<string, unknown> 避免 as T 断言）
// ===================================================================

export interface GuardianshipWSEvents {
  /** 连接建立成功 */
  onConnected?: (msg: Record<string, unknown>) => void
  /** 连接断开 */
  onDisconnected?: (code: number, reason: string) => void
  /** 连接错误 */
  onError?: (err: Record<string, unknown>) => void
  /** 心跳 PING */
  onPing?: (serverTime: string) => void
  /** 驾驶员状态快照推送（≥1Hz） */
  onDriverStatusSnapshot?: (snapshot: Record<string, unknown>) => void
  /** 告警推送 */
  onAlertTriggered?: (alert: Record<string, unknown>) => void
  /** 权限授予通知 */
  onAccessGranted?: (msg: Record<string, unknown>) => void
  /** 权限撤销通知 */
  onAccessRevoked?: (msg: Record<string, unknown>) => void
  /** 订阅确认 */
  onSubscribeStatusAck?: (msg: Record<string, unknown>) => void
  /** 救援触发确认 */
  onRescueTriggered?: (msg: Record<string, unknown>) => void
  /** Token 续签推送 */
  onTokenRenewed?: (msg: Record<string, unknown>) => void
}

// ===================================================================
// 配置选项
// ===================================================================

export interface GuardianshipWebSocketOptions {
  /** WebSocket 基础 URL，默认 wss://api.example.com/ws/guardianship */
  baseUrl?: string
  /** 心跳丢失超时次数（连续 N 次 PING 未收到视为断开），默认 3 */
  maxMissedPings?: number
  /** 重连最大重试次数，默认 5 */
  maxReconnectAttempts?: number
  /** 重连初始退避（ms），默认 1000 */
  reconnectBaseDelay?: number
  /** 是否自动重连，默认 true */
  autoReconnect?: boolean
}

// ===================================================================
// GuardianshipWebSocket
// ===================================================================

export class GuardianshipWebSocket {
  private ws: WebSocket | null = null
  private accessToken: string
  private events: GuardianshipWSEvents
  private options: Required<GuardianshipWebSocketOptions>
  private reconnectAttempts = 0
  private pingTimer: ReturnType<typeof setInterval> | null = null
  private pingMissedCount = 0
  private subscriptions: Map<string, unknown> = new Map()
  private destroyed = false

  constructor(
    accessToken: string,
    events: GuardianshipWSEvents = {},
    options: GuardianshipWebSocketOptions = {}
  ) {
    this.accessToken = accessToken
    this.events = events
    this.options = {
      baseUrl: options.baseUrl ?? 'wss://api.example.com/ws/guardianship',
      maxMissedPings: options.maxMissedPings ?? 3,
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
    this.clearTimers()
    this.subscriptions.clear()
    this.reconnectAttempts = 0
    this.ws?.close()
    this.ws = null
  }

  /** 更新 JWT token（用于 Token 刷新后续连） */
  updateToken(token: string): void {
    this.accessToken = token
  }

  /** 订阅驾驶员状态 */
  subscribeDriverStatus(driverId: string): void {
    this.send({ type: 'subscribe_status', payload: { driverId } })
    this.subscriptions.set(driverId, { driverId })
  }

  /** 取消订阅 */
  unsubscribeStatus(subscriptionId: string): void {
    this.send({ type: 'unsubscribe_status', payload: { subscriptionId } })
  }

  /** 发起对讲/视频请求 */
  requestMedia(payload: {
    familyAccountId: string
    driverId: string
    sessionType: 'AUDIO' | 'VIDEO'
    secondaryAuthToken: string
  }): void {
    this.send({ type: 'request_media', payload })
  }

  /** 挂断对讲 */
  endMedia(sessionHandle: string): void {
    this.send({ type: 'end_media', payload: { sessionHandle } })
  }

  /** 触发手动救援 */
  triggerRescue(payload: {
    familyAccountId: string
    driverId: string
    secondaryAuthToken: string
  }): void {
    this.send({ type: 'trigger_rescue', payload })
  }

  // ---- 内部方法 ----

  private send(data: unknown): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }

  private handleOpen(): void {
    this.reconnectAttempts = 0
    this.pingMissedCount = 0
    this.startPingTimer()
  }

  private handleClose(code: number, reason: string): void {
    this.clearTimers()
    this.events.onDisconnected?.(code, reason)

    if (!this.destroyed && this.options.autoReconnect) {
      this.scheduleReconnect()
    }
  }

  private handleError(): void {
    this.events.onError?.({ code: 'WS_ERROR', message: 'WebSocket 连接错误' })
  }

  private handleMessage(event: MessageEvent): void {
    try {
      const msg: Record<string, unknown> = parseJson(event.data as string)
      const type: string = getStr(msg, 'type')
      const payload: Record<string, unknown> = getRecord(msg, 'payload')

      switch (type) {
        case 'connection_established':
          this.events.onConnected?.(payload)
          break

        case 'ping':
          this.handlePing(payload)
          break

        case 'driver_status_snapshot':
          this.events.onDriverStatusSnapshot?.(payload)
          break

        case 'alert_triggered':
          this.events.onAlertTriggered?.(payload)
          break

        case 'access_granted':
          this.events.onAccessGranted?.(payload)
          break

        case 'access_revoked':
          this.events.onAccessRevoked?.(payload)
          break

        case 'subscribe_status_ack':
          this.events.onSubscribeStatusAck?.(payload)
          break

        case 'rescue_triggered':
          this.events.onRescueTriggered?.(payload)
          break

        case 'token_renewed':
          this.events.onTokenRenewed?.(payload)
          break

        case 'error':
          this.events.onError?.(payload)
          break

        default:
          console.warn(`[WS] 未知消息类型: ${type}`)
      }
    } catch (err) {
      console.error('[WS] 消息解析失败:')
    }
  }

  private handlePing(payload: Record<string, unknown>): void {
    this.events.onPing?.(getStr(payload, 'serverTime'))
    // 收到 PING → 计数重置，连接活跃
    this.pingMissedCount = 0
    // 立即回复 PONG
    this.send({ type: 'pong', payload: {} })
  }

  private startPingTimer(): void {
    this.clearTimers()
    const checkInterval = 30_000 // 30s 检测间隔（与服务端 PING 频率一致）
    this.pingTimer = setInterval(() => {
      this.pingMissedCount++
      if (this.pingMissedCount >= this.options.maxMissedPings) {
        // 连续 N 次检测未收到 PING → 视为断开
        this.ws?.close()
      }
    }, checkInterval)
  }

  private clearTimers(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer)
      this.pingTimer = null
    }
  }

  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts) {
      console.warn('[WS] 已达最大重连次数，停止重连')
      return
    }

    const delay = this.options.reconnectBaseDelay * Math.pow(2, this.reconnectAttempts)
    this.reconnectAttempts++

    setTimeout(() => {
      if (!this.destroyed) {
        this.connect()
      }
    }, delay)
  }
}
