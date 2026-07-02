/**
 * 车队 Tab — 概览 / 疲劳分布 / 脱线车辆 / 驾驶报告。
 * 接 FleetViewModel，支持下拉刷新 + 三态。
 */
import { fleetVM, FleetData, ViewState, OfflineVehicleEntry } from '../../viewmodel'
import { AppCard, SectionTitle, StatItem, SkeletonRow, ErrorRetry } from '../../common/ui'
import { offlineReasonLabel } from '../../common/StatusMap'
import { promptAction } from '@kit.ArkUI'

@Component
export struct FleetTab {
  @State view: ViewState<FleetData> = { state: 'idle', data: null, errorMsg: '' }
  @State refreshing: boolean = false
  @State reportLoading: boolean = false

  aboutToAppear(): void {
    this.refresh(false)
  }

  private async refresh(showRefreshing: boolean): Promise<void> {
    if (showRefreshing) this.refreshing = true
    this.view = { state: 'loading', data: this.view.data, errorMsg: '' }
    const result = await fleetVM.load()
    this.view = result
    this.refreshing = false
  }

  private async generateReport(): Promise<void> {
    if (this.reportLoading) return
    this.reportLoading = true
    promptAction.showToast({ message: '报告生成中…', duration: 1500 })
    this.reportLoading = false
    promptAction.showToast({ message: '报告已生成', duration: 1500 })
  }

  build() {
    Refresh({ refreshing: $$this.refreshing }) {
      Scroll() {
        Column({ space: 14 }) {
          Text($r('app.string.tab_fleet'))
            .fontSize($r('app.float.title_font_size'))
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_primary'))
            .padding({ left: 20, right: 20, top: 24 })
            .width('100%')
            .textAlign(TextAlign.Start)

          if (this.view.state === 'loading' && this.view.data === null) {
            Column({ space: 8 }) {
              SkeletonRow()
              SkeletonRow()
              SkeletonRow()
            }
            .padding({ left: 16, right: 16 })
          } else if (this.view.state === 'error' && this.view.data === null) {
            ErrorRetry({
              message: this.view.errorMsg,
              retryAction: () => { this.refresh(false) },
            })
          } else if (this.view.data !== null) {
            this.OverviewRow(this.view.data)
            this.FatigueCard(this.view.data)
            this.OfflineCard(this.view.data)
            this.ReportCard()
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
  OverviewRow(data: FleetData) {
    Row({ space: 12 }) {
      StatItem({ value: `${data.totalVehicles}`, label: '总车辆', color: $r('app.color.text_primary') })
      StatItem({ value: `${data.offlineCount}`, label: '脱线', color: $r('app.color.status_yellow') })
      StatItem({ value: `${data.highRiskCount}`, label: '高危', color: $r('app.color.status_red') })
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
  }

  @Builder
  FatigueCard(data: FleetData) {
    if (data.fatigue !== null) {
      AppCard({ space: 14 }) {
        Row() {
          Text($r('app.string.fatigue_title'))
            .fontSize($r('app.float.subtitle_font_size'))
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Blank()
          Text('近7日')
            .fontSize(11)
            .fontColor($r('app.color.text_secondary'))
        }
        .width('100%')
        Row({ space: 4 }) {
          this.FatigueBar(data.fatigue.distribution.L1_HINT, $r('app.color.status_green'))
          this.FatigueBar(data.fatigue.distribution.L2_WARNING, $r('app.color.status_yellow'))
          this.FatigueBar(data.fatigue.distribution.L3_CRITICAL, $r('app.color.status_red'))
        }
        .width('100%')
        Row() {
          this.FatigueLabel('L1 提示', data.fatigue.distribution.L1_HINT, $r('app.color.status_green'))
          this.FatigueLabel('L2 警告', data.fatigue.distribution.L2_WARNING, $r('app.color.status_yellow'))
          this.FatigueLabel('L3 危险', data.fatigue.distribution.L3_CRITICAL, $r('app.color.status_red'))
        }
        .width('100%')
        .justifyContent(FlexAlign.SpaceAround)
      }
    }
  }

  @Builder
  FatigueBar(ratio: number, color: Resource) {
    Column() { Blank() }
      .layoutWeight(ratio)
      .height(8)
      .backgroundColor(color)
      .borderRadius(4)
  }

  @Builder
  FatigueLabel(label: string, ratio: number, color: Resource) {
    Column({ space: 3 }) {
      Text(label)
        .fontSize(11)
        .fontColor($r('app.color.text_secondary'))
      Text(`${Math.round(ratio * 100)}%`)
        .fontSize($r('app.float.body_font_size'))
        .fontWeight(FontWeight.Medium)
        .fontColor(color)
    }
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  OfflineCard(data: FleetData) {
    AppCard({ space: 12 }) {
      Row() {
        Text($r('app.string.offline_title'))
          .fontSize($r('app.float.subtitle_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Blank()
        Text(`${data.offlineVehicles.length}辆`)
          .fontSize(11)
          .fontColor($r('app.color.text_secondary'))
      }
      .width('100%')
      Column({ space: 10 }) {
        ForEach(data.offlineVehicles, (v: OfflineVehicleEntry) => {
          this.OfflineRow(v)
        }, (v: OfflineVehicleEntry) => v.vehicleId)
      }
    }
  }

  @Builder
  OfflineRow(v: OfflineVehicleEntry) {
    Row() {
      Column({ space: 3 }) {
        Text(v.licensePlate)
          .fontSize($r('app.float.body_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
        Text(v.driverName)
          .fontSize(11)
          .fontColor($r('app.color.text_secondary'))
      }
      .alignItems(HorizontalAlign.Start)
      Blank()
      Text(offlineReasonLabel(v.offlineReason))
        .fontSize(11)
        .fontColor($r('app.color.status_yellow'))
        .padding({ left: 8, right: 8, top: 3, bottom: 3 })
        .backgroundColor($r('app.color.status_yellow_bg'))
        .borderRadius(4)
    }
    .width('100%')
  }

  @Builder
  ReportCard() {
    AppCard({}) {
      Row() {
        Column({ space: 4 }) {
          Text($r('app.string.report_title'))
            .fontSize($r('app.float.subtitle_font_size'))
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text('生成驾驶行为分析报告')
            .fontSize(11)
            .fontColor($r('app.color.text_secondary'))
        }
        .alignItems(HorizontalAlign.Start)
        Blank()
        Text(this.reportLoading ? '生成中…' : '生成 →')
          .fontSize($r('app.float.body_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.brand_accent'))
          .padding({ left: 14, right: 14, top: 8, bottom: 8 })
          .backgroundColor($r('app.color.brand_primary_light'))
          .borderRadius(8)
          .opacity(this.reportLoading ? 0.6 : 1)
          .onClick(() => this.generateReport())
      }
      .width('100%')
    }
  }
}
