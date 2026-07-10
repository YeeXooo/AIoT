/**
 * Auth 模型 fromJson 构造器单元测试
 *
 * 覆盖 loginResponseFromJson / refreshTokenResponseFromJson / secondaryVerifyResponseFromJson
 */
import { describe, it, expect } from '@ohos/hypium'
import {
  loginResponseFromJson,
  refreshTokenResponseFromJson,
  secondaryVerifyResponseFromJson,
} from '../main/ets/model/auth'
import type { LoginResponse, RefreshTokenResponse, SecondaryVerifyResponse } from '../main/ets/model/auth'

export default function modelAuthTest() {
  describe('ModelAuth', () => {
    // ===================================================================
    // loginResponseFromJson
    // ===================================================================
    it('loginResponseFromJson_完整数据', 0, () => {
      const raw: Record<string, Object> = {
        'accessToken': 'eyJhbGciOiJIUzI1NiJ9.xxx',
        'refreshToken': 'rt_abc123',
        'tokenType': 'Bearer',
        'expiresIn': 3600,
        'accountId': 'acc_001',
        'role': 'FAMILY',
      }
      const dto: LoginResponse = loginResponseFromJson(raw)
      expect(dto.accessToken).assertEqual('eyJhbGciOiJIUzI1NiJ9.xxx')
      expect(dto.refreshToken).assertEqual('rt_abc123')
      expect(dto.tokenType).assertEqual('Bearer')
      expect(dto.expiresIn).assertEqual(3600)
      expect(dto.accountId).assertEqual('acc_001')
      expect(dto.role).assertEqual('FAMILY')
    })

    it('loginResponseFromJson_MANAGER角色', 0, () => {
      const raw: Record<string, Object> = {
        'accessToken': 'tok_mgr',
        'refreshToken': 'rt_mgr',
        'tokenType': 'Bearer',
        'expiresIn': 7200,
        'accountId': 'acc_mgr',
        'role': 'MANAGER',
      }
      const dto = loginResponseFromJson(raw)
      expect(dto.role).assertEqual('MANAGER')
      expect(dto.expiresIn).assertEqual(7200)
    })

    it('loginResponseFromJson_RESCUE角色', 0, () => {
      const raw: Record<string, Object> = {
        'accessToken': 'tok_rescue',
        'refreshToken': 'rt_rescue',
        'tokenType': 'Bearer',
        'expiresIn': 3600,
        'accountId': 'acc_rescue',
        'role': 'RESCUE',
      }
      expect(loginResponseFromJson(raw).role).assertEqual('RESCUE')
    })

    it('loginResponseFromJson_缺失字段返回undefined', 0, () => {
      const raw: Record<string, Object> = {
        'accessToken': 'tok',
        'refreshToken': 'rt',
        'tokenType': 'Bearer',
        'expiresIn': 3600,
        // 故意缺失 accountId / role
      }
      const dto = loginResponseFromJson(raw)
      expect(dto.accountId).assertUndefined()
      expect(dto.role).assertUndefined()
    })

    // ===================================================================
    // refreshTokenResponseFromJson
    // ===================================================================
    it('refreshTokenResponseFromJson_完整数据', 0, () => {
      const raw: Record<string, Object> = {
        'accessToken': 'new_at',
        'refreshToken': 'new_rt',
        'tokenType': 'Bearer',
        'expiresIn': 1800,
      }
      const dto: RefreshTokenResponse = refreshTokenResponseFromJson(raw)
      expect(dto.accessToken).assertEqual('new_at')
      expect(dto.refreshToken).assertEqual('new_rt')
      expect(dto.tokenType).assertEqual('Bearer')
      expect(dto.expiresIn).assertEqual(1800)
    })

    // ===================================================================
    // secondaryVerifyResponseFromJson
    // ===================================================================
    it('secondaryVerifyResponseFromJson_完整数据', 0, () => {
      const raw: Record<string, Object> = {
        'secondaryAuthToken': 'sat_2fa_token',
        'expiresAt': '2026-07-10T17:00:00Z',
      }
      const dto: SecondaryVerifyResponse = secondaryVerifyResponseFromJson(raw)
      expect(dto.secondaryAuthToken).assertEqual('sat_2fa_token')
      expect(dto.expiresAt).assertEqual('2026-07-10T17:00:00Z')
    })
  })
}
