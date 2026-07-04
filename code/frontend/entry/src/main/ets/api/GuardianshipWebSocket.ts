/**
 * 车载安全监测系统 — 家属 APP WebSocket 客户端
 *
 * 基于 docs/ood_interface.md §3.1 家属 APP WebSocket 信令协议
 * 提供实时状态订阅、告警推送、音视频对讲信令等长连接管理
 *
 * 心跳策略：服务端每 30s 下发 PING，客户端在 handlePing 中回复 PONG；
 * 客户端侧另启 30s 检测定时器，连续 maxMissedPings 次（默认 3）未收到 PING
 * 即主动 close，对 TCP 半开连接做兜底检测。
 */

import { getStr, getRecord } from '../common/JsonParser'
import { BaseWebSocket, type BaseWebSocketOptions } from './BaseWebSocket'

// ===================================================================
// 事件回调类型（使用 Record<string, unknown> 避免 as T 断言）
// ===================================================================

export interface GuardianshipWSEvents {
  onConnected?: (msg: Record<string, Object>) => void
  onDisconnected?: (code: number, reason: string) => void
  onError?: (err: Record<string, Object>) => void
  onPing?: (serverTime: string) => void
  onDriverStatusSnapshot?: (snapshot: Record<string, Object>) => void
  onAlertTriggered?: (alert: Record<string, Object>) => void
  onAccessGranted?: (msg: Record<string, Object>) => void
  onAccessRevoked?: (msg: Record<string, Object>) => void
  onSubscribeStatusAck?: (msg: Record<string, Object>) => void
  onRescueTriggered?: (msg: Record<string, Object>) => void
  /** Token 续签推送 */
  onTokenRenewed?: (msg: Record<string, Object>) => void
}

// ===================================================================
// 配置选项
// ===================================================================

export interface GuardianshipWebSocketOptions extends BaseWebSocketOptions {
  /** 心跳丢失超时次数（连续 N 次 PING 未收到视为断开），默认 3 */
  maxMissedPings?: number
  /** 心跳检测间隔（ms），默认 30000 */
  pingInterval?: number
}

// ===================================================================
// GuardianshipWebSocket
// ===================================================================

export class GuardianshipWebSocket extends BaseWebSocket {
  private events: GuardianshipWSEvents
  private guardianshipOptions: Required<GuardianshipWebSocketOptions>
  private pingTimer: ReturnType<typeof setInterval> | null = null
  private pingMissedCount = 0
  private subscriptions: Map<string, unknown> = new Map()

  constructor(
    accessToken: string,
    events: GuardianshipWSEvents = {},
    options: GuardianshipWebSocketOptions = {}
  ) {
    super(accessToken, {
      baseUrl: options.baseUrl ?? 'ws://172.22.103.50:8080/ws/guardianship',
      maxReconnectAttempts: options.maxReconnectAttempts,
      reconnectBaseDelay: options.reconnectBaseDelay,
      autoReconnect: options.autoReconnect,
    })
    this.events = events
    this.guardianshipOptions = {
      baseUrl: options.baseUrl ?? 'ws://172.22.103.50:8080/ws/guardianship',
      maxMissedPings: options.maxMissedPings ?? 3,
      pingInterval: options.pingInterval ?? 30000,
      maxReconnectAttempts: options.maxReconnectAttempts ?? 5,
      reconnectBaseDelay: options.reconnectBaseDelay ?? 1000,
      autoReconnect: options.autoReconnect ?? true,
    }
  }

  /** 断开连接并清空订阅映射（覆盖基类以保留订阅清理） */
  disconnect(): void {
    this.subscriptions.clear()
    super.disconnect()
  }

  // ---- 业务方法 ----

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

  // ---- 基类钩子实现 ----

  protected onConnected(): void {
    // Guardianship 的 connection_established 由服务端下发首帧触发，非 onopen 直接触发
  }

  protected onDisconnected(code: number, reason: string): void {
    this.events.onDisconnected?.(code, reason)
  }

  protected onError(): void {
    this.events.onError?.({ code: 'WS_ERROR', message: 'WebSocket 连接错误' })
  }

  protected dispatchMessage(msg: Record<string, Object>): void {

    const payload: Record<string, Object> = getRecord(msg, 'payload')
    const msgType: string = getStr(msg, 'type')

    switch (msgType) {
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
        console.warn(`[WS] 未知消息类型: ${msgType}`)
    }
  }

  /**
   * 心跳定时器：客户端侧 30s 检测，连续 maxMissedPings 次未收到 PING 即主动 close。
   */
  protected startPingTimer(): void {
    this.clearPingTimer()
    this.pingMissedCount = 0
    this.pingTimer = setInterval(() => {
      this.pingMissedCount++
      if (this.pingMissedCount >= this.guardianshipOptions.maxMissedPings) {
        this.ws?.close()
      }
    }, this.guardianshipOptions.pingInterval)
  }

  protected clearPingTimer(): void {
    if (this.pingTimer) {
      clearInterval(this.pingTimer)
      this.pingTimer = null
    }
  }

  // ---- 私有方法 ----

  private handlePing(payload: Record<string, Object>): void {
    this.events.onPing?.(getStr(payload, 'serverTime'))
    // 收到 PING → 计数重置，连接活跃
    this.pingMissedCount = 0
    // 立即回复 PONG
    this.send({ type: 'pong', payload: {} })
  }
}
