/**
 * 车载安全监测系统 — WebSocket 基类（ArkTS 兼容）
 *
 * 抽取 FleetWebSocket / GuardianshipWebSocket 共有的骨架逻辑：
 * 连接管理、重连退避、消息收发、定时器句柄管理。
 * 子类负责定义消息分发（handleMessageBody）与心跳策略（startPingTimer / clearPingTimer）。
 *
 * ArkTS 限制：
 * - 禁止 `as T`（T 为用户接口/泛型参数）
 * - 禁止 `any` 类型
 * - JSON.parse 返回 `object`，通过 Record 访问字段
 */

import { parseJson } from '../common/JsonParser'

// ===================================================================
// 配置选项（基类共用部分）
// ===================================================================

export interface BaseWebSocketOptions {
  /** WebSocket 基础 URL */
  baseUrl?: string
  /** 重连最大重试次数，默认 5 */
  maxReconnectAttempts?: number
  /** 重连初始退避（ms），默认 1000 */
  reconnectBaseDelay?: number
  /** 是否自动重连，默认 true */
  autoReconnect?: boolean
}

// ===================================================================
// BaseWebSocket
// ===================================================================

export abstract class BaseWebSocket {
  protected ws: WebSocket | null = null
  protected accessToken: string
  protected options: Required<BaseWebSocketOptions>
  protected reconnectAttempts = 0
  protected destroyed = false

  /** 重连定时器句柄（保存以便 disconnect/clearReconnectTimer 取消） */
  private reconnectTimerId: ReturnType<typeof setTimeout> | null = null

  constructor(accessToken: string, options: BaseWebSocketOptions = {}) {
    this.accessToken = accessToken
    this.options = {
      baseUrl: options.baseUrl ?? '',
      maxReconnectAttempts: options.maxReconnectAttempts ?? 5,
      reconnectBaseDelay: options.reconnectBaseDelay ?? 1000,
      autoReconnect: options.autoReconnect ?? true,
    }
  }

  // ---- 子类必须实现的钩子 ----

  /** 子类提供心跳定时器启动逻辑 */
  protected abstract startPingTimer(): void
  /** 子类提供心跳定时器清理逻辑 */
  protected abstract clearPingTimer(): void
  /**
   * 子类提供消息分发逻辑。
   * 入参为已解析的 message 帧（type + payload）。
   * 由基类 handleMessage 在解析成功后调用。
   */
  protected abstract dispatchMessage(msg: Record<string, unknown>): void

  // ---- 子类可选覆盖 ----

  /** 连接建立后的回调（子类可覆盖以触发 onConnected 事件） */
  protected onConnected(): void {}
  /** 连接断开后的回调（子类可覆盖以触发 onDisconnected 事件） */
  protected onDisconnected(code: number, reason: string): void {}
  /** 连接错误回调（子类可覆盖以触发 onError 事件） */
  protected onError(): void {}

  // ---- 公共 API ----

  /**
   * 建立 WebSocket 连接。
   *
   * JWT token 通过 URL query 参数传递，遵循 docs/ood_interface.md §3.1 / §4.2
   * 「连接端点 wss://...?token=<JWT>」契约。服务端在握手阶段校验 token 有效性
   * 与 AccountRole（家属端须为 FAMILY），通过后升级为 WebSocket。
   *
   * 设计说明：浏览器 WebSocket API 不支持自定义握手 header，token 经 URL query 传递
   * 是接口契约规定的方式。若未来需迁移到「首条上行鉴权消息」方案，须后端 + 文档
   * 同步升级（ood_interface.md §3.1 第 2 步、§4.2 端点表），仅改前端会破坏握手契约
   * 导致连接被拒。
   */
  connect(): void {
    // 取消任何待触发的旧重连定时器，避免 disconnect → 立即 connect 场景下
    // 旧定时器再次触发 connect() 覆盖 this.ws 造成连接泄漏（问题 6）
    this.clearReconnectTimer()

    if (this.ws?.readyState === WebSocket.OPEN) return
    this.destroyed = false

    const url = `${this.options.baseUrl}?token=${encodeURIComponent(this.accessToken)}`
    this.ws = new WebSocket(url)
    this.ws.onopen = () => this.handleOpen()
    this.ws.onmessage = (event: MessageEvent) => this.handleMessage(event)
    this.ws.onclose = (event: CloseEvent) => this.handleClose(event.code, event.reason)
    this.ws.onerror = () => this.handleError()
  }

  /** 断开 WebSocket 连接，取消所有待触发定时器 */
  disconnect(): void {
    this.destroyed = true
    this.clearPingTimer()
    this.clearReconnectTimer()
    this.reconnectAttempts = 0
    this.ws?.close()
    this.ws = null
  }

  /** 更新 JWT token */
  updateToken(token: string): void {
    this.accessToken = token
  }

  // ---- 内部共享方法 ----

  protected send(data: unknown): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }

  protected handleOpen(): void {
    this.reconnectAttempts = 0
    this.startPingTimer()
    this.onConnected()
  }

  protected handleClose(code: number, reason: string): void {
    this.clearPingTimer()
    this.onDisconnected(code, reason)

    if (!this.destroyed && this.options.autoReconnect) {
      this.scheduleReconnect()
    }
  }

  protected handleError(): void {
    this.onError()
  }

  protected handleMessage(event: MessageEvent): void {
    try {
      const msg: Record<string, unknown> = parseJson(event.data as string)
      this.dispatchMessage(msg)
    } catch (err) {
      // 问题 2 修复：传入 err 对象，保留堆栈与错误上下文便于生产排查
      console.error(`[${this.constructor.name}] 消息解析失败:`, err)
    }
  }

  /**
   * 指数退避重连：1s → 2s → 4s → 8s → 16s，最多 maxReconnectAttempts 次。
   * 保存 setTimeout 句柄，disconnect 时可取消（问题 6）。
   */
  protected scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts) {
      console.warn(`[${this.constructor.name}] 已达最大重连次数，停止重连`)
      return
    }

    const delay = this.options.reconnectBaseDelay * Math.pow(2, this.reconnectAttempts)
    this.reconnectAttempts++

    this.reconnectTimerId = setTimeout(() => {
      this.reconnectTimerId = null
      if (!this.destroyed) {
        this.connect()
      }
    }, delay)
  }

  /** 取消待触发的重连定时器（问题 6） */
  protected clearReconnectTimer(): void {
    if (this.reconnectTimerId !== null) {
      clearTimeout(this.reconnectTimerId)
      this.reconnectTimerId = null
    }
  }
}
