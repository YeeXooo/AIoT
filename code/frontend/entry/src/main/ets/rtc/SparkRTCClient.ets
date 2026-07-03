/**
 * SparkRTC 音视频通话客户端 — SDK 外观层。
 *
 * 当前为桩实现（SDK 未集成），提供统一的 joinRoom/leaveRoom/toggleMute 等 API，
 * 待 SparkRTC SDK 就绪后替换内部实现。
 */
import { promptAction } from '@kit.ArkUI'

export interface RTCEventCallbacks {
  onRemoteUserJoined?: (userId: string) => void
  onRemoteUserLeft?: (userId: string) => void
  onError?: (code: string, message: string) => void
  onCallEnded?: () => void
}

export interface JoinRoomResult {
  ok: boolean
  message: string
}

export class SparkRTCClient {
  private static instance: SparkRTCClient | null = null
  private callbacks: RTCEventCallbacks = {}
  private inCall: boolean = false
  private muted: boolean = false
  private speakerOn: boolean = true
  private cameraOn: boolean = true

  static getInstance(): SparkRTCClient {
    if (SparkRTCClient.instance === null) {
      SparkRTCClient.instance = new SparkRTCClient()
    }
    return SparkRTCClient.instance
  }

  setCallbacks(callbacks: RTCEventCallbacks): void {
    this.callbacks = callbacks
  }

  get isInCall(): boolean {
    return this.inCall
  }

  get isMuted(): boolean {
    return this.muted
  }

  get isSpeakerOn(): boolean {
    return this.speakerOn
  }

  get isCameraOn(): boolean {
    return this.cameraOn
  }

  async joinRoom(roomId: string, userId: string, token: string, role: string): Promise<JoinRoomResult> {
    promptAction.showToast({ message: `加入通话房间 ${roomId}`, duration: 1500 })
    this.inCall = true
    return { ok: true, message: '已加入通话' }
  }

  leaveRoom(): void {
    this.inCall = false
    this.muted = false
    this.cameraOn = true
    this.speakerOn = true
    promptAction.showToast({ message: '通话已结束', duration: 1500 })
    this.callbacks.onCallEnded?.()
  }

  toggleMute(): boolean {
    this.muted = !this.muted
    promptAction.showToast({ message: this.muted ? '已静音' : '已取消静音', duration: 1000 })
    return this.muted
  }

  toggleSpeaker(): boolean {
    this.speakerOn = !this.speakerOn
    return this.speakerOn
  }

  toggleCamera(): boolean {
    this.cameraOn = !this.cameraOn
    promptAction.showToast({ message: this.cameraOn ? '摄像头已开启' : '摄像头已关闭', duration: 1000 })
    return this.cameraOn
  }
}

export const rtcClient = SparkRTCClient.getInstance()
