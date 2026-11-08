export const TOKEN_STORAGE_KEY = 'agent-token';

const AUTH_ERROR_CODE = 10400;

export function getToken(): string {
  return localStorage.getItem(TOKEN_STORAGE_KEY) || '';
}

export function saveToken(token: string): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, normalizeToken(token));
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY);
  localStorage.removeItem('agent-model-id');
}

export function authorizationHeader(): Record<string, string> {
  const token = getToken();
  if (!token) {
    return {};
  }
  return {
    'X-TOKEN': token.startsWith('Bearer ') ? token : `Bearer ${token}`,
  };
}

export function handleAuthenticationError(code: number): void {
  if (code !== AUTH_ERROR_CODE) {
    return;
  }
  clearToken();
  if (window.location.pathname === '/login') {
    return;
  }
  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  window.location.assign(`/login?redirect=${encodeURIComponent(redirect)}`);
}

function normalizeToken(token: string): string {
  return token.trim().replace(/^Bearer\s+/i, '');
}
