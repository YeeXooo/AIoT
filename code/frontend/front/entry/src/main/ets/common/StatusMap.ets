/**
 * 状态色 / 文案 / 图标集中映射
 *
 * 消除 MainPage 中重复 6+ 处的 `status === 'GREEN' ? ... : YELLOW ? ... : RED` 三元嵌套。
 * 同时统一 RiskLevel (L1/L2/L3) 与 StatusColor (GREEN/YELLOW/RED) 的视觉对应。
 *
 * 注意：本文件必须为 .ets — $r() 全局资源宏仅在 ArkTS UI 文件中可用。
 */
import type { RiskLevel, StatusColor, AlertType, OfflineReason } from '../viewmodel'

// ---- StatusColor → 资源 ----

export interface StatusVisual {
  text: ResourceStr
  color: Resource
  bgColor: Resource
}

export function statusVisual(color: StatusColor): StatusVisual {
  if (color === 'GREEN') {
    return {
      text: $r('app.string.status_normal'),
      color: $r('app.color.status_green'),
      bgColor: $r('app.color.status_green_bg'),
    }
  }
  if (color === 'YELLOW') {
    return {
      text: $r('app.string.status_warning'),
      color: $r('app.color.status_yellow'),
      bgColor: $r('app.color.status_yellow_bg'),
    }
  }
  return {
    text: $r('app.string.status_critical'),
    color: $r('app.color.status_red'),
    bgColor: $r('app.color.status_red_bg'),
  }
}

// 离线态单独处理（灰色）
export function offlineStatusVisual(): StatusVisual {
  return {
    text: $r('app.string.status_offline'),
    color: $r('app.color.text_hint'),
    bgColor: $r('app.color.divider'),
  }
}

/** 司机状态（含 OFFLINE）→ 视觉 */
export function driverStatusVisual(status: StatusColor | 'OFFLINE'): StatusVisual {
  if (status === 'OFFLINE') {
    return offlineStatusVisual()
  }
  return statusVisual(status)
}

// ---- RiskLevel → 资源 ----

export interface RiskVisual {
  label: string
  color: Resource
  bgColor: Resource
}

export function riskVisual(level: RiskLevel): RiskVisual {
  if (level === 'L1_HINT') {
    return { label: 'L1', color: $r('app.color.status_green'), bgColor: $r('app.color.status_green_bg') }
  }
  if (level === 'L2_WARNING') {
    return { label: 'L2', color: $r('app.color.status_yellow'), bgColor: $r('app.color.status_yellow_bg') }
  }
  return { label: 'L3', color: $r('app.color.status_red'), bgColor: $r('app.color.status_red_bg') }
}

// ---- AlertType → 中文文案 ----

export function alertTypeLabel(type: AlertType): string {
  switch (type) {
    case 'FATIGUE': return '疲劳驾驶'
    case 'DISTRACTION': return '分心驾驶'
    case 'ROAD_RAGE': return '路怒行为'
    case 'LIFE_DETECTION': return '生命体征'
    case 'COLLISION_DISABILITY': return '碰撞失能'
    case 'PERFORMANCE_WARNING': return '绩效预警'
    default: return '未知告警'
  }
}

// ---- OfflineReason → 中文文案 ----

export function offlineReasonLabel(reason: OfflineReason): string {
  if (reason === 'SENSOR_FAULT') return '传感器故障'
  return '通信中断'
}

// ---- 时间格式化（ISO → HH:mm） ----

export function formatTime(iso: string): string {
  // 简单截取 HH:mm，避免引入额外依赖
  if (iso.length >= 16) {
    return iso.substring(11, 16)
  }
  return iso
}
