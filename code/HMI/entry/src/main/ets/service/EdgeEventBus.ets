import { DomainEvent, DomainEventType } from '../model/DomainEvent'
import { hilog } from '@kit.PerformanceAnalysisKit'

type EventHandler = (event: DomainEvent) => void

const DOMAIN = 0x0001
const TAG = 'EdgeEventBus'

export class EdgeEventBus {
  private handlers: Map<DomainEventType, EventHandler[]> = new Map()
  private static instance: EdgeEventBus | null = null

  static getInstance(): EdgeEventBus {
    if (EdgeEventBus.instance === null) {
      EdgeEventBus.instance = new EdgeEventBus()
    }
    return EdgeEventBus.instance
  }

  subscribe(eventType: DomainEventType, handler: EventHandler): void {
    let existing = this.handlers.get(eventType)
    if (existing === undefined) {
      existing = []
    }
    existing.push(handler)
    this.handlers.set(eventType, existing)
    hilog.info(DOMAIN, TAG, 'Subscribed to ' + eventType)
  }

  unsubscribe(eventType: DomainEventType, handler: EventHandler): void {
    const existing = this.handlers.get(eventType)
    if (existing !== undefined) {
      const idx = existing.indexOf(handler)
      if (idx >= 0) {
        existing.splice(idx, 1)
        this.handlers.set(eventType, existing)
      }
    }
  }

  publish(event: DomainEvent): void {
    const existing = this.handlers.get(event.eventType)
    if (existing !== undefined) {
      for (let i = 0; i < existing.length; i++) {
        existing[i](event)
      }
    }
    hilog.info(DOMAIN, TAG, 'Published event: ' + event.eventType)
  }
}
