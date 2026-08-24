<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { getDashboardSummary } from '../api/dashboard'
import { getErrorMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'
import type { DashboardSummary, FailingApi, RecentFailure } from '../types/api'

const router = useRouter()
const authStore = useAuthStore()
const loading = ref(true)
const summary = ref<DashboardSummary | null>(null)

const greetingName = computed(() => authStore.displayName || '测试同学')
const responseTimeMax = computed(() => Math.max(
  1,
  ...(summary.value?.responseTimeTrend
    .map((item) => item.averageResponseTimeMs)
    .filter((value): value is number => value !== null) || []),
))

async function loadDashboard() {
  loading.value = true
  try {
    summary.value = await getDashboardSummary()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function methodTag(method: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (method === 'GET') return 'success'
  if (method === 'POST') return 'primary'
  if (method === 'PUT' || method === 'PATCH') return 'warning'
  if (method === 'DELETE') return 'danger'
  return 'info'
}

function openFailure(failure: RecentFailure) {
  router.push({
    path: `/projects/${failure.projectId}`,
    query: { tab: 'test-reports' },
  })
}

function openFailingApi(api: FailingApi) {
  router.push({
    path: `/projects/${api.projectId}`,
    query: { tab: 'test-reports' },
  })
}

function barHeight(value: number | null, max: number) {
  if (value === null) return '4px'
  return `${Math.max(6, (value / Math.max(max, 1)) * 100)}%`
}

function formatTrendDay(value: string) {
  const date = new Date(`${value}T00:00:00`)
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

onMounted(loadDashboard)
</script>

<template>
  <section class="page-section dashboard-page" v-loading="loading">
    <div class="page-heading dashboard-heading">
      <div>
        <p class="eyebrow">DASHBOARD</p>
        <h1>欢迎回来，{{ greetingName }}</h1>
        <p>从测试资产到运行结果，快速掌握当前 API 自动化测试状态。</p>
      </div>
      <div class="dashboard-heading-actions">
        <el-button plain @click="loadDashboard">刷新数据</el-button>
        <el-button type="primary" @click="router.push('/projects')">管理项目</el-button>
      </div>
    </div>

    <template v-if="summary">
      <div class="dashboard-metrics-grid">
        <article class="dashboard-metric-card dashboard-metric-card--projects">
          <span class="dashboard-metric-icon">P</span>
          <div><small>项目数量</small><strong>{{ summary.projectCount }}</strong><p>独立测试空间</p></div>
        </article>
        <article class="dashboard-metric-card dashboard-metric-card--apis">
          <span class="dashboard-metric-icon">API</span>
          <div><small>API 数量</small><strong>{{ summary.apiCount }}</strong><p>已导入接口定义</p></div>
        </article>
        <article class="dashboard-metric-card dashboard-metric-card--cases">
          <span class="dashboard-metric-icon">TC</span>
          <div><small>测试用例</small><strong>{{ summary.testCaseCount }}</strong><p>可执行用例资产</p></div>
        </article>
        <article class="dashboard-metric-card dashboard-metric-card--runs">
          <span class="dashboard-metric-icon">7D</span>
          <div><small>最近执行</small><strong>{{ summary.recentRunCount }}</strong><p>近 {{ summary.recentWindowDays }} 天运行次数</p></div>
        </article>
        <article class="dashboard-metric-card dashboard-metric-card--rate">
          <span class="dashboard-metric-icon">%</span>
          <div><small>总体通过率</small><strong>{{ summary.overallPassRate.toFixed(1) }}%</strong><p>已完成运行的计划步骤</p></div>
        </article>
      </div>

      <div class="dashboard-analytics-grid">
        <section class="dashboard-insight-card">
          <div class="dashboard-insight-heading">
            <div><small>PASS RATE · 7 DAYS</small><h2>7 天通过率趋势</h2></div>
            <span>按计划步骤统计</span>
          </div>
          <div class="dashboard-trend-chart dashboard-trend-chart--rate">
            <div v-for="point in summary.passRateTrend" :key="point.date" class="dashboard-trend-column">
              <b>{{ point.passRate === null ? '—' : `${point.passRate.toFixed(0)}%` }}</b>
              <div class="dashboard-trend-track">
                <i :class="{ 'dashboard-trend-bar--empty': point.passRate === null }" :style="{ height: barHeight(point.passRate, 100) }" />
              </div>
              <small>{{ formatTrendDay(point.date) }}</small>
            </div>
          </div>
        </section>

        <section class="dashboard-insight-card">
          <div class="dashboard-insight-heading">
            <div><small>RESPONSE TIME · 7 DAYS</small><h2>接口平均响应时间</h2></div>
            <span>仅统计有效响应</span>
          </div>
          <div class="dashboard-trend-chart dashboard-trend-chart--response">
            <div v-for="point in summary.responseTimeTrend" :key="point.date" class="dashboard-trend-column">
              <b>{{ point.averageResponseTimeMs === null ? '—' : `${point.averageResponseTimeMs}ms` }}</b>
              <div class="dashboard-trend-track">
                <i :class="{ 'dashboard-trend-bar--empty': point.averageResponseTimeMs === null }" :style="{ height: barHeight(point.averageResponseTimeMs, responseTimeMax) }" />
              </div>
              <small>{{ formatTrendDay(point.date) }}</small>
            </div>
          </div>
        </section>

        <section class="dashboard-insight-card dashboard-top-api-card">
          <div class="dashboard-insight-heading">
            <div><small>FAILURE RANKING · 7 DAYS</small><h2>失败最多 API</h2></div>
            <span>TOP 5</span>
          </div>
          <ol v-if="summary.topFailingApis.length" class="dashboard-top-api-list">
            <li v-for="(api, index) in summary.topFailingApis" :key="api.apiId">
              <button type="button" @click="openFailingApi(api)">
                <span class="dashboard-api-rank">{{ index + 1 }}</span>
                <div><strong>{{ api.method }} {{ api.path }}</strong><small>{{ api.projectName }} · {{ api.failureCount }} 次失败</small></div>
                <b>{{ api.failureRate.toFixed(1) }}%</b>
              </button>
            </li>
          </ol>
          <div v-else class="dashboard-insight-empty">近 7 天暂无失败 API</div>
        </section>
      </div>

      <section class="dashboard-section-card">
        <div class="dashboard-section-heading">
          <div><small>RECENT FAILURES</small><h2>最近失败测试</h2><p>显示当前账号所有项目中最近的断言失败和执行错误。</p></div>
          <el-tag v-if="summary.recentFailures.length" type="danger" effect="light">
            {{ summary.recentFailures.length }} 条待关注
          </el-tag>
        </div>

        <el-table v-if="summary.recentFailures.length" :data="summary.recentFailures" class="dashboard-failure-table">
          <el-table-column label="项目 / 测试用例" min-width="230">
            <template #default="{ row }: { row: RecentFailure }">
              <div class="dashboard-failure-name"><strong>{{ row.testCaseName }}</strong><small>{{ row.projectName }} · Run #{{ row.runId }}</small></div>
            </template>
          </el-table-column>
          <el-table-column label="API" min-width="260">
            <template #default="{ row }: { row: RecentFailure }">
              <div class="dashboard-failure-api"><el-tag :type="methodTag(row.method)" size="small">{{ row.method }}</el-tag><code>{{ row.path }}</code></div>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="115">
            <template #default="{ row }: { row: RecentFailure }">
              <el-tag :type="row.status === 'FAIL' ? 'danger' : 'warning'" size="small">{{ row.status === 'FAIL' ? '断言失败' : '执行错误' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="HTTP / 耗时" width="130">
            <template #default="{ row }: { row: RecentFailure }">{{ row.responseStatus ?? '—' }} · {{ row.responseTimeMs === null ? '—' : `${row.responseTimeMs} ms` }}</template>
          </el-table-column>
          <el-table-column label="执行时间" width="140">
            <template #default="{ row }: { row: RecentFailure }">{{ formatTime(row.executedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }: { row: RecentFailure }"><el-button link type="primary" @click="openFailure(row)">查看报告</el-button></template>
          </el-table-column>
        </el-table>

        <div v-else class="dashboard-empty-state">
          <span>✓</span>
          <div><strong>暂时没有失败记录</strong><p>执行测试后，最新的 FAIL 和 ERROR 会集中显示在这里。</p></div>
          <el-button type="primary" plain @click="router.push('/projects')">进入项目执行测试</el-button>
        </div>
      </section>
    </template>
  </section>
</template>
