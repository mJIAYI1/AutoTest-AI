<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getProject } from '../api/projects'
import { getErrorMessage } from '../api/http'
import type { Project } from '../types/api'
import EnvironmentPanel from '../components/EnvironmentPanel.vue'
import OpenApiPanel from '../components/OpenApiPanel.vue'
import TestCasePanel from '../components/TestCasePanel.vue'
import TestReportPanel from '../components/TestReportPanel.vue'
import TestSuitePanel from '../components/TestSuitePanel.vue'

const props = defineProps<{ projectId: number }>()
const route = useRoute()
const router = useRouter()
const project = ref<Project | null>(null)
const loading = ref(true)
const workspaceTabs = new Set(['apis', 'environments', 'test-cases', 'test-suites', 'test-reports', 'overview'])
const requestedTab = typeof route.query.tab === 'string' ? route.query.tab : 'apis'
const activeTab = ref(workspaceTabs.has(requestedTab) ? requestedTab : 'apis')

async function loadProject() {
  if (!Number.isInteger(props.projectId) || props.projectId <= 0) {
    ElMessage.error('项目 ID 不正确')
    await router.replace('/projects')
    return
  }
  loading.value = true
  try {
    project.value = await getProject(props.projectId)
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
    await router.replace('/projects')
  } finally {
    loading.value = false
  }
}

watch(() => props.projectId, loadProject)
watch(
  () => route.query.tab,
  (tab) => {
    if (typeof tab === 'string' && workspaceTabs.has(tab)) activeTab.value = tab
  },
)
onMounted(loadProject)
</script>

<template>
  <section v-loading="loading" class="page-section project-workspace">
    <template v-if="project">
      <button class="back-link" type="button" @click="router.push('/projects')">← 返回项目列表</button>
      <div class="workspace-heading">
        <div class="workspace-title">
          <span class="project-symbol project-symbol--large">{{ project.name.slice(0, 1).toUpperCase() }}</span>
          <div>
            <p class="eyebrow">PROJECT WORKSPACE</p>
            <h1>{{ project.name }}</h1>
            <p>{{ project.description || '暂无项目描述' }}</p>
          </div>
        </div>
        <div class="workspace-base-url">
          <small>默认 BASE URL</small>
          <code>{{ project.baseUrl || '尚未配置' }}</code>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="workspace-tabs">
        <el-tab-pane label="接口定义" name="apis" lazy>
          <OpenApiPanel :project-id="projectId" />
        </el-tab-pane>
        <el-tab-pane label="测试环境" name="environments" lazy>
          <EnvironmentPanel :project-id="projectId" />
        </el-tab-pane>
        <el-tab-pane label="测试用例" name="test-cases" lazy>
          <TestCasePanel :project-id="projectId" />
        </el-tab-pane>
        <el-tab-pane label="测试套件" name="test-suites" lazy>
          <TestSuitePanel :project-id="projectId" />
        </el-tab-pane>
        <el-tab-pane label="测试报告" name="test-reports" lazy>
          <TestReportPanel :project-id="projectId" />
        </el-tab-pane>
        <el-tab-pane label="项目概览" name="overview" lazy>
          <section class="workspace-panel overview-panel">
            <div class="overview-grid">
              <article class="overview-card">
                <span class="overview-card__number">01</span>
                <div><strong>导入接口定义</strong><p>支持 OpenAPI 3.x 与 Swagger 2.0 的 URL 或本地文件。</p></div>
                <span class="status-pill status-pill--done">已完成</span>
              </article>
              <article class="overview-card">
                <span class="overview-card__number">02</span>
                <div><strong>配置测试环境</strong><p>维护 Base URL、公共请求头与环境变量。</p></div>
                <span class="status-pill status-pill--done">已完成</span>
              </article>
              <article class="overview-card">
                <span class="overview-card__number">03</span>
                <div><strong>编排测试用例</strong><p>按 API 维护请求数据、断言与响应变量提取规则。</p></div>
                <span class="status-pill status-pill--done">已完成</span>
              </article>
              <article class="overview-card">
                <span class="overview-card__number">04</span>
                <div><strong>执行单个用例</strong><p>通过后台线程池发送真实 HTTP 请求，执行断言并保存完整结果。</p></div>
                <span class="status-pill status-pill--done">已完成</span>
              </article>
              <article class="overview-card">
                <span class="overview-card__number">05</span>
                <div><strong>编排测试套件</strong><p>顺序执行多个用例，在同一次 Run 中传递提取变量并支持失败即停止。</p></div>
                <span class="status-pill status-pill--done">已完成</span>
              </article>
              <article class="overview-card">
                <span class="overview-card__number">06</span>
                <div><strong>查看测试报告</strong><p>汇总执行结果、通过率与平均响应时间，并按 API 展开失败证据和 AI 诊断。</p></div>
                <span class="status-pill status-pill--done">已完成</span>
              </article>
            </div>
          </section>
        </el-tab-pane>
      </el-tabs>
    </template>
  </section>
</template>
