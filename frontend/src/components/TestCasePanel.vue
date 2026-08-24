<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listApiDefinitions } from '../api/openapi'
import { listEnvironments } from '../api/environments'
import { generateAiTestCases, getAiProviderStatus } from '../api/ai'
import { createTestCase, deleteTestCase, listTestCases, updateTestCase } from '../api/testCases'
import { getTestRun, runTestCase as startTestCaseRun } from '../api/testRuns'
import { getErrorMessage } from '../api/http'
import TestRunResultDialog from './TestRunResultDialog.vue'
import type {
  ApiDefinition,
  AiProviderStatus,
  AiTestCaseGenerationResponse,
  AssertionType,
  Environment,
  ExtractionRule,
  TestAssertion,
  TestCase,
  TestCasePayload,
  TestCaseType,
  TestRun,
} from '../types/api'

interface EditorAssertion {
  type: AssertionType
  expression: string
  expectedText: string
}

interface EditorState {
  name: string
  description: string
  type: TestCaseType
  enabled: boolean
  headersText: string
  pathParametersText: string
  queryParametersText: string
  bodyText: string
  assertions: EditorAssertion[]
  extractionRules: ExtractionRule[]
}

const props = defineProps<{ projectId: number }>()
const apis = ref<ApiDefinition[]>([])
const testCases = ref<TestCase[]>([])
const environments = ref<Environment[]>([])
const selectedApiId = ref<number | null>(null)
const selectedEnvironmentId = ref<number | null>(null)
const loadingApis = ref(true)
const loadingCases = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const editingVersion = ref(1)
const editorTab = ref('basic')
const keyword = ref('')
const runDialogVisible = ref(false)
const currentRun = ref<TestRun | null>(null)
const currentRunName = ref('')
const activeRunCaseId = ref<number | null>(null)
const aiStatus = ref<AiProviderStatus | null>(null)
const loadingAiStatus = ref(false)
const aiDialogVisible = ref(false)
const generatingAi = ref(false)
const importingAi = ref(false)
const aiCount = ref(6)
const aiFocus = ref('')
const aiGeneration = ref<AiTestCaseGenerationResponse | null>(null)
const selectedCandidateIndexes = ref<number[]>([])
let pollGeneration = 0

const typeOptions: Array<{ value: TestCaseType; label: string }> = [
  { value: 'NORMAL', label: '正常场景' },
  { value: 'BOUNDARY', label: '边界场景' },
  { value: 'NEGATIVE', label: '异常场景' },
  { value: 'MISSING_PARAMETER', label: '缺少参数' },
  { value: 'INVALID_TYPE', label: '类型错误' },
  { value: 'AUTHENTICATION', label: '认证场景' },
]

const assertionOptions: Array<{ value: AssertionType; label: string }> = [
  { value: 'STATUS_CODE', label: 'HTTP 状态码' },
  { value: 'JSON_PATH_EXISTS', label: 'JSON 字段存在' },
  { value: 'JSON_PATH_EQUALS', label: 'JSON 字段相等' },
  { value: 'JSON_PATH_TYPE', label: 'JSON 数据类型' },
  { value: 'RESPONSE_TIME_LT', label: '响应时间小于' },
  { value: 'BODY_CONTAINS', label: '响应正文包含' },
]

function emptyForm(): EditorState {
  return {
    name: '',
    description: '',
    type: 'NORMAL',
    enabled: true,
    headersText: '{\n  "Content-Type": "application/json"\n}',
    pathParametersText: '{}',
    queryParametersText: '{}',
    bodyText: '',
    assertions: [{ type: 'STATUS_CODE', expression: '', expectedText: '200' }],
    extractionRules: [],
  }
}

const form = reactive<EditorState>(emptyForm())
const selectedApi = computed(() => apis.value.find((api) => api.id === selectedApiId.value) || null)
const filteredCases = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  if (!query) return testCases.value
  return testCases.value.filter((item) =>
    [item.name, item.description, typeLabel(item.type)].filter(Boolean)
      .some((value) => value!.toLowerCase().includes(query)),
  )
})
const selectedCandidateCount = computed(() => selectedCandidateIndexes.value.length)

async function loadApis() {
  loadingApis.value = true
  try {
    apis.value = await listApiDefinitions(props.projectId)
    if (!selectedApiId.value && apis.value.length > 0) {
      selectedApiId.value = apis.value[0]!.id
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loadingApis.value = false
  }
}

async function loadCases() {
  if (!selectedApiId.value) {
    testCases.value = []
    return
  }
  loadingCases.value = true
  try {
    testCases.value = await listTestCases(props.projectId, selectedApiId.value)
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loadingCases.value = false
  }
}

async function loadEnvironments() {
  try {
    environments.value = await listEnvironments(props.projectId)
    if (selectedEnvironmentId.value === null && environments.value.length > 0) {
      selectedEnvironmentId.value = environments.value[0]!.id
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  }
}

async function loadAiStatus(showError = false) {
  loadingAiStatus.value = true
  try {
    aiStatus.value = await getAiProviderStatus()
  } catch (error) {
    aiStatus.value = null
    if (showError) ElMessage.error(getErrorMessage(error))
  } finally {
    loadingAiStatus.value = false
  }
}

async function openAiGenerator() {
  if (!selectedApiId.value) {
    ElMessage.warning('请先导入并选择一个 API')
    return
  }
  if (!aiStatus.value) await loadAiStatus(true)
  if (!aiStatus.value?.configured) {
    ElMessage.warning('请先在后端 .env 中配置 SILICONFLOW_API_KEY 和 SILICONFLOW_MODEL，然后重启后端')
    return
  }
  aiCount.value = 6
  aiFocus.value = ''
  aiGeneration.value = null
  selectedCandidateIndexes.value = []
  aiDialogVisible.value = true
}

async function generateCandidates() {
  if (!selectedApiId.value || generatingAi.value) return
  generatingAi.value = true
  try {
    aiGeneration.value = await generateAiTestCases(
      props.projectId,
      selectedApiId.value,
      aiCount.value,
      aiFocus.value,
    )
    selectedCandidateIndexes.value = aiGeneration.value.candidates.map((_, index) => index)
    ElMessage.success(`已生成 ${aiGeneration.value.candidates.length} 条候选用例，请确认后再导入`)
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    generatingAi.value = false
  }
}

async function importCandidates() {
  if (!selectedApiId.value || !aiGeneration.value || selectedCandidateIndexes.value.length === 0) return
  importingAi.value = true
  let imported = 0
  const failures: string[] = []
  const indexes = [...selectedCandidateIndexes.value].sort((left, right) => left - right)
  for (const index of indexes) {
    const candidate = aiGeneration.value.candidates[index]
    if (!candidate) continue
    try {
      await createTestCase(props.projectId, selectedApiId.value, { ...candidate, enabled: true })
      imported += 1
    } catch (error) {
      failures.push(`${candidate.name}：${getErrorMessage(error)}`)
    }
  }
  await loadCases()
  importingAi.value = false
  if (imported > 0) ElMessage.success(`已导入 ${imported} 条 AI 候选用例`)
  if (failures.length > 0) {
    ElMessage.error(`有 ${failures.length} 条导入失败：${failures.join('；')}`)
  } else {
    aiDialogVisible.value = false
  }
}

async function executeCase(testCase: TestCase) {
  if (!selectedApiId.value || activeRunCaseId.value !== null) return
  activeRunCaseId.value = testCase.id
  currentRun.value = null
  currentRunName.value = testCase.name
  runDialogVisible.value = true
  const generation = ++pollGeneration
  try {
    currentRun.value = await startTestCaseRun(
      props.projectId,
      selectedApiId.value,
      testCase.id,
      selectedEnvironmentId.value,
    )
    const terminal = new Set(['PASS', 'FAIL', 'ERROR', 'CANCELLED'])
    for (let attempt = 0; attempt < 80 && generation === pollGeneration; attempt += 1) {
      if (terminal.has(currentRun.value.status)) break
      await new Promise((resolve) => window.setTimeout(resolve, 250))
      currentRun.value = await getTestRun(props.projectId, currentRun.value.id)
    }
    if (currentRun.value && !terminal.has(currentRun.value.status)) {
      ElMessage.warning('任务仍在后台运行，请稍后重新查看')
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
    runDialogVisible.value = false
  } finally {
    activeRunCaseId.value = null
  }
}

function openCreate() {
  if (!selectedApiId.value) {
    ElMessage.warning('请先导入并选择一个 API')
    return
  }
  editingId.value = null
  editingVersion.value = 1
  editorTab.value = 'basic'
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(testCase: TestCase) {
  editingId.value = testCase.id
  editingVersion.value = testCase.version
  editorTab.value = 'basic'
  Object.assign(form, {
    name: testCase.name,
    description: testCase.description || '',
    type: testCase.type,
    enabled: testCase.enabled,
    headersText: JSON.stringify(testCase.requestHeaders || {}, null, 2),
    pathParametersText: JSON.stringify(testCase.pathParameters || {}, null, 2),
    queryParametersText: JSON.stringify(testCase.queryParameters || {}, null, 2),
    bodyText: testCase.requestBody === null ? '' : JSON.stringify(testCase.requestBody, null, 2),
    assertions: testCase.assertions.map((assertion) => ({
      type: assertion.type,
      expression: assertion.expression || '',
      expectedText: expectedToText(assertion),
    })),
    extractionRules: testCase.extractionRules.map((rule) => ({ ...rule })),
  })
  dialogVisible.value = true
}

function addAssertion() {
  form.assertions.push({ type: 'STATUS_CODE', expression: '', expectedText: '200' })
}

function removeAssertion(index: number) {
  if (form.assertions.length === 1) {
    ElMessage.warning('至少需要一条断言')
    return
  }
  form.assertions.splice(index, 1)
}

function resetAssertion(assertion: EditorAssertion) {
  assertion.expression = assertion.type.startsWith('JSON_PATH_') ? '$.' : ''
  const defaults: Record<AssertionType, string> = {
    STATUS_CODE: '200',
    JSON_PATH_EXISTS: '',
    JSON_PATH_EQUALS: '"expected value"',
    JSON_PATH_TYPE: 'STRING',
    RESPONSE_TIME_LT: '1000',
    BODY_CONTAINS: 'success',
  }
  assertion.expectedText = defaults[assertion.type]
}

function addExtraction() {
  form.extractionRules.push({ name: '', jsonPath: '$.' })
}

function parseStringMap(value: string, fieldName: string): Record<string, string> {
  if (!value.trim()) return {}
  const parsed: unknown = JSON.parse(value)
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${fieldName}必须是 JSON 对象`)
  }
  const entries = Object.entries(parsed)
  if (entries.some(([, item]) => typeof item !== 'string')) {
    throw new Error(`${fieldName}中的值必须全部是字符串`)
  }
  return Object.fromEntries(entries) as Record<string, string>
}

function parseAssertion(assertion: EditorAssertion): TestAssertion {
  let expected: unknown = null
  if (assertion.type === 'STATUS_CODE' || assertion.type === 'RESPONSE_TIME_LT') {
    expected = Number(assertion.expectedText)
  } else if (assertion.type === 'JSON_PATH_EQUALS') {
    try {
      expected = JSON.parse(assertion.expectedText)
    } catch {
      expected = assertion.expectedText
    }
  } else if (assertion.type === 'JSON_PATH_TYPE') {
    expected = assertion.expectedText.trim().toUpperCase()
  } else if (assertion.type === 'BODY_CONTAINS') {
    expected = assertion.expectedText
  }
  return {
    type: assertion.type,
    expression: assertion.expression.trim() || null,
    expected,
  }
}

function buildPayload(): TestCasePayload {
  if (!form.name.trim()) throw new Error('请输入用例名称')
  if (form.assertions.length === 0) throw new Error('至少需要一条断言')
  let requestBody: unknown = null
  if (form.bodyText.trim()) requestBody = JSON.parse(form.bodyText)
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    type: form.type,
    enabled: form.enabled,
    requestHeaders: parseStringMap(form.headersText, '请求头'),
    pathParameters: parseStringMap(form.pathParametersText, '路径参数'),
    queryParameters: parseStringMap(form.queryParametersText, '查询参数'),
    requestBody,
    assertions: form.assertions.map(parseAssertion),
    extractionRules: form.extractionRules.map((rule) => ({
      name: rule.name.trim(),
      jsonPath: rule.jsonPath.trim(),
    })),
  }
}

async function save() {
  if (!selectedApiId.value) return
  let payload: TestCasePayload
  try {
    payload = buildPayload()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用例配置格式不正确')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateTestCase(props.projectId, selectedApiId.value, editingId.value, {
        ...payload,
        version: editingVersion.value,
      })
      ElMessage.success('测试用例已更新')
    } else {
      await createTestCase(props.projectId, selectedApiId.value, payload)
      ElMessage.success('测试用例已创建')
    }
    dialogVisible.value = false
    await loadCases()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
    if (editingId.value) await loadCases()
  } finally {
    saving.value = false
  }
}

async function remove(testCase: TestCase) {
  if (!selectedApiId.value) return
  try {
    await ElMessageBox.confirm(`确认删除测试用例“${testCase.name}”吗？`, '删除测试用例', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteTestCase(props.projectId, selectedApiId.value, testCase.id)
    testCases.value = testCases.value.filter((item) => item.id !== testCase.id)
    ElMessage.success('测试用例已删除')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  }
}

function expectedToText(assertion: TestAssertion) {
  if (assertion.expected === null || assertion.expected === undefined) return ''
  if (assertion.type === 'JSON_PATH_EQUALS') return JSON.stringify(assertion.expected) ?? ''
  return String(assertion.expected)
}

function typeLabel(type: TestCaseType) {
  return typeOptions.find((item) => item.value === type)?.label || type
}

function typeTag(type: TestCaseType): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (type === 'NORMAL') return 'success'
  if (type === 'NEGATIVE' || type === 'AUTHENTICATION') return 'danger'
  if (type === 'BOUNDARY') return 'warning'
  return 'info'
}

function needsExpression(type: AssertionType) {
  return type.startsWith('JSON_PATH_')
}

function needsExpected(type: AssertionType) {
  return type !== 'JSON_PATH_EXISTS'
}

function expectedPlaceholder(type: AssertionType) {
  if (type === 'STATUS_CODE') return '例如 200'
  if (type === 'RESPONSE_TIME_LT') return '毫秒，例如 1000'
  if (type === 'JSON_PATH_EQUALS') return 'JSON 值，例如 "success"、1 或 true'
  if (type === 'JSON_PATH_TYPE') return 'STRING / INTEGER / OBJECT 等'
  return '要包含的文本'
}

watch(selectedApiId, () => {
  aiGeneration.value = null
  selectedCandidateIndexes.value = []
  return loadCases()
})
onMounted(() => Promise.all([loadApis(), loadEnvironments(), loadAiStatus()]))
onBeforeUnmount(() => { pollGeneration += 1 })
</script>

<template>
  <section class="workspace-panel test-case-panel">
    <div class="panel-heading">
      <div>
        <h2>测试用例</h2>
        <p>为每个 API 持久化请求数据、断言与响应变量提取规则。</p>
      </div>
      <div class="panel-heading-actions">
        <el-button
          type="primary"
          plain
          :loading="loadingAiStatus"
          :disabled="!selectedApiId"
          @click="openAiGenerator"
        >✦ AI 生成用例</el-button>
        <el-button type="primary" :disabled="!selectedApiId" @click="openCreate">＋ 新建测试用例</el-button>
      </div>
    </div>

    <div v-loading="loadingApis" class="api-selector-card">
      <div>
        <span class="selector-label">当前 API</span>
        <el-select v-model="selectedApiId" filterable placeholder="请选择 API" style="width: min(620px, 100%)">
          <el-option v-for="api in apis" :key="api.id" :value="api.id" :label="`${api.method.toUpperCase()}  ${api.path}  ${api.summary || ''}`">
            <span class="api-option-method">{{ api.method.toUpperCase() }}</span>
            <code>{{ api.path }}</code>
            <small>{{ api.summary }}</small>
          </el-option>
        </el-select>
      </div>
      <div v-if="selectedApi" class="selected-api-meta">
        <el-tag :type="selectedApi.method.toUpperCase() === 'GET' ? 'success' : 'primary'">{{ selectedApi.method.toUpperCase() }}</el-tag>
        <code>{{ selectedApi.path }}</code>
      </div>
    </div>

    <el-empty v-if="!loadingApis && apis.length === 0" description="请先在“接口定义”中导入 OpenAPI 文档" />

    <template v-else>
      <div class="execution-toolbar">
        <div>
          <span class="selector-label">执行环境</span>
          <el-select v-model="selectedEnvironmentId" clearable placeholder="使用项目默认 Base URL">
            <el-option v-for="environment in environments" :key="environment.id" :label="environment.name" :value="environment.id">
              <span>{{ environment.name }}</span><small>{{ environment.baseUrl }}</small>
            </el-option>
          </el-select>
        </div>
        <p>运行任务由后端线程池异步执行；未选择环境时使用项目默认 Base URL。</p>
      </div>

      <div class="case-toolbar">
        <el-input v-model="keyword" placeholder="搜索用例名称、描述或类型" clearable>
          <template #prefix>⌕</template>
        </el-input>
        <span>{{ filteredCases.length }} 个用例</span>
      </div>

      <div class="table-card">
        <el-table v-loading="loadingCases" :data="filteredCases" empty-text="该 API 还没有测试用例">
          <el-table-column label="状态" width="80">
            <template #default="{ row }: { row: TestCase }">
              <span class="case-status" :class="{ 'case-status--disabled': !row.enabled }">{{ row.enabled ? '启用' : '停用' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="用例名称" min-width="220">
            <template #default="{ row }: { row: TestCase }">
              <div class="case-name-cell"><strong>{{ row.name }}</strong><small>{{ row.description || '暂无描述' }}</small></div>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="125">
            <template #default="{ row }: { row: TestCase }"><el-tag :type="typeTag(row.type)" effect="light">{{ typeLabel(row.type) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="断言" width="90">
            <template #default="{ row }: { row: TestCase }">{{ row.assertions.length }} 条</template>
          </el-table-column>
          <el-table-column label="变量提取" width="105">
            <template #default="{ row }: { row: TestCase }">{{ row.extractionRules.length }} 条</template>
          </el-table-column>
          <el-table-column label="版本" width="75">
            <template #default="{ row }: { row: TestCase }">v{{ row.version }}</template>
          </el-table-column>
          <el-table-column label="操作" width="205" fixed="right">
            <template #default="{ row }: { row: TestCase }">
              <el-button
                link
                type="success"
                :loading="activeRunCaseId === row.id"
                :disabled="!row.enabled || (activeRunCaseId !== null && activeRunCaseId !== row.id)"
                @click="executeCase(row)"
              >运行</el-button>
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑测试用例' : '新建测试用例'" width="min(920px, 96vw)" top="4vh" destroy-on-close>
      <div v-if="selectedApi" class="dialog-api-context">
        <span>{{ selectedApi.method.toUpperCase() }}</span><code>{{ selectedApi.path }}</code><small>{{ selectedApi.summary }}</small>
      </div>
      <el-tabs v-model="editorTab" class="case-editor-tabs">
        <el-tab-pane label="基本信息" name="basic">
          <el-form label-position="top">
            <div class="form-row form-row--three">
              <el-form-item label="用例名称" required>
                <el-input v-model="form.name" maxlength="200" placeholder="例如：正常登录" />
              </el-form-item>
              <el-form-item label="测试类型" required>
                <el-select v-model="form.type" style="width: 100%">
                  <el-option v-for="option in typeOptions" :key="option.value" :label="option.label" :value="option.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="是否启用">
                <el-switch v-model="form.enabled" inline-prompt active-text="启用" inactive-text="停用" />
              </el-form-item>
            </div>
            <el-form-item label="用例描述">
              <el-input v-model="form.description" type="textarea" :rows="3" maxlength="4000" placeholder="说明场景、前置条件和预期行为" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="请求配置" name="request">
          <div class="request-config-grid">
            <label>请求头（JSON 对象）<el-input v-model="form.headersText" type="textarea" :rows="7" class="code-textarea" spellcheck="false" /></label>
            <label>查询参数（JSON 对象）<el-input v-model="form.queryParametersText" type="textarea" :rows="7" class="code-textarea" spellcheck="false" /></label>
            <label>路径参数（JSON 对象）<el-input v-model="form.pathParametersText" type="textarea" :rows="7" class="code-textarea" spellcheck="false" /></label>
            <label>请求体（JSON，可留空）<el-input v-model="form.bodyText" type="textarea" :rows="7" class="code-textarea" spellcheck="false" placeholder="{&#10;  &quot;username&quot;: &quot;admin&quot;&#10;}" /></label>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`断言 (${form.assertions.length})`" name="assertions">
          <div class="rule-heading"><p>断言由后续执行器以程序代码执行，不交给 AI 判断。</p><el-button @click="addAssertion">＋ 添加断言</el-button></div>
          <div class="rule-list">
            <div v-for="(assertion, index) in form.assertions" :key="index" class="rule-row assertion-row">
              <span class="rule-index">{{ index + 1 }}</span>
              <el-select v-model="assertion.type" @change="resetAssertion(assertion)">
                <el-option v-for="option in assertionOptions" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <el-input v-if="needsExpression(assertion.type)" v-model="assertion.expression" placeholder="JSONPath，例如 $.data.token" />
              <span v-else class="not-required">无需表达式</span>
              <el-input v-if="needsExpected(assertion.type)" v-model="assertion.expectedText" :placeholder="expectedPlaceholder(assertion.type)" />
              <span v-else class="not-required">无需期望值</span>
              <button class="remove-rule" type="button" aria-label="删除断言" @click="removeAssertion(index)">×</button>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane :label="`变量提取 (${form.extractionRules.length})`" name="extractions">
          <div class="rule-heading"><p>从响应 JSON 中提取变量，后续可通过双花括号语法引用。</p><el-button @click="addExtraction">＋ 添加提取规则</el-button></div>
          <div v-if="form.extractionRules.length" class="rule-list">
            <div v-for="(rule, index) in form.extractionRules" :key="index" class="rule-row extraction-row">
              <span class="rule-index">{{ index + 1 }}</span>
              <el-input v-model="rule.name" placeholder="变量名，例如 token" />
              <el-input v-model="rule.jsonPath" placeholder="JSONPath，例如 $.data.token" />
              <button class="remove-rule" type="button" aria-label="删除提取规则" @click="form.extractionRules.splice(index, 1)">×</button>
            </div>
          </div>
          <el-empty v-else :image-size="72" description="暂无提取规则；不需要接口依赖时可保持为空" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <span v-if="editingId" class="version-hint">当前版本 v{{ editingVersion }}</span>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editingId ? '保存修改' : '创建用例' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="aiDialogVisible" title="AI 生成候选测试用例" width="min(960px, 96vw)" top="4vh" destroy-on-close>
      <div v-if="selectedApi" class="dialog-api-context">
        <span>{{ selectedApi.method.toUpperCase() }}</span><code>{{ selectedApi.path }}</code><small>{{ selectedApi.summary }}</small>
      </div>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="`模型供应商：${aiStatus?.provider || 'SiliconFlow'} · 模型：${aiStatus?.model || '未配置'}`"
        description="AI 只设计候选请求和断言，不会执行接口，也不会判断 PASS/FAIL。只有导入后才会保存。"
      />

      <div class="ai-generation-form">
        <label>
          <span>生成数量</span>
          <el-input-number v-model="aiCount" :min="1" :max="12" controls-position="right" />
        </label>
        <label class="ai-focus-field">
          <span>重点要求（可选）</span>
          <el-input
            v-model="aiFocus"
            maxlength="1000"
            show-word-limit
            placeholder="例如：重点覆盖分页边界、缺少 token 和非法枚举值"
          />
        </label>
        <el-button type="primary" :loading="generatingAi" @click="generateCandidates">
          {{ aiGeneration ? '重新生成' : '开始生成' }}
        </el-button>
      </div>

      <template v-if="aiGeneration">
        <el-alert
          v-for="warning in aiGeneration.warnings"
          :key="warning"
          class="ai-generation-warning"
          type="warning"
          :closable="false"
          :title="warning"
        />
        <div class="ai-candidate-heading">
          <div><strong>候选用例</strong><span>已选择 {{ selectedCandidateCount }} / {{ aiGeneration.candidates.length }} 条</span></div>
          <el-button link type="primary" @click="selectedCandidateIndexes = aiGeneration.candidates.map((_, index) => index)">全选</el-button>
        </div>
        <el-checkbox-group v-model="selectedCandidateIndexes" class="ai-candidate-list">
          <label v-for="(candidate, index) in aiGeneration.candidates" :key="`${candidate.name}-${index}`" class="ai-candidate-card">
            <el-checkbox :value="index" />
            <div class="ai-candidate-copy">
              <div>
                <strong>{{ candidate.name }}</strong>
                <el-tag :type="typeTag(candidate.type)" size="small">{{ typeLabel(candidate.type) }}</el-tag>
              </div>
              <p>{{ candidate.description || '暂无描述' }}</p>
              <small>
                请求头 {{ Object.keys(candidate.requestHeaders || {}).length }} ·
                查询参数 {{ Object.keys(candidate.queryParameters || {}).length }} ·
                路径参数 {{ Object.keys(candidate.pathParameters || {}).length }} ·
                断言 {{ candidate.assertions.length }}
              </small>
              <div class="ai-assertion-tags">
                <el-tag v-for="(assertion, assertionIndex) in candidate.assertions" :key="assertionIndex" size="small" effect="plain">
                  {{ assertionOptions.find((item) => item.value === assertion.type)?.label || assertion.type }}
                </el-tag>
              </div>
            </div>
          </label>
        </el-checkbox-group>
      </template>
      <el-empty v-else :image-size="76" description="设置生成数量和重点要求后，由硅基流动生成结构化候选用例" />

      <template #footer>
        <span class="ai-import-hint">导入后可在现有编辑器中继续修改，再由 Java 执行器运行。</span>
        <el-button @click="aiDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="importingAi"
          :disabled="selectedCandidateCount === 0"
          @click="importCandidates"
        >导入选中的 {{ selectedCandidateCount }} 条</el-button>
      </template>
    </el-dialog>

    <TestRunResultDialog
      v-model="runDialogVisible"
      :run="currentRun"
      :test-case-name="currentRunName"
    />
  </section>
</template>
