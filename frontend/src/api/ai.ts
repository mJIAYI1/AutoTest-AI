import axios from 'axios'
import { http } from './http'
import type {
  AiFailureDiagnosisResponse,
  AiProviderStatus,
  AiTestCaseGenerationResponse,
} from '../types/api'

export async function getAiProviderStatus(): Promise<AiProviderStatus> {
  const { data } = await http.get<AiProviderStatus>('/api/ai/status')
  return data
}

export async function generateAiTestCases(
  projectId: number,
  apiId: number,
  count: number,
  focus: string,
): Promise<AiTestCaseGenerationResponse> {
  const { data } = await http.post<AiTestCaseGenerationResponse>(
    `/api/projects/${projectId}/apis/${apiId}/ai/test-cases/generate`,
    { count, focus: focus.trim() || null },
    { timeout: 120_000 },
  )
  return data
}

export async function getAiFailureDiagnosis(
  projectId: number,
  runId: number,
  resultId: number,
): Promise<AiFailureDiagnosisResponse | null> {
  try {
    const { data } = await http.get<AiFailureDiagnosisResponse>(
      `/api/projects/${projectId}/test-runs/${runId}/results/${resultId}/ai/diagnosis`,
    )
    return data
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) return null
    throw error
  }
}

export async function analyzeAiFailure(
  projectId: number,
  runId: number,
  resultId: number,
): Promise<AiFailureDiagnosisResponse> {
  const { data } = await http.post<AiFailureDiagnosisResponse>(
    `/api/projects/${projectId}/test-runs/${runId}/results/${resultId}/ai/diagnosis`,
    undefined,
    { timeout: 120_000 },
  )
  return data
}
