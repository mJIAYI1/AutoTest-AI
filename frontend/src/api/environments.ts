import { http } from './http'
import type { Environment, EnvironmentPayload } from '../types/api'

const basePath = (projectId: number) => `/api/projects/${projectId}/environments`

export async function listEnvironments(projectId: number): Promise<Environment[]> {
  const { data } = await http.get<Environment[]>(basePath(projectId))
  return data
}

export async function createEnvironment(
  projectId: number,
  payload: EnvironmentPayload,
): Promise<Environment> {
  const { data } = await http.post<Environment>(basePath(projectId), payload)
  return data
}

export async function updateEnvironment(
  projectId: number,
  environmentId: number,
  payload: EnvironmentPayload,
): Promise<Environment> {
  const { data } = await http.put<Environment>(`${basePath(projectId)}/${environmentId}`, payload)
  return data
}

export async function deleteEnvironment(projectId: number, environmentId: number): Promise<void> {
  await http.delete(`${basePath(projectId)}/${environmentId}`)
}
