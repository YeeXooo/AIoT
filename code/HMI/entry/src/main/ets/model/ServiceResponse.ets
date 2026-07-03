import { InterventionInstruction } from './InterventionInstruction'
import { RiskLevel } from './RiskLevel'

export class QueryInterventionStatusResponse {
  readonly riskLevel: RiskLevel
  readonly activeInstructions: InterventionInstruction[]
  readonly tripId: string

  constructor(tripId: string, riskLevel: RiskLevel, activeInstructions: InterventionInstruction[]) {
    this.tripId = tripId
    this.riskLevel = riskLevel
    this.activeInstructions = activeInstructions
  }
}

export class ReportOverrideResponse {
  readonly accepted: boolean
  readonly message: string

  constructor(accepted: boolean, message: string) {
    this.accepted = accepted
    this.message = message
  }
}

export class GetDriverRiskStatusResponse {
  readonly driverId: string
  readonly riskLevel: RiskLevel
  readonly riskDescription: string

  constructor(driverId: string, riskLevel: RiskLevel, riskDescription: string) {
    this.driverId = driverId
    this.riskLevel = riskLevel
    this.riskDescription = riskDescription
  }
}
