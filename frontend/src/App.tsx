import { useEffect, useMemo, useState } from "react";
import {
  ApiOutlined,
  BranchesOutlined,
  CodeOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  FolderOpenOutlined,
  SafetyCertificateOutlined,
  UploadOutlined
} from "@ant-design/icons";
import Editor from "@monaco-editor/react";
import ReactECharts from "echarts-for-react";
import ReactFlow, { Background, Controls, type Edge, type Node } from "reactflow";
import "reactflow/dist/style.css";
import {
  Alert,
  Button,
  Descriptions,
  Flex,
  Form,
  Input,
  Layout,
  Menu,
  Select,
  Space,
  Splitter,
  Steps,
  Table,
  Tag,
  Typography,
  Upload,
  message
} from "antd";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createProject,
  createSourceProject,
  createAiSuggestion,
  fetchConversions,
  fetchDependencies,
  fetchHealth,
  fetchObjects,
  fetchParseIssues,
  fetchRisks,
  fetchSqlVersions,
  fetchReports,
  fetchReviews,
  fetchSqlPackagePreview,
  fetchSqlPackage,
  fetchAiSuggestions,
  editSqlVersion,
  generatePrecheckReport,
  importManualSql,
  importFiles,
  submitReview,
  type ConversionResult,
  type AiSuggestion,
  type DbObject,
  type InputBatchImportResult,
  type InputImportResult,
  type ObjectRiskIssue,
  type ObjectDependency,
  type ParseIssue,
  type ProjectSnapshot,
  type ReviewRecord,
  type SqlPackagePreview,
  type SqlVersion,
  type StageReport
} from "./api/client";

const { Header, Sider, Content } = Layout;

type MenuKey = "dashboard" | "projects" | "imports" | "analysis" | "workbench" | "review" | "export";

type LastImportSummary = {
  batchId: string;
  status: string;
  sourceCount: number;
  contentHash?: string;
};

const menuItems = [
  { key: "dashboard", icon: <DatabaseOutlined />, label: "总览" },
  { key: "projects", icon: <FolderOpenOutlined />, label: "项目" },
  { key: "imports", icon: <UploadOutlined />, label: "输入源" },
  { key: "analysis", icon: <BranchesOutlined />, label: "资产与风险" },
  { key: "workbench", icon: <CodeOutlined />, label: "转换" },
  { key: "review", icon: <SafetyCertificateOutlined />, label: "审核" },
  { key: "export", icon: <FileSearchOutlined />, label: "导出" }
];

function manualImportSummary(result: InputImportResult): LastImportSummary {
  return {
    batchId: result.batch.id,
    status: result.batch.status,
    sourceCount: 1,
    contentHash: result.source.contentHash
  };
}

function batchImportSummary(result: InputBatchImportResult): LastImportSummary {
  return {
    batchId: result.batch.id,
    status: result.batch.status,
    sourceCount: result.sources.length,
    contentHash: result.sources.length === 1 ? result.sources[0].contentHash : undefined
  };
}

function App() {
  const queryClient = useQueryClient();
  const [activeMenu, setActiveMenu] = useState<MenuKey>("dashboard");
  const [projectSnapshot, setProjectSnapshot] = useState<ProjectSnapshot | null>(null);
  const [selectedSourceProjectId, setSelectedSourceProjectId] = useState<string | null>(null);
  const [lastImport, setLastImport] = useState<LastImportSummary | null>(null);
  const [exportPreview, setExportPreview] = useState<SqlPackagePreview | null>(null);
  const [sqlText, setSqlText] = useState("create table users (\n  id number(10,0) not null,\n  name varchar2(80),\n  created_at date default sysdate\n);");
  const [messageApi, contextHolder] = message.useMessage();
  const projectId = projectSnapshot?.project.id;
  const healthQuery = useQuery({ queryKey: ["health"], queryFn: fetchHealth, retry: false });
  const objectQuery = useQuery({ queryKey: ["objects", projectId], queryFn: () => fetchObjects(projectId!), enabled: Boolean(projectId) });
  const riskQuery = useQuery({ queryKey: ["risks", projectId], queryFn: () => fetchRisks(projectId!), enabled: Boolean(projectId) });
  const conversionQuery = useQuery({ queryKey: ["conversions", projectId], queryFn: () => fetchConversions(projectId!), enabled: Boolean(projectId) });
  const dependencyQuery = useQuery({ queryKey: ["dependencies", projectId], queryFn: () => fetchDependencies(projectId!), enabled: Boolean(projectId) });
  const sqlVersionQuery = useQuery({ queryKey: ["sqlVersions", projectId], queryFn: () => fetchSqlVersions(projectId!), enabled: Boolean(projectId) });
  const reportQuery = useQuery({ queryKey: ["reports", projectId], queryFn: () => fetchReports(projectId!), enabled: Boolean(projectId) });
  const reviewQuery = useQuery({ queryKey: ["reviews", projectId], queryFn: () => fetchReviews(projectId!), enabled: Boolean(projectId) });
  const aiSuggestionQuery = useQuery({ queryKey: ["aiSuggestions", projectId], queryFn: () => fetchAiSuggestions(projectId!), enabled: Boolean(projectId) });
  const parseIssueQuery = useQuery({ queryKey: ["parseIssues", projectId], queryFn: () => fetchParseIssues(projectId!), enabled: Boolean(projectId) });

  const createProjectMutation = useMutation({
    mutationFn: createProject,
    onSuccess: (snapshot) => {
      setProjectSnapshot(snapshot);
      setSelectedSourceProjectId(snapshot.sourceProjects[0]?.id ?? null);
      setLastImport(null);
      setExportPreview(null);
      setActiveMenu("imports");
      messageApi.success("项目已创建");
    }
  });

  const importMutation = useMutation({
    mutationFn: async () => {
      if (!projectSnapshot) {
        throw new Error("Project is required");
      }
      const sourceProjectId = selectedSourceProjectId ?? projectSnapshot.sourceProjects[0].id;
      return importManualSql(projectSnapshot.project.id, {
        sourceProjectId,
        name: "manual.sql",
        sql: sqlText
      });
    },
    onSuccess: async (result) => {
      setLastImport(manualImportSummary(result));
      setExportPreview(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["objects", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["risks", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["conversions", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["dependencies", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["sqlVersions", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["reports", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["reviews", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["aiSuggestions", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["parseIssues", projectId] })
      ]);
      setActiveMenu("analysis");
      messageApi.success("SQL 已导入并完成基础建模");
    }
  });

  const importFilesMutation = useMutation({
    mutationFn: async (files: File[]) => {
      if (!projectSnapshot) {
        throw new Error("Project is required");
      }
      const sourceProjectId = selectedSourceProjectId ?? projectSnapshot.sourceProjects[0].id;
      return importFiles(projectSnapshot.project.id, sourceProjectId, files);
    },
    onSuccess: async (result) => {
      setLastImport(batchImportSummary(result));
      setExportPreview(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["objects", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["risks", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["conversions", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["dependencies", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["sqlVersions", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["reports", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["reviews", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["aiSuggestions", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["parseIssues", projectId] })
      ]);
      setActiveMenu("analysis");
      messageApi.success("文件批次已导入并完成基础建模");
    }
  });

  const editSqlVersionMutation = useMutation({
    mutationFn: async (payload: { versionId: string; targetSql: string }) => {
      if (!projectId) {
        throw new Error("Project is required");
      }
      return editSqlVersion(projectId, payload.versionId, payload.targetSql);
    },
    onSuccess: async () => {
      setExportPreview(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["sqlVersions", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["reports", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["reviews", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["aiSuggestions", projectId] })
      ]);
      messageApi.success("SQL 编辑版本已保存");
    }
  });

  const createSourceProjectMutation = useMutation({
    mutationFn: async (payload: { name: string; type: string }) => {
      if (!projectId) {
        throw new Error("Project is required");
      }
      return createSourceProject(projectId, payload);
    },
    onSuccess: (snapshot) => {
      setProjectSnapshot(snapshot);
      const created = snapshot.sourceProjects.at(-1);
      setSelectedSourceProjectId(created?.id ?? snapshot.sourceProjects[0]?.id ?? null);
      messageApi.success("工程单元已创建");
    }
  });

  const generateReportMutation = useMutation({
    mutationFn: async () => {
      if (!projectId) {
        throw new Error("Project is required");
      }
      return generatePrecheckReport(projectId);
    },
    onSuccess: async () => {
      setExportPreview(null);
      await queryClient.invalidateQueries({ queryKey: ["reports", projectId] });
      messageApi.success("预处理报告已生成");
    }
  });

  const submitReviewMutation = useMutation({
    mutationFn: async (reportId: string) => {
      if (!projectId) {
        throw new Error("Project is required");
      }
      return submitReview(projectId, {
        reportId,
        decision: "APPROVED",
        reviewer: "developer",
        comment: "P0 development approval"
      });
    },
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["reviews", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["sqlVersions", projectId] })
      ]);
      messageApi.success("审核已通过，SQL 基线已冻结");
    }
  });

  const exportPreviewMutation = useMutation({
    mutationFn: async () => {
      if (!projectId) {
        throw new Error("Project is required");
      }
      return fetchSqlPackagePreview(projectId);
    },
    onSuccess: (preview) => {
      setExportPreview(preview);
      messageApi.success("SQL 包预览已就绪");
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : "SQL 包预览失败");
    }
  });

  const downloadSqlPackageMutation = useMutation({
    mutationFn: async () => {
      if (!projectId) {
        throw new Error("Project is required");
      }
      return fetchSqlPackage(projectId);
    },
    onSuccess: (content) => {
      const url = URL.createObjectURL(content);
      const link = document.createElement("a");
      link.href = url;
      link.download = "001_schema_baseline.sql";
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      messageApi.success("SQL package downloaded");
    },
    onError: (error) => {
      messageApi.error(error instanceof Error ? error.message : "SQL package download failed");
    }
  });

  const aiSuggestionMutation = useMutation({
    mutationFn: async () => {
      if (!projectId) {
        throw new Error("Project is required");
      }
      return createAiSuggestion(projectId, "REPORT_SUMMARY");
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["aiSuggestions", projectId] });
      messageApi.success("AI 建议草稿已生成");
    }
  });

  return (
    <Layout className="shell">
      {contextHolder}
      <Sider width={232} className="sidebar">
        <div className="brand">
          <ApiOutlined />
          <span>SchemaPilot</span>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[activeMenu]}
          items={menuItems}
          onClick={(event) => setActiveMenu(event.key as MenuKey)}
          className="menu"
        />
      </Sider>
      <Layout>
        <Header className="topbar">
          <Space size={16}>
            <Tag color={healthQuery.data?.status === "UP" ? "success" : "warning"}>
              {healthQuery.data?.status ?? "API"}
            </Tag>
            <Typography.Text type="secondary">{healthQuery.data?.service ?? "schemapilot-backend"}</Typography.Text>
          </Space>
          <Button icon={<CodeOutlined />} onClick={() => setActiveMenu("imports")}>
            输入 SQL
          </Button>
        </Header>
        <Content className="content">{renderContent()}</Content>
      </Layout>
    </Layout>
  );

  function renderContent() {
    switch (activeMenu) {
      case "projects":
        return <ProjectPanel loading={createProjectMutation.isPending} onCreate={createProjectMutation.mutate} snapshot={projectSnapshot} />;
      case "imports":
        return (
          <ImportPanel
            projectSnapshot={projectSnapshot}
            sqlText={sqlText}
            setSqlText={setSqlText}
            selectedSourceProjectId={selectedSourceProjectId}
            setSelectedSourceProjectId={setSelectedSourceProjectId}
            onCreateSourceProject={(name) => createSourceProjectMutation.mutate({ name, type: "DATABASE_EXPORT" })}
            creatingSourceProject={createSourceProjectMutation.isPending}
            importing={importMutation.isPending}
            onImport={() => importMutation.mutate()}
            importingFiles={importFilesMutation.isPending}
            onImportFiles={(files) => importFilesMutation.mutate(files)}
            lastImport={lastImport}
          />
        );
      case "analysis":
        return (
          <AnalysisPanel
            objects={objectQuery.data ?? []}
            risks={riskQuery.data ?? []}
            conversions={conversionQuery.data ?? []}
            dependencies={dependencyQuery.data ?? []}
            parseIssues={parseIssueQuery.data ?? []}
            aiSuggestions={aiSuggestionQuery.data ?? []}
            loading={objectQuery.isFetching || riskQuery.isFetching || conversionQuery.isFetching || dependencyQuery.isFetching || parseIssueQuery.isFetching || aiSuggestionQuery.isFetching || aiSuggestionMutation.isPending}
            onCreateAiSuggestion={() => aiSuggestionMutation.mutate()}
          />
        );
      case "workbench":
        return (
          <WorkbenchPanel
            versions={sqlVersionQuery.data ?? []}
            loading={sqlVersionQuery.isFetching || editSqlVersionMutation.isPending}
            onSave={(versionId, targetSql) => editSqlVersionMutation.mutate({ versionId, targetSql })}
          />
        );
      case "review":
        return (
          <ReviewPanel
            projectSnapshot={projectSnapshot}
            lastImport={lastImport}
            risks={riskQuery.data ?? []}
            reports={reportQuery.data ?? []}
            reviews={reviewQuery.data ?? []}
            generating={generateReportMutation.isPending || submitReviewMutation.isPending || reportQuery.isFetching || reviewQuery.isFetching}
            onGenerate={() => generateReportMutation.mutate()}
            onApprove={(reportId) => submitReviewMutation.mutate(reportId)}
          />
        );
      case "export":
        return (
          <ExportPanel
            projectSnapshot={projectSnapshot}
            conversions={conversionQuery.data ?? []}
            preview={exportPreview}
            loading={exportPreviewMutation.isPending || downloadSqlPackageMutation.isPending}
            onPreview={() => exportPreviewMutation.mutate()}
            onDownload={() => downloadSqlPackageMutation.mutate()}
          />
        );
      default:
        return (
          <DashboardPanel
            healthStatus={healthQuery.data?.status}
            projectSnapshot={projectSnapshot}
            lastImport={lastImport}
            objects={objectQuery.data ?? []}
            risks={riskQuery.data ?? []}
          />
        );
    }
  }
}

function DashboardPanel({
  healthStatus,
  projectSnapshot,
  lastImport,
  objects,
  risks
}: {
  healthStatus?: string;
  projectSnapshot: ProjectSnapshot | null;
  lastImport: LastImportSummary | null;
  objects: DbObject[];
  risks: ObjectRiskIssue[];
}) {
  const chartOption = useMemo(
    () => ({
      color: ["#176B87", "#7AA874", "#DFA878", "#B85C5C"],
      tooltip: {},
      grid: { left: 20, right: 20, top: 24, bottom: 24, containLabel: true },
      xAxis: { type: "category", data: ["对象", "风险", "待审核", "冻结"] },
      yAxis: { type: "value" },
      series: [{ type: "bar", data: [objects.length, risks.length, projectSnapshot ? 1 : 0, 0], barWidth: 28 }]
    }),
    [lastImport, objects.length, projectSnapshot, risks.length]
  );

  return (
    <section className="panel">
      <div className="sectionTitle">
        <Typography.Title level={2}>迁移工作台</Typography.Title>
        <Typography.Text type="secondary">P0 评估、转换、审核闭环</Typography.Text>
      </div>
      <div className="metricGrid">
        <Metric label="API" value={healthStatus ?? "未连接"} />
        <Metric label="项目" value={projectSnapshot ? projectSnapshot.project.name : "未创建"} />
        <Metric label="对象" value={String(objects.length)} />
        <Metric label="风险" value={String(risks.length)} />
      </div>
      <div className="workspaceBand">
        <ReactECharts option={chartOption} style={{ height: 280 }} />
      </div>
    </section>
  );
}

function ProjectPanel({
  loading,
  onCreate,
  snapshot
}: {
  loading: boolean;
  onCreate: (payload: { name: string; description?: string }) => void;
  snapshot: ProjectSnapshot | null;
}) {
  return (
    <section className="panel">
      <div className="sectionTitle">
        <Typography.Title level={2}>项目</Typography.Title>
        <Typography.Text type="secondary">迁移项目与工程单元</Typography.Text>
      </div>
      <Splitter className="splitter">
        <Splitter.Panel defaultSize="38%" min="320px">
          <Form layout="vertical" onFinish={onCreate} initialValues={{ name: "Pilot", description: "Oracle migration assessment" }}>
            <Form.Item name="name" label="项目名" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="description" label="说明">
              <Input.TextArea rows={4} />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={loading}>
              创建项目
            </Button>
          </Form>
        </Splitter.Panel>
        <Splitter.Panel>
          {snapshot ? (
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="项目 ID">{snapshot.project.id}</Descriptions.Item>
              <Descriptions.Item label="状态">{snapshot.project.status}</Descriptions.Item>
              <Descriptions.Item label="默认工程">{snapshot.sourceProjects[0].name}</Descriptions.Item>
              <Descriptions.Item label="工程类型">{snapshot.sourceProjects[0].type}</Descriptions.Item>
            </Descriptions>
          ) : (
            <Alert type="info" showIcon message="尚无项目" />
          )}
        </Splitter.Panel>
      </Splitter>
    </section>
  );
}

function ImportPanel({
  projectSnapshot,
  sqlText,
  setSqlText,
  selectedSourceProjectId,
  setSelectedSourceProjectId,
  onCreateSourceProject,
  creatingSourceProject,
  importing,
  onImport,
  importingFiles,
  onImportFiles,
  lastImport
}: {
  projectSnapshot: ProjectSnapshot | null;
  sqlText: string;
  setSqlText: (value: string) => void;
  selectedSourceProjectId: string | null;
  setSelectedSourceProjectId: (value: string) => void;
  onCreateSourceProject: (name: string) => void;
  creatingSourceProject: boolean;
  importing: boolean;
  onImport: () => void;
  importingFiles: boolean;
  onImportFiles: (files: File[]) => void;
  lastImport: LastImportSummary | null;
}) {
  const [uploadFiles, setUploadFiles] = useState<File[]>([]);
  const [newSourceProjectName, setNewSourceProjectName] = useState("");

  return (
    <section className="panel">
      <div className="sectionTitle">
        <Typography.Title level={2}>输入源</Typography.Title>
        <Typography.Text type="secondary">手工 SQL、文件、文件夹、zip</Typography.Text>
      </div>
      <Splitter className="splitter tall">
        <Splitter.Panel defaultSize="58%" min="420px">
          <Editor
            height="520px"
            defaultLanguage="sql"
            value={sqlText}
            onChange={(value) => setSqlText(value ?? "")}
            options={{ minimap: { enabled: false }, fontSize: 14 }}
          />
        </Splitter.Panel>
        <Splitter.Panel>
          <Flex vertical gap={16}>
            <Space.Compact>
              <Select
                className="sourceSelect"
                disabled={!projectSnapshot}
                value={selectedSourceProjectId ?? undefined}
                placeholder="选择工程单元"
                onChange={setSelectedSourceProjectId}
                options={(projectSnapshot?.sourceProjects ?? []).map((sourceProject) => ({
                  value: sourceProject.id,
                  label: `${sourceProject.name} · ${sourceProject.type}`
                }))}
              />
              <Input
                placeholder="新工程名"
                value={newSourceProjectName}
                onChange={(event) => setNewSourceProjectName(event.target.value)}
              />
              <Button
                loading={creatingSourceProject}
                disabled={!projectSnapshot || !newSourceProjectName.trim()}
                onClick={() => {
                  onCreateSourceProject(newSourceProjectName);
                  setNewSourceProjectName("");
                }}
              >
                新建
              </Button>
            </Space.Compact>
            <Button type="primary" disabled={!projectSnapshot} loading={importing} onClick={onImport}>
              导入手工 SQL
            </Button>
            <Upload.Dragger
              multiple
              beforeUpload={() => false}
              onChange={(info) => {
                setUploadFiles(info.fileList.flatMap((file) => (file.originFileObj ? [file.originFileObj as File] : [])));
              }}
            >
              <p className="ant-upload-drag-icon">
                <UploadOutlined />
              </p>
              <p className="ant-upload-text">文件 / zip</p>
            </Upload.Dragger>
            <Upload.Dragger
              directory
              beforeUpload={() => false}
              onChange={(info) => {
                setUploadFiles(info.fileList.flatMap((file) => (file.originFileObj ? [file.originFileObj as File] : [])));
              }}
            >
              <p className="ant-upload-drag-icon">
                <FolderOpenOutlined />
              </p>
              <p className="ant-upload-text">文件夹 / 工程</p>
            </Upload.Dragger>
            <Button disabled={!projectSnapshot || uploadFiles.length === 0} loading={importingFiles} onClick={() => onImportFiles(uploadFiles)}>
              导入文件批次
            </Button>
            {lastImport && (
              <Descriptions bordered column={1} size="small">
                <Descriptions.Item label="批次">{lastImport.batchId}</Descriptions.Item>
                <Descriptions.Item label="状态">{lastImport.status}</Descriptions.Item>
                <Descriptions.Item label="来源数">{lastImport.sourceCount}</Descriptions.Item>
                {lastImport.contentHash && <Descriptions.Item label="Hash">{lastImport.contentHash}</Descriptions.Item>}
              </Descriptions>
            )}
          </Flex>
        </Splitter.Panel>
      </Splitter>
    </section>
  );
}

function AnalysisPanel({
  objects,
  risks,
  conversions,
  dependencies,
  parseIssues,
  aiSuggestions,
  onCreateAiSuggestion,
  loading
}: {
  objects: DbObject[];
  risks: ObjectRiskIssue[];
  conversions: ConversionResult[];
  dependencies: ObjectDependency[];
  parseIssues: ParseIssue[];
  aiSuggestions: AiSuggestion[];
  onCreateAiSuggestion: () => void;
  loading: boolean;
}) {
  const graph = useMemo(() => buildGraph(objects, risks, dependencies), [objects, risks, dependencies]);
  const objectNameById = useMemo(() => new Map(objects.map((object) => [object.id, object.name])), [objects]);

  return (
    <section className="panel">
      <div className="sectionTitle">
        <Typography.Title level={2}>资产与风险</Typography.Title>
        <Typography.Text type="secondary">对象识别、规则命中、转换草稿</Typography.Text>
      </div>
      {objects.length === 0 && !loading && <Alert type="info" showIcon message="导入 SQL 后会在这里生成真实资产、风险和转换结果" />}
      <div className="flowPane">
        <ReactFlow nodes={graph.nodes} edges={graph.edges} fitView>
          <Background />
          <Controls />
        </ReactFlow>
      </div>
      <Splitter className="splitter analysisSplit">
        <Splitter.Panel defaultSize="38%" min="320px">
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={objects}
            pagination={false}
            columns={[
              { title: "对象", dataIndex: "name" },
              { title: "类型", dataIndex: "type", width: 110 },
              { title: "列", dataIndex: "columns", width: 80, render: (columns: DbObject["columns"]) => columns.length }
            ]}
          />
        </Splitter.Panel>
        <Splitter.Panel>
          <Table
            rowKey="id"
            size="small"
            loading={loading}
            dataSource={risks}
            pagination={false}
            columns={[
              { title: "规则", dataIndex: "code" },
              { title: "等级", dataIndex: "level", width: 100, render: (value) => <Tag color={value === "HIGH" ? "error" : "warning"}>{value}</Tag> },
              { title: "对象", dataIndex: "objectId", width: 140, render: (value) => objectNameById.get(value) ?? value }
            ]}
          />
        </Splitter.Panel>
      </Splitter>
      <Table
        rowKey="id"
        size="small"
        className="dependencyTable"
        loading={loading}
        dataSource={dependencies}
        pagination={false}
        columns={[
          { title: "依赖类型", dataIndex: "type" },
          { title: "来源对象", dataIndex: "sourceObjectId", render: (value) => objectNameById.get(value) ?? value },
          { title: "目标对象", dataIndex: "targetObjectId", render: (value) => objectNameById.get(value) ?? value },
          { title: "证据", dataIndex: "evidence" }
        ]}
      />
      <div className="conversionList">
        {conversions.map((conversion) => (
          <div className="conversionItem" key={conversion.id}>
            <Flex justify="space-between" align="center">
              <Typography.Text strong>{objectNameById.get(conversion.objectId) ?? conversion.objectId}</Typography.Text>
              <Tag color={conversion.level === "AUTO" ? "success" : "warning"}>{conversion.level}</Tag>
            </Flex>
            <pre>{conversion.targetSql}</pre>
          </div>
        ))}
      </div>
      {parseIssues.length > 0 && (
        <Alert
          type="warning"
          showIcon
          className="issueAlert"
          message={`${parseIssues.length} 条语句暂未识别，已保留原文进入后续问题板`}
        />
      )}
      <div className="aiPanel">
        <Flex justify="space-between" align="center">
          <Typography.Title level={4}>AI 迁移建议</Typography.Title>
          <Button loading={loading} onClick={onCreateAiSuggestion}>
            生成建议草稿
          </Button>
        </Flex>
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          dataSource={aiSuggestions}
          pagination={false}
          columns={[
            { title: "类型", dataIndex: "type", width: 140 },
            { title: "状态", dataIndex: "status", width: 110 },
            { title: "摘要", dataIndex: "summary" },
            { title: "规则命中", dataIndex: "ruleHits", width: 100, render: (value: AiSuggestion["ruleHits"]) => value.length },
            { title: "影响对象", dataIndex: "affectedObjects", width: 100, render: (value: string[]) => value.length }
          ]}
        />
      </div>
    </section>
  );
}

function WorkbenchPanel({
  versions,
  loading,
  onSave
}: {
  versions: SqlVersion[];
  loading: boolean;
  onSave: (versionId: string, targetSql: string) => void;
}) {
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const activeVersion = versions.find((version) => version.id === selectedId) ?? versions[0] ?? null;
  const [targetSql, setTargetSql] = useState("");

  useEffect(() => {
    if (!activeVersion) {
      setSelectedId(null);
      return;
    }
    if (selectedId !== activeVersion.id) {
      setSelectedId(activeVersion.id);
    }
  }, [activeVersion, selectedId]);

  useEffect(() => {
    setTargetSql(activeVersion?.targetSql ?? "");
  }, [activeVersion?.id, activeVersion?.targetSql]);

  return (
    <section className="panel">
      <div className="sectionTitle">
        <Typography.Title level={2}>转换工作台</Typography.Title>
        <Typography.Text type="secondary">Oracle 原 SQL、PostgreSQL 目标 SQL、用户编辑版本</Typography.Text>
      </div>
      {versions.length === 0 && <Alert type="info" showIcon message="导入 SQL 后会自动生成转换版本" />}
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        className="versionTable"
        dataSource={versions}
        pagination={false}
        rowClassName={(record) => (record.id === activeVersion?.id ? "selectedRow" : "")}
        onRow={(record) => ({ onClick: () => setSelectedId(record.id) })}
        columns={[
          { title: "对象", dataIndex: "objectId" },
          { title: "版本", dataIndex: "versionNumber", width: 80 },
          { title: "来源", dataIndex: "source", width: 150 },
          { title: "状态", dataIndex: "status", width: 120, render: (value) => <Tag color={value === "EDITED" ? "processing" : "default"}>{value}</Tag> }
        ]}
      />
      <Splitter className="splitter tall workbenchSplit">
        <Splitter.Panel defaultSize="50%" min="360px">
          <Typography.Text strong>Oracle 原 SQL</Typography.Text>
          <Editor
            height="470px"
            defaultLanguage="sql"
            value={activeVersion?.sourceSql ?? ""}
            options={{ readOnly: true, minimap: { enabled: false }, fontSize: 14 }}
          />
        </Splitter.Panel>
        <Splitter.Panel min="360px">
          <Flex justify="space-between" align="center" className="editorHeader">
            <Typography.Text strong>PostgreSQL 目标 SQL</Typography.Text>
            <Button
              type="primary"
              disabled={!activeVersion || targetSql === activeVersion.targetSql}
              loading={loading}
              onClick={() => activeVersion && onSave(activeVersion.id, targetSql)}
            >
              保存编辑版本
            </Button>
          </Flex>
          <Editor
            height="470px"
            defaultLanguage="sql"
            value={targetSql}
            onChange={(value) => setTargetSql(value ?? "")}
            options={{ minimap: { enabled: false }, fontSize: 14 }}
          />
        </Splitter.Panel>
      </Splitter>
    </section>
  );
}

function ReviewPanel({
  projectSnapshot,
  lastImport,
  risks,
  reports,
  reviews,
  generating,
  onGenerate,
  onApprove
}: {
  projectSnapshot: ProjectSnapshot | null;
  lastImport: LastImportSummary | null;
  risks: ObjectRiskIssue[];
  reports: StageReport[];
  reviews: ReviewRecord[];
  generating: boolean;
  onGenerate: () => void;
  onApprove: (reportId: string) => void;
}) {
  const latestReport = reports[0] ?? null;
  const latestReview = latestReport ? reviews.find((review) => review.reportId === latestReport.id) ?? null : null;
  const latestReviewApproved = latestReview?.decision === "APPROVED" || latestReview?.decision === "CONDITIONALLY_APPROVED";

  return (
    <section className="panel">
      <div className="sectionTitle">
        <Typography.Title level={2}>审核</Typography.Title>
        <Space>
          <Typography.Text type="secondary">报告快照、豁免、SQL 基线</Typography.Text>
          <Button type="primary" disabled={!projectSnapshot} loading={generating} onClick={onGenerate}>
            生成预处理报告
          </Button>
          <Button disabled={!latestReport || latestReport.status !== "CURRENT"} loading={generating} onClick={() => latestReport && onApprove(latestReport.id)}>
            审核通过
          </Button>
        </Space>
      </div>
      <Steps
        current={latestReviewApproved ? 3 : latestReport ? 2 : lastImport ? 1 : 0}
        items={[
          { title: "输入扫描" },
          { title: "风险预检" },
          { title: "审核冻结" },
          { title: "SQL 包" }
        ]}
      />
      <Descriptions bordered column={1} size="small" className="reviewBox">
        <Descriptions.Item label="项目">{projectSnapshot?.project.name ?? "未创建"}</Descriptions.Item>
        <Descriptions.Item label="报告状态">{latestReport?.status ?? (lastImport ? "PRECHECK_PENDING" : "PENDING")}</Descriptions.Item>
        <Descriptions.Item label="未处理风险">{risks.length}</Descriptions.Item>
        <Descriptions.Item label="最新报告">{latestReport ? `${latestReport.title} / ${latestReport.id}` : "未生成"}</Descriptions.Item>
        <Descriptions.Item label="SQL 基线">{latestReviewApproved ? "已冻结" : "未冻结"}</Descriptions.Item>
      </Descriptions>
      <Table
        rowKey="id"
        size="small"
        className="reportTable"
        loading={generating}
        dataSource={reports}
        pagination={false}
        columns={[
          { title: "报告", dataIndex: "title" },
          { title: "类型", dataIndex: "type", width: 120 },
          { title: "状态", dataIndex: "status", width: 120, render: (value) => <Tag color={value === "BLOCKED" ? "error" : "success"}>{value}</Tag> },
          { title: "生成时间", dataIndex: "createdAt", width: 240 }
        ]}
      />
      <Table
        rowKey="id"
        size="small"
        className="reportTable"
        loading={generating}
        dataSource={reviews}
        pagination={false}
        columns={[
          { title: "审核决定", dataIndex: "decision" },
          { title: "审核人", dataIndex: "reviewer", width: 140 },
          { title: "意见", dataIndex: "comment" },
          { title: "时间", dataIndex: "createdAt", width: 240 }
        ]}
      />
    </section>
  );
}

function ExportPanel({
  projectSnapshot,
  conversions,
  preview,
  loading,
  onPreview,
  onDownload
}: {
  projectSnapshot: ProjectSnapshot | null;
  conversions: ConversionResult[];
  preview: SqlPackagePreview | null;
  loading: boolean;
  onPreview: () => void;
  onDownload: () => void;
}) {
  return (
    <section className="panel">
      <div className="sectionTitle">
        <Typography.Title level={2}>导出</Typography.Title>
        <Space>
          <Typography.Text type="secondary">SQL 包预览</Typography.Text>
          <Button type="primary" disabled={!projectSnapshot} loading={loading} onClick={onPreview}>
            刷新预览
          </Button>
          <Button disabled={!projectSnapshot} loading={loading} onClick={onDownload}>
            下载 SQL 包
          </Button>
        </Space>
      </div>
      <Alert type={preview ? "success" : projectSnapshot ? "warning" : "info"} showIcon message={preview ? "SQL 包已满足导出门禁" : projectSnapshot ? "等待审核冻结" : "尚无项目"} />
      {preview && (
        <Descriptions bordered column={1} size="small" className="reviewBox">
          <Descriptions.Item label="状态">{preview.status}</Descriptions.Item>
          <Descriptions.Item label="Baseline 数量">{preview.baselineCount}</Descriptions.Item>
          <Descriptions.Item label="审核记录">{preview.approvedReviewCount}</Descriptions.Item>
          <Descriptions.Item label="文件">{preview.fileNames.join(", ")}</Descriptions.Item>
        </Descriptions>
      )}
      <Table
        className="exportTable"
        rowKey="id"
        size="small"
        pagination={false}
        dataSource={conversions}
        columns={[
          { title: "转换结果", dataIndex: "id" },
          { title: "等级", dataIndex: "level", width: 140 }
        ]}
      />
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function buildGraph(objects: DbObject[], risks: ObjectRiskIssue[], dependencies: ObjectDependency[]) {
  const nodes: Node[] = [{ id: "input", position: { x: 0, y: 90 }, data: { label: "InputSource" } }];
  const edges: Edge[] = [];
  objects.forEach((object, index) => {
    nodes.push({
      id: object.id,
      position: { x: 220, y: index * 86 + 40 },
      data: { label: `${object.type}: ${object.name}` }
    });
    edges.push({ id: `input-${object.id}`, source: "input", target: object.id });
  });
  dependencies.forEach((dependency) => {
    edges.push({
      id: dependency.id,
      source: dependency.sourceObjectId,
      target: dependency.targetObjectId,
      label: dependency.type
    });
  });
  risks.forEach((risk, index) => {
    nodes.push({
      id: risk.id,
      position: { x: 520, y: index * 82 + 40 },
      data: { label: `${risk.level}: ${risk.code}` }
    });
    edges.push({ id: `risk-${risk.id}`, source: risk.objectId, target: risk.id });
  });
  return { nodes, edges };
}

export default App;
