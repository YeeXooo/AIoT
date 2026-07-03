import { router } from '@kit.ArkUI'
import { promptAction } from '@kit.ArkUI'
import { guardianshipVM } from '../viewmodel/GuardianshipViewModel'

@Entry
@Component
struct DriverBindPage {
  @State driverId: string = ''
  @State isBinding: boolean = false
  @State idFocused: boolean = false
  @State contentOpacity: number = 0
  @State contentOffsetY: number = 20

  aboutToAppear(): void {
    setTimeout(() => {
      this.contentOpacity = 1
      this.contentOffsetY = 0
    }, 150)
  }

  private async doBind(): Promise<void> {
    const id = this.driverId.trim()
    if (id.length === 0) {
      promptAction.showToast({ message: '请输入驾驶员 ID', duration: 1500 })
      return
    }
    this.isBinding = true
    const result = await guardianshipVM.addDriver(id)
    this.isBinding = false
    promptAction.showToast({ message: result.msg, duration: 1500 })
    if (result.ok) {
      setTimeout(() => {
        router.back()
      }, 600)
    }
  }

  build() {
    Column() {
      Row() {
        Text('←')
          .fontSize(20)
          .fontColor($r('app.color.text_on_dark'))
          .onClick(() => { router.back() })
        Text('绑定驾驶员')
          .fontSize($r('app.float.subtitle_font_size'))
          .fontWeight(FontWeight.Medium)
          .fontColor($r('app.color.text_on_dark'))
          .margin({ left: 12 })
      }
      .width('100%')
      .height(56)
      .padding({ left: 16, right: 16 })
      .alignItems(VerticalAlign.Center)

      Column({ space: 24 }) {
        Column({ space: 8 }) {
          Text('驾驶员 ID')
            .fontSize(11)
            .fontColor($r('app.color.text_muted'))
            .letterSpacing(1)
          Stack({ alignContent: Alignment.BottomStart }) {
            Column()
              .width(this.idFocused ? '100%' : '0%')
              .height(2)
              .backgroundColor($r('app.color.input_border_focus'))
              .animation({ duration: 300, curve: Curve.EaseOut })
            TextInput({ placeholder: '例如 d4', text: $$this.driverId })
              .fontSize($r('app.float.body_font_size'))
              .fontColor($r('app.color.text_on_dark'))
              .placeholderColor($r('app.color.input_placeholder'))
              .backgroundColor($r('app.color.input_bg'))
              .height(50)
              .borderRadius(10)
              .padding({ left: 14, right: 14 })
              .border({
                width: 1,
                color: this.idFocused ? $r('app.color.input_border_focus') : $r('app.color.input_border')
              })
              .animation({ duration: 200 })
              .onFocus(() => { this.idFocused = true })
              .onBlur(() => { this.idFocused = false })
          }
          .width('100%')
        }
        .alignItems(HorizontalAlign.Start)

        Button() {
          if (this.isBinding) {
            Row({ space: 8 }) {
              LoadingProgress()
                .width(20)
                .height(20)
                .color(Color.White)
              Text('绑定中…')
                .fontSize($r('app.float.subtitle_font_size'))
                .fontWeight(FontWeight.Medium)
                .fontColor(Color.White)
            }
          } else {
            Text('确认绑定')
              .fontSize($r('app.float.subtitle_font_size'))
              .fontWeight(FontWeight.Medium)
              .fontColor(Color.White)
          }
        }
        .width('100%')
        .height(50)
        .linearGradient({
          angle: 135,
          colors: [[$r('app.color.login_button_start'), 0.0], [$r('app.color.login_button_end'), 1.0]]
        })
        .borderRadius(12)
        .enabled(!this.isBinding && this.driverId.trim().length > 0)
        .opacity((!this.isBinding && this.driverId.trim().length > 0) ? 1 : 0.4)
        .shadow({
          radius: 12,
          color: $r('app.color.login_button_shadow'),
          offsetX: 0,
          offsetY: 4
        })
        .onClick(() => this.doBind())

        Column({ space: 6 }) {
          Text('绑定后可在「监护」页面查看')
            .fontSize(11)
            .fontColor($r('app.color.text_muted'))
          Text('并使用对讲、救援等功能')
            .fontSize(11)
            .fontColor($r('app.color.text_dim'))
        }
        .alignItems(HorizontalAlign.Center)
      }
      .width('100%')
      .padding({ left: 32, right: 32, top: 24 })
      .opacity(this.contentOpacity)
      .translate({ y: this.contentOffsetY })
      .animation({ duration: 500, curve: Curve.EaseOut })

      Blank()
    }
    .width('100%')
    .height('100%')
    .linearGradient({
      direction: GradientDirection.Bottom,
      colors: [[$r('app.color.page_gradient_top'), 0.0], [$r('app.color.page_gradient_mid'), 0.3], [$r('app.color.page_gradient_bottom'), 1.0]]
    })
  }
}
