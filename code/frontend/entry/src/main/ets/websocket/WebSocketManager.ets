/**
 * WebSocket 统一管理器 — 连接生命周期 + 消息分发。
 *
 * 管理 GuardianshipWebSocket（家属端实时状态+告警）与 FleetWebSocket（车队大屏
 * L3 告警+绩效预警），通过回调接口向 UI 层推送解析后的类型化数据。
 */
import { GuardianshipWebSocket, type GuardianshipWSEvents } from '../api/GuardianshipWebSocket'
import { FleetWebSocket, type FleetWSEvents } from '../api/FleetWebSocket'
import { apiClient } from '../api/ApiClient'
import { getStr, getNum, getRecord, getOptRecord, getOptNum } from '../common/JsonParser'

// ===================================================================
// 回调数据类型
// ===================================================================

export interface DriverStatusSnapshot {
  driverId: string
  vehicleId: string
  timestamp: string
  heartRate: number
  spo2: number
  emotionIndex: number
  speed: number
  statusColor: string
  activeAlertLevels: string[]
}

export interface AlertPush {
  alertId: string
  alertType: string
  riskLevel: string
  occurredAt: string
  tripId: string
}

export interface FleetAlert {
  fleetId: string
  driverId: string
  vehicleId: string
  alertType: string
  occurredAt: string
}

export interface PerformanceWarning {
  driverId: string
  driverName: string
  score: number
  scorePeriod: string
  primaryPenaltyItems: string[]
}

export interface WSManagerCallbacks {
  onDriverStatusSnapshot?: (snapshot: DriverStatusSnapshot) => void
  onAlertTriggered?: (alert: AlertPush) => void
  onL3Alert?: (alert: FleetAlert) => void
  onPerformanceWarning?: (warning: PerformanceWarning) => void
  onConnected?: () => void
  onDisconnected?: () => void
}

// ===================================================================
// WebSocketManager
// ===================================================================

export class WebSocketManager {
  private static instance: WebSocketManager | null = null
  private guardianshipWS: GuardianshipWebSocket | null = null
  private fleetWS: FleetWebSocket | null = null
  private callbacks: WSManagerCallbacks = {}
  private connected: boolean = false

  static getInstance(): WebSocketManager {
    if (WebSocketManager.instance === null) {
      WebSocketManager.instance = new WebSocketManager()
    }
    return WebSocketManager.instance
  }

  /** 增量注册回调（按字段合并，未传的字段保留旧值） */
  setCallbacks(callbacks: WSManagerCallbacks): void {
    if (callbacks.onDriverStatusSnapshot !== undefined) {
      this.callbacks.onDriverStatusSnapshot = callbacks.onDriverStatusSnapshot
    }
    if (callbacks.onAlertTriggered !== undefined) {
      this.callbacks.onAlertTriggered = callbacks.onAlertTriggered
    }
    if (callbacks.onL3Alert !== undefined) {
      this.callbacks.onL3Alert = callbacks.onL3Alert
    }
    if (callbacks.onPerformanceWarning !== undefined) {
      this.callbacks.onPerformanceWarning = callbacks.onPerformanceWarning
    }
    if (callbacks.onConnected !== undefined) {
      this.callbacks.onConnected = callbacks.onConnected
    }
    if (callbacks.onDisconnected !== undefined) {
      this.callbacks.onDisconnected = callbacks.onDisconnected
    }
  }

  /** 建立两条 WebSocket 连接。token 后续由 SessionStore 提供。 */
  connect(): void {
    if (this.connected) return
    this.connected = true

    const token = apiClient.getAccessToken() ?? ''
    this.connectGuardianship(token)
    this.connectFleet(token)
  }

  /** 断开所有连接并清理实例 */
  disconnect(): void {
    this.connected = false
    this.guardianshipWS?.disconnect()
    this.fleetWS?.disconnect()
    this.guardianshipWS = null
    this.fleetWS = null
  }

  /** 订阅指定驾驶员的状态推送 */
  subscribeDriverStatus(driverId: string): void {
    this.guardianshipWS?.subscribeDriverStatus(driverId)
  }

  // ===================================================================
  // 私有：WS 实例创建
  // ===================================================================

  private connectGuardianship(token: string): void {
    const events: GuardianshipWSEvents = {
      onConnected: (_msg: Record<string, unknown>) => {
        this.callbacks.onConnected?.()
      },
      onDisconnected: (_code: number, _reason: string) => {
        this.callbacks.onDisconnected?.()
      },
      onError: (_err: Record<string, unknown>) => {
        // 由 WS 自身处理重连，UI 层不感知瞬时错误
      },
      onDriverStatusSnapshot: (snapshot: Record<string, unknown>) => {
        this.dispatchStatusSnapshot(snapshot)
      },
      onAlertTriggered: (alert: Record<string, unknown>) => {
        this.dispatchAlert(alert)
      },
    }

    this.guardianshipWS = new GuardianshipWebSocket(token, events)
    this.guardianshipWS.connect()
  }

  private connectFleet(token: string): void {
    const events: FleetWSEvents = {
      onConnected: () => {
        this.callbacks.onConnected?.()
      },
      onDisconnected: (_code: number, _reason: string) => {
        this.callbacks.onDisconnected?.()
      },
      onError: (_code: string, _message: string) => {
        // 由 WS 自身处理重连
      },
      onL3Alert: (msg: Record<string, unknown>) => {
        this.dispatchL3Alert(msg)
      },
      onPerformanceWarning: (msg: Record<string, unknown>) => {
        this.dispatchPerformanceWarning(msg)
      },
    }

    this.fleetWS = new FleetWebSocket(token, events)
    this.fleetWS.connect()
  }

  // ===================================================================
  // 私有：消息解析与回调转发
  // ===================================================================

  private dispatchStatusSnapshot(snapshot: Record<string, unknown>): void {
    const cb = this.callbacks.onDriverStatusSnapshot
    if (cb === undefined) return

    // 生理数据从 sub-object 提取
    let heartRate = 0
    let spo2 = 0
    let emotionIndex = 0
    const physSummary = getOptRecord(snapshot, 'physiologicalSummary')
    if (physSummary !== undefined) {
      heartRate = getNum(physSummary, 'heartRate')
      spo2 = getNum(physSummary, 'spo2')
      emotionIndex = getNum(physSummary, 'emotionIndex')
    }

    // 车速
    let speed = 0
    const optSpeed = getOptNum(snapshot, 'speed')
    if (optSpeed !== undefined) {
      speed = optSpeed
    }

    // 活跃告警级别
    const activeAlertLevels: string[] = []
    const rawLevels: Record<string, unknown> = getRecord(snapshot, 'activeAlertLevels')
    const levelKeys = Object.keys(rawLevels)
    for (let i = 0; i < levelKeys.length; i++) {
      activeAlertLevels.push(rawLevels[levelKeys[i]] as string)
    }

    cb({
      driverId: getStr(snapshot, 'driverId'),
      vehicleId: getStr(snapshot, 'vehicleId'),
      timestamp: getStr(snapshot, 'timestamp'),
      heartRate: heartRate,
      spo2: spo2,
      emotionIndex: emotionIndex,
      speed: speed,
      statusColor: 'GREEN',
      activeAlertLevels: activeAlertLevels,
    })
  }

  private dispatchAlert(alert: Record<string, unknown>): void {
    const cb = this.callbacks.onAlertTriggered
    if (cb === undefined) return

    cb({
      alertId: getStr(alert, 'alertId'),
      alertType: getStr(alert, 'alertType'),
      riskLevel: getStr(alert, 'riskLevel'),
      occurredAt: getStr(alert, 'occurredAt'),
      tripId: getStr(alert, 'tripId'),
    })
  }

  private dispatchL3Alert(msg: Record<string, unknown>): void {
    const cb = this.callbacks.onL3Alert
    if (cb === undefined) return

    cb({
      fleetId: getStr(msg, 'fleetId'),
      driverId: getStr(msg, 'driverId'),
      vehicleId: getStr(msg, 'vehicleId'),
      alertType: getStr(msg, 'alertType'),
      occurredAt: getStr(msg, 'occurredAt'),
    })
  }

  private dispatchPerformanceWarning(msg: Record<string, unknown>): void {
    const cb = this.callbacks.onPerformanceWarning
    if (cb === undefined) return

    const penaltyArr: string[] = []
    const rawArr = msg['primaryPenaltyItems']
    if (rawArr !== undefined && rawArr !== null) {
      const arr = rawArr as object[]
      for (let i = 0; i < arr.length; i++) {
        penaltyArr.push(arr[i] as string)
      }
    }

    cb({
      driverId: getStr(msg, 'driverId'),
      driverName: getStr(msg, 'driverName'),
      score: getNum(msg, 'score'),
      scorePeriod: getStr(msg, 'scorePeriod'),
      primaryPenaltyItems: penaltyArr,
    })
  }
}

export const wsManager = WebSocketManager.getInstance()
