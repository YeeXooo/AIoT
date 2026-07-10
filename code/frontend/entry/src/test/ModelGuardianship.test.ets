/**
 * Guardianship 模型 fromJson 构造器单元测试
 *
 * 覆盖 guardianship.ts 中所有 DTO 构造器。
 */
import { describe, it, expect } from '@ohos/hypium'
import {
  requestMediaSessionRespFromJson,
  triggerManualRescueRespFromJson,
  queryWindowStatusRespFromJson,
  guardianshipPermissionEntryFromJson,
  careRelationshipSummaryFromJson,
  queryGuardianshipPermissionsRespFromJson,
  issueSparkRTCTokenRespFromJson,
} from '../main/ets/model/guardianship'
import type {
  RequestMediaSessionResp,
  TriggerManualRescueResp,
  QueryWindowStatusResp,
  GuardianshipPermissionEntry,
  CareRelationshipSummary,
  QueryGuardianshipPermissionsResp,
  IssueSparkRTCTokenResp,
} from '../main/ets/model/guardianship'

export default function modelGuardianshipTest() {
  describe('ModelGuardianship', () => {
    // ===================================================================
    // requestMediaSessionRespFromJson
    // ===================================================================
    it('requestMediaSessionRespFromJson_完整数据', 0, () => {
      const raw: Record<string, Object> = {
        'sessionHandle': 'sh_001',
        'sessionToken': 'st_abc',
        'sparkRTCRoomId': 'room_42',
        'sparkRTCJoinToken': 'join_tok_xyz',
      }
      const dto: RequestMediaSessionResp = requestMediaSessionRespFromJson(raw)
      expect(dto.sessionHandle).assertEqual('sh_001')
      expect(dto.sessionToken).assertEqual('st_abc')
      expect(dto.sparkRTCRoomId).assertEqual('room_42')
      expect(dto.sparkRTCJoinToken).assertEqual('join_tok_xyz')
    })

    // ===================================================================
    // triggerManualRescueRespFromJson
    // ===================================================================
    it('triggerManualRescueRespFromJson_完整数据', 0, () => {
      const raw: Record<string, Object> = {
        'rescueRequestId': 'rr_001',
        'rescueReportId': 'rpt_001',
        'status': 'PENDING',
      }
      const dto: TriggerManualRescueResp = triggerManualRescueRespFromJson(raw)
      expect(dto.rescueRequestId).assertEqual('rr_001')
      expect(dto.rescueReportId).assertEqual('rpt_001')
      expect(dto.status).assertEqual('PENDING')
    })

    it('triggerManualRescueRespFromJson_所有状态', 0, () => {
      const statuses = ['PENDING', 'CONFIRMED', 'REJECTED']
      for (let i = 0; i < statuses.length; i++) {
        const raw: Record<string, Object> = {
          'rescueRequestId': 'rr',
          'rescueReportId': 'rpt',
          'status': statuses[i],
        }
        expect(triggerManualRescueRespFromJson(raw).status).assertEqual(statuses[i])
      }
    })

    // ===================================================================
    // queryWindowStatusRespFromJson
    // ===================================================================
    it('queryWindowStatusRespFromJson_多个车窗', 0, () => {
      const raw: Record<string, Object> = {
        'windowStatuses': [
          { 'windowPosition': 'FRONT_LEFT', 'state': 'CLOSED', 'updatedAt': '2026-07-10T10:00:00Z' } as Record<string, Object>,
          { 'windowPosition': 'FRONT_RIGHT', 'state': 'OPEN', 'updatedAt': '2026-07-10T10:01:00Z' } as Record<string, Object>,
          { 'windowPosition': 'REAR_LEFT', 'state': 'PARTIAL', 'updatedAt': '2026-07-10T10:02:00Z' } as Record<string, Object>,
        ] as Array<Record<string, Object>>,
      }
      const dto: QueryWindowStatusResp = queryWindowStatusRespFromJson(raw)
      expect(dto.windowStatuses.length).assertEqual(3)
      expect(dto.windowStatuses[0].windowPosition).assertEqual('FRONT_LEFT')
      expect(dto.windowStatuses[0].state).assertEqual('CLOSED')
      expect(dto.windowStatuses[1].windowPosition).assertEqual('FRONT_RIGHT')
      expect(dto.windowStatuses[1].state).assertEqual('OPEN')
      expect(dto.windowStatuses[2].windowPosition).assertEqual('REAR_LEFT')
      expect(dto.windowStatuses[2].state).assertEqual('PARTIAL')
    })

    it('queryWindowStatusRespFromJson_空数组', 0, () => {
      const raw: Record<string, Object> = { 'windowStatuses': [] as Array<Record<string, Object>> }
      expect(queryWindowStatusRespFromJson(raw).windowStatuses.length).assertEqual(0)
    })

    // ===================================================================
    // guardianshipPermissionEntryFromJson
    // ===================================================================
    it('guardianshipPermissionEntryFromJson_已授予有到期', 0, () => {
      const raw: Record<string, Object> = {
        'permissionType': 'MEDIA_CALL',
        'granted': true,
        'grantedAt': '2026-07-01T00:00:00Z',
        'expiresAt': '2026-08-01T00:00:00Z',
      }
      const dto: GuardianshipPermissionEntry = guardianshipPermissionEntryFromJson(raw)
      expect(dto.permissionType).assertEqual('MEDIA_CALL')
      expect(dto.granted).assertTrue()
      expect(dto.grantedAt).assertEqual('2026-07-01T00:00:00Z')
      expect(dto.expiresAt).assertEqual('2026-08-01T00:00:00Z')
    })

    it('guardianshipPermissionEntryFromJson_未授予无到期', 0, () => {
      const raw: Record<string, Object> = {
        'permissionType': 'WINDOW_CONTROL',
        'granted': false,
        'grantedAt': '2026-07-01T00:00:00Z',
        // expiresAt 缺失
      }
      const dto = guardianshipPermissionEntryFromJson(raw)
      expect(dto.granted).assertFalse()
      expect(dto.expiresAt).assertUndefined()
    })

    it('guardianshipPermissionEntryFromJson_所有权限类型', 0, () => {
      const types = ['MEDIA_CALL', 'WINDOW_CONTROL', 'MANUAL_RESCUE', 'STATUS_MONITORING']
      for (let i = 0; i < types.length; i++) {
        const raw: Record<string, Object> = {
          'permissionType': types[i],
          'granted': true,
          'grantedAt': '2026-07-01T00:00:00Z',
        }
        expect(guardianshipPermissionEntryFromJson(raw).permissionType).assertEqual(types[i])
      }
    })

    // ===================================================================
    // careRelationshipSummaryFromJson
    // ===================================================================
    it('careRelationshipSummaryFromJson_ACTIVE', 0, () => {
      const raw: Record<string, Object> = {
        'status': 'ACTIVE',
        'establishedAt': '2026-06-15T12:00:00Z',
      }
      const dto: CareRelationshipSummary = careRelationshipSummaryFromJson(raw)
      expect(dto.status).assertEqual('ACTIVE')
      expect(dto.establishedAt).assertEqual('2026-06-15T12:00:00Z')
    })

    it('careRelationshipSummaryFromJson_所有状态', 0, () => {
      const statuses = ['ACTIVE', 'SUSPENDED', 'REVOKED']
      for (let i = 0; i < statuses.length; i++) {
        const raw: Record<string, Object> = {
          'status': statuses[i],
          'establishedAt': '2026-06-15T12:00:00Z',
        }
        expect(careRelationshipSummaryFromJson(raw).status).assertEqual(statuses[i])
      }
    })

    // ===================================================================
    // queryGuardianshipPermissionsRespFromJson
    // ===================================================================
    it('queryGuardianshipPermissionsRespFromJson_完整数据', 0, () => {
      const raw: Record<string, Object> = {
        'familyAccountId': 'fam_001',
        'driverId': 'd_001',
        'permissions': [
          { 'permissionType': 'MEDIA_CALL', 'granted': true, 'grantedAt': 't1' } as Record<string, Object>,
          { 'permissionType': 'STATUS_MONITORING', 'granted': true, 'grantedAt': 't2' } as Record<string, Object>,
        ] as Array<Record<string, Object>>,
        'careRelationship': { 'status': 'ACTIVE', 'establishedAt': 't0' } as Record<string, Object>,
      }
      const dto: QueryGuardianshipPermissionsResp = queryGuardianshipPermissionsRespFromJson(raw)
      expect(dto.familyAccountId).assertEqual('fam_001')
      expect(dto.driverId).assertEqual('d_001')
      expect(dto.permissions.length).assertEqual(2)
      expect(dto.permissions[0].permissionType).assertEqual('MEDIA_CALL')
      expect(dto.careRelationship.status).assertEqual('ACTIVE')
    })

    // ===================================================================
    // issueSparkRTCTokenRespFromJson
    // ===================================================================
    it('issueSparkRTCTokenRespFromJson_完整数据', 0, () => {
      const raw: Record<string, Object> = {
        'token': 'rtc_token_val',
        'expiresAt': '2026-07-10T17:10:00Z',
      }
      const dto: IssueSparkRTCTokenResp = issueSparkRTCTokenRespFromJson(raw)
      expect(dto.token).assertEqual('rtc_token_val')
      expect(dto.expiresAt).assertEqual('2026-07-10T17:10:00Z')
    })
  })
}
