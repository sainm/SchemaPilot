import {
  ApiOutlined,
  AuditOutlined,
  BranchesOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  CodeOutlined,
  DatabaseOutlined,
  DownloadOutlined,
  FileSearchOutlined,
  PlusOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import Editor from '@monaco-editor/react'
import { StatisticCard } from '@ant-design/pro-components'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Alert, Badge, Button, ConfigProvider, Input, Layout, Menu, Progress, Select, Space, Table, Tag, Typography, Upload } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import axios from 'axios'
import './App.css'

type HealthPayload = {
  service: string
  status: string
  javaVersion: string
  virtualThreadsEnabled: boolean
  uptimeMs: number
}

type ApiResponse<T> = {
  success: boolean
  data: T
  message?: string
  timestamp: string
}

type PipelineItem = {
  key: string
  stage: string
  owner: string
  status: 'completed'
  artifact: string
}

type ProjectItem = {
  id: string
  name: string
  description?: string | null
  status: string
  createdAt: string
  updatedAt: string
}

type DetectedRisk = {
  type: string
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'BLOCKER'
  message: string
  suggestion: string
}

type ParseIssue = {
  type: string
  message: string
  suggestion: string
}

type AnalyzedStatement = {
  index: number
  objectType: string
  objectName: string
  originalSql: string
  postgresSql: string
  conversionLevel: string
  riskLevel: DetectedRisk['level']
  risks: DetectedRisk[]
  parseIssues: ParseIssue[]
  aiSuggestion: string
}

type SqlAnalysisResponse = {
  statements: AnalyzedStatement[]
  statementCount: number
  riskCount: number
  compatibilityScore: number
}

type AiSuggestionDraft = {
  provider: string
  model: string
  promptVersion: string
  suggestion: string
  evidence: string[]
  citedChunkKeys: string[]
}

type LocalLlmStatus = {
  enabled: boolean
  endpoint: string
  model: string
  reachable: boolean
  discoveredModels: string[]
  timeoutMs: number
  requestCount: number
  timeoutCount: number
  failureCount: number
  fallbackCount: number
  lastError: string
}

type KnowledgeMetrics = {
  searchCount: number
  hitCount: number
  acceptedCount: number
  rejectedCount: number
  hitRate: number
  adoptionRate: number
}

type AiProviderConfig = {
  type: string
  enabled: boolean
  endpoint?: string | null
  model?: string | null
}

type SavedAiSuggestion = {
  id: string
  statementIndex: number
  provider: string
  model: string
  promptVersion: string
  inputHash: string
  suggestion: string
  evidence: string[]
  citedChunkKeys: string[]
  status: 'GENERATED' | 'ACCEPTED' | 'IGNORED' | 'APPLIED'
}

type SavedSqlVersion = {
  id: string
  statementIndex: number
  source: 'RULE_GENERATED' | 'AI_SUGGESTION' | 'MANUAL_EDIT'
  status: string
  sql: string
  createdAt: string
}

type AuditEvent = {
  id: string
  action: string
  message: string
  createdAt: string
}

type ReviewRecord = {
  id: string
  reportVersion: string
  decision: 'SUBMITTED' | 'APPROVED' | 'CONDITIONALLY_APPROVED' | 'REJECTED' | 'CHANGES_REQUESTED'
  resultingReportStatus: string
  reviewer: string
  comment: string
  boundSqlVersionIds: string[]
  reviewedAt: string
}

type WorkbenchSnapshot = {
  id: string
  createdAt: string
  reportVersion: string
  originalSql: string
  analysis: SqlAnalysisResponse
  reportStatus: string
  baselineStatus: string
  baselineFrozen: boolean
  sqlVersions: SavedSqlVersion[]
  aiSuggestions: SavedAiSuggestion[]
  reviewRecords: ReviewRecord[]
  auditEvents: AuditEvent[]
}

type SqlPackageResponse = {
  snapshotId: string
  fileName: string
  generatedAt: string
  sqlVersionIds: string[]
  content: string
}

type FileImportJob = {
  id: string
  fileName: string
  checksumSha256: string
  encoding: string
  sizeBytes: number
  status: 'QUEUED' | 'PARSING' | 'COMPLETED' | 'FAILED'
  progressPercent: number
  analysis?: SqlAnalysisResponse | null
  errorMessage?: string | null
}

type HighRiskObject = {
  statementIndex: number
  objectType: string
  objectName: string
  riskLevel: DetectedRisk['level']
  riskTypes: string[]
}

type TypeMappingItem = {
  statementIndex: number
  objectName: string
  sourceType: string
  targetType: string
}

type SqlIssueItem = {
  statementIndex: number
  objectName: string
  riskType: string
  level: DetectedRisk['level']
  message: string
  suggestion: string
}

type PrecheckReportResponse = {
  reportVersion: string
  generatedAt: string
  analysis: SqlAnalysisResponse
  objectTypeDistribution: Record<string, number>
  riskDistribution: Partial<Record<DetectedRisk['level'], number>>
  highRiskObjects: HighRiskObject[]
  typeMappings: TypeMappingItem[]
  issues: SqlIssueItem[]
  handlingRecommendations: string[]
  migrationOrderDraft: string[]
  managementSummary: string
  developerSummary: string
  versionRefs: Record<string, string>
}

type SkillDefinition = {
  id: string
  version: string
  status: string
  description: string
  allowedTools: string[]
  requiresReview: boolean
  outputSchema: Record<string, string>
}

type AgentStep = {
  id: string
  sequence: number
  status: string
  action: string
  toolName?: string | null
  inputSummary?: string | null
  outputSummary?: string | null
}

type AgentRun = {
  id: string
  type: 'ASSESSMENT' | 'CONVERSION' | 'ERROR_DIAGNOSIS'
  status: string
  objective: string
  steps: AgentStep[]
  result: Record<string, unknown>
}

type McpStatus = {
  externalClientEnabled: boolean
  allowedTools: string[]
  writeToolsDefaultDryRun: boolean
  auditRecordCount: number
}

type McpResource = {
  id: string
  description: string
  mimeType: string
  content: string
}

type McpPrompt = {
  id: string
  description: string
}

type McpToolCallRecord = {
  id: string
  toolName: string
  usedDefaultDryRun: boolean
  requestedDryRun?: boolean | null
  allowed: boolean
  success: boolean
  message: string
  result?: unknown
}

type DataSourceConfig = {
  id: string
  name: string
  kind: 'ORACLE' | 'POSTGRESQL'
  jdbcUrl: string
  username: string
  passwordConfigured: boolean
  status: 'DRAFT' | 'TESTED' | 'FAILED'
}

type DataSourceConnectionTestResult = {
  configId: string
  success: boolean
  databaseProduct?: string | null
  databaseVersion?: string | null
  username?: string | null
  permissions: string[]
  risks: string[]
  message: string
}

type MigrationPlanStep = {
  id: string
  sequence: number
  objectType: string
  objectName: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  attempts: number
  failureReason?: string | null
  workItem?: string | null
}

type MigrationPlan = {
  id: string
  snapshotId: string
  targetDataSourceId: string
  reportVersion: string
  status: 'DRAFT' | 'READY' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  steps: MigrationPlanStep[]
  executionLog: string[]
}

const pipeline: PipelineItem[] = [
  { key: '1', stage: '输入源', owner: 'Ingest', status: 'completed', artifact: 'InputSource' },
  { key: '2', stage: '对象识别', owner: 'Parser', status: 'completed', artifact: 'DbObject / ParseIssue' },
  { key: '3', stage: '规则转换', owner: 'Converter', status: 'completed', artifact: 'ConversionResult' },
  { key: '4', stage: 'AI 建议', owner: 'AI Copilot', status: 'completed', artifact: 'AiSuggestion' },
  { key: '5', stage: '预处理报告', owner: 'Report', status: 'completed', artifact: 'PrecheckReport' },
  { key: '6', stage: '审核和基线', owner: 'Review', status: 'completed', artifact: 'ReviewRecord / Baseline SQL' },
]

const pipelineColumns: ColumnsType<PipelineItem> = [
  {
    title: '阶段',
    dataIndex: 'stage',
    key: 'stage',
    render: (value, row) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>{value}</Typography.Text>
        <Typography.Text type="secondary">{row.artifact}</Typography.Text>
      </Space>
    ),
  },
  { title: '模块', dataIndex: 'owner', key: 'owner', width: 160 },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 140,
    render: (value: PipelineItem['status']) => {
      return <Tag color={value === 'completed' ? 'green' : 'default'}>已闭环</Tag>
    },
  },
]

const projectColumns: ColumnsType<ProjectItem> = [
  {
    title: '项目',
    dataIndex: 'name',
    key: 'name',
    render: (value, row) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>{value}</Typography.Text>
        <Typography.Text type="secondary">{row.description || '暂无描述'}</Typography.Text>
      </Space>
    ),
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 120,
    render: (value) => <Tag color={value === 'DRAFT' ? 'blue' : 'default'}>{value}</Tag>,
  },
]

const statementColumns: ColumnsType<AnalyzedStatement> = [
  {
    title: '对象',
    key: 'object',
    render: (_, row) => (
      <Space direction="vertical" size={0}>
        <Typography.Text strong>{row.objectName}</Typography.Text>
        <Typography.Text type="secondary">{row.objectType}</Typography.Text>
      </Space>
    ),
  },
  {
    title: '转换',
    dataIndex: 'conversionLevel',
    key: 'conversionLevel',
    width: 150,
    render: (value) => {
      const color = value === 'AUTO' ? 'green' : value === 'DRAFT' ? 'gold' : value === 'MANUAL_REQUIRED' ? 'red' : 'blue'
      return <Tag color={color}>{value}</Tag>
    },
  },
  {
    title: '风险',
    key: 'risks',
    width: 150,
    render: (_, row) => {
      const color = row.riskLevel === 'BLOCKER' ? 'red' : row.riskLevel === 'HIGH' ? 'orange' : row.riskLevel === 'MEDIUM' ? 'gold' : 'green'
      return <Tag color={color}>{row.riskLevel} / {row.risks.length}</Tag>
    },
  },
  {
    title: '解析',
    key: 'parseIssues',
    width: 100,
    render: (_, row) => <Tag color={row.parseIssues.length > 0 ? 'red' : 'green'}>{row.parseIssues.length}</Tag>,
  },
]

const highRiskColumns: ColumnsType<HighRiskObject> = [
  {
    title: '对象',
    key: 'object',
    render: (_, row) => `${row.objectType} ${row.objectName}`,
  },
  {
    title: '等级',
    dataIndex: 'riskLevel',
    key: 'riskLevel',
    width: 110,
    render: (value) => <Tag color={value === 'BLOCKER' ? 'red' : 'orange'}>{value}</Tag>,
  },
  {
    title: '风险',
    key: 'riskTypes',
    render: (_, row) => row.riskTypes.join(', '),
  },
]

const sampleSql = `CREATE TABLE users (
  id NUMBER(19) PRIMARY KEY,
  score NUMBER,
  name VARCHAR2(100),
  created_at DATE DEFAULT SYSDATE,
  bio CLOB
);`

function useBackendHealth() {
  return useQuery({
    queryKey: ['backend-health'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<HealthPayload>>('/api/health')
      return response.data.data
    },
    retry: false,
    refetchInterval: 10000,
  })
}

function useLocalLlmStatus() {
  return useQuery({
    queryKey: ['local-llm-status'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<LocalLlmStatus>>('/api/ai/local-status')
      return response.data.data
    },
    retry: false,
    refetchInterval: 10000,
  })
}

function useKnowledgeMetrics() {
  return useQuery({
    queryKey: ['knowledge-metrics'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<KnowledgeMetrics>>('/api/knowledge/metrics')
      return response.data.data
    },
    retry: false,
    refetchInterval: 10000,
  })
}

function useAiProviderConfigs() {
  return useQuery({
    queryKey: ['ai-provider-configs'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<AiProviderConfig[]>>('/api/ai/provider-configs')
      return response.data.data
    },
    retry: false,
    refetchInterval: 30000,
  })
}

function useProjects() {
  return useQuery({
    queryKey: ['projects'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<ProjectItem[]>>('/api/projects')
      return response.data.data
    },
    retry: false,
  })
}

function useDataSources() {
  return useQuery({
    queryKey: ['datasources'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<DataSourceConfig[]>>('/api/datasources')
      return response.data.data
    },
    retry: false,
  })
}

function useCreateDataSource() {
  return useMutation({
    mutationFn: async (payload: { name: string; kind: DataSourceConfig['kind']; jdbcUrl: string; username: string; password: string }) => {
      const response = await axios.post<ApiResponse<DataSourceConfig>>('/api/datasources', payload)
      return response.data.data
    },
  })
}

function useTestDataSource() {
  return useMutation({
    mutationFn: async (configId: string) => {
      const response = await axios.post<ApiResponse<DataSourceConnectionTestResult>>(`/api/datasources/${configId}/test`)
      return response.data.data
    },
  })
}

function useAnalyzeSql() {
  return useMutation({
    mutationFn: async (sql: string) => {
      const response = await axios.post<ApiResponse<SqlAnalysisResponse>>('/api/manual-sql/analyze', { sql })
      return response.data.data
    },
  })
}

function useSaveWorkbenchSnapshot() {
  return useMutation({
    mutationFn: async (sql: string) => {
      const response = await axios.post<ApiResponse<WorkbenchSnapshot>>('/api/workbench/manual-sql', { sql })
      return response.data.data
    },
  })
}

function useGeneratePrecheck() {
  return useMutation({
    mutationFn: async (sql: string) => {
      const response = await axios.post<ApiResponse<PrecheckReportResponse>>('/api/precheck/manual-sql', { sql })
      return response.data.data
    },
  })
}

function useSaveAiSuggestion() {
  return useMutation({
    mutationFn: async (payload: {
      snapshotId: string
      statementIndex: number
      draft: AiSuggestionDraft
    }) => {
      const response = await axios.post<ApiResponse<WorkbenchSnapshot>>('/api/workbench/ai-suggestions', {
        snapshotId: payload.snapshotId,
        statementIndex: payload.statementIndex,
        provider: payload.draft.provider,
        model: payload.draft.model,
        promptVersion: payload.draft.promptVersion,
        suggestion: payload.draft.suggestion,
        evidence: payload.draft.evidence,
        citedChunkKeys: payload.draft.citedChunkKeys,
      })
      return response.data.data
    },
  })
}

function useReviewAiSuggestion(action: 'accept' | 'ignore') {
  return useMutation({
    mutationFn: async (suggestionId: string) => {
      const response = await axios.post<ApiResponse<WorkbenchSnapshot>>(`/api/workbench/ai-suggestions/${suggestionId}/${action}`)
      return response.data.data
    },
  })
}

function useReviewAction(action: 'submit' | 'approve') {
  return useMutation({
    mutationFn: async (snapshotId: string) => {
      const response = await axios.post<ApiResponse<WorkbenchSnapshot>>(`/api/workbench/snapshots/${snapshotId}/review/${action}`, {
        reviewer: action === 'submit' ? 'developer' : 'reviewer',
        comment: action === 'submit' ? 'Ready for review.' : 'Approved for baseline.',
      })
      return response.data.data
    },
  })
}

function useExportSqlPackage() {
  return useMutation({
    mutationFn: async (snapshotId: string) => {
      const response = await axios.get<ApiResponse<SqlPackageResponse>>(`/api/export/snapshots/${snapshotId}/sql-package`)
      return response.data.data
    },
  })
}

function useEditTargetSql() {
  return useMutation({
    mutationFn: async (payload: { snapshotId: string; statementIndex: number; targetSql: string }) => {
      const response = await axios.post<ApiResponse<WorkbenchSnapshot>>(`/api/workbench/snapshots/${payload.snapshotId}/target-sql`, {
        statementIndex: payload.statementIndex,
        targetSql: payload.targetSql,
      })
      return response.data.data
    },
  })
}

function useRestoreGeneratedSql() {
  return useMutation({
    mutationFn: async (payload: { snapshotId: string; statementIndex: number }) => {
      const response = await axios.post<ApiResponse<WorkbenchSnapshot>>(
        `/api/workbench/snapshots/${payload.snapshotId}/statements/${payload.statementIndex}/restore-generated`,
      )
      return response.data.data
    },
  })
}

function useUploadSqlFile() {
  return useMutation({
    mutationFn: async (file: File) => {
      const body = new FormData()
      body.append('file', file)
      body.append('encoding', 'UTF-8')
      const response = await axios.post<ApiResponse<FileImportJob>>('/api/file-import/sql', body)
      return response.data.data
    },
  })
}

function useSaveFileImportSnapshot() {
  return useMutation({
    mutationFn: async (jobId: string) => {
      const response = await axios.post<ApiResponse<WorkbenchSnapshot>>(`/api/workbench/file-import-jobs/${jobId}`)
      return response.data.data
    },
  })
}

function useFileImportJob(jobId?: string) {
  return useQuery({
    queryKey: ['file-import-job', jobId],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<FileImportJob>>(`/api/file-import/jobs/${jobId}`)
      return response.data.data
    },
    enabled: Boolean(jobId),
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status === 'COMPLETED' || status === 'FAILED' ? false : 1000
    },
  })
}

function useExplainRisk() {
  return useMutation({
    mutationFn: async (statement: AnalyzedStatement) => {
      const risk = statement.risks[0]
      const response = await axios.post<ApiResponse<AiSuggestionDraft>>('/api/ai/explain-risk', {
        riskType: risk.type,
        objectType: statement.objectType,
        message: risk.message,
        originalSql: statement.originalSql,
      })
      return response.data.data
    },
  })
}

function useSuggestSqlRewrite() {
  return useMutation({
    mutationFn: async (statement: AnalyzedStatement) => {
      const response = await axios.post<ApiResponse<AiSuggestionDraft>>('/api/ai/suggest-sql', {
        objectType: statement.objectType,
        originalSql: statement.originalSql,
        postgresSql: statement.postgresSql,
        riskTypes: statement.risks.map((risk) => risk.type),
      })
      return response.data.data
    },
  })
}

function useSkills() {
  return useQuery({
    queryKey: ['skills'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<SkillDefinition[]>>('/api/skills')
      return response.data.data
    },
    retry: false,
  })
}

function useMcpStatus() {
  return useQuery({
    queryKey: ['mcp-status'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<McpStatus>>('/api/mcp/status')
      return response.data.data
    },
    retry: false,
  })
}

function useMcpResources() {
  return useQuery({
    queryKey: ['mcp-resources'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<McpResource[]>>('/api/mcp/resources')
      return response.data.data
    },
    retry: false,
  })
}

function useMcpPrompts() {
  return useQuery({
    queryKey: ['mcp-prompts'],
    queryFn: async () => {
      const response = await axios.get<ApiResponse<McpPrompt[]>>('/api/mcp/prompts')
      return response.data.data
    },
    retry: false,
  })
}

function useRunAgent() {
  return useMutation({
    mutationFn: async (payload: { type: AgentRun['type']; objective: string; sql: string }) => {
      const response = await axios.post<ApiResponse<AgentRun>>('/api/agents/run', payload)
      return response.data.data
    },
  })
}

function useMcpSkillDryRun() {
  return useMutation({
    mutationFn: async (payload: { skillId: string; sql: string }) => {
      const response = await axios.post<ApiResponse<McpToolCallRecord>>('/api/mcp/tools/call', {
        toolName: 'skill.run',
        arguments: payload,
      })
      return response.data.data
    },
  })
}

function useCreateMigrationPlan() {
  return useMutation({
    mutationFn: async (payload: { snapshotId: string; targetDataSourceId: string }) => {
      const response = await axios.post<ApiResponse<MigrationPlan>>('/api/migration-plans', payload)
      return response.data.data
    },
  })
}

function useExecuteMigrationPlan() {
  return useMutation({
    mutationFn: async (planId: string) => {
      const response = await axios.post<ApiResponse<MigrationPlan>>(`/api/migration-plans/${planId}/execute`)
      return response.data.data
    },
  })
}

function App() {
  const health = useBackendHealth()
  const localLlmStatus = useLocalLlmStatus()
  const knowledgeMetrics = useKnowledgeMetrics()
  const aiProviderConfigs = useAiProviderConfigs()
  const projects = useProjects()
  const dataSources = useDataSources()
  const createDataSource = useCreateDataSource()
  const testDataSource = useTestDataSource()
  const analyzeSql = useAnalyzeSql()
  const saveSnapshot = useSaveWorkbenchSnapshot()
  const generatePrecheck = useGeneratePrecheck()
  const explainRisk = useExplainRisk()
  const suggestSqlRewrite = useSuggestSqlRewrite()
  const saveAiSuggestion = useSaveAiSuggestion()
  const acceptAiSuggestion = useReviewAiSuggestion('accept')
  const ignoreAiSuggestion = useReviewAiSuggestion('ignore')
  const submitReview = useReviewAction('submit')
  const approveReview = useReviewAction('approve')
  const exportSqlPackage = useExportSqlPackage()
  const editTargetSql = useEditTargetSql()
  const restoreGeneratedSql = useRestoreGeneratedSql()
  const uploadSqlFile = useUploadSqlFile()
  const saveFileImportSnapshot = useSaveFileImportSnapshot()
  const skills = useSkills()
  const mcpStatus = useMcpStatus()
  const mcpResources = useMcpResources()
  const mcpPrompts = useMcpPrompts()
  const runAgent = useRunAgent()
  const mcpSkillDryRun = useMcpSkillDryRun()
  const createMigrationPlan = useCreateMigrationPlan()
  const executeMigrationPlan = useExecuteMigrationPlan()
  const [manualSql, setManualSql] = useState(sampleSql)
  const [workbenchSnapshot, setWorkbenchSnapshot] = useState<WorkbenchSnapshot | null>(null)
  const [migrationPlan, setMigrationPlan] = useState<MigrationPlan | null>(null)
  const [fileImportJobId, setFileImportJobId] = useState<string>()
  const [selectedStatementIndex, setSelectedStatementIndex] = useState<number>()
  const [objectTypeFilter, setObjectTypeFilter] = useState('ALL')
  const [riskLevelFilter, setRiskLevelFilter] = useState('ALL')
  const [targetSqlDraftByStatement, setTargetSqlDraftByStatement] = useState<Record<number, string>>({})
  const [pushedFileImportJob, setPushedFileImportJob] = useState<FileImportJob | null>(null)
  const [dataSourceDraft, setDataSourceDraft] = useState({
    name: 'oracle-source',
    kind: 'ORACLE' as DataSourceConfig['kind'],
    jdbcUrl: 'jdbc:oracle:thin:@localhost:1521/FREEPDB1',
    username: 'system',
    password: '',
  })
  const fileImportJob = useFileImportJob(fileImportJobId)
  const visibleFileImportJob = fileImportJobId ? (pushedFileImportJob ?? fileImportJob.data) : undefined
  const activeAnalysis = analyzeSql.data ?? visibleFileImportJob?.analysis ?? null
  const filteredStatements = useMemo(() => {
    const statements = activeAnalysis?.statements ?? []
    return statements.filter((statement) => {
      const objectMatches = objectTypeFilter === 'ALL' || statement.objectType === objectTypeFilter
      const riskMatches = riskLevelFilter === 'ALL' || statement.riskLevel === riskLevelFilter
      return objectMatches && riskMatches
    })
  }, [activeAnalysis, objectTypeFilter, riskLevelFilter])
  const selectedStatement = useMemo(() => {
    if (!activeAnalysis) {
      return undefined
    }
    return activeAnalysis.statements.find((statement) => statement.index === selectedStatementIndex) ?? filteredStatements[0]
  }, [activeAnalysis, filteredStatements, selectedStatementIndex])
  const latestTargetSql = useMemo(() => {
    if (!selectedStatement) {
      return ''
    }
    const latestVersion = [...(workbenchSnapshot?.sqlVersions ?? [])]
      .reverse()
      .find((version) => version.statementIndex === selectedStatement.index)
    return latestVersion?.sql ?? selectedStatement.postgresSql
  }, [selectedStatement, workbenchSnapshot])
  const targetSqlDraft = selectedStatement ? (targetSqlDraftByStatement[selectedStatement.index] ?? latestTargetSql) : ''
  const selectedSavedSuggestion = workbenchSnapshot?.aiSuggestions.find((suggestion) => suggestion.statementIndex === selectedStatement?.index)
  const objectTypeOptions = useMemo(() => {
    const values = Array.from(new Set((activeAnalysis?.statements ?? []).map((statement) => statement.objectType))).sort()
    return [{ value: 'ALL', label: '全部对象' }, ...values.map((value) => ({ value, label: value }))]
  }, [activeAnalysis])
  const riskLevelOptions = [
    { value: 'ALL', label: '全部风险' },
    { value: 'LOW', label: 'LOW' },
    { value: 'MEDIUM', label: 'MEDIUM' },
    { value: 'HIGH', label: 'HIGH' },
    { value: 'BLOCKER', label: 'BLOCKER' },
  ]
  const postgresTargets = (dataSources.data ?? []).filter((item) => item.kind === 'POSTGRESQL')
  const cloudProviderEnabled = (aiProviderConfigs.data ?? []).some((config) => config.type.includes('CLOUD') && config.enabled)

  useEffect(() => {
    if (!fileImportJobId) {
      return
    }
    const events = new EventSource(`/api/file-import/jobs/${fileImportJobId}/events`)
    events.addEventListener('file-import-progress', (event) => {
      setPushedFileImportJob(JSON.parse((event as MessageEvent).data) as FileImportJob)
    })
    events.onerror = () => events.close()
    return () => events.close()
  }, [fileImportJobId])

  return (
    <ConfigProvider
      theme={{
        token: {
          borderRadius: 6,
          colorPrimary: '#1677ff',
          fontFamily: 'Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        },
      }}
    >
      <Layout className="app-shell">
        <Layout.Sider className="sidebar" width={248}>
          <div className="brand">
            <DatabaseOutlined />
            <span>SchemaPilot</span>
          </div>
          <Menu
            theme="dark"
            mode="inline"
            defaultSelectedKeys={['dashboard']}
            items={[
              { key: 'dashboard', icon: <FileSearchOutlined />, label: '项目总览' },
              { key: 'imports', icon: <CloudUploadOutlined />, label: '输入源' },
              { key: 'inventory', icon: <DatabaseOutlined />, label: '对象清单' },
              { key: 'workbench', icon: <CodeOutlined />, label: '转换工作台' },
              { key: 'ai', icon: <RobotOutlined />, label: 'AI 副驾驶' },
              { key: 'review', icon: <AuditOutlined />, label: '审核中心' },
              { key: 'execution', icon: <BranchesOutlined />, label: '迁移计划' },
            ]}
          />
        </Layout.Sider>

        <Layout>
          <Layout.Header className="topbar">
            <Space direction="vertical" size={0}>
              <Typography.Title level={4}>Oracle 到 PostgreSQL 迁移项目</Typography.Title>
              <Typography.Text type="secondary">P0 目标：输入、识别、转换、AI 建议、报告、审核、SQL 包导出闭环</Typography.Text>
            </Space>
            <Space>
              <Badge status={health.data?.status === 'UP' ? 'success' : 'error'} text={health.data?.status ?? '未连接'} />
              <Button icon={<SafetyCertificateOutlined />}>审核门禁</Button>
            </Space>
          </Layout.Header>

          <Layout.Content className="content">
            <section className="summary-band">
              <StatisticCard
                statistic={{
                  title: '闭环完成度',
                  value: generatePrecheck.data ? 28 : 20,
                  suffix: '%',
                }}
                chart={<Progress percent={generatePrecheck.data ? 28 : 20} strokeColor="#1677ff" showInfo={false} />}
              />
              <StatisticCard
                statistic={{
                  title: '兼容性评分',
                  value: generatePrecheck.data?.analysis.compatibilityScore ?? analyzeSql.data?.compatibilityScore ?? '未计算',
                }}
              />
              <StatisticCard
                statistic={{
                  title: '报告状态',
                  value: generatePrecheck.data ? '已生成快照' : '未生成',
                }}
              />
              <StatisticCard
                statistic={{
                  title: 'AI / RAG',
                  value: mcpStatus.data ? '已接入' : '待连接',
                }}
              />
            </section>

            <Alert
              className="status-alert"
              type={health.data?.status === 'UP' ? 'success' : 'warning'}
              showIcon
              icon={<CheckCircleOutlined />}
              message={health.data?.status === 'UP' ? '后端 API 已连接' : '等待后端 API'}
              description={
                health.data
                  ? `运行服务 ${health.data.service}，Java ${health.data.javaVersion}，虚拟线程配置已启用。`
                  : '启动后端后，前端会通过 /api/health 自动刷新连接状态。'
              }
            />

            <section className="workspace-grid">
              <div className="panel wide">
                <div className="panel-header">
                  <Space>
                    <CodeOutlined />
                    <Typography.Title level={5}>手工 SQL 分析</Typography.Title>
                  </Space>
                  <Space>
                    <Button
                      loading={saveSnapshot.isPending}
                      onClick={() => saveSnapshot.mutate(manualSql, { onSuccess: setWorkbenchSnapshot })}
                    >
                      保存快照
                    </Button>
                    <Button loading={generatePrecheck.isPending} onClick={() => generatePrecheck.mutate(manualSql)}>
                      生成报告
                    </Button>
                    <Button type="primary" loading={analyzeSql.isPending} onClick={() => analyzeSql.mutate(manualSql)}>
                      分析 SQL
                    </Button>
                  </Space>
                </div>
                <Input.TextArea
                  className="sql-input"
                  value={manualSql}
                  onChange={(event) => setManualSql(event.target.value)}
                  spellCheck={false}
                  autoSize={{ minRows: 8, maxRows: 14 }}
                />
                {analyzeSql.isError && (
                  <Alert className="inline-alert" type="error" showIcon message="SQL 分析失败" description={String(analyzeSql.error)} />
                )}
                {analyzeSql.data && (
                  <div className="analysis-result">
                    <Space className="analysis-metrics">
                      <Tag color="blue">语句 {activeAnalysis?.statementCount}</Tag>
                      <Tag color={(activeAnalysis?.riskCount ?? 0) > 0 ? 'orange' : 'green'}>风险 {activeAnalysis?.riskCount}</Tag>
                      <Tag color={(activeAnalysis?.compatibilityScore ?? 0) >= 80 ? 'green' : (activeAnalysis?.compatibilityScore ?? 0) >= 60 ? 'gold' : 'red'}>
                        兼容 {activeAnalysis?.compatibilityScore}
                      </Tag>
                      <Select value={objectTypeFilter} options={objectTypeOptions} onChange={setObjectTypeFilter} size="small" className="filter-select" />
                      <Select value={riskLevelFilter} options={riskLevelOptions} onChange={setRiskLevelFilter} size="small" className="filter-select" />
                    </Space>
                    <Table
                      columns={statementColumns}
                      dataSource={filteredStatements}
                      pagination={false}
                      rowKey="index"
                      size="small"
                      rowClassName={(row) => row.index === selectedStatement?.index ? 'selected-row' : ''}
                      onRow={(row) => ({
                        onClick: () => setSelectedStatementIndex(row.index),
                      })}
                    />
                    {selectedStatement && (
                      <div className="workbench-panel">
                        <div className="workbench-toolbar">
                          <Space wrap>
                            <Typography.Text strong>{selectedStatement.objectType} {selectedStatement.objectName}</Typography.Text>
                            <Tag color={selectedStatement.conversionLevel === 'AUTO' ? 'green' : 'gold'}>{selectedStatement.conversionLevel}</Tag>
                            <Tag color={selectedStatement.riskLevel === 'BLOCKER' ? 'red' : selectedStatement.riskLevel === 'HIGH' ? 'orange' : 'blue'}>
                              {selectedStatement.riskLevel}
                            </Tag>
                          </Space>
                          <Space>
                            <Button
                              size="small"
                              disabled={!workbenchSnapshot}
                              loading={restoreGeneratedSql.isPending}
                              onClick={() => {
                                if (workbenchSnapshot) {
                                  restoreGeneratedSql.mutate(
                                    { snapshotId: workbenchSnapshot.id, statementIndex: selectedStatement.index },
                                    {
                                      onSuccess: (snapshot) => {
                                        setWorkbenchSnapshot(snapshot)
                                        setTargetSqlDraftByStatement((current) => {
                                          const next = { ...current }
                                          delete next[selectedStatement.index]
                                          return next
                                        })
                                      },
                                    },
                                  )
                                }
                              }}
                            >
                              恢复规则版本
                            </Button>
                            <Button
                              size="small"
                              type="primary"
                              disabled={!workbenchSnapshot}
                              loading={editTargetSql.isPending}
                              onClick={() => {
                                if (workbenchSnapshot) {
                                  editTargetSql.mutate(
                                    { snapshotId: workbenchSnapshot.id, statementIndex: selectedStatement.index, targetSql: targetSqlDraft },
                                    { onSuccess: setWorkbenchSnapshot },
                                  )
                                }
                              }}
                            >
                              保存编辑
                            </Button>
                          </Space>
                        </div>
                        <div className="sql-preview-grid">
                          <div className="editor-pane">
                            <Typography.Text type="secondary">Oracle 原始 SQL</Typography.Text>
                            <Editor
                              height="260px"
                              defaultLanguage="sql"
                              value={selectedStatement.originalSql}
                              options={{ readOnly: true, minimap: { enabled: false }, fontSize: 13, wordWrap: 'on' }}
                            />
                          </div>
                          <div className="editor-pane">
                            <Typography.Text type="secondary">PostgreSQL 目标 SQL</Typography.Text>
                            <Editor
                              height="260px"
                              defaultLanguage="sql"
                              value={targetSqlDraft}
                              onChange={(value) => {
                                setTargetSqlDraftByStatement((current) => ({
                                  ...current,
                                  [selectedStatement.index]: value ?? '',
                                }))
                              }}
                              options={{ minimap: { enabled: false }, fontSize: 13, wordWrap: 'on' }}
                            />
                          </div>
                        </div>
                        {(selectedStatement.risks.length > 0 || selectedStatement.parseIssues.length > 0) && (
                          <div className="risk-strip">
                            {selectedStatement.risks.map((risk) => (
                              <Tag key={risk.type} color={risk.level === 'BLOCKER' ? 'red' : risk.level === 'HIGH' ? 'orange' : 'gold'}>{risk.type}</Tag>
                            ))}
                            {selectedStatement.parseIssues.map((issue) => (
                              <Tag key={issue.type} color="red">{issue.type}</Tag>
                            ))}
                          </div>
                        )}
                        <div className="ai-workbench">
                          <Space className="preview-title">
                            <Typography.Text type="secondary">AI 建议</Typography.Text>
                            <Space>
                              {selectedStatement.risks.length > 0 && (
                                <Button size="small" loading={explainRisk.isPending} onClick={() => explainRisk.mutate(selectedStatement)}>
                                  解释首个风险
                                </Button>
                              )}
                              <Button size="small" loading={suggestSqlRewrite.isPending} onClick={() => suggestSqlRewrite.mutate(selectedStatement)}>
                                改造建议
                              </Button>
                            </Space>
                          </Space>
                          <pre>{selectedStatement.aiSuggestion}</pre>
                          {explainRisk.data && (
                            <div className="ai-answer">
                              <Typography.Text strong>{explainRisk.data.provider} / {explainRisk.data.model}</Typography.Text>
                              <p>{explainRisk.data.suggestion}</p>
                              <Typography.Text type="secondary">证据：{explainRisk.data.evidence.join(' | ')}</Typography.Text>
                            </div>
                          )}
                          {suggestSqlRewrite.data && (
                            <div className="ai-answer">
                              <Typography.Text strong>
                                {suggestSqlRewrite.data.provider} / {suggestSqlRewrite.data.model}
                              </Typography.Text>
                              <p>{suggestSqlRewrite.data.suggestion}</p>
                              <Typography.Text type="secondary">证据：{suggestSqlRewrite.data.evidence.join(' | ')}</Typography.Text>
                              <div className="ai-actions">
                                <Button
                                  size="small"
                                  disabled={!workbenchSnapshot}
                                  loading={saveAiSuggestion.isPending}
                                  onClick={() => {
                                    if (workbenchSnapshot && selectedStatement) {
                                      saveAiSuggestion.mutate(
                                        { snapshotId: workbenchSnapshot.id, statementIndex: selectedStatement.index, draft: suggestSqlRewrite.data },
                                        { onSuccess: setWorkbenchSnapshot },
                                      )
                                    }
                                  }}
                                >
                                  保存建议
                                </Button>
                              </div>
                            </div>
                          )}
                          {selectedSavedSuggestion && (
                            <div className="ai-answer">
                              <Space className="preview-title">
                                <Typography.Text strong>已保存建议：{selectedSavedSuggestion.status}</Typography.Text>
                                <Space>
                                  <Button
                                    size="small"
                                    disabled={selectedSavedSuggestion.status !== 'GENERATED'}
                                    loading={acceptAiSuggestion.isPending}
                                    onClick={() => acceptAiSuggestion.mutate(selectedSavedSuggestion.id, { onSuccess: setWorkbenchSnapshot })}
                                  >
                                    接受
                                  </Button>
                                  <Button
                                    size="small"
                                    disabled={selectedSavedSuggestion.status !== 'GENERATED'}
                                    loading={ignoreAiSuggestion.isPending}
                                    onClick={() => ignoreAiSuggestion.mutate(selectedSavedSuggestion.id, { onSuccess: setWorkbenchSnapshot })}
                                  >
                                    忽略
                                  </Button>
                                </Space>
                              </Space>
                              <Typography.Text type="secondary">输入 hash：{selectedSavedSuggestion.inputHash.slice(0, 12)}...</Typography.Text>
                            </div>
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>

              <div className="panel">
                <div className="panel-header">
                  <Space>
                    <CloudUploadOutlined />
                    <Typography.Title level={5}>SQL 文件导入</Typography.Title>
                  </Space>
                  <Upload
                    accept=".sql,.txt"
                    showUploadList={false}
                    beforeUpload={(file) => {
                      uploadSqlFile.mutate(file as File, {
                        onSuccess: (job) => {
                          setPushedFileImportJob(null)
                          setFileImportJobId(job.id)
                        },
                      })
                      return false
                    }}
                  >
                    <Button loading={uploadSqlFile.isPending}>上传</Button>
                  </Upload>
                </div>
                {uploadSqlFile.isError && (
                  <Alert className="inline-alert" type="error" showIcon message="文件上传失败" description={String(uploadSqlFile.error)} />
                )}
                {visibleFileImportJob && (
                  <div className="file-import">
                    <Space className="analysis-metrics" wrap>
                      <Tag color="blue">{visibleFileImportJob.fileName}</Tag>
                      <Tag>{visibleFileImportJob.encoding}</Tag>
                      <Tag>{visibleFileImportJob.sizeBytes} bytes</Tag>
                      <Tag color={visibleFileImportJob.status === 'COMPLETED' ? 'green' : visibleFileImportJob.status === 'FAILED' ? 'red' : 'gold'}>
                        {visibleFileImportJob.status}
                      </Tag>
                    </Space>
                    <Progress percent={visibleFileImportJob.progressPercent} />
                    <Typography.Text type="secondary">checksum {visibleFileImportJob.checksumSha256.slice(0, 20)}...</Typography.Text>
                    <div className="agent-actions">
                      <Button
                        size="small"
                        disabled={visibleFileImportJob.status !== 'COMPLETED'}
                        loading={saveFileImportSnapshot.isPending}
                        onClick={() => saveFileImportSnapshot.mutate(visibleFileImportJob.id, { onSuccess: setWorkbenchSnapshot })}
                      >
                        保存为快照
                      </Button>
                    </div>
                    {visibleFileImportJob.errorMessage && (
                      <Alert className="inline-alert" type="error" showIcon message={visibleFileImportJob.errorMessage} />
                    )}
                    {visibleFileImportJob.analysis && (
                      <Table
                        className="report-table"
                        columns={statementColumns}
                        dataSource={visibleFileImportJob.analysis.statements}
                        pagination={false}
                        rowKey="index"
                        size="small"
                      />
                    )}
                  </div>
                )}
              </div>

              {workbenchSnapshot && (
                <div className="panel">
                  <div className="panel-header">
                    <Space>
                      <AuditOutlined />
                      <Typography.Title level={5}>版本链</Typography.Title>
                    </Space>
                    <Tag color={workbenchSnapshot.reportStatus === 'EXPIRED' ? 'red' : 'blue'}>{workbenchSnapshot.reportStatus}</Tag>
                  </div>
                  <div className="version-summary">
                    <Tag>SQL 版本 {workbenchSnapshot.sqlVersions.length}</Tag>
                    <Tag>AI 建议 {workbenchSnapshot.aiSuggestions.length}</Tag>
                    <Tag>审核 {workbenchSnapshot.reviewRecords.length}</Tag>
                    <Tag color={workbenchSnapshot.baselineFrozen ? 'green' : 'default'}>基线 {workbenchSnapshot.baselineStatus}</Tag>
                  </div>
                  <Space className="review-actions">
                    <Button
                      size="small"
                      loading={submitReview.isPending}
                      disabled={workbenchSnapshot.reportStatus !== 'DRAFT'}
                      onClick={() => submitReview.mutate(workbenchSnapshot.id, { onSuccess: setWorkbenchSnapshot })}
                    >
                      提交审核
                    </Button>
                    <Button
                      size="small"
                      loading={approveReview.isPending}
                      disabled={workbenchSnapshot.reportStatus !== 'READY_FOR_REVIEW'}
                      onClick={() => approveReview.mutate(workbenchSnapshot.id, { onSuccess: setWorkbenchSnapshot })}
                    >
                      审核通过
                    </Button>
                    <Button
                      size="small"
                      icon={<DownloadOutlined />}
                      loading={exportSqlPackage.isPending}
                      disabled={!workbenchSnapshot.baselineFrozen}
                      onClick={() => exportSqlPackage.mutate(workbenchSnapshot.id)}
                    >
                      导出 SQL
                    </Button>
                  </Space>
                  {exportSqlPackage.data && (
                    <div className="export-preview">
                      <Typography.Text strong>{exportSqlPackage.data.fileName}</Typography.Text>
                      <pre>{exportSqlPackage.data.content}</pre>
                    </div>
                  )}
                  {workbenchSnapshot.reviewRecords.length > 0 && (
                    <div className="review-list">
                      <Typography.Text type="secondary">审核记录</Typography.Text>
                      <ul>
                        {workbenchSnapshot.reviewRecords.map((record) => (
                          <li key={record.id}>
                            {record.decision} / {record.reviewer}: {record.comment || '无备注'}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}
                  <ul className="audit-list">
                    {workbenchSnapshot.auditEvents.slice(-4).map((event) => (
                      <li key={event.id}>{event.action}: {event.message}</li>
                    ))}
                  </ul>
                </div>
              )}

              {generatePrecheck.data && (
                <div className="panel wide">
                  <div className="panel-header">
                    <Space>
                      <FileSearchOutlined />
                      <Typography.Title level={5}>预处理报告</Typography.Title>
                    </Space>
                    <Tag color="blue">{generatePrecheck.data.reportVersion}</Tag>
                  </div>
                  <div className="report-grid">
                    <div>
                      <Typography.Text type="secondary">管理摘要</Typography.Text>
                      <p>{generatePrecheck.data.managementSummary}</p>
                    </div>
                    <div>
                      <Typography.Text type="secondary">开发摘要</Typography.Text>
                      <p>{generatePrecheck.data.developerSummary}</p>
                    </div>
                    <div>
                      <Typography.Text type="secondary">对象分布</Typography.Text>
                      <div className="tag-list">
                        {Object.entries(generatePrecheck.data.objectTypeDistribution).map(([key, value]) => (
                          <Tag key={key}>{key} {value}</Tag>
                        ))}
                      </div>
                    </div>
                    <div>
                      <Typography.Text type="secondary">风险分布</Typography.Text>
                      <div className="tag-list">
                        {Object.entries(generatePrecheck.data.riskDistribution).map(([key, value]) => (
                          <Tag key={key} color={key === 'BLOCKER' ? 'red' : key === 'HIGH' ? 'orange' : 'gold'}>{key} {value}</Tag>
                        ))}
                      </div>
                    </div>
                  </div>
                  <Table
                    className="report-table"
                    columns={highRiskColumns}
                    dataSource={generatePrecheck.data.highRiskObjects}
                    pagination={false}
                    rowKey={(row) => `${row.statementIndex}-${row.objectName}`}
                    size="small"
                  />
                  <div className="report-lists">
                    <div>
                      <Typography.Text type="secondary">类型映射</Typography.Text>
                      <ul>
                        {generatePrecheck.data.typeMappings.map((item) => (
                          <li key={`${item.statementIndex}-${item.sourceType}`}>{item.objectName}: {item.sourceType} → {item.targetType}</li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <Typography.Text type="secondary">处理建议</Typography.Text>
                      <ul>
                        {generatePrecheck.data.handlingRecommendations.map((item) => (
                          <li key={item}>{item}</li>
                        ))}
                      </ul>
                    </div>
                    <div>
                      <Typography.Text type="secondary">迁移顺序草案</Typography.Text>
                      <ol>
                        {generatePrecheck.data.migrationOrderDraft.map((item) => (
                          <li key={item}>{item}</li>
                        ))}
                      </ol>
                    </div>
                  </div>
                </div>
              )}

              <div className="panel wide">
                <div className="panel-header">
                  <Space>
                    <ApiOutlined />
                    <Typography.Title level={5}>P0 闭环流水线</Typography.Title>
                  </Space>
                  <Button type="primary">新建输入源</Button>
                </div>
                <Table columns={pipelineColumns} dataSource={pipeline} pagination={false} size="middle" />
              </div>

              <div className="panel">
                <div className="panel-header">
                  <Space>
                    <DatabaseOutlined />
                    <Typography.Title level={5}>迁移项目</Typography.Title>
                  </Space>
                  <Button icon={<PlusOutlined />}>新建</Button>
                </div>
                <Table
                  columns={projectColumns}
                  dataSource={projects.data ?? []}
                  loading={projects.isLoading}
                  locale={{
                    emptyText: projects.isError ? '元数据库未连接或项目表未迁移' : '暂无项目',
                  }}
                  pagination={false}
                  rowKey="id"
                  size="small"
                />
              </div>

              <div className="panel">
                <div className="panel-header">
                  <Space>
                    <DatabaseOutlined />
                    <Typography.Title level={5}>数据源管理</Typography.Title>
                  </Space>
                  <Button
                    size="small"
                    loading={createDataSource.isPending}
                    onClick={() => createDataSource.mutate(dataSourceDraft, { onSuccess: () => dataSources.refetch() })}
                  >
                    保存
                  </Button>
                </div>
                <div className="datasource-form">
                  <Input
                    value={dataSourceDraft.name}
                    onChange={(event) => setDataSourceDraft((current) => ({ ...current, name: event.target.value }))}
                    placeholder="名称"
                  />
                  <Select
                    value={dataSourceDraft.kind}
                    options={[
                      { value: 'ORACLE', label: 'Oracle' },
                      { value: 'POSTGRESQL', label: 'PostgreSQL' },
                    ]}
                    onChange={(kind) => setDataSourceDraft((current) => ({
                      ...current,
                      kind,
                      jdbcUrl: kind === 'ORACLE' ? 'jdbc:oracle:thin:@localhost:1521/FREEPDB1' : 'jdbc:postgresql://localhost:5432/schemapilot',
                    }))}
                  />
                  <Input
                    value={dataSourceDraft.jdbcUrl}
                    onChange={(event) => setDataSourceDraft((current) => ({ ...current, jdbcUrl: event.target.value }))}
                    placeholder="JDBC URL"
                  />
                  <Input
                    value={dataSourceDraft.username}
                    onChange={(event) => setDataSourceDraft((current) => ({ ...current, username: event.target.value }))}
                    placeholder="用户名"
                  />
                  <Input.Password
                    value={dataSourceDraft.password}
                    onChange={(event) => setDataSourceDraft((current) => ({ ...current, password: event.target.value }))}
                    placeholder="密码"
                  />
                </div>
                {createDataSource.isError && (
                  <Alert className="inline-alert" type="error" showIcon message="保存数据源失败" description={String(createDataSource.error)} />
                )}
                <div className="datasource-list">
                  {(dataSources.data ?? []).map((item) => (
                    <div key={item.id} className="datasource-row">
                      <Space wrap>
                        <Typography.Text strong>{item.name}</Typography.Text>
                        <Tag color={item.kind === 'ORACLE' ? 'orange' : 'blue'}>{item.kind}</Tag>
                        <Tag color={item.status === 'TESTED' ? 'green' : item.status === 'FAILED' ? 'red' : 'default'}>{item.status}</Tag>
                        <Tag>{item.passwordConfigured ? '密码已加密保存' : '无密码'}</Tag>
                      </Space>
                      <Typography.Text type="secondary">{item.jdbcUrl} / {item.username}</Typography.Text>
                      <Button size="small" loading={testDataSource.isPending} onClick={() => testDataSource.mutate(item.id, { onSuccess: () => dataSources.refetch() })}>
                        测试连接
                      </Button>
                    </div>
                  ))}
                </div>
                {testDataSource.data && (
                  <Alert
                    className="inline-alert"
                    type={testDataSource.data.success ? 'success' : 'warning'}
                    showIcon
                    message={testDataSource.data.message}
                    description={[...testDataSource.data.permissions, ...testDataSource.data.risks].join(' / ')}
                  />
                )}
              </div>

              <div className="panel">
                <div className="panel-header">
                  <Space>
                    <RobotOutlined />
                    <Typography.Title level={5}>Agent / MCP / Skills</Typography.Title>
                  </Space>
                  <Tag color={mcpStatus.data?.externalClientEnabled ? 'orange' : 'green'}>
                    MCP Client {mcpStatus.data?.externalClientEnabled ? '开启' : '关闭'}
                  </Tag>
                </div>
                <Space className="analysis-metrics" wrap>
                  <Tag color={mcpStatus.data?.writeToolsDefaultDryRun ? 'green' : 'red'}>写工具默认 dry-run</Tag>
                  <Tag>审计 {mcpStatus.data?.auditRecordCount ?? 0}</Tag>
                  {(mcpStatus.data?.allowedTools ?? []).map((tool) => (
                    <Tag key={tool} color="blue">{tool}</Tag>
                  ))}
                </Space>
                <div className="agent-actions">
                  <Button
                    size="small"
                    loading={runAgent.isPending}
                    onClick={() => runAgent.mutate({ type: 'ASSESSMENT', objective: '预处理风险评估', sql: manualSql })}
                  >
                    运行评估 Agent
                  </Button>
                  <Button
                    size="small"
                    loading={runAgent.isPending}
                    onClick={() => runAgent.mutate({ type: 'CONVERSION', objective: '生成转换草稿', sql: manualSql })}
                  >
                    运行转换 Agent
                  </Button>
                  <Button
                    size="small"
                    loading={mcpSkillDryRun.isPending}
                    onClick={() => mcpSkillDryRun.mutate({ skillId: 'oracle-table-ddl', sql: manualSql })}
                  >
                    MCP Skill dry-run
                  </Button>
                </div>
                {skills.data && (
                  <div className="skill-list">
                    <Typography.Text type="secondary">内置 Skills</Typography.Text>
                    {skills.data.map((skill) => (
                      <div key={skill.id} className="skill-row">
                        <Space wrap>
                          <Typography.Text strong>{skill.id}</Typography.Text>
                          <Tag>{skill.version}</Tag>
                          <Tag color={skill.requiresReview ? 'gold' : 'green'}>{skill.requiresReview ? '需审核' : '自动草稿'}</Tag>
                        </Space>
                        <Typography.Text type="secondary">{skill.allowedTools.join(', ')}</Typography.Text>
                      </div>
                    ))}
                  </div>
                )}
                {runAgent.data && (
                  <div className="agent-run">
                    <Space className="preview-title">
                      <Typography.Text strong>{runAgent.data.type} / {runAgent.data.status}</Typography.Text>
                      <Tag>{runAgent.data.steps.length} steps</Tag>
                    </Space>
                    <ul>
                      {runAgent.data.steps.slice(-5).map((step) => (
                        <li key={step.id}>{step.sequence}. {step.status} {step.toolName ? `/ ${step.toolName}` : ''} - {step.action}</li>
                      ))}
                    </ul>
                  </div>
                )}
                {mcpSkillDryRun.data && (
                  <Alert
                    className="inline-alert"
                    type={mcpSkillDryRun.data.success ? 'success' : 'warning'}
                    showIcon
                    message={`MCP ${mcpSkillDryRun.data.toolName}: ${mcpSkillDryRun.data.message}`}
                    description={mcpSkillDryRun.data.usedDefaultDryRun ? '本次调用使用默认 dry-run，没有执行写入。' : undefined}
                  />
                )}
                <div className="mcp-assets">
                  <Typography.Text type="secondary">Resources</Typography.Text>
                  <div className="tag-list">
                    {(mcpResources.data ?? []).map((resource) => <Tag key={resource.id}>{resource.id}</Tag>)}
                  </div>
                  <Typography.Text type="secondary">Prompts</Typography.Text>
                  <div className="tag-list">
                    {(mcpPrompts.data ?? []).map((prompt) => <Tag key={prompt.id}>{prompt.id}</Tag>)}
                  </div>
                </div>
              </div>

              <div className="panel">
                <div className="panel-header">
                  <Space>
                    <BranchesOutlined />
                    <Typography.Title level={5}>迁移计划</Typography.Title>
                  </Space>
                  <Tag color={migrationPlan?.status === 'FAILED' ? 'red' : migrationPlan?.status === 'COMPLETED' ? 'green' : 'blue'}>
                    {migrationPlan?.status ?? '未生成'}
                  </Tag>
                </div>
                <Space className="agent-actions">
                  <Button
                    size="small"
                    disabled={!workbenchSnapshot?.baselineFrozen || postgresTargets.length === 0}
                    loading={createMigrationPlan.isPending}
                    onClick={() => {
                      if (workbenchSnapshot && postgresTargets[0]) {
                        createMigrationPlan.mutate(
                          { snapshotId: workbenchSnapshot.id, targetDataSourceId: postgresTargets[0].id },
                          { onSuccess: setMigrationPlan },
                        )
                      }
                    }}
                  >
                    生成计划
                  </Button>
                  <Button
                    size="small"
                    disabled={!migrationPlan}
                    loading={executeMigrationPlan.isPending}
                    onClick={() => {
                      if (migrationPlan) {
                        executeMigrationPlan.mutate(migrationPlan.id, { onSuccess: setMigrationPlan })
                      }
                    }}
                  >
                    执行 DDL
                  </Button>
                </Space>
                {postgresTargets.length === 0 && (
                  <Alert className="inline-alert" type="warning" showIcon message="需要先保存一个 PostgreSQL 目标数据源" />
                )}
                {migrationPlan && (
                  <div className="datasource-list">
                    {migrationPlan.steps.map((step) => (
                      <div key={step.id} className="datasource-row">
                        <Space wrap>
                          <Typography.Text strong>{step.sequence}. {step.objectType} {step.objectName}</Typography.Text>
                          <Tag color={step.status === 'FAILED' ? 'red' : step.status === 'COMPLETED' ? 'green' : 'default'}>{step.status}</Tag>
                          <Tag>attempts {step.attempts}</Tag>
                        </Space>
                        {step.failureReason && <Typography.Text type="secondary">{step.failureReason}</Typography.Text>}
                        {step.workItem && <Tag color="red">{step.workItem}</Tag>}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="panel">
                <div className="panel-header">
                  <Space>
                    <RobotOutlined />
                    <Typography.Title level={5}>AI 副驾驶边界</Typography.Title>
                  </Space>
                </div>
                <div className="ai-status-grid">
                  <div className="status-line">
                    <Typography.Text type="secondary">Local LLM</Typography.Text>
                    <Badge status={localLlmStatus.data?.reachable ? 'success' : 'error'} text={localLlmStatus.data?.reachable ? 'reachable' : 'offline'} />
                  </div>
                  <div className="status-line">
                    <Typography.Text type="secondary">Endpoint</Typography.Text>
                    <Typography.Text ellipsis>{localLlmStatus.data?.endpoint ?? 'unknown'}</Typography.Text>
                  </div>
                  <div className="status-line">
                    <Typography.Text type="secondary">Model</Typography.Text>
                    <Typography.Text>{localLlmStatus.data?.model ?? 'unknown'}</Typography.Text>
                  </div>
                  <div className="status-line">
                    <Typography.Text type="secondary">Discovered</Typography.Text>
                    <Tag>{localLlmStatus.data?.discoveredModels.length ?? 0}</Tag>
                  </div>
                  <div className="status-line">
                    <Typography.Text type="secondary">RAG hit rate</Typography.Text>
                    <Tag color={(knowledgeMetrics.data?.hitRate ?? 0) > 0 ? 'green' : 'default'}>
                      {Math.round((knowledgeMetrics.data?.hitRate ?? 0) * 100)}%
                    </Tag>
                  </div>
                  <div className="status-line">
                    <Typography.Text type="secondary">Fallbacks</Typography.Text>
                    <Tag color={(localLlmStatus.data?.fallbackCount ?? 0) > 0 ? 'gold' : 'green'}>{localLlmStatus.data?.fallbackCount ?? 0}</Tag>
                  </div>
                  <div className="status-line">
                    <Typography.Text type="secondary">Cloud provider</Typography.Text>
                    <Tag color={cloudProviderEnabled ? 'orange' : 'green'}>{cloudProviderEnabled ? 'enabled' : 'off'}</Tag>
                  </div>
                  {localLlmStatus.data?.lastError && (
                    <div className="status-line wide-status">
                      <Typography.Text type="secondary">Last error</Typography.Text>
                      <Typography.Text type="danger" ellipsis>{localLlmStatus.data.lastError}</Typography.Text>
                    </div>
                  )}
                </div>
                <ul className="guardrails">
                  <li>AI 只能生成建议、解释和草稿</li>
                  <li>建议被接受后才进入 SQL 版本链</li>
                  <li>审核通过后才能冻结执行基线</li>
                  <li>敏感信息不得进入提示词和 embedding</li>
                </ul>
              </div>
            </section>
          </Layout.Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  )
}

export default App
