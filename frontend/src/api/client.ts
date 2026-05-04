import axios from "axios";

export type ApiResponse<T> = {
  success: boolean;
  data: T;
  error: null | {
    code: string;
    message: string;
  };
};

export type HealthStatus = {
  status: string;
  service: string;
  checkedAt: string;
};

export type MigrationProject = {
  id: string;
  name: string;
  description: string;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type SourceProject = {
  id: string;
  projectId: string;
  name: string;
  type: string;
  createdAt: string;
};

export type ProjectSnapshot = {
  project: MigrationProject;
  sourceProjects: SourceProject[];
};

export type InputImportResult = {
  batch: {
    id: string;
    projectId: string;
    sourceProjectId: string;
    status: string;
    createdAt: string;
  };
  source: {
    id: string;
    type: string;
    name: string;
    contentHash: string;
    sizeBytes: number;
    encoding: string;
  };
};

export type InputBatchImportResult = {
  batch: InputImportResult["batch"];
  sources: InputImportResult["source"][];
};

export type DbColumn = {
  name: string;
  sourceType: string;
  targetType: string | null;
  nullable: boolean;
  ordinalPosition: number;
};

export type DbObject = {
  id: string;
  projectId: string;
  sourceProjectId: string;
  inputSourceId: string;
  type: string;
  schema: string | null;
  name: string;
  status: string;
  originalSql: string;
  columns: DbColumn[];
};

export type ParseIssue = {
  id: string;
  code: string;
  message: string;
  originalText: string;
};

export type RuleHit = {
  ruleCode: string;
  title: string;
  sourceFragment: string;
  explanation: string;
};

export type ObjectRiskIssue = {
  id: string;
  objectId: string;
  code: string;
  level: string;
  message: string;
  evidence: string;
  ruleHit: RuleHit;
};

export type ConversionResult = {
  id: string;
  objectId: string;
  sourceSql: string;
  targetSql: string;
  level: string;
  ruleHits: RuleHit[];
  notes: string[];
};

export type ObjectDependency = {
  id: string;
  sourceObjectId: string;
  targetObjectId: string;
  type: string;
  evidence: string;
};

export type SqlVersion = {
  id: string;
  projectId: string;
  sourceProjectId: string;
  inputSourceId: string;
  objectId: string;
  conversionResultId: string;
  parentVersionId: string | null;
  versionNumber: number;
  source: string;
  status: string;
  sourceSql: string;
  targetSql: string;
  createdAt: string;
};

export type StageReport = {
  id: string;
  projectId: string;
  type: string;
  status: string;
  title: string;
  createdAt: string;
};

export type PrecheckReport = {
  id: string;
  projectId: string;
  stageReportId: string;
  status: string;
  objectCount: number;
  parseIssueCount: number;
  riskCount: number;
  blockerCount: number;
  highRiskCount: number;
  conversionCount: number;
  sqlVersionCount: number;
};

export type GeneratedReport = {
  stageReport: StageReport;
  precheckReport: PrecheckReport;
};

export type ReviewRecord = {
  id: string;
  projectId: string;
  reportId: string;
  decision: string;
  reviewer: string;
  comment: string;
  createdAt: string;
};

export type SqlPackagePreview = {
  projectId: string;
  status: string;
  baselineCount: number;
  approvedReviewCount: number;
  blockerCount: number;
  fileNames: string[];
  orderedObjectIds: string[];
};

export type AiSuggestion = {
  id: string;
  projectId: string;
  type: string;
  status: string;
  title: string;
  summary: string;
  ruleHits: RuleHit[];
  affectedObjects: string[];
  changePlan: string[];
  validationPlan: string[];
  uncertainties: string[];
  createdAt: string;
};

const http = axios.create({
  baseURL: "/api",
  timeout: 60000
});

export async function fetchHealth() {
  const response = await http.get<ApiResponse<HealthStatus>>("/health");
  return response.data.data;
}

export async function createProject(payload: { name: string; description?: string }) {
  const response = await http.post<ApiResponse<ProjectSnapshot>>("/projects", payload);
  return response.data.data;
}

export async function createSourceProject(projectId: string, payload: { name: string; type: string }) {
  const response = await http.post<ApiResponse<ProjectSnapshot>>(`/projects/${projectId}/source-projects`, payload);
  return response.data.data;
}

export async function importManualSql(projectId: string, payload: { sourceProjectId: string; name: string; sql: string }) {
  const response = await http.post<ApiResponse<InputImportResult>>(`/projects/${projectId}/inputs/manual-sql`, payload);
  return response.data.data;
}

export async function importFiles(projectId: string, sourceProjectId: string, files: File[]) {
  const form = new FormData();
  form.append("sourceProjectId", sourceProjectId);
  if (files.length === 1 && files[0].name.toLowerCase().endsWith(".zip")) {
    form.append("file", files[0], files[0].name);
    const response = await http.post<ApiResponse<InputBatchImportResult>>(`/projects/${projectId}/inputs/zip`, form);
    return response.data.data;
  }
  files.forEach((file) => form.append("files", file, file.webkitRelativePath || file.name));
  const response = await http.post<ApiResponse<InputBatchImportResult>>(`/projects/${projectId}/inputs/files`, form);
  return response.data.data;
}

export async function fetchObjects(projectId: string) {
  const response = await http.get<ApiResponse<DbObject[]>>(`/projects/${projectId}/assets/objects`);
  return response.data.data;
}

export async function fetchParseIssues(projectId: string) {
  const response = await http.get<ApiResponse<ParseIssue[]>>(`/projects/${projectId}/assets/parse-issues`);
  return response.data.data;
}

export async function fetchRisks(projectId: string) {
  const response = await http.get<ApiResponse<ObjectRiskIssue[]>>(`/projects/${projectId}/risks`);
  return response.data.data;
}

export async function fetchConversions(projectId: string) {
  const response = await http.get<ApiResponse<ConversionResult[]>>(`/projects/${projectId}/conversions`);
  return response.data.data;
}

export async function fetchDependencies(projectId: string) {
  const response = await http.get<ApiResponse<ObjectDependency[]>>(`/projects/${projectId}/dependencies`);
  return response.data.data;
}

export async function fetchSqlVersions(projectId: string) {
  const response = await http.get<ApiResponse<SqlVersion[]>>(`/projects/${projectId}/sql-versions`);
  return response.data.data;
}

export async function editSqlVersion(projectId: string, versionId: string, targetSql: string) {
  const response = await http.post<ApiResponse<SqlVersion>>(`/projects/${projectId}/sql-versions/${versionId}/edits`, { targetSql });
  return response.data.data;
}

export async function fetchReports(projectId: string) {
  const response = await http.get<ApiResponse<StageReport[]>>(`/projects/${projectId}/reports`);
  return response.data.data;
}

export async function generatePrecheckReport(projectId: string) {
  const response = await http.post<ApiResponse<GeneratedReport>>(`/projects/${projectId}/reports/precheck`);
  return response.data.data;
}

export async function fetchReviews(projectId: string) {
  const response = await http.get<ApiResponse<ReviewRecord[]>>(`/projects/${projectId}/reviews`);
  return response.data.data;
}

export async function submitReview(projectId: string, payload: { reportId: string; decision: string; reviewer: string; comment: string }) {
  const response = await http.post<ApiResponse<ReviewRecord>>(`/projects/${projectId}/reviews`, payload);
  return response.data.data;
}

export async function fetchSqlPackagePreview(projectId: string) {
  const response = await http.get<ApiResponse<SqlPackagePreview>>(`/projects/${projectId}/exports/sql-package/preview`);
  return response.data.data;
}

export async function fetchSqlPackage(projectId: string) {
  const response = await http.get<Blob>(`/projects/${projectId}/exports/sql-package.sql`, {
    responseType: "blob"
  });
  return response.data;
}

export async function fetchAiSuggestions(projectId: string) {
  const response = await http.get<ApiResponse<AiSuggestion[]>>(`/projects/${projectId}/ai/suggestions`);
  return response.data.data;
}

export async function createAiSuggestion(projectId: string, type: string) {
  const response = await http.post<ApiResponse<AiSuggestion>>(`/projects/${projectId}/ai/suggestions/${type}`);
  return response.data.data;
}
