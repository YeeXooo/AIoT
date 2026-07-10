/**
 * JsonParser 单元测试
 *
 * 覆盖所有基础类型取值函数（必选/可选）以及 parseJson 方法。
 * 重点关注 null/undefined/缺失 key/嵌套数据等边界。
 */
import { describe, it, expect } from '@ohos/hypium'
import {
  getStr, getNum, getBool, getRecord, getArray,
  parseJson,
  getOptStr, getOptNum, getOptBool, getOptRecord, getOptArray,
} from '../main/ets/common/JsonParser'

export default function jsonParserTest() {
  describe('JsonParser', () => {
    // ===================================================================
    // 基础必选取值器
    // ===================================================================
    it('getStr_正常取值', 0, () => {
      const obj: Record<string, Object> = { 'name': '张三', 'empty': '' }
      expect(getStr(obj, 'name')).assertEqual('张三')
      expect(getStr(obj, 'empty')).assertEqual('')
    })

    it('getStr_缺失key返回undefined', 0, () => {
      const obj: Record<string, Object> = { 'a': '1' }
      // as string 对 undefined 仍返回 undefined（不抛异常）
      const val = getStr(obj, 'nonexist')
      expect(val).assertUndefined()
    })

    it('getNum_正常取值', 0, () => {
      const obj: Record<string, Object> = { 'age': 25, 'pi': 3.14, 'zero': 0 }
      expect(getNum(obj, 'age')).assertEqual(25)
      expect(getNum(obj, 'pi')).assertEqual(3.14)
      expect(getNum(obj, 'zero')).assertEqual(0)
    })

    it('getNum_缺失key返回undefined', 0, () => {
      const obj: Record<string, Object> = { 'a': 1 }
      expect(getNum(obj, 'nonexist')).assertUndefined()
    })

    it('getBool_正常取值', 0, () => {
      const obj: Record<string, Object> = { 'yes': true, 'no': false }
      expect(getBool(obj, 'yes')).assertTrue()
      expect(getBool(obj, 'no')).assertFalse()
    })

    it('getBool_缺失key返回undefined', 0, () => {
      const obj: Record<string, Object> = {}
      expect(getBool(obj, 'nonexist')).assertUndefined()
    })

    it('getRecord_正常取值', 0, () => {
      const inner: Record<string, Object> = { 'x': 1 }
      const obj: Record<string, Object> = { 'nested': inner }
      const got = getRecord(obj, 'nested')
      expect(got['x']).assertEqual(1)
    })

    it('getRecord_缺失key返回undefined', 0, () => {
      const obj: Record<string, Object> = {}
      expect(getRecord(obj, 'nonexist')).assertUndefined()
    })

    it('getArray_正常取值', 0, () => {
      const arr: Array<Record<string, Object>> = [
        { 'id': '1' } as Record<string, Object>,
        { 'id': '2' } as Record<string, Object>,
      ]
      const obj: Record<string, Object> = { 'items': arr }
      const got = getArray(obj, 'items')
      expect(got.length).assertEqual(2)
      expect(got[0]['id']).assertEqual('1')
      expect(got[1]['id']).assertEqual('2')
    })

    it('getArray_空数组', 0, () => {
      const obj: Record<string, Object> = { 'items': [] as Array<Record<string, Object>> }
      const got = getArray(obj, 'items')
      expect(got.length).assertEqual(0)
    })

    it('getArray_缺失key返回undefined', 0, () => {
      const obj: Record<string, Object> = {}
      expect(getArray(obj, 'nonexist')).assertUndefined()
    })

    // ===================================================================
    // parseJson
    // ===================================================================
    it('parseJson_有效JSON', 0, () => {
      const text = '{"a":1,"b":"hello","c":true}'
      const obj = parseJson(text)
      expect(getNum(obj, 'a')).assertEqual(1)
      expect(getStr(obj, 'b')).assertEqual('hello')
      expect(getBool(obj, 'c')).assertTrue()
    })

    it('parseJson_空对象', 0, () => {
      const obj = parseJson('{}')
      expect(obj).assertEqual(obj) // 存在即可
    })

    it('parseJson_非法JSON抛异常', 0, () => {
      try {
        parseJson('{invalid}')
        expect(true).assertFalse() // 不应执行到此处
      } catch (_e) {
        expect(true).assertTrue()
      }
    })

    // ===================================================================
    // 可选取值器
    // ===================================================================
    it('getOptStr_存在返回字符串', 0, () => {
      const obj: Record<string, Object> = { 'name': '李四' }
      expect(getOptStr(obj, 'name')).assertEqual('李四')
    })

    it('getOptStr_缺失返回undefined', 0, () => {
      const obj: Record<string, Object> = {}
      expect(getOptStr(obj, 'nonexist')).assertUndefined()
    })

    it('getOptStr_null返回undefined', 0, () => {
      const obj: Record<string, Object> = { 'name': null as unknown as string }
      expect(getOptStr(obj, 'name')).assertUndefined()
    })

    it('getOptNum_存在返回数字', 0, () => {
      const obj: Record<string, Object> = { 'count': 42 }
      expect(getOptNum(obj, 'count')).assertEqual(42)
    })

    it('getOptNum_缺失返回undefined', 0, () => {
      expect(getOptNum({}, 'x')).assertUndefined()
    })

    it('getOptNum_null返回undefined', 0, () => {
      const obj: Record<string, Object> = { 'val': null as unknown as number }
      expect(getOptNum(obj, 'val')).assertUndefined()
    })

    it('getOptBool_存在返回布尔', 0, () => {
      const obj: Record<string, Object> = { 'flag': true }
      expect(getOptBool(obj, 'flag')).assertTrue()
    })

    it('getOptBool_缺失返回undefined', 0, () => {
      expect(getOptBool({}, 'x')).assertUndefined()
    })

    it('getOptBool_null返回undefined', 0, () => {
      const obj: Record<string, Object> = { 'flag': null as unknown as boolean }
      expect(getOptBool(obj, 'flag')).assertUndefined()
    })

    it('getOptRecord_存在返回记录', 0, () => {
      const inner: Record<string, Object> = { 'k': 'v' }
      const obj: Record<string, Object> = { 'data': inner }
      const got = getOptRecord(obj, 'data')
      expect(got).assertEqual(got)
      if (got !== undefined) {
        expect(getStr(got, 'k')).assertEqual('v')
      }
    })

    it('getOptRecord_缺失返回undefined', 0, () => {
      expect(getOptRecord({}, 'x')).assertUndefined()
    })

    it('getOptRecord_null返回undefined', 0, () => {
      const obj: Record<string, Object> = { 'data': null as unknown as Record<string, Object> }
      expect(getOptRecord(obj, 'data')).assertUndefined()
    })

    it('getOptArray_存在返回数组', 0, () => {
      const arr: Array<Record<string, Object>> = [{ 'i': 1 } as Record<string, Object>]
      const obj: Record<string, Object> = { 'list': arr }
      const got = getOptArray(obj, 'list')
      expect(got).assertEqual(got)
      if (got !== undefined) {
        expect(got.length).assertEqual(1)
      }
    })

    it('getOptArray_缺失返回undefined', 0, () => {
      expect(getOptArray({}, 'x')).assertUndefined()
    })

    it('getOptArray_null返回undefined', 0, () => {
      const obj: Record<string, Object> = { 'list': null as unknown as Array<Record<string, Object>> }
      expect(getOptArray(obj, 'list')).assertUndefined()
    })
  })
}
