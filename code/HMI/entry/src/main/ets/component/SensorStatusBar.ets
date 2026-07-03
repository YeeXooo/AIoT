import { SensorStatus, SensorStatusItem } from '../model/SensorStatus'

class SensorIndicatorParams {
  item: SensorStatusItem

  constructor(item: SensorStatusItem) {
    this.item = item
  }
}

@Component
export struct SensorStatusBar {
  @Prop sensorStatus: SensorStatus = SensorStatus.createDefault()
  @Prop sensorFailureAlert: boolean = false
  @State greenBreath: number = 1.0
  @State failurePulse: number = 1.0
  private greenTimer: number = -1
  private failureTimer: number = -1
  private greenGoingDown: boolean = true
  private failureGoingDown: boolean = true

  aboutToAppear(): void {
    this.startGreenBreathing()
  }

  aboutToDisappear(): void {
    if (this.greenTimer !== -1) { clearInterval(this.greenTimer) }
    if (this.failureTimer !== -1) { clearInterval(this.failureTimer) }
  }

  private startGreenBreathing(): void {
    this.greenTimer = setInterval(() => {
      if (this.greenGoingDown) {
        this.greenBreath = Math.max(0.3, this.greenBreath - 0.05)
        if (this.greenBreath <= 0.3) { this.greenGoingDown = false }
      } else {
        this.greenBreath = Math.min(1.0, this.greenBreath + 0.05)
        if (this.greenBreath >= 1.0) { this.greenGoingDown = true }
      }
    }, 50)
  }

  build() {
    Row({ space: 12 }) {
      this.SensorIndicator(new SensorIndicatorParams(this.sensorStatus.camera))
      this.SensorIndicator(new SensorIndicatorParams(this.sensorStatus.radar))
      this.SensorIndicator(new SensorIndicatorParams(this.sensorStatus.physiological))

      Blank()

      if (this.sensorFailureAlert) {
        Row({ space: 8 }) {
          Row()
            .width(6)
            .height(6)
            .borderRadius(3)
            .backgroundColor('#FF453A')
            .shadow({ radius: 8, color: '#66FF453A', offsetX: 0, offsetY: 0 })
          Text('MONITOR OFFLINE')
            .fontSize(11)
            .fontColor('#FF453A')
            .fontWeight(FontWeight.Medium)
            .letterSpacing(2)
        }
        .padding({ left: 14, right: 14, top: 6, bottom: 6 })
        .borderRadius(12)
        .backgroundColor('rgba(255,69,58,0.15)')
        .opacity(this.failurePulse)
        .animation({ duration: 50, curve: Curve.Linear })
        .onAppear(() => {
          this.startFailurePulse()
        })
        .onDisAppear(() => {
          if (this.failureTimer !== -1) { clearInterval(this.failureTimer) }
        })
      }
    }
    .width('100%')
    .height(52)
    .padding({ left: 32, right: 32 })
    .alignItems(VerticalAlign.Center)
  }

  private startFailurePulse(): void {
    this.failureTimer = setInterval(() => {
      if (this.failureGoingDown) {
        this.failurePulse = Math.max(0.3, this.failurePulse - 0.06)
        if (this.failurePulse <= 0.3) { this.failureGoingDown = false }
      } else {
        this.failurePulse = Math.min(1.0, this.failurePulse + 0.06)
        if (this.failurePulse >= 1.0) { this.failureGoingDown = true }
      }
    }, 50)
  }

  @Builder
  SensorIndicator(params: SensorIndicatorParams) {
    Row({ space: 8 }) {
      Row()
        .width(8)
        .height(8)
        .borderRadius(4)
        .backgroundColor(params.item.healthy ? '#30D158' : '#FF453A')
        .opacity(params.item.healthy ? this.greenBreath : this.failurePulse)
        .animation({ duration: 50, curve: Curve.Linear })
        .shadow({
          radius: params.item.healthy ? this.greenBreath * 10 + 2 : 10,
          color: params.item.healthy ? '#5530D158' : '#66FF453A',
          offsetX: 0,
          offsetY: 0
        })
        .animation({ duration: 50, curve: Curve.Linear })
      Text(params.item.name)
        .fontSize(13)
        .fontColor(params.item.healthy ? $r('app.color.hmi_text_secondary') : '#FF453A')
        .fontWeight(FontWeight.Medium)
    }
    .padding({ left: 12, right: 12, top: 6, bottom: 6 })
    .borderRadius(12)
    .backgroundColor(params.item.healthy ? 'rgba(255,255,255,0.05)' : 'rgba(255,69,58,0.1)')
  }
}
