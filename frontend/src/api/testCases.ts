import { http } from './http'
import type { TestCase, TestCasePayload, UpdateTestCasePayload } from '../types/api'

const basePath = (projectId: number, apiId: number) =>
  `/api/projects/${projectId}/apis/${apiId}/test-cases`

export async function listTestCases(projectId: number, apiId: number): Promise<TestCase[]> {
  const { data } = await http.get<TestCase[]>(basePath(projectId, apiId))
  return data
}

export async function createTestCase(
  projectId: number,
  apiId: number,
  payload: TestCasePayload,
): Promise<TestCase> {
  const { data } = await http.post<TestCase>(basePath(projectId, apiId), payload)
  return data
}

export async function updateTestCase(
  projectId: number,
  apiId: number,
  testCaseId: number,
  payload: UpdateTestCasePayload,
): Promise<TestCase> {
  const { data } = await http.put<TestCase>(`${basePath(projectId, apiId)}/${testCaseId}`, payload)
  return data
}

export async function deleteTestCase(
  projectId: number,
  apiId: number,
  testCaseId: number,
): Promise<void> {
  await http.delete(`${basePath(projectId, apiId)}/${testCaseId}`)
}
