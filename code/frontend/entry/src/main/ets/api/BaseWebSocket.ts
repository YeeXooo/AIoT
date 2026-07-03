import { webSocket } from '@kit.NetworkKit'
import { BusinessError } from '@kit.BasicServicesKit'
import { parseJson } from '../common/JsonParser'

export interface BaseWebSocketOptions {
  baseUrl?: string
  maxReconnectAttempts?: number
  reconnectBaseDelay?: number
  autoReconnect?: boolean
}

export abstract class BaseWebSocket {
  protected ws: webSocket.WebSocket | null = null
  protected accessToken: string
  protected options: Required<BaseWebSocketOptions>
  protected reconnectAttempts = 0
  protected destroyed = false
  private reconnectTimerId: number = -1

  constructor(accessToken: string, options: BaseWebSocketOptions = {}) {
    this.accessToken = accessToken
    this.options = {
      baseUrl: options.baseUrl ?? '',
      maxReconnectAttempts: options.maxReconnectAttempts ?? 5,
      reconnectBaseDelay: options.reconnectBaseDelay ?? 1000,
      autoReconnect: options.autoReconnect ?? true,
    }
  }

  protected abstract startPingTimer(): void
  protected abstract clearPingTimer(): void
  protected abstract dispatchMessage(msg: Record<string, Object>): void

  protected onConnected(): void {}
  protected onDisconnected(_code: number, _reason: string): void {}
  protected onError(): void {}

  connect(): void {
    this.clearReconnectTimer()
    if (this.ws !== null) {
      return
    }
    this.destroyed = false

    const url = `${this.options.baseUrl}?token=${encodeURIComponent(this.accessToken)}`
    this.ws = webSocket.createWebSocket()

    this.ws.on('open', (_err: BusinessError, _value: Object) => {
      this.handleOpen()
    })

    this.ws.on('message', (_err: BusinessError, value: string | ArrayBuffer) => {
      const dataStr = typeof value === 'string' ? value : ''
      if (dataStr.length > 0) {
        this.handleMessageStr(dataStr)
      }
    })

    this.ws.on('close', (_err: BusinessError, value: webSocket.CloseResult) => {
      this.ws = null
      this.handleClose(value.code, value.reason)
    })

    this.ws.on('error', (_err: BusinessError) => {
      this.handleError()
    })

    this.ws.connect(url, (_err: BusinessError, _value: boolean) => {
      if (_err) {
        this.handleError()
      }
    })
  }

  disconnect(): void {
    this.destroyed = true
    this.clearPingTimer()
    this.clearReconnectTimer()
    this.reconnectAttempts = 0
    if (this.ws !== null) {
      this.ws.close((_err: BusinessError, _value: boolean) => {})
      this.ws = null
    }
  }

  updateToken(token: string): void {
    this.accessToken = token
  }

  protected send(data: Object): void {
    if (this.ws !== null) {
      this.ws.send(JSON.stringify(data), (_err: BusinessError, _value: boolean) => {})
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

  protected handleMessageStr(data: string): void {
    try {
      const msg: Record<string, Object> = parseJson(data)
      this.dispatchMessage(msg)
    } catch (_err) {
    }
  }

  protected scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.options.maxReconnectAttempts) {
      return
    }
    const delay = this.options.reconnectBaseDelay * Math.pow(2, this.reconnectAttempts)
    this.reconnectAttempts++
    this.reconnectTimerId = setTimeout(() => {
      this.reconnectTimerId = -1
      if (!this.destroyed) {
        this.connect()
      }
    }, delay) as number
  }

  protected clearReconnectTimer(): void {
    if (this.reconnectTimerId !== -1) {
      clearTimeout(this.reconnectTimerId)
      this.reconnectTimerId = -1
    }
  }
}
