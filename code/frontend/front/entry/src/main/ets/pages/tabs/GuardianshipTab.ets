/**
 * 监护 Tab — 司机列表 + 快捷操作 + 司机详情。
 * 接 GuardianshipViewModel，快捷操作接 GuardianshipApi。
 */
import { guardianshipVM, GuardianDriver, ActionResult } from '../../viewmodel'
import { sessionStore } from '../../viewmodel/SessionStore'
import { AppCard, SectionTitle, StatusBadge } from '../../common/ui'
import { promptAction } from '@kit.ArkUI'

@Component
export struct GuardianshipTab {
  @State drivers: GuardianDriver[] = guardianshipVM.drivers
  @State selectedDriver: number = 0
  @State permissions: string[] = ['MEDIA_CALL', 'WINDOW_CONTROL', 'MANUAL_RESCUE']
  @State actionLoading: boolean = false

  aboutToAppear(): void {
    this.refreshDrivers()
  }

  private async refreshDrivers(): Promise<void> {
    const result = await guardianshipVM.loadDrivers()
    if (result.state === 'success' && result.data !== null) {
      this.drivers = result.data.drivers
      if (this.drivers.length > 0) {
        this.loadPerms(this.drivers[this.selectedDriver].id)
      }
    } else {
      this.drivers = []
    }
  }

  private async loadPerms(driverId: string): Promise<void> {
    this.permissions = await guardianshipVM.loadPermissions(driverId)
  }

  private async onAction(action: string): Promise<void> {
    if (this.actionLoading || this.drivers.length === 0) return
    const driver = this.drivers[this.selectedDriver]
    const account = sessionStore.account
    const accountId = account !== null ? account.accountId : ''
    this.actionLoading = true
    let result: ActionResult = { ok: false, msg: '未实现' }
    if (action === 'MEDIA_CALL') {
      result = await guardianshipVM.requestCall(driver.id, accountId)
    } else if (action === 'RESCUE') {
      result = await guardianshipVM.triggerRescue(driver.id, accountId)
    } else if (action === 'WINDOW') {
      result = { ok: true, msg: '车窗控制已打开' }
    } else if (action === 'NOTIFY') {
      result = { ok: true, msg: '通知偏好已打开' }
    }
    promptAction.showToast({ message: result.msg, duration: 1800 })
    this.actionLoading = false
  }

  build() {
    Column({ space: 14 }) {
      Text($r('app.string.tab_guardianship'))
        .fontSize($r('app.float.title_font_size'))
        .fontWeight(FontWeight.Bold)
        .fontColor($r('app.color.text_primary'))
        .padding({ left: 20, right: 20, top: 24, bottom: 4 })
        .width('100%')
        .textAlign(TextAlign.Start)

      Scroll() {
        Row({ space: 10 }) {
          ForEach(this.drivers, (driver: GuardianDriver, index: number) => {
            this.DriverChip(driver, index)
          }, (driver: GuardianDriver) => driver.id)
        }
        .padding({ left: 16, right: 16 })
      }
      .scrollable(ScrollDirection.Horizontal)
      .scrollBar(BarState.Off)
      .width('100%')

      this.QuickActions()

      if (this.drivers.length > 0 && this.selectedDriver < this.drivers.length) {
        this.DriverDetail(this.drivers[this.selectedDriver])
      }

      Blank()
    }
    .width('100%')
    .height('100%')
  }

  @Builder
  DriverChip(driver: GuardianDriver, index: number) {
    Column({ space: 3 }) {
      Text(driver.name)
        .fontSize($r('app.float.body_font_size'))
        .fontWeight(this.selectedDriver === index ? FontWeight.Medium : FontWeight.Normal)
        .fontColor(this.selectedDriver === index ? $r('app.color.brand_primary') : $r('app.color.text_primary'))
      StatusBadge({ status: driver.status })
    }
    .padding({ left: 16, right: 16, top: 10, bottom: 10 })
    .backgroundColor(this.selectedDriver === index ? $r('app.color.brand_primary_light') : $r('app.color.bg_card'))
    .borderRadius(14)
    .border({
      width: 1,
      color: this.selectedDriver === index ? $r('app.color.brand_primary') : $r('app.color.divider'),
      style: BorderStyle.Solid
    })
    .onClick(() => {
      this.selectedDriver = index
      this.loadPerms(driver.id)
    })
  }

  @Builder
  QuickActions() {
    Column() {
      SectionTitle({ title: $r('app.string.quick_actions_title') })
      Row({ space: 12 }) {
        this.ActionItem($r('app.string.action_media_call'), $r('app.media.ic_action_call'), 'MEDIA_CALL', this.permissions.includes('MEDIA_CALL'))
        this.ActionItem($r('app.string.action_rescue'), $r('app.media.ic_action_rescue'), 'RESCUE', this.permissions.includes('MANUAL_RESCUE'))
        this.ActionItem($r('app.string.action_window'), $r('app.media.ic_action_window'), 'WINDOW', this.permissions.includes('WINDOW_CONTROL'))
        this.ActionItem($r('app.string.action_notification'), $r('app.media.ic_action_notify'), 'NOTIFY', true)
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
    }
  }

  @Builder
  ActionItem(label: Resource, icon: Resource, action: string, enabled: boolean) {
    Column({ space: 8 }) {
      Image(icon)
        .width(28)
        .height(28)
        .opacity(enabled ? 1 : 0.35)
      Text(label)
        .fontSize(11)
        .fontColor(enabled ? $r('app.color.text_primary') : $r('app.color.text_hint'))
    }
    .layoutWeight(1)
    .padding({ top: 16, bottom: 16 })
    .justifyContent(FlexAlign.Center)
    .backgroundColor($r('app.color.bg_card'))
    .borderRadius($r('app.float.card_radius'))
    .shadow({ radius: 6, color: $r('app.color.shadow_card'), offsetX: 0, offsetY: 1 })
    .opacity(this.actionLoading ? 0.6 : 1)
    .onClick(() => {
      if (enabled) this.onAction(action)
    })
  }

  @Builder
  DriverDetail(driver: GuardianDriver) {
    AppCard({ space: 12 }) {
      Row() {
        Text('车辆')
          .fontSize(11)
          .fontColor($r('app.color.text_secondary'))
        Text(driver.vehicleId)
          .fontSize($r('app.float.body_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_primary'))
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
      Divider().color($r('app.color.divider'))
      Row() {
        Text('当前状态')
          .fontSize(11)
          .fontColor($r('app.color.text_secondary'))
        StatusBadge({ status: driver.status })
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
      Divider().color($r('app.color.divider'))
      Row() {
        Text('监护权限')
          .fontSize(11)
          .fontColor($r('app.color.text_secondary'))
        Text(this.permLabel())
          .fontSize($r('app.float.body_font_size'))
          .fontColor($r('app.color.text_primary'))
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceBetween)
    }
  }

  private permLabel(): string {
    const parts: string[] = []
    if (this.permissions.includes('MEDIA_CALL')) parts.push('对讲')
    if (this.permissions.includes('WINDOW_CONTROL')) parts.push('车窗')
    if (this.permissions.includes('MANUAL_RESCUE')) parts.push('救援')
    return parts.length > 0 ? parts.join(' · ') : '仅查看'
  }
}
