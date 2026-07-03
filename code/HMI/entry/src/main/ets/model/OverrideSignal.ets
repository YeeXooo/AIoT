export enum OverrideType {
  STEER = 'STEER',
  BRAKE = 'BRAKE',
  ACCELERATE = 'ACCELERATE'
}

export class OverrideSignal {
  readonly type: OverrideType
  readonly timestamp: number

  constructor(type: OverrideType, timestamp: number) {
    this.type = type
    this.timestamp = timestamp
  }
}
