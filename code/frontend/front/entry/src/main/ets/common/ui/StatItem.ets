/**
 * 统计数字项 — 车队概览/生命体征共用。
 */
@Component
export struct StatItem {
  @Prop value: string = ''
  @Prop label: string = ''
  @Prop unit: string = ''
  @Prop color: Resource
  @Prop valueSize: number = 26
  @Prop vertical: boolean = true  // true: 数字在上；false: 数字与单位横排

  build() {
    Column({ space: 6 }) {
      if (this.unit.length > 0 && !this.vertical) {
        Row({ space: 2 }) {
          Text(this.value)
            .fontSize(this.valueSize)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.color)
          Text(this.unit)
            .fontSize(11)
            .fontColor($r('app.color.text_secondary'))
            .margin({ bottom: 3 })
        }
        .alignItems(VerticalAlign.Bottom)
      } else {
        Text(this.value)
          .fontSize(this.valueSize)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.color)
      }
      Text(this.label)
        .fontSize(11)
        .fontColor($r('app.color.text_secondary'))
    }
    .layoutWeight(1)
    .padding({ top: 14, bottom: 14 })
    .justifyContent(FlexAlign.Center)
    .alignItems(HorizontalAlign.Center)
    .backgroundColor($r('app.color.bg_card'))
    .borderRadius($r('app.float.card_radius'))
    .shadow({ radius: 8, color: $r('app.color.shadow_card'), offsetX: 0, offsetY: 1 })
  }
}
