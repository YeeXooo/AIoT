import { guardianshipVM, GuardianDriver, ActionResult } from '../../viewmodel'
import { sessionStore } from '../../viewmodel/SessionStore'
import { guardianshipApi } from '../../api/GuardianshipApi'
import { rtcClient } from '../../rtc/SparkRTCClient'
import { AppCard, SectionTitle, StatusBadge } from '../../common/ui'
import { promptAction } from '@kit.ArkUI'
import { router } from '@kit.ArkUI'

@Component
export struct GuardianshipTab {
  @State drivers: GuardianDriver[] = guardianshipVM.drivers
  @State selectedDriver: number = 0
  @State permissions: string[] = ['MEDIA_CALL', 'WINDOW_CONTROL', 'MANUAL_RESCUE']
  @State actionLoading: boolean = false
  @State contentOpacity: number = 0
  @State contentOffsetY: number = 20
  @State actionScale: number = 1.0

  aboutToAppear(): void {
    this.refreshDrivers()
    setTimeout(() => {
      this.contentOpacity = 1
      this.contentOffsetY = 0
    }, 150)
  }

  private async refreshDrivers(): Promise<void> {
    const result = await guardianshipVM.loadDrivers()
    if (result.state === 'success' && result.data !== null) {
      this.drivers = result.data.drivers
      if (this.drivers.length > 0) {
        this.loadPerms(this.drivers[this.selectedDriver].id)
        this.syncCurrentDriver()
      }
    } else {
      this.drivers = []
    }
  }

  private syncCurrentDriver(): void {
    if (this.selectedDriver < this.drivers.length) {
      const d = this.drivers[this.selectedDriver]
      AppStorage.setOrCreate('currentDriverId', d.id)
      AppStorage.setOrCreate('currentDriverName', d.name)
      AppStorage.setOrCreate('currentDriverVehicleId', d.vehicleId)
    }
  }

  onPageShow(): void {
    this.refreshDrivers()
  }

  private async loadPerms(driverId: string): Promise<void> {
    const result = await guardianshipVM.loadPermissions(driverId)
    // 查询失败（403 等）时保留默认权限，避免后端鉴权异常清空按钮可用态
    if (result !== undefined) {
      this.permissions = result
    }
  }

  private async onAction(action: string): Promise<void> {
    if (this.actionLoading || this.drivers.length === 0) return
    const driver = this.drivers[this.selectedDriver]
    const account = sessionStore.account
    const accountId = account !== null ? account.accountId : ''
    this.actionLoading = true
    animateTo({ duration: 150, curve: Curve.EaseOut }, () => {
      this.actionScale = 0.92
    })
    setTimeout(() => {
      animateTo({ duration: 200, curve: Curve.EaseOut }, () => {
        this.actionScale = 1.0
      })
    }, 150)
    let result: ActionResult = { ok: false, msg: '未实现' }
    // 对讲/救援需二次验证 token，先确保 token 有效（自动签发或复用缓存）
    if (action === 'MEDIA_CALL' || action === 'RESCUE') {
      const token = await guardianshipVM.ensureSecondaryAuth()
      if (token.length === 0) {
        result = { ok: false, msg: '二次验证失败，请稍后重试' }
        promptAction.showToast({ message: result.msg, duration: 1800 })
        this.actionLoading = false
        return
      }
      if (action === 'MEDIA_CALL') {
        const reqBody: Record<string, Object> = {}
        reqBody['familyAccountId'] = accountId
        reqBody['driverId'] = driver.id
        reqBody['sessionType'] = 'AUDIO'
        reqBody['secondaryAuthToken'] = token
        const resp = await guardianshipApi.requestMediaSession(reqBody)
        if (resp.success && resp.data !== undefined) {
          const joinResult = await rtcClient.joinRoom(resp.data.sparkRTCRoomId, accountId, resp.data.sparkRTCJoinToken, 'subscriber')
          result = { ok: joinResult.ok, msg: joinResult.message }
        } else {
          const errMsg = resp.error !== undefined ? resp.error.message : '请求失败'
          result = { ok: false, msg: errMsg }
        }
      } else {
        const reqBody: Record<string, Object> = {}
        reqBody['familyAccountId'] = accountId
        reqBody['driverId'] = driver.id
        reqBody['secondaryAuthToken'] = token
        const resp = await guardianshipApi.triggerManualRescue(reqBody)
        if (resp.success) {
          result = { ok: true, msg: '救援请求已提交' }
        } else {
          const errMsg = resp.error !== undefined ? resp.error.message : '救援请求失败'
          result = { ok: false, msg: errMsg }
        }
      }
    } else if (action === 'WINDOW') {
      // vehicleId 为空时后端 /guardianship/list 未返回车辆字段，直接提示而非发空请求（避免 /vehicles//windows 400）
      if (driver.vehicleId.length === 0) {
        result = { ok: false, msg: '暂无车辆信息，无法查询车窗状态' }
      } else {
        const resp = await guardianshipApi.queryWindowStatus(driver.vehicleId)
        if (resp.success && resp.data !== undefined) {
          const count: number = resp.data.windowStatuses.length
          result = { ok: true, msg: `车窗状态：${count} 个窗户信息已获取` }
        } else {
          const errMsg = resp.error !== undefined ? resp.error.message : '查询车窗状态失败'
          result = { ok: false, msg: errMsg }
        }
      }
    } else if (action === 'NOTIFY') {
      const reqBody: Record<string, Object> = {}
      reqBody['familyAccountId'] = accountId
      reqBody['driverId'] = driver.id
      reqBody['preferredRiskLevels'] = ['L1_HINT', 'L2_WARNING', 'L3_CRITICAL']
      const resp = await guardianshipApi.updateNotificationPreference(reqBody)
      if (resp.success) {
        result = { ok: true, msg: '通知偏好已更新' }
      } else {
        const errMsg = resp.error !== undefined ? resp.error.message : '更新失败'
        result = { ok: false, msg: errMsg }
      }
    }
    promptAction.showToast({ message: result.msg, duration: 1800 })
    this.actionLoading = false
  }

  build() {
    Column({ space: 14 }) {
      Text($r('app.string.tab_guardianship'))
        .fontSize($r('app.float.title_font_size'))
        .fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.text_on_dark'))
        .padding({ left: 20, right: 20, top: 24, bottom: 4 })
        .width('100%')
        .textAlign(TextAlign.Start)
        .opacity(this.contentOpacity)
        .translate({ y: this.contentOffsetY })
        .animation({ duration: 500, curve: Curve.EaseOut })

      Scroll() {
        Row({ space: 10 }) {
          ForEach(this.drivers, (driver: GuardianDriver, index: number) => {
            Column({ space: 3 }) {
              Text(this.drivers[index].name)
                .fontSize($r('app.float.body_font_size'))
                .fontWeight(this.selectedDriver === index ? FontWeight.Medium : FontWeight.Normal)
                .fontColor(this.selectedDriver === index ? $r('app.color.icon_active') : $r('app.color.text_on_card'))
              StatusBadge({ status: this.drivers[index].status })
            }
            .padding({ left: 18, right: 18, top: 12, bottom: 12 })
            .backgroundColor(this.selectedDriver === index ? $r('app.color.chip_selected_bg') : $r('app.color.chip_unselected_bg'))
            .borderRadius(16)
            .border({
              width: 1,
              color: this.selectedDriver === index ? $r('app.color.chip_selected_border') : $r('app.color.chip_unselected_border'),
              style: BorderStyle.Solid
            })
            .scale({ x: this.selectedDriver === index ? 1.03 : 1.0, y: this.selectedDriver === index ? 1.03 : 1.0 })
            .animation({ duration: 250, curve: Curve.EaseOut })
            .onClick(() => {
              this.selectedDriver = index
              this.loadPerms(this.drivers[index].id)
              this.syncCurrentDriver()
            })
          }, (driver: GuardianDriver) => driver.id)
        }
        .padding({ left: 16, right: 16 })
      }
      .scrollable(ScrollDirection.Horizontal)
      .scrollBar(BarState.Off)
      .width('100%')
      .opacity(this.contentOpacity)
      .translate({ y: this.contentOffsetY })
      .animation({ duration: 500, curve: Curve.EaseOut, delay: 80 })

      this.QuickActions()

      if (this.drivers.length > 0 && this.selectedDriver < this.drivers.length) {
        this.DriverDetail()
      }

      this.BindDriverButton()

      Blank()
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  QuickActions() {
    Column() {
      SectionTitle({ title: $r('app.string.quick_actions_title') })
      Row({ space: 10 }) {
        this.ActionItem($r('app.string.action_media_call'), $r('app.media.ic_action_call'), 'MEDIA_CALL', this.permissions.includes('MEDIA_CALL'))
        this.ActionItem($r('app.string.action_rescue'), $r('app.media.ic_action_rescue'), 'RESCUE', this.permissions.includes('MANUAL_RESCUE'))
        this.ActionItem($r('app.string.action_window'), $r('app.media.ic_action_window'), 'WINDOW', this.permissions.includes('WINDOW_CONTROL'))
        this.ActionItem($r('app.string.action_notification'), $r('app.media.ic_action_notify'), 'NOTIFY', true)
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
    }
    .opacity(this.contentOpacity)
    .translate({ y: this.contentOffsetY })
    .animation({ duration: 500, curve: Curve.EaseOut, delay: 160 })
  }

  @Builder
  ActionItem(label: Resource, icon: Resource, action: string, enabled: boolean) {
    Column({ space: 8 }) {
      Stack({ alignContent: Alignment.Center }) {
        if (enabled) {
          Circle()
            .width(44)
            .height(44)
            .fill($r('app.color.action_bg_glow'))
            .opacity(this.actionLoading ? 0.3 : 0.6)
        }
        Image(icon)
          .width(24)
          .height(24)
          .opacity(enabled ? 1 : 0.3)
      }
      .width(44)
      .height(44)
      Text(label)
        .fontSize(11)
        .fontColor(enabled ? $r('app.color.text_on_card') : $r('app.color.text_faint'))
    }
    .layoutWeight(1)
    .padding({ top: 16, bottom: 16 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor($r('app.color.card_bg_dark'))
    .borderRadius(16)
    .border({ width: 1, color: enabled ? $r('app.color.card_border') : $r('app.color.card_bg_dark') })
    .shadow({ radius: 8, color: $r('app.color.card_shadow_subtle'), offsetX: 0, offsetY: 2 })
    .scale({ x: this.actionScale, y: this.actionScale })
    .animation({ duration: 200, curve: Curve.EaseOut })
    .onClick(() => {
      if (enabled) this.onAction(action)
    })
  }

  @Builder
  DriverDetail() {
    Column({ space: 14 }) {
      Row() {
        Text('车辆')
          .fontSize(11)
          .fontColor($r('app.color.text_muted'))
        if (this.selectedDriver < this.drivers.length) {
          Text(this.drivers[this.selectedDriver].vehicleId)
            .fontSize($r('app.float.body_font_size'))
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_on_dark'))
        }
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
      Column()
        .width('100%')
        .height(1)
        .backgroundColor($r('app.color.divider_dark'))
      Row() {
        Text('当前状态')
          .fontSize(11)
          .fontColor($r('app.color.text_muted'))
        if (this.selectedDriver < this.drivers.length) {
          StatusBadge({ status: this.drivers[this.selectedDriver].status })
        }
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
      Column()
        .width('100%')
        .height(1)
        .backgroundColor($r('app.color.divider_dark'))
      Row() {
        Text('监护权限')
          .fontSize(11)
          .fontColor($r('app.color.text_muted'))
        Text(this.permLabel())
          .fontSize($r('app.float.body_font_size'))
          .fontColor($r('app.color.text_on_dark'))
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
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
  BindDriverButton() {
    Column() {
      Text('+ 添加驾驶员')
        .fontSize($r('app.float.body_font_size'))
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.accent_blue'))
    }
    .width('100%')
    .height(52)
    .justifyContent(FlexAlign.Center)
    .backgroundColor($r('app.color.card_bg_dark'))
    .borderRadius(16)
    .border({ width: 1, color: $r('app.color.dashed_border'), style: BorderStyle.Dashed })
    .margin({ left: 16, right: 16 })
    .opacity(this.contentOpacity)
    .translate({ y: this.contentOffsetY })
    .animation({ duration: 500, curve: Curve.EaseOut, delay: 320 })
    .onClick(() => {
      router.pushUrl({ url: 'pages/DriverBindPage' })
    })
  }

  private permLabel(): string {
    const parts: string[] = []
    if (this.permissions.includes('MEDIA_CALL')) parts.push('对讲')
    if (this.permissions.includes('WINDOW_CONTROL')) parts.push('车窗')
    if (this.permissions.includes('MANUAL_RESCUE')) parts.push('救援')
    return parts.length > 0 ? parts.join(' · ') : '仅查看'
  }
}
