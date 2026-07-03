export class OtaProgressInfo {
  readonly stage: string
  readonly percent: number
  readonly estimatedTimeRemaining: string

  constructor(stage: string, percent: number, estimatedTimeRemaining: string) {
    this.stage = stage
    this.percent = percent
    this.estimatedTimeRemaining = estimatedTimeRemaining
  }
}
