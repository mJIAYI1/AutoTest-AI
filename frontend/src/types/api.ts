export interface ApiError {
  timestamp: string
  status: number
  code: string
  message: string
  path: string
  fieldErrors: Record<string, string>
}

export interface User {
  id: number
  username: string
  email: string
  displayName: string | null
  status: string
  createdAt: string
  updatedAt: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  user: User
}

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload extends LoginPayload {
  email: string
  displayName: string
}

export interface Project {
  id: number
  name: string
  description: string | null
  baseUrl: string | null
  createdAt: string
  updatedAt: string
}

export interface ProjectPayload {
  name: string
  description: string
  baseUrl: string
}

export interface Environment {
  id: number
  projectId: number
  name: string
  baseUrl: string
  headers: Record<string, string>
  variables: Record<string, string>
  createdAt: string
  updatedAt: string
}

export interface EnvironmentPayload {
  name: string
  baseUrl: string
  headers: Record<string, string>
  variables: Record<string, string>
}

export interface ApiDefinition {
  id: number
  projectId: number
  operationId: string | null
  method: string
  path: string
  summary: string | null
  description: string | null
  tags: string[]
  parameters: unknown
  requestSchema: unknown
  responseSchema: unknown
  security: unknown
  createdAt: string
  updatedAt: string
}

export interface OpenApiImportResponse {
  projectId: number
  title: string
  version: string
  importedCount: number
  warnings: string[]
}

export type TestCaseType =
  | 'NORMAL'
  | 'BOUNDARY'
  | 'NEGATIVE'
  | 'MISSING_PARAMETER'
  | 'INVALID_TYPE'
  | 'AUTHENTICATION'

export type AssertionType =
  | 'STATUS_CODE'
  | 'JSON_PATH_EXISTS'
  | 'JSON_PATH_EQUALS'
  | 'JSON_PATH_TYPE'
  | 'RESPONSE_TIME_LT'
  | 'BODY_CONTAINS'

export interface TestAssertion {
  type: AssertionType
  expression: string | null
  expected: unknown
}

export interface ExtractionRule {
  name: string
  jsonPath: string
}

export interface TestCasePayload {
  name: string
  description: string
  type: TestCaseType
  requestHeaders: Record<string, string>
  pathParameters: Record<string, string>
  queryParameters: Record<string, string>
  requestBody: unknown
  assertions: TestAssertion[]
  extractionRules: ExtractionRule[]
  enabled: boolean
}

export interface UpdateTestCasePayload extends TestCasePayload {
  version: number
}

export interface TestCase extends Omit<TestCasePayload, 'description'> {
  id: number
  apiId: number
  description: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface AiProviderStatus {
  provider: string
  configured: boolean
  model: string | null
  capability: string
}

export type AiGeneratedTestCase = Omit<TestCasePayload, 'enabled'>

export interface AiTestCaseGenerationResponse {
  apiId: number
  provider: string
  model: string
  candidates: AiGeneratedTestCase[]
  warnings: string[]
  generatedAt: string
}

export type AiDiagnosisSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface AiFailureDiagnosis {
  summary: string
  severity: AiDiagnosisSeverity
  possibleCauses: string[]
  checkLocations: string[]
  repairSuggestions: string[]
}

export interface AiFailureDiagnosisResponse {
  id: number
  testRunId: number
  testResultId: number
  provider: string
  model: string
  diagnosis: AiFailureDiagnosis
  generatedAt: string
}

export type TestRunStatus = 'PENDING' | 'RUNNING' | 'PASS' | 'FAIL' | 'ERROR' | 'CANCELLED'

export interface AssertionResult {
  type: AssertionType
  expression: string | null
  expected: unknown
  actual: unknown
  passed: boolean
  message: string
}

export interface ExtractedValue {
  name: string
  value: string
  sourceExpression: string
}

export interface TestResult {
  id: number
  testCaseId: number
  apiId: number
  apiMethod: string
  apiPath: string
  apiSummary: string | null
  sequenceNumber: number
  testCaseName: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  requestUrl: string
  requestMethod: string
  requestHeaders: Record<string, string[]>
  requestBody: string | null
  responseStatus: number | null
  responseHeaders: Record<string, string[]>
  responseBody: string | null
  responseTimeMs: number | null
  assertions: AssertionResult[]
  extractedVariables: ExtractedValue[]
  errorMessage: string | null
  executedAt: string
}

export interface TestRun {
  id: number
  projectId: number
  testCaseId: number | null
  testSuiteId: number | null
  environmentId: number | null
  runType: 'SINGLE_CASE' | 'SUITE'
  status: TestRunStatus
  totalCount: number
  passedCount: number
  failedCount: number
  errorCount: number
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  result: TestResult | null
  results: TestResult[]
}

export interface TestReportSummary {
  id: number
  projectId: number
  title: string
  runType: 'SINGLE_CASE' | 'SUITE'
  testSuiteId: number | null
  testSuiteName: string | null
  testCaseId: number | null
  testCaseName: string | null
  environmentId: number | null
  environmentName: string | null
  status: TestRunStatus
  totalCount: number
  executedCount: number
  skippedCount: number
  passedCount: number
  failedCount: number
  errorCount: number
  passRate: number
  averageResponseTimeMs: number | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

export interface ApiTestReport {
  apiId: number
  method: string
  path: string
  summary: string | null
  totalCount: number
  passedCount: number
  failedCount: number
  errorCount: number
  passRate: number
  averageResponseTimeMs: number | null
  results: TestResult[]
}

export interface TestReportDetail {
  summary: TestReportSummary
  run: TestRun
  apis: ApiTestReport[]
}

export interface RecentFailure {
  resultId: number
  runId: number
  projectId: number
  projectName: string
  apiId: number
  method: string
  path: string
  apiSummary: string | null
  testCaseId: number
  testCaseName: string
  status: 'FAIL' | 'ERROR'
  responseStatus: number | null
  responseTimeMs: number | null
  errorMessage: string | null
  executedAt: string
}

export interface DailyPassRatePoint {
  date: string
  passedCount: number
  totalCount: number
  passRate: number | null
}

export interface DailyResponseTimePoint {
  date: string
  sampleCount: number
  averageResponseTimeMs: number | null
}

export interface FailingApi {
  apiId: number
  projectId: number
  projectName: string
  method: string
  path: string
  summary: string | null
  failureCount: number
  executionCount: number
  failureRate: number
}

export interface DashboardSummary {
  recentWindowDays: number
  projectCount: number
  apiCount: number
  testCaseCount: number
  recentRunCount: number
  overallPassRate: number
  recentFailures: RecentFailure[]
  passRateTrend: DailyPassRatePoint[]
  responseTimeTrend: DailyResponseTimePoint[]
  topFailingApis: FailingApi[]
}

export interface TestSuiteCasePayload {
  testCaseId: number
  enabled: boolean
}

export interface TestSuitePayload {
  name: string
  description: string
  stopOnFailure: boolean
  cases: TestSuiteCasePayload[]
}

export interface UpdateTestSuitePayload extends TestSuitePayload {
  version: number
}

export interface TestSuiteCase {
  testCaseId: number
  sortOrder: number
  enabled: boolean
  testCaseName: string
  testCaseEnabled: boolean
  apiId: number
  method: string
  path: string
}

export interface TestSuiteCaseCandidate {
  testCaseId: number
  testCaseName: string
  enabled: boolean
  apiId: number
  method: string
  path: string
}

export interface TestSuite {
  id: number
  projectId: number
  name: string
  description: string | null
  stopOnFailure: boolean
  status: 'ACTIVE' | 'ARCHIVED'
  version: number
  cases: TestSuiteCase[]
  createdAt: string
  updatedAt: string
}
