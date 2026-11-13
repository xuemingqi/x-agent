import { afterEach, describe, expect, it, vi } from 'vitest';

import { request } from './http';
import { executeAgentAppOperation } from './agent-app';

vi.mock('./http', () => ({ request: vi.fn().mockResolvedValue(null) }));
afterEach(() => vi.clearAllMocks());

describe('application runtime requests', () => {
  it('wraps the explicit HTTP request without leaking the host token into the target request', async () => {
    const signal = new AbortController().signal;
    const outgoing = { method: 'GET', url: 'https://example.test', headers: [], query: [], body: '', timeoutMs: 1000 };
    await executeAgentAppOperation('app-1', 'v1', 'http.request', outgoing, signal);
    expect(request).toHaveBeenCalledWith({ url: '/apps/app-1/runtime/http', method: 'POST',
      data: { version: 'v1', request: outgoing }, timeout: 40000, signal });
  });
  it('pins the running version and keeps authentication inside the shared request layer', async () => {
    const signal = new AbortController().signal;
    await executeAgentAppOperation('app-1', 'version-1', 'model.generate', { prompt: '整理', version: 'forged' }, signal);
    expect(request).toHaveBeenCalledWith(expect.objectContaining({
      url: '/apps/app-1/runtime/generate', method: 'POST', data: { prompt: '整理', version: 'version-1' }, signal,
    }));
  });

  it('allows underscore-prefixed storage keys and rejects normalized path segments before HTTP', async () => {
    const signal = new AbortController().signal;
    await executeAgentAppOperation('app-1', 'version-1', 'storage.get', { key: '_state' }, signal);
    expect(request).toHaveBeenCalledWith(expect.objectContaining({ url: '/apps/app-1/data/_state', params: { version: 'version-1' } }));
    vi.mocked(request).mockClear();
    expect(() => executeAgentAppOperation('app-1', 'version-1', 'storage.remove', { key: '..' }, signal)).toThrow('数据键无效');
    expect(() => executeAgentAppOperation('app-1', 'version-1', 'storage.remove', { key: '.' }, signal)).toThrow('数据键无效');
    expect(request).not.toHaveBeenCalled();
  });
});
