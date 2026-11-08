import { API_BASE_URL, authHeaders, request } from './http';
import type { BaseResult } from './types';
import type { AgentEvent, AgentRequest } from '@/types/chat';
import { handleAuthenticationError } from '@/utils/auth';
import { parseSseChunk } from '@/utils/sse';

export async function streamChat(
  payload: AgentRequest,
  onEvent: (event: AgentEvent) => void,
  signal: AbortSignal,
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/chat/stream/run`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
    },
    body: JSON.stringify(payload),
    signal,
  });
  const contentType = response.headers.get('Content-Type') || '';
  if (contentType.includes('application/json')) {
    const result = await response.json() as BaseResult<unknown>;
    handleAuthenticationError(result.code);
    throw new Error(result.code !== 200 ? result.msg || 'Agent 执行失败' : '服务器未返回事件流');
  }
  if (!response.ok || !response.body) {
    throw new Error(`流式请求失败：${response.status}`);
  }
  if (!contentType.includes('text/event-stream')) {
    await response.body.cancel();
    throw new Error('服务器未返回事件流');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let remainder = '';
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) {
        break;
      }
      const parsed = parseSseChunk(remainder, decoder.decode(value, { stream: true }));
      remainder = parsed.remainder;
      for (const data of parsed.events) {
        const result = JSON.parse(data) as BaseResult<AgentEvent>;
        if (result.code !== 200) {
          handleAuthenticationError(result.code);
          throw new Error(result.msg || 'Agent 执行失败');
        }
        onEvent(result.data);
      }
    }
  } finally {
    await reader.cancel().catch(() => undefined);
    reader.releaseLock();
  }
}

export function interruptTask(taskId: string): Promise<{ accepted: boolean }> {
  return request<{ accepted: boolean }>({
    url: `/chat/task/${encodeURIComponent(taskId)}/interrupt`,
    method: 'POST',
  });
}

export function completeBrowserScreenshot(taskId: string, requestId: string,
  result: { accepted: boolean; fileId?: string }): Promise<boolean> {
  return request<boolean>({
    url: `/chat/task/${encodeURIComponent(taskId)}/screenshots/${encodeURIComponent(requestId)}`,
    method: 'POST',
    data: result,
  });
}
