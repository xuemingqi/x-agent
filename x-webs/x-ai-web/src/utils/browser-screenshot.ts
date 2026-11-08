import { BROWSER_PREVIEW_CONFIG } from '@/config/browser-preview';

export function supportsBrowserScreenshot(): boolean {
  return typeof window !== 'undefined' && window.isSecureContext
    && typeof navigator.mediaDevices?.getDisplayMedia === 'function';
}

/** 用户点击处理器须直接调用，不能先等待网络请求，否则浏览器的临时激活会失效。 */
export async function captureBrowserScreenshot(signal: AbortSignal): Promise<File> {
  if (!supportsBrowserScreenshot()) {
    throw new Error('当前浏览器不支持截图，请使用 HTTPS 或 localhost 下支持屏幕共享的浏览器。');
  }
  signal.throwIfAborted();
  const stream = await navigator.mediaDevices.getDisplayMedia(BROWSER_PREVIEW_CONFIG.displayMediaOptions);
  const video = document.createElement('video');
  const stop = () => stream.getTracks().forEach((track) => track.stop());
  signal.addEventListener('abort', stop, { once: true });
  try {
    signal.throwIfAborted();
    video.muted = true;
    video.playsInline = true;
    video.srcObject = stream;
    await waitForFrame(video, stream, signal);
    signal.throwIfAborted();
    const canvas = document.createElement('canvas');
    // 控制大屏截图体积，同时保留页面文字的可读性。
    const scale = Math.min(1, BROWSER_PREVIEW_CONFIG.captureMaxDimension / Math.max(video.videoWidth, video.videoHeight));
    canvas.width = Math.max(1, Math.round(video.videoWidth * scale));
    canvas.height = Math.max(1, Math.round(video.videoHeight * scale));
    const context = canvas.getContext('2d');
    if (!context) throw new Error('无法创建截图画布');
    context.drawImage(video, 0, 0, canvas.width, canvas.height);
    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob((value) => value ? resolve(value) : reject(new Error('截图生成失败')), 'image/png');
    });
    signal.throwIfAborted();
    return new File([blob], `browser-screenshot-${Date.now()}.png`, { type: 'image/png' });
  } finally {
    stop();
    signal.removeEventListener('abort', stop);
    video.pause();
    video.srcObject = null;
  }
}

/** 等待真实视频帧，防止授权后立即绘制得到全黑图片；取消、断流和超时均释放监听器。 */
function waitForFrame(video: HTMLVideoElement, stream: MediaStream, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    const track = stream.getVideoTracks()[0];
    const cleanup = () => {
      clearTimeout(timer);
      video.removeEventListener('loadeddata', ready);
      video.removeEventListener('resize', ready);
      signal.removeEventListener('abort', aborted);
      track?.removeEventListener('ended', ended);
    };
    const fail = (error: unknown) => { cleanup(); reject(error); };
    const ready = () => {
      if (video.readyState < 2 || !video.videoWidth || !video.videoHeight) return;
      cleanup();
      resolve();
    };
    const aborted = () => fail(new DOMException('截图已取消', 'AbortError'));
    const ended = () => fail(new Error('屏幕共享已结束'));
    const timer = setTimeout(() => fail(new Error('等待截图画面超时，请重试')), BROWSER_PREVIEW_CONFIG.frameTimeoutMs);
    video.addEventListener('loadeddata', ready);
    video.addEventListener('resize', ready);
    signal.addEventListener('abort', aborted, { once: true });
    track?.addEventListener('ended', ended, { once: true });
    if (!track || track.readyState === 'ended' || signal.aborted) {
      aborted();
      return;
    }
    void video.play().then(ready, fail);
  });
}
