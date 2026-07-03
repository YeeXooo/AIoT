export function getStr(obj: Record<string, Object>, key: string): string {
  return obj[key] as string
}

export function getNum(obj: Record<string, Object>, key: string): number {
  return obj[key] as number
}

export function getBool(obj: Record<string, Object>, key: string): boolean {
  return obj[key] as boolean
}

export function getRecord(obj: Record<string, Object>, key: string): Record<string, Object> {
  return obj[key] as Record<string, Object>
}

export function getArray(obj: Record<string, Object>, key: string): Array<Record<string, Object>> {
  return obj[key] as Array<Record<string, Object>>
}

export function parseJson(text: string): Record<string, Object> {
  return JSON.parse(text) as Record<string, Object>
}

export function getOptStr(obj: Record<string, Object>, key: string): string | undefined {
  if (obj[key] === undefined || obj[key] === null) {
    return undefined
  }
  return obj[key] as string
}

export function getOptNum(obj: Record<string, Object>, key: string): number | undefined {
  if (obj[key] === undefined || obj[key] === null) {
    return undefined
  }
  return obj[key] as number
}

export function getOptRecord(obj: Record<string, Object>, key: string): Record<string, Object> | undefined {
  if (obj[key] === undefined || obj[key] === null) {
    return undefined
  }
  return obj[key] as Record<string, Object>
}

export function getOptArray(obj: Record<string, Object>, key: string): Array<Record<string, Object>> | undefined {
  if (obj[key] === undefined || obj[key] === null) {
    return undefined
  }
  return obj[key] as Array<Record<string, Object>>
}
