import { InterventionType } from './InterventionType'

export class InterventionInstruction {
  readonly type: InterventionType
  readonly params: Map<string, string>
  readonly description: string

  constructor(type: InterventionType, params: Map<string, string>, description: string) {
    this.type = type
    this.params = params
    this.description = description
  }

  static createAmbientLight(color: string): InterventionInstruction {
    const params = new Map<string, string>()
    params.set('color', color)
    return new InterventionInstruction(
      InterventionType.AMBIENT_LIGHT_COLOR,
      params,
      '氛围灯颜色: ' + color
    )
  }

  static createVoiceBroadcast(message: string): InterventionInstruction {
    const params = new Map<string, string>()
    params.set('message', message)
    return new InterventionInstruction(
      InterventionType.VOICE_BROADCAST,
      params,
      message
    )
  }

  static createSeatVibration(): InterventionInstruction {
    return new InterventionInstruction(
      InterventionType.SEAT_VIBRATION,
      new Map<string, string>(),
      '座椅高强度震动'
    )
  }

  static createHazardLights(): InterventionInstruction {
    return new InterventionInstruction(
      InterventionType.HAZARD_LIGHTS,
      new Map<string, string>(),
      '开启双闪'
    )
  }

  static createAirConditioning(tempDelta: number): InterventionInstruction {
    const params = new Map<string, string>()
    params.set('tempDelta', tempDelta.toString())
    return new InterventionInstruction(
      InterventionType.AIR_CONDITIONING,
      params,
      '空调降温' + tempDelta.toString() + '°C'
    )
  }

  static createAudioPlayback(audioType: string): InterventionInstruction {
    const params = new Map<string, string>()
    params.set('audioType', audioType)
    return new InterventionInstruction(
      InterventionType.AUDIO_PLAYBACK,
      params,
      '播放' + audioType
    )
  }

  static createCanDecelerationRequest(): InterventionInstruction {
    return new InterventionInstruction(
      InterventionType.CAN_DECELERATION_REQUEST,
      new Map<string, string>(),
      'CAN减速请求'
    )
  }

  static createNavigateDeceleration(): InterventionInstruction {
    return new InterventionInstruction(
      InterventionType.NAVIGATE_DECELERATION,
      new Map<string, string>(),
      '建议减速'
    )
  }

  static createNavigateToShoulder(): InterventionInstruction {
    return new InterventionInstruction(
      InterventionType.NAVIGATE_TO_SHOULDER,
      new Map<string, string>(),
      '引导靠边停车'
    )
  }

  static createAlert(): InterventionInstruction {
    return new InterventionInstruction(
      InterventionType.ALERT,
      new Map<string, string>(),
      '告警提示'
    )
  }
}
