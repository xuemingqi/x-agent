export interface HttpRequestField {
  name: string;
  value: string;
  enabled: boolean;
}

export interface HttpRequestSpec {
  method: string;
  url: string;
  query: HttpRequestField[];
  headers: HttpRequestField[];
  body: string;
  timeoutMs: number;
}

export interface HttpExecution {
  url: string;
  statusCode: number;
  elapsedMs: number;
  responseBytes: number;
  truncated: boolean;
  body: string;
  headers: Record<string, string[]>;
  error: string;
}

export interface HttpLoadOptions {
  concurrency: number;
  durationSeconds: number;
  requestsPerSecond: number;
}

export interface HttpLoadResult extends HttpLoadOptions {
  id: string;
  status: 'RUNNING' | 'COMPLETED' | 'STOPPED' | 'FAILED';
  method: string;
  url: string;
  elapsedSeconds: number;
  started: number;
  completed: number;
  failed: number;
  cancelled: number;
  throughput: number;
  errorRate: number;
  averageMs: number;
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
  statusCounts: Record<string, number>;
  timeline: { second: number; completed: number; failed: number; averageMs: number }[];
  message: string;
}

export interface HttpAnalysis {
  summary: string;
  observations: string[];
  suggestions: string[];
  limitations: string[];
}

export interface HttpAnalysisRequest {
  request: HttpRequestSpec;
  response?: HttpExecution;
  loadTest?: HttpLoadResult;
  question: string;
  modelId?: string;
}

export interface HttpRequestDraft {
  id: string;
  name: string;
  request: HttpRequestSpec;
  response?: HttpExecution;
  responseRequest?: HttpRequestSpec;
  loadTest?: HttpLoadResult;
  loadRequest?: HttpRequestSpec;
  analysis?: HttpAnalysis;
  analysisSource?: string;
}
