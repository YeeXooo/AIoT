export enum DomainEventType {
  CAMERA_OCCLUSION_DETECTED = 'CameraOcclusionDetectedEvent',
  CAMERA_OCCLUSION_REMOVED = 'CameraOcclusionRemovedEvent',
  FAMILY_ACCESS_GRANTED = 'FamilyAccessGrantedEvent',
  FAMILY_ACCESS_REVOKED = 'FamilyAccessRevokedEvent',
  RISK_DETERMINED = 'RiskDeterminedEvent',
  RISK_RESOLVED = 'RiskResolvedEvent',
  SENSOR_FAILURE = 'SensorFailureEvent',
  LIFE_DETECTED = 'LifeDetectedEvent',
  EMERGENCY_ACTIVATED = 'EmergencyActivatedEvent'
}

export class DomainEvent {
  readonly eventType: DomainEventType
  readonly payload: Map<string, string>
  readonly timestamp: number

  constructor(eventType: DomainEventType, payload: Map<string, string>, timestamp: number) {
    this.eventType = eventType
    this.payload = payload
    this.timestamp = timestamp
  }
}
