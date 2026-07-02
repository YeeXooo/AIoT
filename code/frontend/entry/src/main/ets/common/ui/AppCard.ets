/**
 * 通用卡片 — 统一圆角 / 阴影 / 内边距 / 页边距。
 * 用法：AppCard({ cardPadding: 18 }) { ... }
 */
@Component
export struct AppCard {
  @Prop marginH: number = 16
  @Prop cardPadding: number = 18
  @Prop radius: number = 14
  @Prop shadowRadius: number = 10
  @Prop space: number = 0
  @BuilderParam content: () => void

  build() {
    Column({ space: this.space }) {
      this.content()
    }
    .width('100%')
    .padding(this.cardPadding)
    .backgroundColor($r('app.color.bg_card'))
    .borderRadius(this.radius)
    .margin({ left: this.marginH, right: this.marginH })
    .shadow({ radius: this.shadowRadius, color: $r('app.color.shadow_card'), offsetX: 0, offsetY: 2 })
  }
}

/** 区块标题（标题 + 右侧可选操作文字） */
@Component
export struct SectionTitle {
  @Prop title: ResourceStr = ''
  @Prop actionText: string = ''
  @Prop actionId: string = ''  // 标识哪个 action 被点击，由父组件在 onAction 回调中区分
  onAction: (id: string) => void = () => {}

  build() {
    Row() {
      Text(this.title)
        .fontSize($r('app.float.subtitle_font_size'))
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
      Blank()
      if (this.actionText.length > 0) {
        Text(this.actionText)
          .fontSize($r('app.float.caption_font_size'))
          .fontColor($r('app.color.brand_accent'))
          .onClick(() => this.onAction(this.actionId))
      }
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 4, bottom: 8 })
  }
}
