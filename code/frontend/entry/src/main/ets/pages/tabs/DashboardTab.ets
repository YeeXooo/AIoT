import { dashboardVM, DashboardData, ViewState, AlertSummary } from '../../viewmodel'
import { AppCard, StatusBadge, RiskBadge, EmptyState, SkeletonRow, ErrorRetry } from '../../common/ui'
import { alertTypeLabel, formatTime } from '../../common/StatusMap'
import { wsManager, type DriverStatusSnapshot, type AlertPush } from '../../websocket/WebSocketManager'

@Component
export struct DashboardTab {
  @State view: ViewState<DashboardData> = { state: 'idle', data: null, errorMsg: '' }
  @State refreshing: boolean = false
  @StorageProp('currentDriverName') @Watch('onDriverChanged') driverName: string = '张明'
  @StorageProp('currentDriverVehicleId') vehicleId: string = '京A·88562'
  @StorageProp('currentDriverId') @Watch('onDriverChanged') driverId: string = 'd1'
  @State private heartRate: number = 72
  @State private speed: number = 68
  @State private spo2: number = 98
  @State private emotionIndex: number = 0.72
  private tripDuration: string = '1小时23分'
  @State private card1OffsetY: number = 30
  @State private card1Opacity: number = 0
  @State private card2OffsetY: number = 30
  @State private card2Opacity: number = 0
  @State private card3OffsetY: number = 30
  @State private card3Opacity: number = 0
  @State private speedDisplay: number = 0

  onDriverChanged(): void {
    dashboardVM.driverId = this.driverId
    this.refresh(false)
  }

  aboutToAppear(): void {
    dashboardVM.driverId = this.driverId
    this.refresh(false)
    wsManager.setCallbacks({
      onDriverStatusSnapshot: (snapshot: DriverStatusSnapshot) => {
        this.heartRate = snapshot.heartRate > 0 ? snapshot.heartRate : this.heartRate
        this.speed = snapshot.speed > 0 ? snapshot.speed : this.speed
        this.spo2 = snapshot.spo2 > 0 ? snapshot.spo2 : this.spo2
        this.emotionIndex = snapshot.emotionIndex > 0 ? snapshot.emotionIndex : this.emotionIndex
      },
      onAlertTriggered: (_alert: AlertPush) => {
        this.refresh(false)
      },
    })
    wsManager.connect()
    wsManager.subscribeDriverStatus('d1')
    setTimeout(() => {
      this.speedDisplay = this.speed
    }, 600)
  }

  private async refresh(showRefreshing: boolean): Promise<void> {
    if (showRefreshing) this.refreshing = true
    this.view = { state: 'loading', data: this.view.data, errorMsg: '' }
    const result = await dashboardVM.load()
    this.view = result
    this.refreshing = false
    if (result.data !== null) {
      setTimeout(() => {
        this.card1OffsetY = 0; this.card1Opacity = 1
      }, 100)
      setTimeout(() => {
        this.card2OffsetY = 0; this.card2Opacity = 1
      }, 250)
      setTimeout(() => {
        this.card3OffsetY = 0; this.card3Opacity = 1
      }, 400)
    }
  }

  build() {
    Refresh({ refreshing: $$this.refreshing }) {
      Scroll() {
        Column({ space: 16 }) {
          Row() {
            Column({ space: 2 }) {
              Text($r('app.string.driver_status_title'))
                .fontSize($r('app.float.title_font_size'))
                .fontWeight(FontWeight.Bold)
                .fontColor($r('app.color.text_on_dark'))
              Text(this.vehicleId)
                .fontSize($r('app.float.caption_font_size'))
                .fontColor($r('app.color.text_muted'))
            }
            .alignItems(HorizontalAlign.Start)
            Blank()
            if (this.view.data !== null) {
              StatusBadge({ status: this.view.data.statusColor })
            }
          }
          .width('100%')
          .padding({ left: 20, right: 20, top: 24 })

          if (this.view.state === 'loading' && this.view.data === null) {
            this.LoadingBlock()
          } else if (this.view.state === 'error' && this.view.data === null) {
            ErrorRetry({
              message: this.view.errorMsg,
              retryAction: () => { this.refresh(false) },
            })
          } else if (this.view.data !== null) {
            this.StatusCard()
            this.VitalRow()
            this.AlertSection(this.view.data)
          }
        }
      }
      .scrollBar(BarState.Off)
      .edgeEffect(EdgeEffect.Spring)
      .width('100%')
      .height('100%')
    }
    .onRefreshing(() => this.refresh(true))
    .width('100%')
    .height('100%')
  }

  @Builder
  LoadingBlock() {
    Column({ space: 8 }) {
      SkeletonRow()
      SkeletonRow()
      SkeletonRow()
    }
    .padding({ left: 16, right: 16, top: 8 })
  }

  @Builder
  StatusCard() {
    Column() {
      Row() {
        Column({ space: 4 }) {
          Text(this.driverName)
            .fontSize($r('app.float.subtitle_font_size'))
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_on_dark'))
          Text(`行程中 · ${this.tripDuration}`)
            .fontSize($r('app.float.caption_font_size'))
            .fontColor($r('app.color.text_muted'))
          if (this.view.data !== null && this.view.data.activeAlerts.length > 0) {
            Text(`${this.view.data.activeAlerts.length}条活跃告警`)
              .fontSize(11)
              .fontColor($r('app.color.danger'))
              .margin({ top: 2 })
          }
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        Stack() {
          Column() {
            Text(`${this.speedDisplay}`)
              .fontSize(40)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.text_on_dark'))
              .animation({ duration: 600, curve: Curve.EaseOut })
            Text('km/h')
              .fontSize(11)
              .fontColor($r('app.color.text_dim'))
              .margin({ top: -4 })
          }
          .alignItems(HorizontalAlign.Center)
          Circle()
            .width(90)
            .height(90)
            .fill(Color.Transparent)
            .stroke($r('app.color.speed_ring_stroke'))
            .strokeWidth(2)
        }
        .width(90)
        .height(90)
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
    }
    .width('100%')
    .padding(16)
    .backgroundColor($r('app.color.card_bg_dark'))
    .borderRadius(16)
    .border({ width: 1, color: $r('app.color.card_border') })
    .shadow({ radius: 12, color: $r('app.color.card_shadow_subtle'), offsetX: 0, offsetY: 4 })
    .opacity(this.card1Opacity)
    .translate({ y: this.card1OffsetY })
    .animation({ duration: 500, curve: Curve.EaseOut })
    .margin({ left: 16, right: 16 })
  }

  @Builder
  VitalRow() {
    Row({ space: 10 }) {
      this.VitalItem('心率', `${this.heartRate}`, 'bpm', $r('app.color.vital_heart'), $r('app.color.vital_heart_dark'))
      this.VitalItem('血氧', `${this.spo2}`, '%', $r('app.color.vital_spo2'), $r('app.color.vital_spo2_dark'))
      this.VitalItem('情绪', this.emotionIndex.toFixed(2), '', $r('app.color.vital_emotion'), $r('app.color.vital_emotion_dark'))
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
    .opacity(this.card2Opacity)
    .translate({ y: this.card2OffsetY })
    .animation({ duration: 500, curve: Curve.EaseOut })
  }

  @Builder
  VitalItem(label: string, value: string, unit: string, color: Resource, darkColor: Resource) {
    Column({ space: 8 }) {
      Text(label)
        .fontSize(11)
        .fontColor($r('app.color.text_muted'))
        .letterSpacing(1)
      Row({ space: 2 }) {
        Text(value)
          .fontSize(24)
          .fontWeight(FontWeight.Bold)
          .fontColor(color)
          .animation({ duration: 400, curve: Curve.EaseOut })
        if (unit.length > 0) {
          Text(unit)
            .fontSize(11)
            .fontColor($r('app.color.text_dim'))
            .margin({ bottom: 4 })
        }
      }
      .alignItems(VerticalAlign.Bottom)
      Column()
        .width('100%')
        .height(3)
        .borderRadius(2)
        .linearGradient({
          direction: GradientDirection.Right,
          colors: [[darkColor, 0.0], [color, 1.0]]
        })
    }
    .layoutWeight(1)
    .padding({ top: 16, bottom: 14, left: 12, right: 12 })
    .alignItems(HorizontalAlign.Center)
    .backgroundColor($r('app.color.card_bg_dark'))
    .borderRadius(14)
    .border({ width: 1, color: $r('app.color.card_border') })
    .shadow({ radius: 8, color: $r('app.color.card_shadow_subtle'), offsetX: 0, offsetY: 2 })
  }

  @Builder
  AlertSection(data: DashboardData) {
    Column() {
      Row() {
        Text($r('app.string.alert_history_title'))
          .fontSize($r('app.float.subtitle_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_on_dark'))
        Blank()
        Text('查看全部')
          .fontSize($r('app.float.caption_font_size'))
          .fontColor($r('app.color.accent_blue'))
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 4, bottom: 8 })

      if (data.alertHistory.length === 0) {
        EmptyState({ icon: '✓', message: $r('app.string.no_alert'), hint: '当前行程无告警记录' })
      } else {
        Column({ space: 10 }) {
          ForEach(data.alertHistory, (item: AlertSummary, index: number) => {
            this.AlertRow(item, index)
          }, (item: AlertSummary) => item.alertId)
        }
        .width('100%')
        .padding({ left: 16, right: 16 })
      }
    }
    .width('100%')
    .opacity(this.card3Opacity)
    .translate({ y: this.card3OffsetY })
    .animation({ duration: 500, curve: Curve.EaseOut })
  }

  @Builder
  AlertRow(item: AlertSummary, index: number) {
    Row() {
      Stack({ alignContent: Alignment.Center }) {
        Circle()
          .width(8)
          .height(8)
          .fill(item.riskLevel === 'L3_CRITICAL' ? $r('app.color.danger') :
                item.riskLevel === 'L2_WARNING' ? $r('app.color.warning_color') : $r('app.color.success_color'))
        if (item.riskLevel === 'L3_CRITICAL') {
          Circle()
            .width(16)
            .height(16)
            .fill($r('app.color.alert_critical_glow'))
            .opacity(0.3)
            .blur(4)
        }
      }
      .width(20)
      .height(20)
      Column({ space: 3 }) {
        Text(alertTypeLabel(item.alertType))
          .fontSize($r('app.float.body_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_on_dark'))
        Text(formatTime(item.occurredAt))
          .fontSize(11)
          .fontColor($r('app.color.text_dim'))
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      .margin({ left: 8 })
      Blank()
      RiskBadge({ level: item.riskLevel })
    }
    .width('100%')
    .padding({ left: 14, right: 14, top: 14, bottom: 14 })
    .backgroundColor($r('app.color.card_bg_dark'))
    .borderRadius(14)
    .border({ width: 1, color: $r('app.color.card_border') })
    .shadow({ radius: 6, color: $r('app.color.card_shadow_subtle'), offsetX: 0, offsetY: 2 })
  }
}
