/**
 * 空状态 / 加载骨架 / 错误重试 — 取代各 Tab 内联的零散占位。
 */
@Component
export struct EmptyState {
  @Prop icon: string = '✓'
  @Prop message: ResourceStr = ''
  @Prop hint: string = ''

  build() {
    Column({ space: 8 }) {
      Text(this.icon)
        .fontSize(40)
        .fontColor($r('app.color.text_hint'))
      Text(this.message)
        .fontSize($r('app.float.body_font_size'))
        .fontColor($r('app.color.text_secondary'))
      if (this.hint.length > 0) {
        Text(this.hint)
          .fontSize(11)
          .fontColor($r('app.color.text_hint'))
      }
    }
    .width('100%')
    .height(120)
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor($r('app.color.bg_card'))
    .borderRadius($r('app.float.card_radius'))
    .margin({ left: 16, right: 16 })
    .shadow({ radius: 8, color: $r('app.color.shadow_card'), offsetX: 0, offsetY: 1 })
  }
}

/** 骨架屏行（加载中占位） */
@Component
export struct SkeletonRow {
  build() {
    Row() {
      Column({ space: 6 }) {
        // 灰条用 Column 高度模拟
        Column().width(80).height(14).backgroundColor($r('app.color.divider')).borderRadius(4)
        Column().width(50).height(10).backgroundColor($r('app.color.divider')).borderRadius(4)
      }
      .alignItems(HorizontalAlign.Start)
      Blank()
      Column().width(40).height(18).backgroundColor($r('app.color.divider')).borderRadius(4)
    }
    .width('100%')
    .padding({ left: 14, right: 14, top: 12, bottom: 12 })
    .backgroundColor($r('app.color.bg_card'))
    .borderRadius($r('app.float.card_radius'))
    .opacity(0.6)
  }
}

/** 错误重试块 */
@Component
export struct ErrorRetry {
  @Prop message: string = '加载失败'
  retryAction: () => void = () => {}

  build() {
    Column({ space: 10 }) {
      Text('⚠')
        .fontSize(32)
        .fontColor($r('app.color.status_yellow'))
      Text(this.message)
        .fontSize($r('app.float.body_font_size'))
        .fontColor($r('app.color.text_secondary'))
      Text('点击重试')
        .fontSize($r('app.float.caption_font_size'))
        .fontColor($r('app.color.brand_accent'))
        .padding({ left: 14, right: 14, top: 6, bottom: 6 })
        .backgroundColor($r('app.color.brand_primary_light'))
        .borderRadius(8)
        .onClick(() => this.retryAction())
    }
    .width('100%')
    .padding({ top: 24, bottom: 24 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor($r('app.color.bg_card'))
    .borderRadius($r('app.float.card_radius'))
    .margin({ left: 16, right: 16 })
    .shadow({ radius: 8, color: $r('app.color.shadow_card'), offsetX: 0, offsetY: 1 })
  }
}
