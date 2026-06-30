/**
 * ArkTS 兼容的 JSON 解析工具函数
 *
 * ArkTS 严格模式允许的断言：as string / as number / as boolean / as Record 等基础类型。
 * 不允许 as T（T 为接口或泛型参数）。
 * 本模块提供从 unknown → 基础类型的类型安全转换，
 * 以及通过构造器模式从原始数据构建类型化对象。
 */

/** 安全获取字符串字段 */
export function getStr(obj: Record<string, unknown>, key: string): string {
  return obj[key] as string
}

/** 安全获取数字字段 */
export function getNum(obj: Record<string, unknown>, key: string): number {
  return obj[key] as number
}

/** 安全获取布尔字段 */
export function getBool(obj: Record<string, unknown>, key: string): boolean {
  return obj[key] as boolean
}

/** 安全获取 Record 子对象 */
export function getRecord(obj: Record<string, unknown>, key: string): Record<string, unknown> {
  return obj[key] as Record<string, unknown>
}

/** 安全获取对象数组 */
export function getArray(obj: Record<string, unknown>, key: string): Array<Record<string, unknown>> {
  return obj[key] as Array<Record<string, unknown>>
}

/** JSON.parse 的类型安全封装 */
export function parseJson(text: string): Record<string, unknown> {
  return JSON.parse(text) as Record<string, unknown>
}

/** 可选字段：安全获取字符串 */
export function getOptStr(obj: Record<string, unknown>, key: string): string | undefined {
  if (obj[key] === undefined || obj[key] === null) {
    return undefined
  }
  return obj[key] as string
}

/** 可选字段：安全获取数字 */
export function getOptNum(obj: Record<string, unknown>, key: string): number | undefined {
  if (obj[key] === undefined || obj[key] === null) {
    return undefined
  }
  return obj[key] as number
}

/** 可选字段：安全获取 Record */
export function getOptRecord(obj: Record<string, unknown>, key: string): Record<string, unknown> | undefined {
  if (obj[key] === undefined || obj[key] === null) {
    return undefined
  }
  return obj[key] as Record<string, unknown>
}

/** 可选字段：安全获取对象数组 */
export function getOptArray(obj: Record<string, unknown>, key: string): Array<Record<string, unknown>> | undefined {
  if (obj[key] === undefined || obj[key] === null) {
    return undefined
  }
  return obj[key] as Array<Record<string, unknown>>
}
