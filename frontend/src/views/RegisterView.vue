<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { getErrorMessage } from '../api/http'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(false)
const confirmPassword = ref('')
const form = reactive({ username: '', email: '', displayName: '', password: '' })

async function submit() {
  if (!form.username.trim() || !form.email.trim() || !form.password) {
    ElMessage.warning('请完整填写必填项')
    return
  }
  if (!/^[A-Za-z0-9_.-]{3,64}$/.test(form.username.trim())) {
    ElMessage.warning('用户名需为 3-64 位字母、数字、点、下划线或连字符')
    return
  }
  if (form.password.length < 8) {
    ElMessage.warning('密码至少需要 8 位')
    return
  }
  if (form.password !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  loading.value = true
  try {
    await authStore.register({
      username: form.username.trim(),
      email: form.email.trim(),
      displayName: form.displayName.trim(),
      password: form.password,
    })
    ElMessage.success('注册成功，欢迎使用 AutoTest AI')
    await router.replace('/dashboard')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <section class="auth-hero auth-hero--register">
      <RouterLink class="brand brand--light" to="/login">
        <span class="brand-mark">A</span>
        <span><strong>AutoTest AI</strong><small>API 自动化测试平台</small></span>
      </RouterLink>
      <div class="auth-hero__content">
        <span class="hero-badge">START BUILDING</span>
        <h1>从一个项目开始，<br />沉淀测试能力。</h1>
        <p>创建账号后即可建立项目、配置不同测试环境，并从 Swagger 或 OpenAPI 文档导入接口。</p>
      </div>
      <p class="auth-hero__footer">安全认证 · 项目数据隔离</p>
    </section>

    <main class="auth-form-wrap auth-form-wrap--register">
      <div class="auth-form-card">
        <p class="eyebrow">CREATE ACCOUNT</p>
        <h2>创建账号</h2>
        <p class="form-subtitle">加入你的 API 测试工作台</p>
        <form class="register-grid" @submit.prevent="submit">
          <div>
            <label class="field-label" for="username">用户名 *</label>
            <el-input id="username" v-model="form.username" size="large" maxlength="64" placeholder="例如 autotester" autocomplete="username" />
          </div>
          <div>
            <label class="field-label" for="displayName">显示名称</label>
            <el-input id="displayName" v-model="form.displayName" size="large" maxlength="100" placeholder="怎么称呼你" />
          </div>
          <div class="span-2">
            <label class="field-label" for="email">邮箱 *</label>
            <el-input id="email" v-model="form.email" size="large" type="email" maxlength="255" placeholder="name@example.com" autocomplete="email" />
          </div>
          <div>
            <label class="field-label" for="password">密码 *</label>
            <el-input id="password" v-model="form.password" size="large" type="password" show-password maxlength="72" placeholder="至少 8 位" autocomplete="new-password" />
          </div>
          <div>
            <label class="field-label" for="confirmPassword">确认密码 *</label>
            <el-input id="confirmPassword" v-model="confirmPassword" size="large" type="password" show-password maxlength="72" placeholder="再次输入" autocomplete="new-password" />
          </div>
          <el-button class="submit-button span-2" type="primary" size="large" native-type="submit" :loading="loading">创建账号</el-button>
        </form>
        <p class="auth-switch">已有账号？<RouterLink to="/login">返回登录</RouterLink></p>
      </div>
    </main>
  </div>
</template>
