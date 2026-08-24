import { http } from './http'
import type { Project, ProjectPayload } from '../types/api'

export async function listProjects(): Promise<Project[]> {
  const { data } = await http.get<Project[]>('/api/projects')
  return data
}

export async function getProject(projectId: number): Promise<Project> {
  const { data } = await http.get<Project>(`/api/projects/${projectId}`)
  return data
}

export async function createProject(payload: ProjectPayload): Promise<Project> {
  const { data } = await http.post<Project>('/api/projects', payload)
  return data
}

export async function updateProject(projectId: number, payload: ProjectPayload): Promise<Project> {
  const { data } = await http.put<Project>(`/api/projects/${projectId}`, payload)
  return data
}

export async function deleteProject(projectId: number): Promise<void> {
  await http.delete(`/api/projects/${projectId}`)
}
