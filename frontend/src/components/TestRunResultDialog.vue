<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { analyzeAiFailure, getAiFailureDiagnosis, getAiProviderStatus } from '../api/ai'
import { getErrorMessage } from '../api/http'
import type {
  AiDiagnosisSeverity,
  AiFailureDiagnosisResponse,
  AiProviderStatus,
  AssertionResult,
  TestResult,
  TestRun,
  TestRunStatus,
} from '../types/api'

const props = defineProps<{
  modelValue: boolean
  run: TestRun | null
  testCaseName: string
  initialResultId?: number | null
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const selectedResultId = ref<number | null>(null)
const aiStatus = ref<AiProviderStatus | null>(null)
const diagnosis = ref<AiFailureDiagnosisResponse | null>(null)
const loadingDiagnosis = ref(false)
const generatingDiagnosis = ref(false)
let diagnosisRequestGeneration = 0

const terminal = computed(() => props.run && ['PASS', 'FAIL', 'ERROR', 'CANCELLED'].includes(props.run.status))
const isSuite = computed(() => props.run?.runType === 'SUITE')
const activeResult = computed<TestResult | null>(() => {
  if (!props.run) return null
  if (props.run.runType === 'SINGLE_CASE') return props.run.result
  return props.run.results.find((item) => item.id === selectedResultId.value)
    || props.run.results[0]
    || null
})
const diagnosisApplicable = computed(() => ['FAIL', 'ERROR'].includes(activeResult.value?.status || ''))

watch(
  () => props.run?.results,
  (results) => {
    if (!results?.length) {
      selectedResultId.value = null
      return
    }
    if (!results.some((item) => item.id === selectedResultId.value)) {
      selectedResultId.value = results[0]!.id
    }
  },
  { immediate: true, deep: true },
)

watch(
  [() => props.modelValue, () => props.initialResultId],
  ([visible, resultId]) => {
    if (visible && resultId) selectedResultId.value = resultId
  },
  { immediate: true },
)

watch(
  [() => props.modelValue, () => activeResult.value?.id],
  async ([visible, resultId]) => {
    const generation = ++diagnosisRequestGeneration
    diagnosis.value = null
    if (!visible || !resultId || !props.run || !diagnosisApplicable.value) return
    loadingDiagnosis.value = true
    try {
      const [status, savedDiagnosis] = await Promise.all([
        getAiProviderStatus(),
        getAiFailureDiagnosis(props.run.projectId, props.run.id, resultId),
      ])
      if (generation !== diagnosisRequestGeneration) return
      aiStatus.value = status
      diagnosis.value = savedDiagnosis
    } catch (error) {
      if (generation === diagnosisRequestGeneration) ElMessage.error(getErrorMessage(error))
    } finally {
      if (generation === diagnosisRequestGeneration) loadingDiagnosis.value = false
    }
  },
  { immediate: true },
)

const statusText = computed(() => {
  const labels: Record<TestRunStatus, string> = {
    PENDING: '等待执行',
    RUNNING: '执行中',
    PASS: '通过',
    FAIL: '断言失败',
    ERROR: '执行错误',
    CANCELLED: '已取消',
  }
  return props.run ? labels[props.run.status] : '正在创建任务'
})

function statusTag(status?: TestRunStatus): 'success' | 'danger' | 'warning' | 'info' | 'primary' {
  if (status === 'PASS') return 'success'
  if (status === 'FAIL') return 'danger'
  if (status === 'ERROR' || status === 'CANCELLED') return 'warning'
  return 'primary'
}

function assertionLabel(assertion: AssertionResult) {
  const labels: Record<string, string> = {
    STATUS_CODE: 'HTTP 状态码',
    JSON_PATH_EXISTS: 'JSON 字段存在',
    JSON_PATH_EQUALS: 'JSON 字段相等',
    JSON_PATH_TYPE: 'JSON 数据类型',
    RESPONSE_TIME_LT: '响应时间小于',
    BODY_CONTAINS: '响应正文包含',
  }
  return labels[assertion.type] || assertion.type
}

function formatValue(value: unknown) {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

function prettyBody(value: string | null | undefined) {
  if (!value) return '（空）'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function severityTag(severity: AiDiagnosisSeverity): 'info' | 'warning' | 'danger' {
  if (severity === 'CRITICAL' || severity === 'HIGH') return 'danger'
  if (severity === 'MEDIUM') return 'warning'
  return 'info'
}

function severityLabel(severity: AiDiagnosisSeverity) {
  return { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重' }[severity]
}

async function generateDiagnosis() {
  if (!props.run || !activeResult.value || !diagnosisApplicable.value) return
  if (aiStatus.value && !aiStatus.value.configured) {
    ElMessage.warning('请先在后端 .env 配置硅基流动 API Key 和模型并重启服务')
    return
  }
  const resultId = activeResult.value.id
  generatingDiagnosis.value = true
  try {
    const generated = await analyzeAiFailure(props.run.projectId, props.run.id, resultId)
    if (activeResult.value?.id === resultId) diagnosis.value = generated
    ElMessage.success('AI 失败诊断已生成并保存')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    generatingDiagnosis.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    title="测试执行结果"
    width="min(1040px, 96vw)"
    top="3vh"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="run-result-heading">
      <div>
        <small>{{ isSuite ? 'TEST SUITE' : 'TEST CASE' }}</small>
        <strong>{{ testCaseName }}</strong>
        <span v-if="run">Run #{{ run.id }}</span>
      </div>
      <el-tag :type="statusTag(run?.status)" size="large" effect="light">{{ statusText }}</el-tag>
    </div>

    <div v-if="!run || !terminal" class="run-progress-card">
      <span class="run-spinner" />
      <div><strong>{{ statusText }}</strong><p>任务在线程池中运行，页面正在自动刷新结果。</p></div>
    </div>

    <template v-else>
      <el-alert
        v-if="run.errorMessage"
        :type="run.status === 'FAIL' ? 'warning' : 'error'"
        show-icon
        :closable="false"
        :title="run.errorMessage"
      />

      <div v-if="isSuite" class="suite-run-summary">
        <div><small>计划步骤</small><strong>{{ run.totalCount }}</strong></div>
        <div><small>已通过</small><strong class="suite-count--pass">{{ run.passedCount }}</strong></div>
        <div><small>断言失败</small><strong class="suite-count--fail">{{ run.failedCount }}</strong></div>
        <div><small>执行错误</small><strong class="suite-count--error">{{ run.errorCount }}</strong></div>
      </div>

      <div v-if="isSuite && run.results.length" class="suite-result-steps">
        <button
          v-for="item in run.results"
          :key="item.id"
          type="button"
          :class="['suite-result-step', { 'suite-result-step--active': activeResult?.id === item.id }]"
          @click="selectedResultId = item.id"
        >
          <span>{{ item.sequenceNumber }}</span>
          <div><strong>{{ item.testCaseName }}</strong><small>{{ item.requestMethod }} · {{ item.responseTimeMs ?? '—' }} ms</small></div>
          <el-tag :type="statusTag(item.status)" size="small">{{ item.status }}</el-tag>
        </button>
      </div>

      <template v-if="activeResult">
        <el-alert
          v-if="activeResult.errorMessage"
          type="error"
          show-icon
          :closable="false"
          :title="activeResult.errorMessage"
        />

        <div class="run-metrics">
          <div><small>HTTP 状态</small><strong>{{ activeResult.responseStatus ?? '—' }}</strong></div>
          <div><small>响应耗时</small><strong>{{ activeResult.responseTimeMs === null ? '—' : `${activeResult.responseTimeMs} ms` }}</strong></div>
          <div><small>断言</small><strong>{{ activeResult.assertions.filter((item) => item.passed).length }} / {{ activeResult.assertions.length }}</strong></div>
          <div><small>提取变量</small><strong>{{ activeResult.extractedVariables.length }}</strong></div>
        </div>

        <section v-if="diagnosisApplicable" class="ai-diagnosis-card">
          <header class="ai-diagnosis-card__header">
            <div><small>SPRING AI · SILICONFLOW</small><h3>失败诊断与修复建议</h3></div>
            <el-button
              type="primary"
              plain
              :loading="generatingDiagnosis"
              :disabled="loadingDiagnosis || aiStatus?.configured === false"
              @click="generateDiagnosis"
            >
              {{ diagnosis ? '重新分析' : '✦ AI 分析失败原因' }}
            </el-button>
          </header>

          <el-alert
            v-if="aiStatus && !aiStatus.configured"
            type="warning"
            :closable="false"
            show-icon
            title="AI 尚未配置：请在后端 .env 填写 SILICONFLOW_API_KEY 和 SILICONFLOW_MODEL 后重启服务"
          />
          <el-skeleton v-if="loadingDiagnosis" :rows="3" animated />
          <template v-else-if="diagnosis">
            <div class="ai-diagnosis-summary">
              <el-tag :type="severityTag(diagnosis.diagnosis.severity)" effect="dark">
                {{ severityLabel(diagnosis.diagnosis.severity) }}严重度
              </el-tag>
              <strong>{{ diagnosis.diagnosis.summary }}</strong>
            </div>
            <div class="ai-diagnosis-columns">
              <div>
                <h4>可能原因</h4>
                <ol><li v-for="item in diagnosis.diagnosis.possibleCauses" :key="item">{{ item }}</li></ol>
              </div>
              <div>
                <h4>建议检查位置</h4>
                <ol><li v-for="item in diagnosis.diagnosis.checkLocations" :key="item">{{ item }}</li></ol>
              </div>
              <div>
                <h4>修复建议</h4>
                <ol><li v-for="item in diagnosis.diagnosis.repairSuggestions" :key="item">{{ item }}</li></ol>
              </div>
            </div>
            <footer>
              <span>{{ diagnosis.provider }} · {{ diagnosis.model }}</span>
              <span>AI 建议仅供排查，测试结果仍由 Java 断言引擎判定。</span>
            </footer>
          </template>
          <el-empty v-else :image-size="64" description="尚未生成诊断；AI 只会分析失败或错误结果" />
        </section>

        <el-tabs class="run-detail-tabs">
          <el-tab-pane label="断言结果">
            <el-table :data="activeResult.assertions" empty-text="没有执行断言">
              <el-table-column label="结果" width="78">
                <template #default="{ row }: { row: AssertionResult }">
                  <el-tag :type="row.passed ? 'success' : 'danger'" size="small">{{ row.passed ? 'PASS' : 'FAIL' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="断言类型" min-width="145">
                <template #default="{ row }: { row: AssertionResult }">{{ assertionLabel(row) }}</template>
              </el-table-column>
              <el-table-column prop="expression" label="表达式" min-width="145" />
              <el-table-column label="期望值" min-width="130">
                <template #default="{ row }: { row: AssertionResult }"><code>{{ formatValue(row.expected) }}</code></template>
              </el-table-column>
              <el-table-column label="实际值" min-width="130">
                <template #default="{ row }: { row: AssertionResult }"><code>{{ formatValue(row.actual) }}</code></template>
              </el-table-column>
              <el-table-column prop="message" label="说明" min-width="180" />
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="请求">
            <div class="execution-meta"><strong>{{ activeResult.requestMethod }}</strong><code>{{ activeResult.requestUrl || '请求构造失败' }}</code></div>
            <div class="execution-json-grid">
              <div><span>请求头（敏感值已脱敏）</span><pre>{{ JSON.stringify(activeResult.requestHeaders, null, 2) }}</pre></div>
              <div><span>请求体</span><pre>{{ prettyBody(activeResult.requestBody) }}</pre></div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="响应">
            <div class="execution-json-grid">
              <div><span>响应头</span><pre>{{ JSON.stringify(activeResult.responseHeaders, null, 2) }}</pre></div>
              <div><span>响应体</span><pre>{{ prettyBody(activeResult.responseBody) }}</pre></div>
            </div>
          </el-tab-pane>
          <el-tab-pane :label="`变量 (${activeResult.extractedVariables.length})`">
            <div v-if="activeResult.extractedVariables.length" class="extracted-value-list">
              <div v-for="item in activeResult.extractedVariables" :key="item.name">
                <strong>{{ item.name }}</strong><code>{{ item.value }}</code><small>{{ item.sourceExpression }}</small>
              </div>
            </div>
            <el-empty v-else :image-size="70" description="本步骤没有提取变量" />
          </el-tab-pane>
        </el-tabs>
      </template>

      <el-empty v-else :image-size="80" description="任务没有生成步骤结果" />
    </template>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>
