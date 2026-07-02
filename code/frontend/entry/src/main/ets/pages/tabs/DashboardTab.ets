/**
 * 监测 Tab — 驾驶员风险状态 + 生命体征 + 告警历史。
 * 接 DashboardViewModel，支持下拉刷新 + 加载/错误/空三态。
 */
import { dashboardVM, DashboardData, ViewState, AlertSummary } from '../../viewmodel'
import { AppCard, StatusBadge, RiskBadge, EmptyState, SkeletonRow, ErrorRetry } from '../../common/ui'
import { alertTypeLabel, formatTime } from '../../common/StatusMap'

@Component
export struct DashboardTab {
  @State view: ViewState<DashboardData> = { state: 'idle', data: null, errorMsg: '' }
  @State refreshing: boolean = false
  private driverName: string = '张明'
  private vehicleId: string = '京A·88562'
  private heartRate: number = 72
  private speed: number = 68
  private tripDuration: string = '1小时23分'

  aboutToAppear(): void {
    this.refresh(false)
  }

  private async refresh(showRefreshing: boolean): Promise<void> {
    if (showRefreshing) this.refreshing = true
    this.view = { state: 'loading', data: this.view.data, errorMsg: '' }
    const result = await dashboardVM.load()
    this.view = result
    this.refreshing = false
  }

  build() {
    Refresh({ refreshing: $$this.refreshing }) {
      Scroll() {
        Column({ space: 14 }) {
          Row() {
            Column({ space: 2 }) {
              Text($r('app.string.driver_status_title'))
                .fontSize($r('app.float.title_font_size'))
                .fontWeight(FontWeight.Bold)
                .fontColor($r('app.color.text_primary'))
              Text(this.vehicleId)
                .fontSize($r('app.float.caption_font_size'))
                .fontColor($r('app.color.text_secondary'))
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
    AppCard({ space: 16 }) {
      Column({ space: 4 }) {
        Text(this.driverName)
          .fontSize($r('app.float.subtitle_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Text(`行程中 · ${this.tripDuration}`)
          .fontSize($r('app.float.caption_font_size'))
          .fontColor($r('app.color.text_secondary'))
        if (this.view.data !== null && this.view.data.activeAlerts.length > 0) {
          Text(`${this.view.data.activeAlerts.length}条活跃告警`)
            .fontSize(11)
            .fontColor($r('app.color.status_red'))
            .margin({ top: 2 })
        }
      }
      .alignItems(HorizontalAlign.Start)
      .layoutWeight(1)
      Row({ space: 2 }) {
        Text(`${this.speed}`)
          .fontSize(32)
          .fontWeight(FontWeight.Bold)
          .fontColor($r('app.color.text_primary'))
        Text('km/h')
          .fontSize(11)
          .fontColor($r('app.color.text_secondary'))
          .margin({ bottom: 4 })
      }
      .alignItems(VerticalAlign.Bottom)
    }
  }

  @Builder
  VitalRow() {
    Row({ space: 12 }) {
      this.VitalItem('心率', `${this.heartRate}`, 'bpm', $r('app.color.status_green'))
      this.VitalItem('血氧', '98', '%', $r('app.color.status_green'))
      this.VitalItem('情绪', '0.72', '', $r('app.color.brand_accent'))
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
  }

  @Builder
  VitalItem(label: string, value: string, unit: string, color: Resource) {
    Column({ space: 6 }) {
      Text(label)
        .fontSize(11)
        .fontColor($r('app.color.text_secondary'))
      Row({ space: 2 }) {
        Text(value)
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor(color)
        if (unit.length > 0) {
          Text(unit)
            .fontSize(11)
            .fontColor($r('app.color.text_secondary'))
            .margin({ bottom: 3 })
        }
      }
      .alignItems(VerticalAlign.Bottom)
    }
    .layoutWeight(1)
    .padding({ top: 14, bottom: 14 })
    .alignItems(HorizontalAlign.Center)
    .backgroundColor($r('app.color.bg_card'))
    .borderRadius($r('app.float.card_radius'))
    .shadow({ radius: 8, color: $r('app.color.shadow_card'), offsetX: 0, offsetY: 1 })
  }

  @Builder
  AlertSection(data: DashboardData) {
    Column() {
      Row() {
        Text($r('app.string.alert_history_title'))
          .fontSize($r('app.float.subtitle_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Blank()
        Text('查看全部')
          .fontSize($r('app.float.caption_font_size'))
          .fontColor($r('app.color.brand_accent'))
      }
      .width('100%')
      .padding({ left: 16, right: 16, top: 4, bottom: 8 })

      if (data.alertHistory.length === 0) {
        EmptyState({ icon: '✓', message: $r('app.string.no_alert'), hint: '当前行程无告警记录' })
      } else {
        Column({ space: 8 }) {
          ForEach(data.alertHistory, (item: AlertSummary) => {
            this.AlertRow(item)
          }, (item: AlertSummary) => item.alertId)
        }
        .width('100%')
        .padding({ left: 16, right: 16 })
      }
    }
    .width('100%')
  }

  @Builder
  AlertRow(item: AlertSummary) {
    Row() {
      Column({ space: 3 }) {
        Text(alertTypeLabel(item.alertType))
          .fontSize($r('app.float.body_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Text(formatTime(item.occurredAt))
          .fontSize(11)
          .fontColor($r('app.color.text_secondary'))
      }
      .alignItems(HorizontalAlign.Start)
      Blank()
      RiskBadge({ level: item.riskLevel })
    }
    .width('100%')
    .padding({ left: 14, right: 14, top: 12, bottom: 12 })
    .backgroundColor($r('app.color.bg_card'))
    .borderRadius($r('app.float.card_radius'))
    .shadow({ radius: 6, color: $r('app.color.shadow_card'), offsetX: 0, offsetY: 1 })
  }
}
