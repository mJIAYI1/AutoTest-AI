import { http } from './http'
import type { AuthResponse, LoginPayload, RegisterPayload, User } from '../types/api'

export async function login(payload: LoginPayload): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>('/api/auth/login', payload)
  return data
}

export async function register(payload: RegisterPayload): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>('/api/auth/register', payload)
  return data
}

export async function getCurrentUser(): Promise<User> {
  const { data } = await http.get<User>('/api/users/me')
  return data
}
