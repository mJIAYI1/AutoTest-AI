<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createEnvironment,
  deleteEnvironment,
  listEnvironments,
  updateEnvironment,
} from '../api/environments'
import { getErrorMessage } from '../api/http'
import type { Environment, EnvironmentPayload } from '../types/api'

const props = defineProps<{ projectId: number }>()
const environments = ref<Environment[]>([])
const loading = ref(true)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({
  name: '',
  baseUrl: '',
  headersText: '{}',
  variablesText: '{}',
})

async function load() {
  loading.value = true
  try {
    environments.value = await listEnvironments(props.projectId)
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', baseUrl: '', headersText: '{}', variablesText: '{}' })
  dialogVisible.value = true
}

function openEdit(environment: Environment) {
  editingId.value = environment.id
  Object.assign(form, {
    name: environment.name,
    baseUrl: environment.baseUrl,
    headersText: JSON.stringify(environment.headers || {}, null, 2),
    variablesText: JSON.stringify(environment.variables || {}, null, 2),
  })
  dialogVisible.value = true
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

async function save() {
  if (!form.name.trim() || !form.baseUrl.trim()) {
    ElMessage.warning('请填写环境名称和 Base URL')
    return
  }

  let payload: EnvironmentPayload
  try {
    payload = {
      name: form.name.trim(),
      baseUrl: form.baseUrl.trim(),
      headers: parseStringMap(form.headersText, '请求头'),
      variables: parseStringMap(form.variablesText, '环境变量'),
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'JSON 格式不正确')
    return
  }

  saving.value = true
  try {
    if (editingId.value) {
      await updateEnvironment(props.projectId, editingId.value, payload)
      ElMessage.success('环境已更新')
    } else {
      await createEnvironment(props.projectId, payload)
      ElMessage.success('环境已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    saving.value = false
  }
}

async function remove(environment: Environment) {
  try {
    await ElMessageBox.confirm(`确认删除环境“${environment.name}”吗？`, '删除环境', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteEnvironment(props.projectId, environment.id)
    environments.value = environments.value.filter((item) => item.id !== environment.id)
    ElMessage.success('环境已删除')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  }
}

onMounted(load)
</script>

<template>
  <section class="workspace-panel">
    <div class="panel-heading">
      <div>
        <h2>测试环境</h2>
        <p>为开发、测试、预发布等环境维护独立的地址、请求头和变量。</p>
      </div>
      <el-button type="primary" @click="openCreate">＋ 新建环境</el-button>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="environments" empty-text="暂无环境配置" style="width: 100%">
        <el-table-column label="环境" min-width="150">
          <template #default="{ row }: { row: Environment }">
            <div class="primary-cell">
              <span class="environment-dot" />
              <strong>{{ row.name }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Base URL" min-width="280">
          <template #default="{ row }: { row: Environment }"><code class="url-code">{{ row.baseUrl }}</code></template>
        </el-table-column>
        <el-table-column label="请求头" width="110">
          <template #default="{ row }: { row: Environment }">{{ Object.keys(row.headers || {}).length }} 项</template>
        </el-table-column>
        <el-table-column label="变量" width="100">
          <template #default="{ row }: { row: Environment }">{{ Object.keys(row.variables || {}).length }} 项</template>
        </el-table-column>
        <el-table-column label="操作" width="145" fixed="right">
          <template #default="{ row }: { row: Environment }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="info-callout">
      <span>i</span>
      <p><strong>配置提示</strong>：请求头和环境变量使用 JSON 对象，例如 <code v-text="'{ &quot;Authorization&quot;: &quot;Bearer {{token}}&quot; }'" />。</p>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑测试环境' : '新建测试环境'" width="min(620px, 94vw)" destroy-on-close>
      <el-form label-position="top">
        <div class="form-row">
          <el-form-item label="环境名称" required>
            <el-input v-model="form.name" maxlength="80" placeholder="例如：测试环境" />
          </el-form-item>
          <el-form-item label="Base URL" required>
            <el-input v-model="form.baseUrl" maxlength="2048" placeholder="https://test-api.example.com" />
          </el-form-item>
        </div>
        <el-form-item label="公共请求头（JSON）">
          <el-input v-model="form.headersText" type="textarea" :rows="5" class="code-textarea" spellcheck="false" />
        </el-form-item>
        <el-form-item label="环境变量（JSON）">
          <el-input v-model="form.variablesText" type="textarea" :rows="5" class="code-textarea" spellcheck="false" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ editingId ? '保存修改' : '创建环境' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>
