/**
 * 视图状态机 — 所有 VM 共用的加载/错误/数据三态。
 */
export type LoadState = 'idle' | 'loading' | 'success' | 'error'

export interface ViewState<T> {
  state: LoadState
  data: T | null
  errorMsg: string
}

export function successState<T>(data: T): ViewState<T> {
  return { state: 'success', data, errorMsg: '' }
}
