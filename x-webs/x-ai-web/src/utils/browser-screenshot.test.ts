// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';

import { captureBrowserScreenshot } from './browser-screenshot';

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

describe('browser screenshot capture lifecycle', () => {
  it('requests permission immediately and releases sharing if the task was cancelled while choosing a window', async () => {
    let authorize!: (stream: MediaStream) => void;
    const getDisplayMedia = vi.fn(() => new Promise<MediaStream>((resolve) => { authorize = resolve; }));
    vi.stubGlobal('isSecureContext', true);
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getDisplayMedia } });
    const stop = vi.fn();
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {});
    const controller = new AbortController();
    const capturing = captureBrowserScreenshot(controller.signal);
    expect(getDisplayMedia).toHaveBeenCalledExactlyOnceWith({
      video: true, audio: false, selfBrowserSurface: 'include', preferCurrentTab: true,
    });
    controller.abort();
    authorize({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    await expect(capturing).rejects.toMatchObject({ name: 'AbortError' });
    expect(stop).toHaveBeenCalled();
  });

  it('does not open the browser picker in an insecure context', async () => {
    vi.stubGlobal('isSecureContext', false);
    const getDisplayMedia = vi.fn();
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getDisplayMedia } });
    await expect(captureBrowserScreenshot(new AbortController().signal)).rejects.toThrow('HTTPS');
    expect(getDisplayMedia).not.toHaveBeenCalled();
  });
});
