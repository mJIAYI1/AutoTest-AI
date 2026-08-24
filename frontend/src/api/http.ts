import axios from 'axios'
import type { AxiosError } from 'axios'
import type { ApiError } from '../types/api'

export const AUTH_TOKEN_KEY = 'autotest_ai_access_token'
export const AUTH_USER_KEY = 'autotest_ai_user'

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

export const http = axios.create({
  baseURL: configuredBaseUrl || '',
  timeout: 20_000,
  headers: {
    Accept: 'application/json',
  },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    const isAuthRequest = error.config?.url?.includes('/api/auth/')
    if (error.response?.status === 401 && !isAuthRequest) {
      localStorage.removeItem(AUTH_TOKEN_KEY)
      localStorage.removeItem(AUTH_USER_KEY)
      if (!window.location.pathname.startsWith('/login')) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.assign(`/login?expired=1&redirect=${redirect}`)
      }
    }
    return Promise.reject(error)
  },
)

export function getErrorMessage(error: unknown): string {
  if (!axios.isAxiosError<ApiError>(error)) {
    return error instanceof Error ? error.message : '操作失败，请稍后重试'
  }

  const response = error.response?.data
  if (response?.fieldErrors && Object.keys(response.fieldErrors).length > 0) {
    return Object.entries(response.fieldErrors)
      .map(([field, message]) => `${field}: ${message}`)
      .join('；')
  }
  return response?.message || (error.code === 'ECONNABORTED' ? '请求超时，请检查后端服务' : error.message)
}
