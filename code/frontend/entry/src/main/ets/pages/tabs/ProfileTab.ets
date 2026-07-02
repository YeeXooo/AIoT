/**
 * 我的 Tab — 用户信息 + 设置项 + 退出登录。
 * 接 SessionStore。
 */
import { AppCard } from '../../common/ui'
import { sessionStore } from '../../viewmodel/SessionStore'
import { router } from '@kit.ArkUI'
import { promptAction } from '@kit.ArkUI'

@Component
export struct ProfileTab {
  @State userName: string = '家属用户'
  @State role: string = '家属端'
  @State phone: string = '138****6789'
  @State showSettings: boolean = false

  aboutToAppear(): void {
    const acc = sessionStore.account
    if (acc !== null) {
      this.userName = acc.role === 'FAMILY' ? '家属用户' : (acc.role === 'MANAGER' ? '车队管理员' : '救援专员')
    }
  }

  private toggleSettings(): void {
    this.showSettings = !this.showSettings
  }

  private async logout(): Promise<void> {
    await sessionStore.clear()
    promptAction.showToast({ message: '已退出登录', duration: 1500 })
    router.replaceUrl({ url: 'pages/LoginPage' })
  }

  build() {
    Scroll() {
      Column({ space: 14 }) {
        Column({ space: 10 }) {
          Stack() {
            Circle()
              .width(64)
              .height(64)
              .fill($r('app.color.brand_primary_light'))
            Text(this.userName.substring(0, 1))
              .fontSize(26)
              .fontWeight(FontWeight.Bold)
              .fontColor($r('app.color.brand_primary'))
          }
          Text(this.userName)
            .fontSize($r('app.float.subtitle_font_size'))
            .fontWeight(FontWeight.Medium)
            .fontColor($r('app.color.text_primary'))
          Text(this.role)
            .fontSize(11)
            .fontColor($r('app.color.text_secondary'))
        }
        .width('100%')
        .padding({ top: 36, bottom: 20 })
        .alignItems(HorizontalAlign.Center)

        AppCard({ space: 0, cardPadding: 0 }) {
          this.ProfileRow('手机号', this.phone)
          Divider().color($r('app.color.divider')).margin({ left: 16 })
          this.ProfileRow($r('app.string.profile_settings'), '')
            .onClick(() => this.toggleSettings())
          if (this.showSettings) {
            Column({ space: 0 }) {
              this.SettingsRow('接收推送通知', true)
              Divider().color($r('app.color.divider')).margin({ left: 32 })
              this.SettingsRow('夜间模式', false)
              Divider().color($r('app.color.divider')).margin({ left: 32 })
              this.SettingsRow('清除缓存', false)
            }
          }
          Divider().color($r('app.color.divider')).margin({ left: 16 })
          this.ProfileRow($r('app.string.profile_about'), '')
            .onClick(() => {
              promptAction.showDialog({
                title: '关于',
                message: '车载安全监测 v1.0.0\nBuild 2026\n© 2026 Safety Monitor',
                buttons: [{ text: '确定', color: '#1677FF' }]
              })
            })
        }

        Column() {
          Row() {
            Text($r('app.string.profile_logout'))
              .fontSize($r('app.float.body_font_size'))
              .fontColor($r('app.color.status_red'))
          }
          .width('100%')
          .height(50)
          .justifyContent(FlexAlign.Center)
          .backgroundColor($r('app.color.bg_card'))
          .borderRadius($r('app.float.card_radius'))
          .onClick(() => this.logout())
        }
        .padding({ left: 16, right: 16 })
        .shadow({ radius: 10, color: $r('app.color.shadow_card'), offsetX: 0, offsetY: 2 })
      }
    }
    .scrollBar(BarState.Off)
    .edgeEffect(EdgeEffect.Spring)
    .width('100%')
    .height('100%')
  }

  @Builder
  ProfileRow(label: ResourceStr, value: string) {
    Row() {
      Text(label)
        .fontSize($r('app.float.body_font_size'))
        .fontColor($r('app.color.text_primary'))
      Blank()
      if (value.length > 0) {
        Text(value)
          .fontSize($r('app.float.body_font_size'))
          .fontColor($r('app.color.text_secondary'))
      }
      Text('›')
        .fontSize(18)
        .fontColor($r('app.color.text_hint'))
        .margin({ left: 6 })
    }
    .width('100%')
    .height(52)
    .padding({ left: 16, right: 16 })
  }

  @Builder
  SettingsRow(label: string, enabled: boolean) {
    Row() {
      Text(label)
        .fontSize($r('app.float.body_font_size'))
        .fontColor($r('app.color.text_primary'))
      Blank()
      Text(enabled ? '已开启' : '未开启')
        .fontSize(11)
        .fontColor($r('app.color.text_secondary'))
        .margin({ right: 6 })
    }
    .width('100%')
    .height(48)
    .padding({ left: 32, right: 16 })
    .onClick(() => {
      promptAction.showToast({ message: `${label} -- 已${enabled ? '' : '未'}开启`, duration: 1000 })
    })
  }
}
