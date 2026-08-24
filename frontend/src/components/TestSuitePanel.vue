<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listEnvironments } from '../api/environments'
import {
  createTestSuite,
  deleteTestSuite,
  listTestSuiteCandidates,
  listTestSuites,
  updateTestSuite,
} from '../api/testSuites'
import { getTestRun, runTestSuite as startTestSuiteRun } from '../api/testRuns'
import { getErrorMessage } from '../api/http'
import TestRunResultDialog from './TestRunResultDialog.vue'
import type {
  Environment,
  TestRun,
  TestSuite,
  TestSuiteCaseCandidate,
  TestSuitePayload,
} from '../types/api'

interface EditorCase extends TestSuiteCaseCandidate {
  stepEnabled: boolean
}

const props = defineProps<{ projectId: number }>()
const suites = ref<TestSuite[]>([])
const candidates = ref<TestSuiteCaseCandidate[]>([])
const environments = ref<Environment[]>([])
const selectedEnvironmentId = ref<number | null>(null)
const loading = ref(true)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const editingVersion = ref(1)
const candidateToAdd = ref<number | null>(null)
const editorCases = ref<EditorCase[]>([])
const activeRunSuiteId = ref<number | null>(null)
const runDialogVisible = ref(false)
const currentRun = ref<TestRun | null>(null)
const currentRunName = ref('')
let pollGeneration = 0

const form = reactive({
  name: '',
  description: '',
  stopOnFailure: true,
})

const availableCandidates = computed(() => {
  const selected = new Set(editorCases.value.map((item) => item.testCaseId))
  return candidates.value.filter((item) => item.enabled && !selected.has(item.testCaseId))
})

function methodTag(method: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (method === 'GET') return 'success'
  if (method === 'POST') return 'primary'
  if (method === 'PUT' || method === 'PATCH') return 'warning'
  if (method === 'DELETE') return 'danger'
  return 'info'
}

async function loadAll() {
  loading.value = true
  try {
    const [suiteData, candidateData, environmentData] = await Promise.all([
      listTestSuites(props.projectId),
      listTestSuiteCandidates(props.projectId),
      listEnvironments(props.projectId),
    ])
    suites.value = suiteData
    candidates.value = candidateData
    environments.value = environmentData
    if (selectedEnvironmentId.value === null && environmentData.length > 0) {
      selectedEnvironmentId.value = environmentData[0]!.id
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function resetEditor() {
  form.name = ''
  form.description = ''
  form.stopOnFailure = true
  editorCases.value = []
  candidateToAdd.value = null
}

function openCreate() {
  editingId.value = null
  editingVersion.value = 1
  resetEditor()
  dialogVisible.value = true
}

function openEdit(suite: TestSuite) {
  editingId.value = suite.id
  editingVersion.value = suite.version
  form.name = suite.name
  form.description = suite.description || ''
  form.stopOnFailure = suite.stopOnFailure
  editorCases.value = suite.cases.map((item) => ({
    testCaseId: item.testCaseId,
    testCaseName: item.testCaseName,
    enabled: item.testCaseEnabled,
    apiId: item.apiId,
    method: item.method,
    path: item.path,
    stepEnabled: item.enabled,
  }))
  candidateToAdd.value = null
  dialogVisible.value = true
}

function addCase() {
  const candidate = candidates.value.find((item) => item.testCaseId === candidateToAdd.value)
  if (!candidate) return
  editorCases.value.push({ ...candidate, stepEnabled: true })
  candidateToAdd.value = null
}

function moveCase(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= editorCases.value.length) return
  const [item] = editorCases.value.splice(index, 1)
  editorCases.value.splice(target, 0, item!)
}

function removeCase(index: number) {
  editorCases.value.splice(index, 1)
}

function buildPayload(): TestSuitePayload {
  if (!form.name.trim()) throw new Error('请输入套件名称')
  if (editorCases.value.length === 0) throw new Error('至少添加一个测试用例')
  return {
    name: form.name.trim(),
    description: form.description.trim(),
    stopOnFailure: form.stopOnFailure,
    cases: editorCases.value.map((item) => ({
      testCaseId: item.testCaseId,
      enabled: item.stepEnabled,
    })),
  }
}

async function save() {
  let payload: TestSuitePayload
  try {
    payload = buildPayload()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '测试套件配置不正确')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateTestSuite(props.projectId, editingId.value, {
        ...payload,
        version: editingVersion.value,
      })
      ElMessage.success('测试套件已更新')
    } else {
      await createTestSuite(props.projectId, payload)
      ElMessage.success('测试套件已创建')
    }
    dialogVisible.value = false
    await loadAll()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
    if (editingId.value) await loadAll()
  } finally {
    saving.value = false
  }
}

async function remove(suite: TestSuite) {
  try {
    await ElMessageBox.confirm(`确认删除测试套件“${suite.name}”吗？`, '删除测试套件', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteTestSuite(props.projectId, suite.id)
    suites.value = suites.value.filter((item) => item.id !== suite.id)
    ElMessage.success('测试套件已删除')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  }
}

async function executeSuite(suite: TestSuite) {
  if (activeRunSuiteId.value !== null) return
  activeRunSuiteId.value = suite.id
  currentRun.value = null
  currentRunName.value = suite.name
  runDialogVisible.value = true
  const generation = ++pollGeneration
  try {
    currentRun.value = await startTestSuiteRun(
      props.projectId,
      suite.id,
      selectedEnvironmentId.value,
    )
    const terminal = new Set(['PASS', 'FAIL', 'ERROR', 'CANCELLED'])
    for (let attempt = 0; attempt < 160 && generation === pollGeneration; attempt += 1) {
      if (terminal.has(currentRun.value.status)) break
      await new Promise((resolve) => window.setTimeout(resolve, 250))
      currentRun.value = await getTestRun(props.projectId, currentRun.value.id)
    }
    if (currentRun.value && !terminal.has(currentRun.value.status)) {
      ElMessage.warning('套件仍在后台运行，请稍后重新查看')
    }
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
    runDialogVisible.value = false
  } finally {
    activeRunSuiteId.value = null
  }
}

watch(() => props.projectId, loadAll)
onMounted(loadAll)
onBeforeUnmount(() => { pollGeneration += 1 })
</script>

<template>
  <section class="workspace-panel" v-loading="loading">
    <div class="panel-heading">
      <div><h2>测试套件</h2><p>把测试用例组合成有顺序的业务流程，前序提取变量可供后续步骤引用。</p></div>
      <el-button type="primary" @click="openCreate">+ 新建测试套件</el-button>
    </div>

    <div class="execution-toolbar suite-execution-toolbar">
      <div>
        <span class="selector-label">执行环境</span>
        <el-select v-model="selectedEnvironmentId" clearable placeholder="使用项目默认 Base URL">
          <el-option v-for="item in environments" :key="item.id" :label="item.name" :value="item.id">
            <span>{{ item.name }}</span><small>{{ item.baseUrl }}</small>
          </el-option>
        </el-select>
      </div>
      <p>套件将在独立线程池中按顺序执行；选择“失败即停止”后，首个 FAIL / ERROR 会终止后续步骤。</p>
    </div>

    <div v-if="suites.length" class="table-card">
      <el-table :data="suites">
        <el-table-column label="套件名称" min-width="250">
          <template #default="{ row }: { row: TestSuite }">
            <div class="case-name-cell"><strong>{{ row.name }}</strong><small>{{ row.description || '暂无描述' }}</small></div>
          </template>
        </el-table-column>
        <el-table-column label="流程步骤" min-width="290">
          <template #default="{ row }: { row: TestSuite }">
            <div class="suite-flow-preview">
              <template v-for="(item, index) in row.cases.slice(0, 4)" :key="item.testCaseId">
                <span :class="{ 'suite-step--disabled': !item.enabled || !item.testCaseEnabled }">{{ item.sortOrder }}. {{ item.testCaseName }}</span>
                <b v-if="index < Math.min(row.cases.length, 4) - 1">→</b>
              </template>
              <small v-if="row.cases.length > 4">+{{ row.cases.length - 4 }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="失败策略" width="120">
          <template #default="{ row }: { row: TestSuite }">
            <el-tag :type="row.stopOnFailure ? 'warning' : 'info'" size="small">
              {{ row.stopOnFailure ? '失败即停止' : '继续执行' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="70">
          <template #default="{ row }: { row: TestSuite }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column label="操作" width="205" fixed="right">
          <template #default="{ row }: { row: TestSuite }">
            <el-button link type="success" :loading="activeRunSuiteId === row.id" :disabled="activeRunSuiteId !== null" @click="executeSuite(row)">运行</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty v-else :image-size="92" description="尚未创建测试套件">
      <el-button type="primary" @click="openCreate">创建第一个业务流程</el-button>
    </el-empty>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑测试套件' : '新建测试套件'" width="min(920px, 96vw)" destroy-on-close>
      <div class="form-row form-row--three">
        <el-form-item label="套件名称" required>
          <el-input v-model="form.name" maxlength="160" placeholder="例如：用户完整购买流程" />
        </el-form-item>
        <el-form-item label="失败策略">
          <el-switch v-model="form.stopOnFailure" active-text="失败即停止" inactive-text="继续执行" />
        </el-form-item>
        <el-form-item label="当前版本"><el-input :model-value="`v${editingVersion}`" disabled /></el-form-item>
      </div>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="4000" show-word-limit />
      </el-form-item>

      <div class="suite-case-picker">
        <div>
          <span class="selector-label">添加测试用例</span>
          <el-select v-model="candidateToAdd" filterable placeholder="按名称、方法或路径选择">
            <el-option v-for="item in availableCandidates" :key="item.testCaseId" :label="`${item.method} ${item.path} · ${item.testCaseName}`" :value="item.testCaseId">
              <el-tag :type="methodTag(item.method)" size="small">{{ item.method }}</el-tag>
              <code>{{ item.path }}</code><small>{{ item.testCaseName }}</small>
            </el-option>
          </el-select>
        </div>
        <el-button type="primary" plain :disabled="candidateToAdd === null" @click="addCase">添加到流程</el-button>
      </div>

      <div v-if="editorCases.length" class="suite-editor-list">
        <div v-for="(item, index) in editorCases" :key="item.testCaseId" class="suite-editor-step">
          <span class="suite-step-number">{{ index + 1 }}</span>
          <div class="suite-editor-step__copy">
            <strong>{{ item.testCaseName }}</strong>
            <small><b>{{ item.method }}</b> {{ item.path }}</small>
          </div>
          <el-switch v-model="item.stepEnabled" size="small" active-text="启用" />
          <div class="suite-order-actions">
            <el-button text :disabled="index === 0" @click="moveCase(index, -1)">↑</el-button>
            <el-button text :disabled="index === editorCases.length - 1" @click="moveCase(index, 1)">↓</el-button>
            <el-button text type="danger" @click="removeCase(index)">移除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else :image-size="65" description="请按业务顺序添加测试用例" />

      <template #footer>
        <span v-if="editingId" class="version-hint">使用 v{{ editingVersion }} 乐观锁更新</span>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存套件</el-button>
      </template>
    </el-dialog>

    <TestRunResultDialog v-model="runDialogVisible" :run="currentRun" :test-case-name="currentRunName" />
  </section>
</template>
