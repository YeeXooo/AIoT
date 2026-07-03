export class FamilyAccessInfo {
  readonly granted: boolean
  readonly memberName: string
  readonly accessType: string
  readonly timestamp: number

  constructor(granted: boolean, memberName: string, accessType: string, timestamp: number) {
    this.granted = granted
    this.memberName = memberName
    this.accessType = accessType
    this.timestamp = timestamp
  }
}
