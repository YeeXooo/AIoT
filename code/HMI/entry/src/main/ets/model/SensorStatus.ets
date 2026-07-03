export class SensorStatusItem {
  readonly name: string
  readonly healthy: boolean
  readonly lastUpdated: number

  constructor(name: string, healthy: boolean, lastUpdated: number) {
    this.name = name
    this.healthy = healthy
    this.lastUpdated = lastUpdated
  }
}

export class SensorStatus {
  readonly camera: SensorStatusItem
  readonly radar: SensorStatusItem
  readonly physiological: SensorStatusItem

  constructor(camera: SensorStatusItem, radar: SensorStatusItem, physiological: SensorStatusItem) {
    this.camera = camera
    this.radar = radar
    this.physiological = physiological
  }

  static createDefault(): SensorStatus {
    const now = Date.now()
    return new SensorStatus(
      new SensorStatusItem('摄像头', true, now),
      new SensorStatusItem('雷达', true, now),
      new SensorStatusItem('生理', true, now)
    )
  }
}
