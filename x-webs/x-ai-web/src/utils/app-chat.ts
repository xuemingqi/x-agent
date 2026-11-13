import { createUniqueIdentifier } from '@/utils/identifier';

const APPLICATION_CHAT_REQUEST_KEY = 'x-ai-application-chat-request';
const REQUEST_MAX_AGE_MS = 10 * 60 * 1000;
const MAX_PROMPT_LENGTH = 20000;

/**
 * 仅由应用页的用户操作创建一次性交接，外部链接不能直接触发 Agent 请求。
 */
export function createApplicationChatRoute(prompt: string): { path: string; query: { appRequest: string } } {
  const content = prompt.trim();
  if (!content || content.length > MAX_PROMPT_LENGTH) throw new Error('应用需求不能为空或超过 20000 字符');
  const token = createUniqueIdentifier();
  sessionStorage.setItem(APPLICATION_CHAT_REQUEST_KEY, JSON.stringify({ token, prompt: content, createdAt: Date.now() }));
  return { path: '/chat', query: { appRequest: token } };
}

/**
 * 同一标签页中的请求只消费一次；URL 中不携带需求正文。
 */
export function consumeApplicationChatRequest(token: string): string | null {
  const content = sessionStorage.getItem(APPLICATION_CHAT_REQUEST_KEY);
  if (!content) return null;
  try {
    const request = JSON.parse(content);
    if (request.token !== token) return null;
    sessionStorage.removeItem(APPLICATION_CHAT_REQUEST_KEY);
    const age = Date.now() - request.createdAt;
    return typeof request.prompt === 'string' && request.prompt.length <= MAX_PROMPT_LENGTH
      && request.prompt.trim() && Number.isFinite(age) && age >= 0 && age <= REQUEST_MAX_AGE_MS
      ? request.prompt : null;
  } catch {
    sessionStorage.removeItem(APPLICATION_CHAT_REQUEST_KEY);
    return null;
  }
}
