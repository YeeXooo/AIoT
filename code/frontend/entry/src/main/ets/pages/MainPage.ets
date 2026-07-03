import { DashboardTab } from './tabs/DashboardTab'
import { GuardianshipTab } from './tabs/GuardianshipTab'
import { FleetTab } from './tabs/FleetTab'
import { ProfileTab } from './tabs/ProfileTab'

@Entry
@Component
struct MainPage {
  @State currentIndex: number = 0
  @State indicatorX: number = 0

  private tabLabels: ResourceStr[] = [
    $r('app.string.tab_dashboard'),
    $r('app.string.tab_guardianship'),
    $r('app.string.tab_fleet'),
    $r('app.string.tab_profile'),
  ]
  private tabIconOn: Resource[] = [
    $r('app.media.ic_tab_dashboard'),
    $r('app.media.ic_tab_guardianship'),
    $r('app.media.ic_tab_fleet'),
    $r('app.media.ic_tab_profile'),
  ]
  private tabIconOff: Resource[] = [
    $r('app.media.ic_tab_dashboard_off'),
    $r('app.media.ic_tab_guardianship_off'),
    $r('app.media.ic_tab_fleet_off'),
    $r('app.media.ic_tab_profile_off'),
  ]

  aboutToAppear(): void {
    this.updateIndicator(0)
  }

  private updateIndicator(index: number): void {
    const tabWidth = 100 / 4
    this.indicatorX = tabWidth * index
  }

  @Builder
  TabBuilder(title: ResourceStr, index: number, iconOn: Resource, iconOff: Resource) {
    Column({ space: 4 }) {
      Stack({ alignContent: Alignment.Center }) {
        if (this.currentIndex === index) {
          Circle()
            .width(32)
            .height(32)
            .fill($r('app.color.accent_blue_glow'))
            .opacity(1)
            .animation({ duration: 200, curve: Curve.EaseOut })
        }
      }
      .width(32)
      .height(32)
      Text(title)
        .fontSize($r('app.float.caption_font_size'))
        .fontWeight(this.currentIndex === index ? FontWeight.Medium : FontWeight.Normal)
        .fontColor(this.currentIndex === index ? $r('app.color.icon_active') : $r('app.color.icon_inactive'))
        .animation({ duration: 200, curve: Curve.EaseOut })
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .scale({ x: this.currentIndex === index ? 1.05 : 1.0, y: this.currentIndex === index ? 1.05 : 1.0 })
    .animation({ duration: 250, curve: Curve.EaseOut })
  }

  build() {
      Stack() {
        Column() {
        Tabs({ barPosition: BarPosition.End, index: this.currentIndex }) {
          TabContent() {
            DashboardTab()
          }
          .tabBar(this.TabBuilder(this.tabLabels[0], 0, this.tabIconOn[0], this.tabIconOff[0]))

          TabContent() {
            GuardianshipTab()
          }
          .tabBar(this.TabBuilder(this.tabLabels[1], 1, this.tabIconOn[1], this.tabIconOff[1]))

          TabContent() {
            FleetTab()
          }
          .tabBar(this.TabBuilder(this.tabLabels[2], 2, this.tabIconOn[2], this.tabIconOff[2]))

          TabContent() {
            ProfileTab()
          }
          .tabBar(this.TabBuilder(this.tabLabels[3], 3, this.tabIconOn[3], this.tabIconOff[3]))
        }
        .scrollable(false)
        .barHeight(64)
        .barMode(BarMode.Fixed)
        .barBackgroundColor($r('app.color.tabbar_bg_dark'))
        .onChange((index: number) => {
          this.currentIndex = index
          this.updateIndicator(index)
        })
      }
      .width('100%')
      .height('100%')
    }
    .width('100%')
    .height('100%')
    .linearGradient({
      direction: GradientDirection.Bottom,
      colors: [[$r('app.color.page_gradient_top'), 0.0], [$r('app.color.page_gradient_mid'), 0.3], [$r('app.color.page_gradient_bottom'), 1.0]]
    })
  }
}
