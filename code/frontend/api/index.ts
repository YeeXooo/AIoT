/**
 * 车载安全监测系统 — API 客户端统一导出
 */

export { ApiClient, apiClient } from './ApiClient'
export type { ApiResponse, ApiErrorBody, RequestOptions } from './ApiClient'
export { AuthApi, authApi } from './AuthApi'
export { DriverApi, driverApi } from './DriverApi'
export { GuardianshipApi, guardianshipApi } from './GuardianshipApi'
export { FleetApi, fleetApi } from './FleetApi'
export { GuardianshipWebSocket } from './GuardianshipWebSocket'
export type { GuardianshipWSEvents, GuardianshipWebSocketOptions } from './GuardianshipWebSocket'
export { FleetWebSocket } from './FleetWebSocket'
export type { FleetWSEvents, FleetWebSocketOptions } from './FleetWebSocket'
