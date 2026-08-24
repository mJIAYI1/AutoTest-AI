<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '../api/http'
import { getTestReport, listTestReports } from '../api/testReports'
import TestRunResultDialog from './TestRunResultDialog.vue'
import type {
  ApiTestReport,
  TestReportDetail,
  TestReportSummary,
  TestResult,
  TestRunStatus,
} from '../types/api'

const props = defineProps<{ projectId: number }>()
const reports = ref<TestReportSummary[]>([])
const statusFilter = ref<'ALL' | TestRunStatus>('ALL')
const loading = ref(true)
const detailLoading = ref(false)
const detailVisible = ref(false)
const selectedDetail = ref<TestReportDetail | null>(null)
const resultVisible = ref(false)
const selectedResultId = ref<number | null>(null)

const filteredReports = computed(() => statusFilter.value === 'ALL'
  ? reports.value
  : reports.value.filter((item) => item.status === statusFilter.value))

async function loadReports() {
  loading.value = true
  try {
    reports.value = await listTestReports(props.projectId, 50)
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

async function openReport(report: TestReportSummary) {
  detailVisible.value = true
  detailLoading.value = true
  selectedDetail.value = null
  try {
    selectedDetail.value = await getTestReport(props.projectId, report.id)
  } catch (error) {
    detailVisible.value = false
    ElMessage.error(getErrorMessage(error))
  } finally {
    detailLoading.value = false
  }
}

function openResult(result: TestResult) {
  selectedResultId.value = result.id
  resultVisible.value = true
}

function statusTag(status: TestRunStatus): 'success' | 'danger' | 'warning' | 'info' | 'primary' {
  if (status === 'PASS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'ERROR' || status === 'CANCELLED') return 'warning'
  return 'primary'
}

function statusText(status: TestRunStatus) {
  return {
    PENDING: '等待执行',
    RUNNING: '执行中',
    PASS: '通过',
    FAIL: '断言失败',
    ERROR: '执行错误',
    CANCELLED: '已取消',
  }[status]
}

function methodTag(method: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (method === 'GET') return 'success'
  if (method === 'POST') return 'primary'
  if (method === 'PUT' || method === 'PATCH') return 'warning'
  if (method === 'DELETE') return 'danger'
  return 'info'
}

function formatTime(value: string | null) {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))
}

function resultCounts(report: TestReportSummary | ApiTestReport) {
  return `通过 ${report.passedCount} · 失败 ${report.failedCount} · 错误 ${report.errorCount}`
}

watch(() => props.projectId, loadReports)
onMounted(loadReports)
</script>

<template>
  <section class="workspace-panel report-panel" v-loading="loading">
    <div class="panel-heading">
      <div><h2>测试报告</h2><p>汇总单用例与测试套件执行历史，并按 API 查看请求、响应、断言和 AI 诊断。</p></div>
      <el-button type="primary" plain @click="loadReports">刷新报告</el-button>
    </div>

    <div class="report-toolbar">
      <div>
        <span class="selector-label">运行状态</span>
        <el-select v-model="statusFilter" style="width: 150px">
          <el-option label="全部状态" value="ALL" />
          <el-option label="通过" value="PASS" />
          <el-option label="断言失败" value="FAIL" />
          <el-option label="执行错误" value="ERROR" />
          <el-option label="执行中" value="RUNNING" />
          <el-option label="等待执行" value="PENDING" />
        </el-select>
      </div>
      <span>显示最近 {{ reports.length }} 次执行</span>
    </div>

    <div v-if="filteredReports.length" class="table-card report-table-card">
      <el-table :data="filteredReports">
        <el-table-column label="报告" min-width="230">
          <template #default="{ row }: { row: TestReportSummary }">
            <div class="report-name-cell">
              <strong>{{ row.title }}</strong>
              <small>Run #{{ row.id }} · {{ row.runType === 'SUITE' ? '测试套件' : '单用例' }} · {{ row.environmentName || '项目默认环境' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结果" min-width="190">
          <template #default="{ row }: { row: TestReportSummary }">
            <div class="report-result-cell">
              <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
              <small>{{ resultCounts(row) }}<span v-if="row.skippedCount"> · 跳过 {{ row.skippedCount }}</span></small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="通过率" width="105">
          <template #default="{ row }: { row: TestReportSummary }"><strong class="report-rate">{{ row.passRate.toFixed(1) }}%</strong></template>
        </el-table-column>
        <el-table-column label="平均耗时" width="110">
          <template #default="{ row }: { row: TestReportSummary }">{{ row.averageResponseTimeMs === null ? '—' : `${row.averageResponseTimeMs} ms` }}</template>
        </el-table-column>
        <el-table-column label="执行时间" width="155">
          <template #default="{ row }: { row: TestReportSummary }">{{ formatTime(row.finishedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }: { row: TestReportSummary }"><el-button link type="primary" @click="openReport(row)">查看报告</el-button></template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty v-else :image-size="90" description="当前筛选条件下没有测试报告" />

    <el-dialog v-model="detailVisible" title="测试报告详情" width="min(1120px, 97vw)" top="2vh" destroy-on-close>
      <div v-loading="detailLoading" class="report-detail-body">
        <template v-if="selectedDetail">
          <div class="report-detail-heading">
            <div><small>TEST REPORT · RUN #{{ selectedDetail.summary.id }}</small><h3>{{ selectedDetail.summary.title }}</h3><span>{{ formatTime(selectedDetail.summary.finishedAt || selectedDetail.summary.createdAt) }}</span></div>
            <el-tag :type="statusTag(selectedDetail.summary.status)" size="large" effect="dark">{{ statusText(selectedDetail.summary.status) }}</el-tag>
          </div>

          <div class="report-summary-grid">
            <div><small>总用例</small><strong>{{ selectedDetail.summary.totalCount }}</strong></div>
            <div><small>通过</small><strong class="suite-count--pass">{{ selectedDetail.summary.passedCount }}</strong></div>
            <div><small>失败</small><strong class="suite-count--fail">{{ selectedDetail.summary.failedCount }}</strong></div>
            <div><small>错误</small><strong class="suite-count--error">{{ selectedDetail.summary.errorCount }}</strong></div>
            <div><small>通过率</small><strong>{{ selectedDetail.summary.passRate.toFixed(1) }}%</strong></div>
            <div><small>平均响应</small><strong>{{ selectedDetail.summary.averageResponseTimeMs === null ? '—' : `${selectedDetail.summary.averageResponseTimeMs} ms` }}</strong></div>
          </div>

          <el-alert v-if="selectedDetail.summary.errorMessage" type="warning" show-icon :closable="false" :title="selectedDetail.summary.errorMessage" />

          <div class="report-api-heading"><h3>按接口查看结果</h3><span>{{ selectedDetail.apis.length }} 个 API · {{ selectedDetail.summary.executedCount }} 个已执行步骤</span></div>
          <el-collapse class="report-api-collapse">
            <el-collapse-item v-for="api in selectedDetail.apis" :key="api.apiId" :name="api.apiId">
              <template #title>
                <div class="report-api-title">
                  <el-tag :type="methodTag(api.method)" size="small">{{ api.method }}</el-tag>
                  <code>{{ api.path }}</code>
                  <span>{{ api.summary || '暂无接口说明' }}</span>
                  <b>{{ api.passRate.toFixed(1) }}%</b>
                </div>
              </template>
              <div class="report-api-metrics"><span>{{ resultCounts(api) }}</span><span>平均响应 {{ api.averageResponseTimeMs === null ? '—' : `${api.averageResponseTimeMs} ms` }}</span></div>
              <el-table :data="api.results" size="small">
                <el-table-column prop="sequenceNumber" label="#" width="55" />
                <el-table-column prop="testCaseName" label="测试用例" min-width="200" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }: { row: TestResult }"><el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag></template>
                </el-table-column>
                <el-table-column label="HTTP" width="80">
                  <template #default="{ row }: { row: TestResult }">{{ row.responseStatus ?? '—' }}</template>
                </el-table-column>
                <el-table-column label="响应耗时" width="105">
                  <template #default="{ row }: { row: TestResult }">{{ row.responseTimeMs === null ? '—' : `${row.responseTimeMs} ms` }}</template>
                </el-table-column>
                <el-table-column label="断言" width="95">
                  <template #default="{ row }: { row: TestResult }">{{ row.assertions.filter((item) => item.passed).length }} / {{ row.assertions.length }}</template>
                </el-table-column>
                <el-table-column label="操作" width="105" fixed="right">
                  <template #default="{ row }: { row: TestResult }"><el-button link type="primary" @click="openResult(row)">完整详情</el-button></template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
          <el-empty v-if="!selectedDetail.apis.length" :image-size="75" description="本次运行尚未产生步骤结果" />
        </template>
      </div>
      <template #footer><el-button @click="detailVisible = false">关闭</el-button></template>
    </el-dialog>

    <TestRunResultDialog
      v-model="resultVisible"
      :run="selectedDetail?.run || null"
      :test-case-name="selectedDetail?.summary.title || '测试结果'"
      :initial-result-id="selectedResultId"
    />
  </section>
</template>
