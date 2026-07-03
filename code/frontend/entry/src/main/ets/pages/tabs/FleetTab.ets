import { fleetVM, FleetData, ViewState, OfflineVehicleEntry } from '../../viewmodel'
import { fleetApi } from '../../api/FleetApi'
import { AppCard, SectionTitle, StatItem, SkeletonRow, ErrorRetry } from '../../common/ui'
import { offlineReasonLabel } from '../../common/StatusMap'
import { promptAction } from '@kit.ArkUI'
import { wsManager, type FleetAlert, type PerformanceWarning } from '../../websocket/WebSocketManager'

@Component
export struct FleetTab {
  @State view: ViewState<FleetData> = { state: 'idle', data: null, errorMsg: '' }
  @State refreshing: boolean = false
  @State reportLoading: boolean = false
  @State reportDownloadUrl: string = ''
  @State reportFile: string = ''
  @State private contentOpacity: number = 0
  @State private contentOffsetY: number = 20
  @State private fatigueBarScale: number = 0

  aboutToAppear(): void {
    this.refresh(false)
    wsManager.setCallbacks({
      onL3Alert: (_alert: FleetAlert) => {
        promptAction.showToast({ message: '收到L3高危告警', duration: 2000 })
      },
      onPerformanceWarning: (_warning: PerformanceWarning) => {
        promptAction.showToast({ message: '收到绩效预警', duration: 2000 })
      },
    })
  }

  private async refresh(showRefreshing: boolean): Promise<void> {
    if (showRefreshing) this.refreshing = true
    this.view = { state: 'loading', data: this.view.data, errorMsg: '' }
    const result = await fleetVM.load()
    this.view = result
    this.refreshing = false
    if (result.data !== null) {
      setTimeout(() => {
        this.contentOpacity = 1
        this.contentOffsetY = 0
      }, 100)
      setTimeout(() => {
        this.fatigueBarScale = 1
      }, 300)
    }
  }

  private async generateReport(): Promise<void> {
    if (this.reportLoading) return
    this.reportLoading = true
    try {
      const resp = await fleetApi.generateReport({
        'driverId': 'd1',
        'timeRange': { 'start': '2026-06-25T00:00:00Z', 'end': '2026-07-02T00:00:00Z' },
        'reportType': 'WEEKLY',
      })
      if (resp.success && resp.data !== undefined) {
        if (resp.data.isEmpty) {
          promptAction.showToast({ message: '暂无数据生成报告', duration: 1500 })
        } else {
          promptAction.showToast({ message: `报告已生成 (${resp.data.reportId})`, duration: 1500 })
          this.reportDownloadUrl = resp.data.downloadUrl
          const dlResp = await fleetApi.downloadReport(resp.data.reportId, 'pdf')
          if (dlResp.success && dlResp.data !== undefined) {
            this.reportFile = 'report.pdf'
          }
        }
      } else {
        promptAction.showToast({ message: resp.error?.message ?? '报告生成失败', duration: 1500 })
      }
    } catch (_e) {
      promptAction.showToast({ message: '网络错误', duration: 1500 })
    } finally {
      this.reportLoading = false
    }
  }

  build() {
    Refresh({ refreshing: $$this.refreshing }) {
      Scroll() {
        Column({ space: 16 }) {
          Text($r('app.string.tab_fleet'))
            .fontSize($r('app.float.title_font_size'))
            .fontWeight(FontWeight.Bold)
            .fontColor($r('app.color.text_on_dark'))
            .padding({ left: 20, right: 20, top: 24 })
            .width('100%')
            .textAlign(TextAlign.Start)
            .opacity(this.contentOpacity)
            .translate({ y: this.contentOffsetY })
            .animation({ duration: 500, curve: Curve.EaseOut })

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
    Row({ space: 10 }) {
      this.OverviewStatItem(`${data.totalVehicles}`, '总车辆', $r('app.color.overview_stat_blue'))
      this.OverviewStatItem(`${data.offlineCount}`, '脱线', $r('app.color.overview_stat_yellow'))
      this.OverviewStatItem(`${data.highRiskCount}`, '高危', $r('app.color.overview_stat_red'))
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
    .opacity(this.contentOpacity)
    .translate({ y: this.contentOffsetY })
    .animation({ duration: 500, curve: Curve.EaseOut, delay: 80 })
  }

  @Builder
  OverviewStatItem(value: string, label: string, color: Resource) {
    Column({ space: 6 }) {
      Text(value)
        .fontSize(28)
        .fontWeight(FontWeight.Bold)
        .fontColor(color)
      Text(label)
        .fontSize(11)
        .fontColor($r('app.color.text_muted'))
        .letterSpacing(1)
    }
    .layoutWeight(1)
    .padding({ top: 18, bottom: 18 })
    .alignItems(HorizontalAlign.Center)
    .backgroundColor($r('app.color.card_bg_dark'))
    .borderRadius(16)
    .border({ width: 1, color: $r('app.color.card_border') })
    .shadow({ radius: 8, color: $r('app.color.card_shadow_subtle'), offsetX: 0, offsetY: 2 })
  }

  @Builder
  FatigueCard(data: FleetData) {
    if (data.fatigue !== null) {
      Column({ space: 14 }) {
        Row() {
          Text($r('app.string.fatigue_title'))
            .fontSize($r('app.float.subtitle_font_size'))
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_on_dark'))
          Blank()
          Text('近7日')
            .fontSize(11)
            .fontColor($r('app.color.text_muted'))
            .padding({ left: 8, right: 8, top: 3, bottom: 3 })
            .backgroundColor($r('app.color.card_bg_dark'))
            .borderRadius(8)
        }
        .width('100%')
        Row({ space: 3 }) {
          this.FatigueBar(data.fatigue.distribution.L1_HINT, $r('app.color.success_color'), $r('app.color.fatigue_bar_green_end'))
          this.FatigueBar(data.fatigue.distribution.L2_WARNING, $r('app.color.warning_color'), $r('app.color.fatigue_bar_yellow_end'))
          this.FatigueBar(data.fatigue.distribution.L3_CRITICAL, $r('app.color.danger'), $r('app.color.fatigue_bar_red_end'))
        }
        .width('100%')
        Row() {
          this.FatigueLabel('L1 提示', data.fatigue.distribution.L1_HINT, $r('app.color.success_color'))
          this.FatigueLabel('L2 警告', data.fatigue.distribution.L2_WARNING, $r('app.color.warning_color'))
          this.FatigueLabel('L3 危险', data.fatigue.distribution.L3_CRITICAL, $r('app.color.danger'))
        }
        .width('100%')
        .justifyContent(FlexAlign.SpaceAround)
      }
      .width('100%')
      .padding(16)
      .backgroundColor($r('app.color.card_bg_dark'))
      .borderRadius(16)
      .border({ width: 1, color: $r('app.color.card_border') })
      .shadow({ radius: 10, color: $r('app.color.card_shadow_subtle'), offsetX: 0, offsetY: 3 })
      .margin({ left: 16, right: 16 })
      .opacity(this.contentOpacity)
      .translate({ y: this.contentOffsetY })
      .animation({ duration: 500, curve: Curve.EaseOut, delay: 160 })
    }
  }

  @Builder
  FatigueBar(ratio: number, color: Resource, colorEnd: Resource) {
    Column() { Blank() }
      .layoutWeight(ratio)
      .height(10)
      .borderRadius(5)
      .linearGradient({
        direction: GradientDirection.Right,
        colors: [[color, 0.0], [colorEnd, 1.0]]
      })
      .scale({ x: this.fatigueBarScale, y: 1, centerX: '0%', centerY: '50%' })
      .animation({ duration: 600, curve: Curve.EaseOut })
  }

  @Builder
  FatigueLabel(label: string, ratio: number, color: Resource) {
    Column({ space: 4 }) {
      Text(label)
        .fontSize(11)
        .fontColor($r('app.color.text_muted'))
      Text(`${Math.round(ratio * 100)}%`)
        .fontSize($r('app.float.body_font_size'))
        .fontWeight(FontWeight.Medium)
        .fontColor(color)
    }
    .alignItems(HorizontalAlign.Center)
  }

  @Builder
  OfflineCard(data: FleetData) {
    Column({ space: 12 }) {
      Row() {
        Text($r('app.string.offline_title'))
          .fontSize($r('app.float.subtitle_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_on_dark'))
        Blank()
        Text(`${data.offlineVehicles.length}辆`)
          .fontSize(11)
          .fontColor($r('app.color.text_muted'))
      }
      .width('100%')
      Column({ space: 10 }) {
        ForEach(data.offlineVehicles, (v: OfflineVehicleEntry) => {
          this.OfflineRow(v)
        }, (v: OfflineVehicleEntry) => v.vehicleId)
      }
    }
    .width('100%')
    .padding(16)
    .backgroundColor($r('app.color.card_bg_dark'))
    .borderRadius(16)
    .border({ width: 1, color: $r('app.color.card_border') })
    .shadow({ radius: 8, color: $r('app.color.card_shadow_subtle'), offsetX: 0, offsetY: 2 })
    .margin({ left: 16, right: 16 })
    .opacity(this.contentOpacity)
    .translate({ y: this.contentOffsetY })
    .animation({ duration: 500, curve: Curve.EaseOut, delay: 240 })
  }

  @Builder
  OfflineRow(v: OfflineVehicleEntry) {
    Row() {
      Stack() {
        Circle()
          .width(36)
          .height(36)
          .fill($r('app.color.offline_avatar_bg'))
        Text(v.driverName.substring(0, 1))
          .fontSize(14)
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.accent_blue'))
      }
      .width(36)
      .height(36)
      Column({ space: 3 }) {
        Text(v.licensePlate)
          .fontSize($r('app.float.body_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_on_dark'))
        Text(v.driverName)
          .fontSize(11)
          .fontColor($r('app.color.text_dim'))
      }
      .alignItems(HorizontalAlign.Start)
      .margin({ left: 10 })
      .layoutWeight(1)
      Blank()
      Text(offlineReasonLabel(v.offlineReason))
        .fontSize(11)
        .fontColor($r('app.color.warning_color'))
        .padding({ left: 8, right: 8, top: 4, bottom: 4 })
        .backgroundColor($r('app.color.warning_bg_tag'))
        .borderRadius(6)
    }
    .width('100%')
  }

  @Builder
  ReportCard() {
    Column() {
      Row() {
        Column({ space: 4 }) {
          Text($r('app.string.report_title'))
            .fontSize($r('app.float.subtitle_font_size'))
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_on_dark'))
          Text('生成驾驶行为分析报告')
            .fontSize(11)
            .fontColor($r('app.color.text_muted'))
        }
        .alignItems(HorizontalAlign.Start)
        .layoutWeight(1)
        Blank()
        Text(this.reportLoading ? '生成中…' : '生成 →')
          .fontSize($r('app.float.body_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor(Color.White)
          .padding({ left: 16, right: 16, top: 10, bottom: 10 })
          .linearGradient({
            angle: 135,
            colors: [[$r('app.color.report_button_start'), 0.0], [$r('app.color.report_button_end'), 1.0]]
          })
          .borderRadius(10)
          .opacity(this.reportLoading ? 0.6 : 1)
          .shadow({ radius: 8, color: $r('app.color.report_button_shadow'), offsetX: 0, offsetY: 2 })
          .onClick(() => this.generateReport())
      }
      .width('100%')
    }
    .width('100%')
    .padding(16)
    .backgroundColor($r('app.color.card_bg_dark'))
    .borderRadius(16)
    .border({ width: 1, color: $r('app.color.card_border') })
    .shadow({ radius: 8, color: $r('app.color.card_shadow_subtle'), offsetX: 0, offsetY: 2 })
    .margin({ left: 16, right: 16 })
    .opacity(this.contentOpacity)
    .translate({ y: this.contentOffsetY })
    .animation({ duration: 500, curve: Curve.EaseOut, delay: 320 })
  }
}
