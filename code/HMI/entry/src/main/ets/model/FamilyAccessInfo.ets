export class FamilyAccessInfo {
  readonly granted: boolean
  readonly memberName: string
  readonly accessType: string
  readonly timestamp: number
  readonly pending: boolean
  readonly requestId: string

  constructor(granted: boolean, memberName: string, accessType: string, timestamp: number,
    pending: boolean = false, requestId: string = '') {
    this.granted = granted
    this.memberName = memberName
    this.accessType = accessType
    this.timestamp = timestamp
    this.pending = pending
    this.requestId = requestId
  }

  static createPending(requestId: string, accessType: string): FamilyAccessInfo {
    return new FamilyAccessInfo(false, '家属',
      accessType === 'VIDEO' ? '视频通话' : '语音对讲',
      Date.now(), true, requestId)
  }

  static createGranted(accessType: string): FamilyAccessInfo {
    return new FamilyAccessInfo(true, '家属', accessType, Date.now(), false, '')
  }
}
