export const BROWSER_PREVIEW_CONFIG = {
  // Chrome 默认排除发起共享的标签页，预览截图需要显式包含并优先推荐当前页。
  displayMediaOptions: {
    video: true,
    audio: false,
    selfBrowserSurface: 'include',
    preferCurrentTab: true,
  },
  captureMaxDimension: 2560,
  frameTimeoutMs: 10000,
  maxHtmlSizeBytes: 2 * 1024 * 1024,
  contentSecurityPolicy: "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
    + "img-src data: blob:; font-src data:; connect-src 'none'; frame-src 'none'; "
    + "object-src 'none'; base-uri 'none'; form-action 'none'",
} as const;
