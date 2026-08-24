import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '../api/auth'
import { AUTH_TOKEN_KEY, AUTH_USER_KEY } from '../api/http'
import type { AuthResponse, LoginPayload, RegisterPayload, User } from '../types/api'

function loadStoredUser(): User | null {
  const stored = localStorage.getItem(AUTH_USER_KEY)
  if (!stored) return null
  try {
    return JSON.parse(stored) as User
  } catch {
    localStorage.removeItem(AUTH_USER_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(AUTH_TOKEN_KEY))
  const user = ref<User | null>(loadStoredUser())
  const isAuthenticated = computed(() => Boolean(token.value))
  const displayName = computed(() => user.value?.displayName || user.value?.username || '用户')

  function applyAuth(response: AuthResponse) {
    token.value = response.accessToken
    user.value = response.user
    localStorage.setItem(AUTH_TOKEN_KEY, response.accessToken)
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(response.user))
  }

  async function login(payload: LoginPayload) {
    applyAuth(await authApi.login(payload))
  }

  async function register(payload: RegisterPayload) {
    applyAuth(await authApi.register(payload))
  }

  async function refreshUser() {
    if (!token.value) return
    user.value = await authApi.getCurrentUser()
    localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user.value))
  }

  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem(AUTH_TOKEN_KEY)
    localStorage.removeItem(AUTH_USER_KEY)
  }

  return {
    token,
    user,
    isAuthenticated,
    displayName,
    login,
    register,
    refreshUser,
    logout,
  }
})
