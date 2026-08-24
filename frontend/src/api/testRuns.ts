import { http } from './http'
import type { TestRun } from '../types/api'

export async function runTestCase(
  projectId: number,
  apiId: number,
  testCaseId: number,
  environmentId: number | null,
): Promise<TestRun> {
  const { data } = await http.post<TestRun>(
    `/api/projects/${projectId}/apis/${apiId}/test-cases/${testCaseId}/runs`,
    { environmentId },
  )
  return data
}

export async function getTestRun(projectId: number, runId: number): Promise<TestRun> {
  const { data } = await http.get<TestRun>(`/api/projects/${projectId}/test-runs/${runId}`)
  return data
}

export async function runTestSuite(
  projectId: number,
  suiteId: number,
  environmentId: number | null,
): Promise<TestRun> {
  const { data } = await http.post<TestRun>(
    `/api/projects/${projectId}/test-suites/${suiteId}/runs`,
    { environmentId },
  )
  return data
}
