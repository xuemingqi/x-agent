// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

import { consumeApplicationChatRequest, createApplicationChatRoute } from './app-chat';

afterEach(() => {
  sessionStorage.clear();
  vi.useRealTimers();
});

describe('application chat handoff', () => {
  it('keeps the prompt out of the URL and consumes a local request only once', () => {
    const route = createApplicationChatRoute('创建一个待办应用');
    expect(JSON.stringify(route)).not.toContain('待办');
    expect(consumeApplicationChatRequest('external-link-token')).toBeNull();
    expect(consumeApplicationChatRequest(route.query.appRequest)).toBe('创建一个待办应用');
    expect(consumeApplicationChatRequest(route.query.appRequest)).toBeNull();
  });

  it('rejects expired handoffs and empty requests', () => {
    vi.useFakeTimers();
    const route = createApplicationChatRoute('创建一个待办应用');
    vi.setSystemTime(Date.now() + 11 * 60 * 1000);
    expect(consumeApplicationChatRequest(route.query.appRequest)).toBeNull();
    expect(() => createApplicationChatRoute('  ')).toThrow();
  });
});
