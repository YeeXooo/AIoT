/**
 * 车载安全监测系统 — 基础 HTTP 客户端（ArkTS 兼容）
 *
 * ArkTS 限制：
 * - 禁止 `as T`（T 为用户接口/泛型参数）
 * - 禁止 `any` 类型
 * - JSON.parse 返回 `object`，通过 Record 访问字段
 */

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
  params?: object
}

// ===================================================================
// ApiClient — JSON 解析采用 Record + getter 模式
// ===================================================================

export class ApiClient {
  private baseUrl: string
  private accessToken: string | null = null
  private onTokenExpired?: () => void

  constructor(baseUrl: string = '/api/v1') {
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

  private buildUrl(path: string, params?: object): string {
    const url = `${this.baseUrl}${path.startsWith('/') ? path : `/${path}`}`
    if (!params) return url

    const searchParams = new URLSearchParams()
    const entries = Object.entries(params)
    for (let i = 0; i < entries.length; i++) {
      const [key, value] = entries[i]
      if (value !== undefined && value !== null) {
        searchParams.append(key, String(value))
      }
    }
    const qs = searchParams.toString()
    return qs ? `${url}?${qs}` : url
  }

  /**
   * 通用请求 — 内部返回 Record<string, unknown>
   * 不执行 as T 断言（ArkTS 禁止），调用方通过泛型 T 标注类型
   */
  private async request(
    method: HttpMethod,
    path: string,
    body?: object,
    options: RequestOptions = {}
  ): Promise<ApiResponse<Record<string, unknown>>> {
    const { headers = {}, timeout = 30000, params } = options
    const url = this.buildUrl(path, params)

    const reqHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      ...headers,
    }

    if (this.accessToken) {
      reqHeaders['Authorization'] = `Bearer ${this.accessToken}`
    }

    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), timeout)

    try {
      const response = await fetch(url, {
        method,
        headers: reqHeaders,
        body: body ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      })

      clearTimeout(timeoutId)

      // 204 No Content
      if (response.status === 204) {
        return { success: true, status: 204 }
      }

      const contentType = response.headers.get('content-type') || ''
      if (contentType.includes('application/json')) {
        const raw: Record<string, unknown> = JSON.parse(await response.text())

        if (!response.ok) {
          if (response.status === 401 && this.onTokenExpired) {
            this.onTokenExpired()
          }

          const errorBody: ApiErrorBody = {
            errorCode: raw['errorCode'] as string,
            message: raw['message'] as string,
            requestId: raw['requestId'] as string,
          }
          return {
            success: false,
            error: errorBody,
            status: response.status,
          }
        }

        // 返回原始数据（不执行 as T 断言 — ArkTS 禁止）
        return {
          success: true,
          data: raw,
          status: response.status,
        }
      }

      // 非 JSON 响应 → 失败
      if (!response.ok) {
        return { success: false, status: response.status }
      }

      // 文件下载（Blob） — 单独处理，不依赖泛型断言
      return {
        success: true,
        status: response.status,
      }
    } catch (err) {
      clearTimeout(timeoutId)

      // ArkTS 安全判断：不使用 instanceof DOMException
      const errObj: Record<string, unknown> = err as Record<string, unknown>
      if (errObj['name'] !== undefined && errObj['name'] === 'AbortError') {
        return {
          success: false,
          error: { errorCode: 'RequestTimeout', message: `请求超时（${timeout}ms）`, requestId: '' },
          status: 0,
        }
      }

      const message = errObj['message'] !== undefined ? errObj['message'] as string : String(err)
      return {
        success: false,
        error: { errorCode: 'NetworkError', message: `网络错误: ${message}`, requestId: '' },
        status: 0,
      }
    }
  }

  /**
   * 文件下载 — 返回 Blob
   * 不通过泛型，避免 as T 断言
   */
  async downloadBlob(path: string, options?: RequestOptions): Promise<ApiResponse<Blob>> {
    const { headers = {}, timeout = 30000, params } = options ?? {}
    const url = this.buildUrl(path, params)

    const reqHeaders: Record<string, string> = { ...headers }
    if (this.accessToken) {
      reqHeaders['Authorization'] = `Bearer ${this.accessToken}`
    }

    const controller = new AbortController()
    const timeoutId = setTimeout(() => controller.abort(), timeout)

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: reqHeaders,
        signal: controller.signal,
      })
      clearTimeout(timeoutId)

      if (!response.ok) {
        return { success: false, status: response.status }
      }

      const blob = await response.blob()
      return { success: true, data: blob, status: response.status }
    } catch (err) {
      clearTimeout(timeoutId)
      return { success: false, status: 0 }
    }
  }

  // ---- 便捷方法（ArkTS 兼容：返回原始 Record，无 as T 断言） ----

  async get(path: string, options?: RequestOptions): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request('GET', path, undefined, options)
  }

  async post(path: string, body?: object, options?: RequestOptions): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request('POST', path, body, options)
  }

  async put(path: string, body?: object, options?: RequestOptions): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request('PUT', path, body, options)
  }

  async delete(path: string, options?: RequestOptions): Promise<ApiResponse<Record<string, unknown>>> {
    return this.request('DELETE', path, undefined, options)
  }
}

/** 全局单例 */
export const apiClient = new ApiClient()
