<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getErrorMessage } from '../api/http'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

if (route.query.expired === '1') {
  ElMessage.warning('登录状态已失效，请重新登录')
}

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await authStore.login({ username: form.username.trim(), password: form.password })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-hero">
      <RouterLink class="brand brand--light" to="/login">
        <span class="brand-mark">A</span>
        <span><strong>AutoTest AI</strong><small>API 自动化测试平台</small></span>
      </RouterLink>
      <div class="auth-hero__content">
        <span class="hero-badge">JDK 17 · Vue 3 · OpenAPI</span>
        <h1>让每一个接口，<br />都经得起验证。</h1>
        <p>集中管理项目、测试环境与 OpenAPI 接口资产，为后续自动化执行和 AI 用例生成打好基础。</p>
        <div class="hero-points">
          <span>✓ 项目隔离</span><span>✓ 环境配置</span><span>✓ 规范导入</span>
        </div>
      </div>
      <p class="auth-hero__footer">Phase 1 · 基础平台</p>
    </section>

    <main class="auth-form-wrap">
      <div class="auth-form-card">
        <p class="eyebrow">WELCOME BACK</p>
        <h2>登录工作台</h2>
        <p class="form-subtitle">继续管理你的 API 测试项目</p>

        <form @submit.prevent="submit">
          <label class="field-label" for="username">用户名</label>
          <el-input id="username" v-model="form.username" size="large" maxlength="64" placeholder="请输入用户名" autocomplete="username" />
          <label class="field-label" for="password">密码</label>
          <el-input id="password" v-model="form.password" size="large" type="password" show-password maxlength="72" placeholder="请输入密码" autocomplete="current-password" @keyup.enter="submit" />
          <el-button class="submit-button" type="primary" size="large" native-type="submit" :loading="loading">登录</el-button>
        </form>

        <p class="auth-switch">还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
      </div>
    </main>
  </div>
</template>
