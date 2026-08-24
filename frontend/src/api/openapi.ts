import { http } from './http'
import type { ApiDefinition, OpenApiImportResponse } from '../types/api'

const basePath = (projectId: number) => `/api/projects/${projectId}/apis`

export async function listApiDefinitions(projectId: number): Promise<ApiDefinition[]> {
  const { data } = await http.get<ApiDefinition[]>(basePath(projectId))
  return data
}

export async function importOpenApiUrl(
  projectId: number,
  url: string,
): Promise<OpenApiImportResponse> {
  const { data } = await http.post<OpenApiImportResponse>(`${basePath(projectId)}/import/url`, { url })
  return data
}

export async function importOpenApiFile(
  projectId: number,
  file: File,
): Promise<OpenApiImportResponse> {
  const formData = new FormData()
  formData.append('file', file)
  const { data } = await http.post<OpenApiImportResponse>(`${basePath(projectId)}/import/file`, formData)
  return data
}
