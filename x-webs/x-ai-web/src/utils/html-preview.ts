import { BROWSER_PREVIEW_CONFIG } from '@/config/browser-preview';

/** 自包含产物允许内嵌脚本和样式，限制外部资源与网络请求。 */
export function buildHtmlPreview(html: string): string {
  const document = new DOMParser().parseFromString(html, 'text/html');
  document.querySelectorAll('base, meta[http-equiv]').forEach((element) => element.remove());
  const policy = document.createElement('meta');
  policy.httpEquiv = 'Content-Security-Policy';
  policy.content = BROWSER_PREVIEW_CONFIG.contentSecurityPolicy;
  document.head.prepend(policy);
  return `<!doctype html>\n${document.documentElement.outerHTML}`;
}
