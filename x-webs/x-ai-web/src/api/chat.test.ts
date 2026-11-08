import { afterEach, describe, expect, it, vi } from 'vitest';

import { streamChat } from './chat';
import { handleAuthenticationError } from '@/utils/auth';

vi.mock('./http', () => ({ API_BASE_URL: '/ai', authHeaders: () => ({}), request: vi.fn() }));
vi.mock('@/utils/auth', () => ({ handleAuthenticationError: vi.fn() }));

const payload = { request: 'Hello', sessionId: 'session', modelId: 'model' };

afterEach(() => {
  vi.unstubAllGlobals();
  vi.clearAllMocks();
});

describe('streamChat', () => {
  it('handles an expired login returned as JSON instead of silently ending the chat', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ code: 10400, msg: '登录已过期', data: null })));
    const onEvent = vi.fn();

    await expect(streamChat(payload, onEvent, new AbortController().signal)).rejects.toThrow('登录已过期');

    expect(handleAuthenticationError).toHaveBeenCalledWith(10400);
    expect(onEvent).not.toHaveBeenCalled();
  });

  it('rejects a non-stream response instead of treating it as a successful task', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Proxy error</html>', {
      headers: { 'Content-Type': 'text/html' },
    })));

    await expect(streamChat(payload, vi.fn(), new AbortController().signal)).rejects.toThrow('服务器未返回事件流');
  });

  it('passes complete SSE events to the callback and releases the response reader', async () => {
    const event = { eventType: 'text_delta', text: '你好', structuredData: {} };
    const response = new Response(`data: ${JSON.stringify({ code: 200, data: event })}\n\n`, {
      headers: { 'Content-Type': 'text/event-stream; charset=UTF-8' },
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));
    const onEvent = vi.fn();

    await streamChat(payload, onEvent, new AbortController().signal);

    expect(onEvent).toHaveBeenCalledExactlyOnceWith(event);
    expect(response.body?.locked).toBe(false);
  });

  it('cancels the stream after a backend error event', async () => {
    const cancel = vi.fn();
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('data: {"code":10501,"msg":"模型不可用","data":null}\n\n'));
      },
      cancel,
    });
    const response = new Response(stream, { headers: { 'Content-Type': 'text/event-stream' } });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response));

    await expect(streamChat(payload, vi.fn(), new AbortController().signal)).rejects.toThrow('模型不可用');

    expect(cancel).toHaveBeenCalledOnce();
    expect(response.body?.locked).toBe(false);
  });
});
