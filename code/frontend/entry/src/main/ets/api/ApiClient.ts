/**
 * 车载安全监测系统 — 基础 HTTP 客户端（ArkTS 原生）
 *
 * 使用 @kit.NetworkKit.http（ArkTS 原生 HTTP），替代原 fetch/AbortController
 * （web API，ArkTS 不可用）。
 *
 * ArkTS 限制遵守：
 * - 禁止 `any` / `unknown`（arkts-no-any-unknown）
 * - 对象字面量必须对应显式 interface（arkts-no-untyped-obj-literals）
 * - 禁止 `as T`（T 为用户接口/泛型参数）；仅允许 as 基础类型 / Record
 * - JSON.parse 返回 object，通过 Record 访问字段
 */

import { http } from '@kit.NetworkKit'
import { hilog } from '@kit.PerformanceAnalysisKit'

const DOMAIN = 0x0501
const TAG = 'ApiClient'

// ===================================================================
// 通用类型
// ===================================================================

/** API 错误响应体（遵循 docs/ood_interface.md §一 约定） */
export interface ApiErrorBody {
  errorCode: string
  message: string
  requestId: string
}

/** API 通用响应包装（ArkTS 支持泛型接口） */
export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: ApiErrorBody
  status: number
}

/** HTTP 方法 */
type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

/** 请求选项 */
export interface RequestOptions {
  headers?: Record<string, string>
  timeout?: number
  params?: Object
}

/** 内部请求结果（避免 untyped object literal） */
interface InternalResult {
  ok: boolean
  status: number
  body: string
  contentType: string
}

// ===================================================================
// ApiClient — 基于 @kit.NetworkKit.http
// ===================================================================

export class ApiClient {
  private baseUrl: string
  private accessToken: string | null = null
  private onTokenExpired?: () => void

  constructor(baseUrl: string = 'http://172.22.103.50:8080/api/v1') {
    this.baseUrl = baseUrl.replace(/\/+$/, '')
  }

  setAccessToken(token: string | null): void {
    this.accessToken = token
  }

  getAccessToken(): string | null {
    return this.accessToken
  }

  setOnTokenExpired(callback: () => void): void {
    this.onTokenExpired = callback
  }

  /** 拼 query string（手写，ArkTS 无 URLSearchParams） */
  private buildUrl(path: string, params?: Object): string {
    const url = `${this.baseUrl}${path.startsWith('/') ? path : `/${path}`}`
    if (!params) return url
    const parts: string[] = []
    const entries = Object.entries(params)
    for (let i = 0; i < entries.length; i++) {
      const [key, value] = entries[i]
      if (value !== undefined && value !== null) {
        parts.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
      }
    }
    return parts.length > 0 ? `${url}?${parts.join('&')}` : url
  }

  /** HttpMethod → http.RequestMethod 映射 */
  private toRequestMethod(method: HttpMethod): http.RequestMethod {
    if (method === 'POST') return http.RequestMethod.POST
    if (method === 'PUT') return http.RequestMethod.PUT
    if (method === 'DELETE') return http.RequestMethod.DELETE
    return http.RequestMethod.GET
  }

  /**
   * 底层请求 — 返回 InternalResult（显式 interface，满足 arkts-no-untyped-obj-literals）。
   */
  private async doRequest(
    method: HttpMethod,
    url: string,
    body: Object | undefined,
    headers: Record<string, string>,
    timeout: number,
  ): Promise<InternalResult> {
    const reqHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      ...headers,
    }
    if (this.accessToken) {
      reqHeaders['Authorization'] = `Bearer ${this.accessToken}`
    }

    const options: http.HttpRequestOptions = {
      method: this.toRequestMethod(method),
      header: reqHeaders,
      connectTimeout: timeout,
      readTimeout: timeout,
      expectDataType: http.HttpDataType.STRING,
    }
    if (body !== undefined) {
      options.extraData = JSON.stringify(body)
    }

    const httpRequest = http.createHttp()
    try {
      hilog.info(DOMAIN, TAG, '→ %{public}s %{public}s', method, url)
      const resp = await httpRequest.request(url, options)
      const status: number = resp.responseCode
      hilog.info(DOMAIN, TAG, '← %{public}d %{public}s', status, url)
      // resp.header 是 object；HTTP header 大小写不敏感，键名可能为
      // 'Content-Type' / 'content-type' 等，需遍历键做大小写无关匹配
      const headerObj: Record<string, Object> = resp.header as Record<string, Object>
      let contentType = ''
      const headerKeys = Object.keys(headerObj)
      for (let i = 0; i < headerKeys.length; i++) {
        if (headerKeys[i].toLowerCase() === 'content-type') {
          const ctVal = headerObj[headerKeys[i]]
          if (ctVal !== undefined && ctVal !== null) {
            contentType = String(ctVal)
          }
          break
        }
      }
      const resultStr: string = (resp.result !== undefined && resp.result !== null)
        ? String(resp.result) : ''
      return { ok: status >= 200 && status < 300, status, body: resultStr, contentType }
    } catch (err) {
      const errObj: Record<string, Object> = err as Record<string, Object>
      const msgVal = errObj['message']
      const message: string = (msgVal !== undefined && msgVal !== null) ? String(msgVal) : String(err)
      hilog.error(DOMAIN, TAG, '✗ %{public}s %{public}s | err: %{public}s', method, url, message)
      return { ok: false, status: 0, body: message, contentType: '' }
    } finally {
      httpRequest.destroy()
    }
  }

  /** 构造错误体（解析 JSON 错误响应） */
  private parseErrorBody(body: string): ApiErrorBody {
    try {
      const raw: Record<string, Object> = JSON.parse(body)
      return {
        errorCode: raw['errorCode'] as string,
        message: raw['message'] as string,
        requestId: raw['requestId'] as string,
      }
    } catch {
      return { errorCode: 'UnknownError', message: body, requestId: '' }
    }
  }

  /**
   * 通用请求 — 返回 ApiResponse<Record<string, Object>>
   */
  private async request(
    method: HttpMethod,
    path: string,
    body?: Object,
    options: RequestOptions = {},
  ): Promise<ApiResponse<Record<string, Object>>> {
    const { headers = {}, timeout = 30000, params } = options
    const url = this.buildUrl(path, params)

    const result = await this.doRequest(method, url, body, headers, timeout)

    // 204 No Content
    if (result.status === 204) {
      return { success: true, status: 204 }
    }

    // 非 JSON → 仅凭状态码判断。
    // 兜底：Content-Type 缺失但 body 以 { 或 [ 开头时仍按 JSON 尝试解析，
    // 避免部分网关/代理丢失 Content-Type 时误判为非 JSON。
    const isJson: boolean = result.contentType.toLowerCase().includes('application/json')
      || (result.body.length > 0 && (result.body.charAt(0) === '{' || result.body.charAt(0) === '['))
    if (!isJson) {
      return result.ok
        ? { success: true, status: result.status }
        : { success: false, status: result.status }
    }

    // JSON 响应
    if (!result.ok) {
      if (result.status === 401 && this.onTokenExpired) {
        this.onTokenExpired()
      }
      return {
        success: false,
        error: this.parseErrorBody(result.body),
        status: result.status,
      }
    }

    try {
      const raw: Record<string, Object> = JSON.parse(result.body) as Record<string, Object>
      return { success: true, data: raw, status: result.status }
    } catch (e) {
      const errMsg = String(e)
      hilog.error(DOMAIN, TAG, 'JSON parse failed: %{public}s | body: %{public}s', errMsg, result.body)
      return {
        success: false,
        error: { errorCode: 'ParseError', message: '响应 JSON 解析失败', requestId: '' },
        status: result.status,
      }
    }
  }

  /**
   * 文件下载 — 返回 ArrayBuffer（ArkTS 无 Blob；用 ArrayBuffer 替代）。
   */
  async downloadBlob(path: string, options?: RequestOptions): Promise<ApiResponse<ArrayBuffer>> {
    const { headers = {}, timeout = 30000, params } = options ?? {}
    const url = this.buildUrl(path, params)

    const reqHeaders: Record<string, string> = { ...headers }
    if (this.accessToken) {
      reqHeaders['Authorization'] = `Bearer ${this.accessToken}`
    }
    const optionsHttp: http.HttpRequestOptions = {
      method: http.RequestMethod.GET,
      header: reqHeaders,
      connectTimeout: timeout,
      readTimeout: timeout,
      expectDataType: http.HttpDataType.ARRAY_BUFFER,
    }

    const httpRequest = http.createHttp()
    try {
      const resp = await httpRequest.request(url, optionsHttp)
      const status: number = resp.responseCode
      if (status < 200 || status >= 300) {
        if (status === 401 && this.onTokenExpired) {
          this.onTokenExpired()
        }
        return { success: false, status }
      }
      const buf = resp.result as ArrayBuffer
      return { success: true, data: buf, status }
    } catch (err) {
      return { success: false, status: 0 }
    } finally {
      httpRequest.destroy()
    }
  }

  // ---- 便捷方法（返回原始 Record，无 as T 断言） ----

  async get(path: string, options?: RequestOptions): Promise<ApiResponse<Record<string, Object>>> {
    return this.request('GET', path, undefined, options)
  }

  async post(path: string, body?: Object, options?: RequestOptions): Promise<ApiResponse<Record<string, Object>>> {
    return this.request('POST', path, body, options)
  }

  async put(path: string, body?: Object, options?: RequestOptions): Promise<ApiResponse<Record<string, Object>>> {
    return this.request('PUT', path, body, options)
  }

  async delete(path: string, options?: RequestOptions): Promise<ApiResponse<Record<string, Object>>> {
    return this.request('DELETE', path, undefined, options)
  }
}

/** 全局单例 */
export const apiClient = new ApiClient()
