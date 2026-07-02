/**
 * 状态徽章 — 取代 MainPage 中重复的状态色 pill 渲染。
 * 用法：StatusBadge({ status: this.statusColor })
 */
import { driverStatusVisual } from '../StatusMap'
import type { StatusColor } from '../../model/types'

@Component
export struct StatusBadge {
  @Prop status: StatusColor | 'OFFLINE' = 'GREEN'

  build() {
    Row({ space: 4 }) {
      Circle()
        .width(6)
        .height(6)
        .fill(driverStatusVisual(this.status).color)
      Text(driverStatusVisual(this.status).text)
        .fontSize(11)
        .fontWeight(FontWeight.Medium)
        .fontColor(driverStatusVisual(this.status).color)
    }
    .padding({ left: 10, right: 10, top: 5, bottom: 5 })
    .borderRadius(20)
    .backgroundColor(driverStatusVisual(this.status).bgColor)
  }
}

/** 风险等级小标签（L1/L2/L3） */
@Component
export struct RiskBadge {
  @Prop level: string = 'L1_HINT'

  build() {
    Text(this.shortLabel())
      .fontSize(11)
      .fontWeight(FontWeight.Medium)
      .fontColor(this.color())
      .padding({ left: 8, right: 8, top: 3, bottom: 3 })
      .borderRadius(4)
      .backgroundColor(this.bgColor())
  }

  private shortLabel(): string {
    if (this.level.startsWith('L1')) return 'L1'
    if (this.level.startsWith('L2')) return 'L2'
    if (this.level.startsWith('L3')) return 'L3'
    return this.level
  }

  private color(): Resource {
    if (this.level.startsWith('L1')) return $r('app.color.status_green')
    if (this.level.startsWith('L2')) return $r('app.color.status_yellow')
    return $r('app.color.status_red')
  }

  private bgColor(): Resource {
    if (this.level.startsWith('L1')) return $r('app.color.status_green_bg')
    if (this.level.startsWith('L2')) return $r('app.color.status_yellow_bg')
    return $r('app.color.status_red_bg')
  }
}
