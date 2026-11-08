import { request } from '@/api/http';
import type { HttpAnalysis, HttpAnalysisRequest, HttpExecution, HttpLoadOptions, HttpLoadResult, HttpRequestSpec } from '@/types/http-lab';

export function sendHttpRequest(data: HttpRequestSpec, signal?: AbortSignal): Promise<HttpExecution> {
  return request({ url: '/apps/http-lab/send', method: 'POST', data, signal, timeout: 40_000 });
}

export function startHttpLoadTest(data: HttpLoadOptions & { request: HttpRequestSpec }): Promise<HttpLoadResult> {
  return request({ url: '/apps/http-lab/runs', method: 'POST', data });
}

export function getHttpLoadTest(id: string): Promise<HttpLoadResult> {
  return request({ url: `/apps/http-lab/runs/${encodeURIComponent(id)}`, method: 'GET' });
}

export function stopHttpLoadTest(id: string): Promise<HttpLoadResult> {
  return request({ url: `/apps/http-lab/runs/${encodeURIComponent(id)}/stop`, method: 'POST' });
}

export function analyzeHttpRequest(data: HttpAnalysisRequest, signal?: AbortSignal): Promise<HttpAnalysis> {
  return request({ url: '/apps/http-lab/analyze', method: 'POST', data, signal, timeout: 150_000 });
}
