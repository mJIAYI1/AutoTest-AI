import { http } from './http'
import type { TestReportDetail, TestReportSummary } from '../types/api'

export async function listTestReports(
  projectId: number,
  limit = 30,
): Promise<TestReportSummary[]> {
  const { data } = await http.get<TestReportSummary[]>(
    `/api/projects/${projectId}/test-reports`,
    { params: { limit } },
  )
  return data
}

export async function getTestReport(
  projectId: number,
  runId: number,
): Promise<TestReportDetail> {
  const { data } = await http.get<TestReportDetail>(
    `/api/projects/${projectId}/test-reports/${runId}`,
  )
  return data
}
