/**
 * 主页容器 — 4 Tab + 底部图标导航栏。
 * 各 Tab 内容拆分至 pages/tabs/，本文件只负责容器与 TabBar。
 */
import { DashboardTab } from './tabs/DashboardTab'
import { GuardianshipTab } from './tabs/GuardianshipTab'
import { FleetTab } from './tabs/FleetTab'
import { ProfileTab } from './tabs/ProfileTab'
import { router } from '@kit.ArkUI'
import { sessionStore } from '../viewmodel/SessionStore'

@Entry
@Component
struct MainPage {
  @State currentIndex: number = 0

  aboutToAppear(): void {
    if (!sessionStore.isLoggedIn) {
      router.replaceUrl({ url: 'pages/LoginPage' })
      return
    }
  }

  @Builder
  TabBuilder(title: ResourceStr, index: number, iconOn: Resource, iconOff: Resource) {
    Column({ space: 3 }) {
      Image(this.currentIndex === index ? iconOn : iconOff)
        .width(22)
        .height(22)
      Text(title)
        .fontSize($r('app.float.caption_font_size'))
        .fontWeight(this.currentIndex === index ? FontWeight.Medium : FontWeight.Normal)
        .fontColor(this.currentIndex === index ? $r('app.color.brand_primary') : $r('app.color.text_hint'))
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  build() {
    Column() {
      Tabs({ barPosition: BarPosition.End, index: this.currentIndex }) {
        TabContent() {
          DashboardTab()
        }
        .tabBar(this.TabBuilder($r('app.string.tab_dashboard'), 0,
          $r('app.media.ic_tab_dashboard'), $r('app.media.ic_tab_dashboard_off')))

        TabContent() {
          GuardianshipTab()
        }
        .tabBar(this.TabBuilder($r('app.string.tab_guardianship'), 1,
          $r('app.media.ic_tab_guardianship'), $r('app.media.ic_tab_guardianship_off')))

        TabContent() {
          FleetTab()
        }
        .tabBar(this.TabBuilder($r('app.string.tab_fleet'), 2,
          $r('app.media.ic_tab_fleet'), $r('app.media.ic_tab_fleet_off')))

        TabContent() {
          ProfileTab()
        }
        .tabBar(this.TabBuilder($r('app.string.tab_profile'), 3,
          $r('app.media.ic_tab_profile'), $r('app.media.ic_tab_profile_off')))
      }
      .scrollable(false)
      .barHeight(62)
      .barMode(BarMode.Fixed)
      .onChange((index: number) => {
        this.currentIndex = index
      })
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.bg_page'))
  }
}
