<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '../api/http'
import { importOpenApiFile, importOpenApiUrl, listApiDefinitions } from '../api/openapi'
import type { ApiDefinition, OpenApiImportResponse } from '../types/api'

const props = defineProps<{ projectId: number }>()
const apis = ref<ApiDefinition[]>([])
const loading = ref(true)
const importingUrl = ref(false)
const importingFile = ref(false)
const sourceUrl = ref('')
const keyword = ref('')
const methodFilter = ref('')
const tagFilter = ref('')
const selectedApi = ref<ApiDefinition | null>(null)
const drawerVisible = ref(false)
const lastImport = ref<OpenApiImportResponse | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)

const methods = computed(() => [...new Set(apis.value.map((api) => api.method.toUpperCase()))].sort())
const tags = computed(() => [...new Set(apis.value.flatMap((api) => api.tags || []))].sort())
const filteredApis = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return apis.value.filter((api) => {
    const matchesQuery = !query || [api.path, api.summary, api.operationId]
      .filter(Boolean)
      .some((value) => value!.toLowerCase().includes(query))
    const matchesMethod = !methodFilter.value || api.method.toUpperCase() === methodFilter.value
    const matchesTag = !tagFilter.value || api.tags?.includes(tagFilter.value)
    return matchesQuery && matchesMethod && matchesTag
  })
})

async function load() {
  loading.value = true
  try {
    apis.value = await listApiDefinitions(props.projectId)
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

async function importUrl() {
  if (!sourceUrl.value.trim()) {
    ElMessage.warning('请输入 OpenAPI 文档 URL')
    return
  }
  importingUrl.value = true
  try {
    lastImport.value = await importOpenApiUrl(props.projectId, sourceUrl.value.trim())
    ElMessage.success(`成功导入 ${lastImport.value.importedCount} 个接口`)
    await load()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    importingUrl.value = false
  }
}

function chooseFile() {
  fileInput.value?.click()
}

async function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  importingFile.value = true
  try {
    lastImport.value = await importOpenApiFile(props.projectId, file)
    ElMessage.success(`成功导入 ${lastImport.value.importedCount} 个接口`)
    await load()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    importingFile.value = false
    input.value = ''
  }
}

function openDetail(api: ApiDefinition) {
  selectedApi.value = api
  drawerVisible.value = true
}

function methodTagType(method: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  const types: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    PATCH: 'warning',
    DELETE: 'danger',
  }
  return types[method.toUpperCase()] || 'info'
}

function prettyJson(value: unknown) {
  if (value === null || value === undefined) return '无'
  return JSON.stringify(value, null, 2)
}

onMounted(load)
</script>

<template>
  <section class="workspace-panel">
    <div class="panel-heading">
      <div>
        <h2>接口定义</h2>
        <p>从 OpenAPI 3.x 或 Swagger 2.0 文档导入接口，并查看已解析的请求与响应结构。</p>
      </div>
      <el-button @click="load">↻ 刷新</el-button>
    </div>

    <div class="import-card">
      <div class="import-card__copy">
        <span class="import-icon">⇩</span>
        <div><strong>导入 API 文档</strong><p>重复导入会按当前文档重新同步该项目的接口定义。</p></div>
      </div>
      <div class="import-actions">
        <el-input v-model="sourceUrl" size="large" placeholder="https://example.com/openapi.json" clearable @keyup.enter="importUrl">
          <template #prepend>URL</template>
        </el-input>
        <el-button type="primary" size="large" :loading="importingUrl" @click="importUrl">从 URL 导入</el-button>
        <span class="or-divider">或</span>
        <input ref="fileInput" class="visually-hidden" type="file" accept=".json,.yaml,.yml,application/json,application/yaml,text/yaml" @change="handleFileChange" />
        <el-button size="large" :loading="importingFile" @click="chooseFile">选择本地文件</el-button>
      </div>
    </div>

    <el-alert v-if="lastImport" class="import-result" type="success" show-icon :closable="true" @close="lastImport = null">
      <template #title>已导入 {{ lastImport.title || 'OpenAPI 文档' }} {{ lastImport.version }}，共 {{ lastImport.importedCount }} 个接口</template>
      <template v-if="lastImport.warnings.length" #default>
        <p v-for="warning in lastImport.warnings" :key="warning">{{ warning }}</p>
      </template>
    </el-alert>

    <div class="filter-bar">
      <el-input v-model="keyword" class="search-input" placeholder="搜索路径、摘要或 operationId" clearable>
        <template #prefix>⌕</template>
      </el-input>
      <el-select v-model="methodFilter" placeholder="全部方法" clearable>
        <el-option v-for="method in methods" :key="method" :label="method" :value="method" />
      </el-select>
      <el-select v-model="tagFilter" placeholder="全部标签" clearable filterable>
        <el-option v-for="tag in tags" :key="tag" :label="tag" :value="tag" />
      </el-select>
      <span class="result-count">{{ filteredApis.length }} / {{ apis.length }} 个接口</span>
    </div>

    <div class="table-card">
      <el-table v-loading="loading" :data="filteredApis" empty-text="暂无接口，请先导入 OpenAPI 文档" row-class-name="clickable-row" @row-click="openDetail">
        <el-table-column label="方法" width="105">
          <template #default="{ row }: { row: ApiDefinition }">
            <el-tag :type="methodTagType(row.method)" effect="light" class="method-tag">{{ row.method.toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="接口路径" min-width="250">
          <template #default="{ row }: { row: ApiDefinition }"><code class="path-code">{{ row.path }}</code></template>
        </el-table-column>
        <el-table-column label="摘要" min-width="220">
          <template #default="{ row }: { row: ApiDefinition }"><span class="muted-text">{{ row.summary || '暂无摘要' }}</span></template>
        </el-table-column>
        <el-table-column label="标签" min-width="170">
          <template #default="{ row }: { row: ApiDefinition }">
            <div class="tag-list"><el-tag v-for="tag in row.tags?.slice(0, 2)" :key="tag" size="small" type="info">{{ tag }}</el-tag></div>
          </template>
        </el-table-column>
        <el-table-column label="Operation ID" min-width="180">
          <template #default="{ row }: { row: ApiDefinition }"><code class="operation-code">{{ row.operationId || '—' }}</code></template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="drawerVisible" size="min(720px, 92vw)" :with-header="false">
      <div v-if="selectedApi" class="api-detail">
        <div class="api-detail__header">
          <div>
            <el-tag :type="methodTagType(selectedApi.method)" class="method-tag">{{ selectedApi.method.toUpperCase() }}</el-tag>
            <code>{{ selectedApi.path }}</code>
          </div>
          <button class="icon-button" type="button" aria-label="关闭" @click="drawerVisible = false">×</button>
        </div>
        <h2>{{ selectedApi.summary || selectedApi.operationId || '接口详情' }}</h2>
        <p class="api-description">{{ selectedApi.description || '该接口没有提供详细描述。' }}</p>
        <div class="detail-meta">
          <span><small>OPERATION ID</small><code>{{ selectedApi.operationId || '—' }}</code></span>
          <span><small>TAGS</small><strong>{{ selectedApi.tags?.join(', ') || '—' }}</strong></span>
        </div>

        <el-tabs>
          <el-tab-pane label="参数">
            <pre class="json-viewer">{{ prettyJson(selectedApi.parameters) }}</pre>
          </el-tab-pane>
          <el-tab-pane label="请求体">
            <pre class="json-viewer">{{ prettyJson(selectedApi.requestSchema) }}</pre>
          </el-tab-pane>
          <el-tab-pane label="响应">
            <pre class="json-viewer">{{ prettyJson(selectedApi.responseSchema) }}</pre>
          </el-tab-pane>
          <el-tab-pane label="安全定义">
            <pre class="json-viewer">{{ prettyJson(selectedApi.security) }}</pre>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </section>
</template>
