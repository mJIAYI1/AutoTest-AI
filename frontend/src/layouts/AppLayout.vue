<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const activeMenu = computed(() => {
  if (route.path.startsWith('/projects')) return '/projects'
  if (route.path.startsWith('/dashboard')) return '/dashboard'
  return route.path
})

onMounted(() => {
  authStore.refreshUser().catch(() => undefined)
})

async function handleLogout() {
  authStore.logout()
  await router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside class="app-sidebar">
      <RouterLink class="brand" to="/dashboard">
        <span class="brand-mark">A</span>
        <span>
          <strong>AutoTest AI</strong>
          <small>API 自动化测试平台</small>
        </span>
      </RouterLink>

      <nav class="sidebar-nav">
        <p class="nav-label">工作台</p>
        <el-menu :default-active="activeMenu" router>
          <el-menu-item index="/dashboard">
            <span class="nav-icon">◫</span>
            <span>数据概览</span>
          </el-menu-item>
          <el-menu-item index="/projects">
            <span class="nav-icon">▦</span>
            <span>项目管理</span>
          </el-menu-item>
        </el-menu>
      </nav>

      <div class="phase-card">
        <span class="phase-card__dot" />
        <div>
          <strong>Phase 4B</strong>
          <p>Dashboard 核心概览</p>
        </div>
      </div>
    </aside>

    <div class="app-content">
      <header class="app-header">
        <div>
          <p class="eyebrow">AUTOTEST AI CONSOLE</p>
          <strong>测试资产工作台</strong>
        </div>
        <el-dropdown trigger="click">
          <button class="user-button" type="button">
            <span class="user-avatar">{{ authStore.displayName.slice(0, 1).toUpperCase() }}</span>
            <span class="user-copy">
              <strong>{{ authStore.displayName }}</strong>
              <small>{{ authStore.user?.email || '已登录' }}</small>
            </span>
            <span class="chevron">⌄</span>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </header>

      <main class="app-main">
        <RouterView />
      </main>
    </div>
  </div>
</template>
