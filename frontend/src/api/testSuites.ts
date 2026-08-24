import { http } from './http'
import type {
  TestSuite,
  TestSuiteCaseCandidate,
  TestSuitePayload,
  UpdateTestSuitePayload,
} from '../types/api'

export async function listTestSuites(projectId: number): Promise<TestSuite[]> {
  const { data } = await http.get<TestSuite[]>(`/api/projects/${projectId}/test-suites`)
  return data
}

export async function listTestSuiteCandidates(projectId: number): Promise<TestSuiteCaseCandidate[]> {
  const { data } = await http.get<TestSuiteCaseCandidate[]>(
    `/api/projects/${projectId}/test-suites/candidates`,
  )
  return data
}

export async function createTestSuite(
  projectId: number,
  payload: TestSuitePayload,
): Promise<TestSuite> {
  const { data } = await http.post<TestSuite>(`/api/projects/${projectId}/test-suites`, payload)
  return data
}

export async function updateTestSuite(
  projectId: number,
  suiteId: number,
  payload: UpdateTestSuitePayload,
): Promise<TestSuite> {
  const { data } = await http.put<TestSuite>(
    `/api/projects/${projectId}/test-suites/${suiteId}`,
    payload,
  )
  return data
}

export async function deleteTestSuite(projectId: number, suiteId: number): Promise<void> {
  await http.delete(`/api/projects/${projectId}/test-suites/${suiteId}`)
}
