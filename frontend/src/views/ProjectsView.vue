<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  createProject,
  deleteProject,
  listProjects,
  updateProject,
} from '../api/projects'
import { getErrorMessage } from '../api/http'
import type { Project, ProjectPayload } from '../types/api'

const router = useRouter()
const projects = ref<Project[]>([])
const loading = ref(true)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<ProjectPayload>({ name: '', description: '', baseUrl: '' })

function formatDate(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value))
}

async function loadProjects() {
  loading.value = true
  try {
    projects.value = await listProjects()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', description: '', baseUrl: '' })
  dialogVisible.value = true
}

function openEdit(project: Project) {
  editingId.value = project.id
  Object.assign(form, {
    name: project.name,
    description: project.description || '',
    baseUrl: project.baseUrl || '',
  })
  dialogVisible.value = true
}

async function saveProject() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入项目名称')
    return
  }
  saving.value = true
  const payload = {
    name: form.name.trim(),
    description: form.description.trim(),
    baseUrl: form.baseUrl.trim(),
  }
  try {
    if (editingId.value) {
      await updateProject(editingId.value, payload)
      ElMessage.success('项目已更新')
    } else {
      await createProject(payload)
      ElMessage.success('项目已创建')
    }
    dialogVisible.value = false
    await loadProjects()
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  } finally {
    saving.value = false
  }
}

async function removeProject(project: Project) {
  try {
    await ElMessageBox.confirm(
      `删除“${project.name}”会同时删除其环境和接口数据，是否继续？`,
      '删除项目',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }

  try {
    await deleteProject(project.id)
    projects.value = projects.value.filter((item) => item.id !== project.id)
    ElMessage.success('项目已删除')
  } catch (error) {
    ElMessage.error(getErrorMessage(error))
  }
}

onMounted(loadProjects)
</script>

<template>
  <section class="page-section">
    <div class="page-heading">
      <div>
        <p class="eyebrow">PROJECTS</p>
        <h1>项目管理</h1>
        <p>用项目隔离不同系统的接口、环境和测试资产。</p>
      </div>
      <el-button type="primary" size="large" @click="openCreate">＋ 新建项目</el-button>
    </div>

    <div class="summary-strip">
      <div>
        <span class="summary-number">{{ projects.length }}</span>
        <span>项目总数</span>
      </div>
      <div>
        <span class="summary-number">OpenAPI</span>
        <span>当前导入能力</span>
      </div>
      <div>
        <span class="summary-number summary-number--green">运行中</span>
        <span>平台状态</span>
      </div>
    </div>

    <div v-loading="loading" class="project-grid" :class="{ 'project-grid--loading': loading }">
      <article v-for="project in projects" :key="project.id" class="project-card" @click="router.push(`/projects/${project.id}`)">
        <div class="project-card__top">
          <span class="project-symbol">{{ project.name.slice(0, 1).toUpperCase() }}</span>
          <el-dropdown trigger="click" @click.stop>
            <button class="icon-button" type="button" aria-label="项目操作">•••</button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openEdit(project)">编辑项目</el-dropdown-item>
                <el-dropdown-item divided @click="removeProject(project)">删除项目</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <h2>{{ project.name }}</h2>
        <p class="project-description">{{ project.description || '暂无项目描述，进入项目后开始配置。' }}</p>
        <div class="base-url">
          <span>BASE URL</span>
          <code>{{ project.baseUrl || '尚未配置' }}</code>
        </div>
        <footer>
          <span>更新于 {{ formatDate(project.updatedAt) }}</span>
          <span class="enter-link">进入项目 →</span>
        </footer>
      </article>

      <button v-if="!loading" class="project-card project-card--create" type="button" @click="openCreate">
        <span class="create-plus">＋</span>
        <strong>创建新项目</strong>
        <small>建立独立的 API 测试空间</small>
      </button>
    </div>

    <el-empty v-if="!loading && projects.length === 0" description="还没有项目，点击上方按钮创建第一个项目" />

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑项目' : '新建项目'" width="min(520px, 92vw)" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.name" maxlength="120" show-word-limit placeholder="例如：订单服务 API" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="4000" placeholder="说明项目范围和测试目标" />
        </el-form-item>
        <el-form-item label="默认 Base URL">
          <el-input v-model="form.baseUrl" maxlength="2048" placeholder="https://api.example.com" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProject">{{ editingId ? '保存修改' : '创建项目' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>
