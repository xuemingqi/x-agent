import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createConversation, getConversation, listConversations, updateConversationKnowledgeBases } from '@/api/conversation';
import { listKnowledgeBases } from '@/api/knowledge';
import { listModels } from '@/api/model';
import { completeBrowserScreenshot, interruptTask, streamChat } from '@/api/chat';
import { captureBrowserScreenshot, supportsBrowserScreenshot } from '@/utils/browser-screenshot';
import { deletePendingConversationFile, uploadConversationFiles } from '@/api/conversation-file';
import { useChatStore } from './chat';
import type { AgentEvent, Conversation, ConversationFile, TokenUsage } from '@/types/chat';

vi.mock('@/api/chat', () => ({
  interruptTask: vi.fn(),
  streamChat: vi.fn(),
  completeBrowserScreenshot: vi.fn(),
}));

vi.mock('@/utils/browser-screenshot', () => ({
  captureBrowserScreenshot: vi.fn(),
  supportsBrowserScreenshot: vi.fn(),
}));

vi.mock('@/api/conversation', () => ({
  createConversation: vi.fn(),
  deleteConversation: vi.fn(),
  getConversation: vi.fn(),
  listConversations: vi.fn(),
  updateConversationKnowledgeBases: vi.fn(),
}));

vi.mock('@/api/knowledge', () => ({ listKnowledgeBases: vi.fn() }));

vi.mock('@/api/conversation-file', () => ({
  deletePendingConversationFile: vi.fn(),
  uploadConversationFiles: vi.fn(),
}));

vi.mock('@/api/model', () => ({
  listModels: vi.fn(),
}));

describe('chat store', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => ''),
      setItem: vi.fn(),
    });
    setActivePinia(createPinia());
    vi.mocked(listModels).mockResolvedValue([{ id: 'model-1', name: 'Model 1', modelName: 'model-1', isDefault: true, modelUrl: 'https://example.com/v1', contextWindowSize: 131072, temperature: null }]);
    vi.mocked(listConversations).mockResolvedValue([]);
    vi.mocked(listKnowledgeBases).mockResolvedValue([
      { id: 'common', name: '公共知识库', description: '', isDefault: true },
      { id: 'rules', name: '业务规则', description: '', isDefault: false },
      { id: 'product', name: '产品说明', description: '', isDefault: false },
    ]);
    vi.mocked(createConversation).mockImplementation(async (ids = []) => ({
      ...emptyConversation('new-session'), knowledgeBaseIds: [...ids],
    }));
    vi.mocked(updateConversationKnowledgeBases).mockImplementation(async (sessionId, ids) => ({
      ...emptyConversation(sessionId), knowledgeBaseIds: [...ids],
    }));
    vi.mocked(getConversation).mockImplementation(async (sessionId) => emptyConversation(sessionId));
    vi.mocked(interruptTask).mockResolvedValue({ accepted: true });
    vi.mocked(supportsBrowserScreenshot).mockReturnValue(true);
    vi.mocked(completeBrowserScreenshot).mockResolvedValue(true);
  });

  it('persists multiple draft knowledge bases before the first request and always leaves the common base automatic', async () => {
    const streams = captureStreams();
    const chat = useChatStore();
    await chat.initialize();
    await chat.selectKnowledgeBases(['common', 'rules', 'rules', 'product']);
    expect(chat.selectedKnowledgeBaseIds).toEqual(['rules', 'product']);
    expect(updateConversationKnowledgeBases).not.toHaveBeenCalled();

    await chat.createConversation();
    expect(createConversation).toHaveBeenCalledWith(['rules', 'product']);
    expect(chat.selectedKnowledgeBaseIds).toEqual(['rules', 'product']);
    const sending = chat.send('按知识库回答');
    expect(streamChat).toHaveBeenCalledWith(expect.objectContaining({ sessionId: 'new-session' }),
      expect.any(Function), expect.any(AbortSignal));
    streams[0].finish();
    await sending;
    chat.closeConversation();
    expect(chat.selectedKnowledgeBaseIds).toEqual([]);
  });

  it('restores each conversation selection and keeps late updates with their originating conversation', async () => {
    vi.mocked(getConversation).mockImplementation(async (sessionId) => ({
      ...emptyConversation(sessionId), knowledgeBaseIds: sessionId === 'session-1' ? ['rules'] : ['product'],
    }));
    const chat = await openChat();
    expect(chat.selectedKnowledgeBaseIds).toEqual(['rules']);
    let finish!: (conversation: Conversation) => void;
    vi.mocked(updateConversationKnowledgeBases).mockReturnValue(new Promise((resolve) => { finish = resolve; }));
    const updating = chat.selectKnowledgeBases(['rules', 'product']);
    expect(chat.savingKnowledgeBases).toBe(true);
    await chat.openConversation('session-2');
    expect(chat.selectedKnowledgeBaseIds).toEqual(['product']);
    finish({ ...emptyConversation('session-1'), knowledgeBaseIds: ['rules', 'product'] });
    await updating;
    expect(chat.selectedKnowledgeBaseIds).toEqual(['product']);
    expect(updateConversationKnowledgeBases).toHaveBeenCalledWith('session-1', ['rules', 'product']);
  });

  it('hides deleted knowledge bases from the selector while keeping common knowledge implicit', async () => {
    const savedIds = ['deleted-base', 'rules', 'common'];
    vi.mocked(getConversation).mockResolvedValue({
      ...emptyConversation('session-1'), knowledgeBaseIds: savedIds,
    });
    const chat = await openChat();

    expect(chat.selectedKnowledgeBaseIds).toEqual(['rules']);
    expect(chat.knowledgeBases.find((base) => base.isDefault)?.id).toBe('common');
    expect(updateConversationKnowledgeBases).not.toHaveBeenCalled();
    expect(savedIds).toEqual(['deleted-base', 'rules', 'common']);
  });

  it('keeps the saved selection on failure and prevents generation while knowledge sources are saving', async () => {
    const chat = await openChat();
    let reject!: (error: Error) => void;
    vi.mocked(updateConversationKnowledgeBases).mockReturnValue(new Promise((_, fail) => { reject = fail; }));
    const updating = chat.selectKnowledgeBases(['rules']);
    const failure = expect(updating).rejects.toThrow('保存失败');
    await chat.send('等待关联保存');
    expect(streamChat).not.toHaveBeenCalled();
    expect(chat.knowledgeSelectionDisabled).toBe(true);
    reject(new Error('保存失败'));
    await failure;
    expect(chat.selectedKnowledgeBaseIds).toEqual([]);
    expect(chat.savingKnowledgeBases).toBe(false);
  });

  it('keeps knowledge sources fixed throughout generation and a paused queue', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('查询规则');
    const failure = expect(sending).rejects.toThrow('连接已断开');
    await chat.selectKnowledgeBases(['product']);
    expect(updateConversationKnowledgeBases).not.toHaveBeenCalled();
    expect(chat.knowledgeSelectionDisabled).toBe(true);
    await chat.send('后续查询');
    streams[0].close();
    await failure;
    expect(chat.queuePaused).toBe(true);
    expect(chat.knowledgeSelectionDisabled).toBe(true);
    await chat.selectKnowledgeBases(['product']);
    expect(updateConversationKnowledgeBases).not.toHaveBeenCalled();
  });

  it('replaces cumulative token snapshots without double counting and restores them from history', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('查询资料');
    const usage: TokenUsage = {
      inputTokens: 150, outputTokens: 50, totalTokens: 200, cachedTokens: 30,
      cacheCreationTokens: 10, reasoningTokens: 20, toolUsePromptTokens: 15, modelCalls: 2, timeSeconds: 0.75,
    };
    streams[0].onEvent(agentEvent('token_usage', 'session-1', { usage }));
    streams[0].onEvent(agentEvent('token_usage', 'session-1', { usage }));
    streams[0].onEvent(agentEvent('agent_result', 'session-1', { text: '已找到资料' }));
    streams[0].finish();
    await sending;
    expect(chat.messages.find((message) => message.role === 'ASSISTANT')?.usage).toEqual(usage);

    vi.mocked(getConversation).mockResolvedValue({
      ...emptyConversation('history'),
      messages: [
        { id: '1', parentId: null, role: 'USER', content: '查询资料', toolCallId: null, createdAt: null },
        { id: '2', parentId: '1', role: 'ASSISTANT', content: '已找到资料', toolCallId: null, createdAt: null, usage },
      ],
    });
    await chat.openConversation('history');
    expect(chat.messages.find((message) => message.role === 'ASSISTANT')?.usage).toEqual(usage);
  });

  it('never drains a previous login queue after the store is reset', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const previousSending = chat.send('previous user request');
    const previousResult = previousSending.catch((error: unknown) => error);
    await chat.send('must not send after logout');
    chat.reset();
    await chat.initialize();
    await chat.openConversation('session-1');
    const currentSending = chat.send('current login request');

    streams[0].finish();
    await previousResult;
    expect(streamChat).toHaveBeenCalledTimes(2);
    expect(chat.queuedMessages).toEqual([]);
    expect(chat.messages.filter((message) => message.role === 'USER').map((message) => message.content))
      .toEqual(['current login request']);
    expect(chat.sending).toBe(true);

    streams[1].finish();
    await currentSending;
  });

  it('shows image-only results and deduplicates files repeated across tool and final events', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('画张图');
    const file = {
      id: 'image-1', sessionId: 'session-1', messageId: 'user-1', kind: 2,
      name: 'image.png', contentType: 'image/png', sizeBytes: 100, workspacePath: 'output/image.png', createdAt: null,
    } as ConversationFile;
    streams[0]!.onEvent(agentEvent('tool_result_end', 'session-1', { files: [file] }));
    streams[0]!.onEvent(agentEvent('agent_result', 'session-1', { text: '', files: [file] }));
    streams[0]!.finish();
    await sending;

    expect(chat.messages.at(-1)?.files).toEqual([file]);
    expect(chat.messages.at(-1)?.content).toBe('');
    expect(chat.messages.at(-1)?.processing).toBe(false);
  });

  it('opens HTML and waits for consent, then returns screenshot without draining the ordinary queue', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('写一个 HTML');
    const preview = htmlFile();
    streams[0].onEvent(agentEvent('tool_result_end', 'session-1', { files: [preview] }));
    expect(chat.previewFile?.id).toBe(preview.id);
    expect(chat.previewOpen).toBe(true);
    chat.closeHtmlPreview();
    streams[0].onEvent(agentEvent('agent_result', 'session-1', { files: [preview] }));
    expect(chat.previewFile?.id).toBe(preview.id);
    expect(chat.previewOpen).toBe(false);
    streams[0].onEvent(screenshotEvent(preview));
    expect(chat.previewOpen).toBe(true);
    const request = chat.messages.at(-1)!.screenshots![0];
    expect(captureBrowserScreenshot).not.toHaveBeenCalled();
    expect(uploadConversationFiles).not.toHaveBeenCalled();
    await chat.send('稍后再处理这条消息');
    const screenshot = { ...preview, id: 'screenshot-1', name: 'screenshot.png', contentType: 'image/png', kind: 1 } as ConversationFile;
    vi.mocked(captureBrowserScreenshot).mockResolvedValue(new File(['png'], 'screenshot.png'));
    vi.mocked(uploadConversationFiles).mockResolvedValue([screenshot]);
    await chat.respondToScreenshot(request, true);
    expect(completeBrowserScreenshot).toHaveBeenCalledWith('task-1', 'capture-1', { accepted: true, fileId: 'screenshot-1' });
    expect(request.status).toBe('received');
    expect(request.files).toEqual([screenshot]);
    expect(chat.pendingFiles).toEqual([]);
    expect(chat.queuedMessages).toHaveLength(1);
    expect(streams).toHaveLength(1);
    streams[0].finish();
    await vi.waitFor(() => expect(streams).toHaveLength(2));
    streams[1].finish();
    await sending;
  });

  it('keeps screenshot upload and preview scoped to the originating conversation when switching sessions', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('写 HTML');
    streams[0].onEvent(screenshotEvent(htmlFile()));
    const request = chat.messages.at(-1)!.screenshots![0];
    let captured!: (file: File) => void;
    vi.mocked(captureBrowserScreenshot).mockReturnValue(new Promise((resolve) => { captured = resolve; }));
    vi.mocked(uploadConversationFiles).mockResolvedValue([{ ...htmlFile(), id: 'screenshot' }]);
    const responding = chat.respondToScreenshot(request, true);
    await chat.openConversation('session-2');
    expect(chat.previewFile).toBeUndefined();
    captured(new File(['png'], 'screenshot.png'));
    await responding;
    expect(uploadConversationFiles).toHaveBeenCalledWith('session-1', expect.any(Array));
    expect(chat.messages).toEqual([]);
    streams[0].finish();
    await sending;
  });

  it('anchors screenshot requests between earlier and later streamed reasoning', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('检查页面');
    streams[0].onEvent(agentEvent('text_delta', 'session-1', { delta: '先查看页面效果' }));
    streams[0].onEvent(screenshotEvent(htmlFile()));
    const message = chat.messages.at(-1)!;
    const first = message.screenshots![0];
    expect(message.pendingText).toBe('');
    expect(first.reasoningOffset).toBe(1);
    expect(message.reasoning![0].content).toBe('先查看页面效果');

    streams[0].onEvent(agentEvent('thinking_block_delta', 'session-1', { delta: '收到截图，分析棋盘' }));
    expect(message.reasoning).toHaveLength(2);
    expect(message.reasoning![0].content).toBe('先查看页面效果');
    const second = screenshotEvent(htmlFile());
    second.structuredData.requestId = 'capture-2';
    streams[0].onEvent(second);
    expect(message.screenshots![1].reasoningOffset).toBe(2);
    streams[0].onEvent(agentEvent('text_delta', 'session-1', { delta: '布局检查完成' }));
    streams[0].finish();
    await sending;
    expect(message.content).toBe('布局检查完成');
    expect(message.screenshots!.map((request) => request.reasoningOffset)).toEqual([1, 2]);
  });

  it('returns browser cancellation as a refusal without uploading or opening a new chat turn', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('写 HTML');
    streams[0].onEvent(screenshotEvent(htmlFile()));
    const request = chat.messages.at(-1)!.screenshots![0];
    vi.mocked(captureBrowserScreenshot).mockRejectedValue(new DOMException('cancel', 'NotAllowedError'));
    await chat.respondToScreenshot(request, true);
    expect(request.status).toBe('declined');
    expect(completeBrowserScreenshot).toHaveBeenCalledWith('task-1', 'capture-1', { accepted: false });
    expect(uploadConversationFiles).not.toHaveBeenCalled();
    expect(streams).toHaveLength(1);
    streams[0].finish();
    await sending;
  });

  it('cancels an outstanding capture when the task ends and never uploads its late image', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('写 HTML');
    streams[0].onEvent(screenshotEvent(htmlFile()));
    const request = chat.messages.at(-1)!.screenshots![0];
    let captured!: (file: File) => void;
    vi.mocked(captureBrowserScreenshot).mockReturnValue(new Promise((resolve) => { captured = resolve; }));
    const responding = chat.respondToScreenshot(request, true);
    streams[0].finish();
    await sending;
    expect(request.status).toBe('cancelled');
    expect(vi.mocked(captureBrowserScreenshot).mock.calls[0][0].aborted).toBe(true);
    captured(new File(['png'], 'screenshot.png'));
    await responding;
    expect(uploadConversationFiles).not.toHaveBeenCalled();
    expect(completeBrowserScreenshot).not.toHaveBeenCalled();
  });

  it('preserves a screenshot acknowledged by SSE even if the HTTP acknowledgement is lost', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('写 HTML');
    streams[0].onEvent(screenshotEvent(htmlFile()));
    const request = chat.messages.at(-1)!.screenshots![0];
    vi.mocked(captureBrowserScreenshot).mockResolvedValue(new File(['png'], 'screenshot.png'));
    const screenshot = { ...htmlFile(), id: 'screenshot' };
    vi.mocked(uploadConversationFiles).mockResolvedValue([screenshot]);
    vi.mocked(completeBrowserScreenshot).mockImplementation(async () => {
      streams[0].onEvent(agentEvent('browser_screenshot_completed', 'session-1', {
        requestId: 'capture-1', status: 'received', files: [screenshot],
      }));
      throw new Error('网络断开');
    });
    await chat.respondToScreenshot(request, true);
    expect(request.status).toBe('received');
    expect(deletePendingConversationFile).not.toHaveBeenCalled();
    streams[0].finish();
    await sending;
  });

  it('restores a persisted image-only assistant reply', async () => {
    const file = {
      id: 'image-1', sessionId: 'history', messageId: 'user-1', kind: 2,
      name: 'image.png', contentType: 'image/png', sizeBytes: 100, workspacePath: 'output/image.png', createdAt: null,
    } as ConversationFile;
    vi.mocked(getConversation).mockResolvedValue({
      ...emptyConversation('history'),
      messages: [
        { id: 'user-1', parentId: null, role: 'USER', content: '画图', toolCallId: null, createdAt: null },
        { id: 'assistant-1', parentId: 'user-1', role: 'ASSISTANT', content: '', toolCallId: null, createdAt: null },
      ],
      files: [file],
    });
    const chat = useChatStore();
    await chat.initialize();
    await chat.openConversation('history');

    expect(chat.messages.at(-1)?.role).toBe('ASSISTANT');
    expect(chat.messages.at(-1)?.files).toEqual([file]);
  });

  it('keeps a session stream and its incremental output alive while another conversation is open', async () => {
    let onEvent: ((event: AgentEvent) => void) | undefined;
    let finishStream: (() => void) | undefined;
    let streamSignal: AbortSignal | undefined;
    vi.mocked(streamChat).mockImplementation(async (_payload, eventHandler, signal) => {
      onEvent = eventHandler;
      streamSignal = signal;
      await new Promise<void>((resolve) => {
        finishStream = resolve;
      });
    });

    const chat = useChatStore();
    await chat.initialize();
    await chat.openConversation('session-1');
    const sendPromise = chat.send('first question');
    onEvent?.(agentEvent('task_started', 'session-1'));
    onEvent?.(agentEvent('text_delta', 'session-1', { delta: 'partial ' }));

    await chat.openConversation('session-2');
    onEvent?.(agentEvent('text_delta', 'session-1', { delta: 'answer' }));

    expect(chat.currentSessionId).toBe('session-2');
    expect(chat.messages).toEqual([]);
    expect(streamSignal?.aborted).toBe(false);

    await chat.openConversation('session-1');

    expect(chat.sending).toBe(true);
    expect(chat.messages.at(-1)?.pendingText).toBe('partial answer');
    expect(vi.mocked(getConversation)).toHaveBeenCalledTimes(2);

    onEvent?.(agentEvent('task_end', 'session-1'));
    finishStream?.();
    await sendPromise;
  });

  it('queues repeated sends and drains them in order with their original model and attachments', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('second');
    await chat.selectModel('model-2');
    const file = { id: 'file-1', sessionId: 'session-1', name: 'report.txt' } as ConversationFile;
    vi.mocked(uploadConversationFiles).mockResolvedValue([file]);
    await chat.uploadFiles([new File(['report'], 'report.txt')]);
    await chat.send('third');

    expect(streamChat).toHaveBeenCalledTimes(1);
    expect(chat.pendingFiles).toEqual([]);
    expect(chat.queuedMessages.map((message) => message.content)).toEqual(['second', 'third']);
    streams[0].finish();
    await vi.waitFor(() => expect(streams).toHaveLength(2));
    expect(streamChat).toHaveBeenNthCalledWith(2,
      expect.objectContaining({ request: 'second', modelId: 'model-1', attachmentIds: [] }),
      expect.any(Function), expect.any(AbortSignal));
    streams[1].finish();
    await vi.waitFor(() => expect(streams).toHaveLength(3));
    expect(streamChat).toHaveBeenNthCalledWith(3,
      expect.objectContaining({ request: 'third', modelId: 'model-2', attachmentIds: ['file-1'] }),
      expect.any(Function), expect.any(AbortSignal));
    streams[2].finish();
    await sending;
    expect(chat.sending).toBe(false);
    expect(chat.queuedMessages).toEqual([]);
  });

  it('prioritizes a steered message only after the interrupted stream closes', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('second');
    await chat.send('new direction');
    const steering = chat.steer(chat.queuedMessages[1].id);
    await vi.waitFor(() => expect(interruptTask).toHaveBeenCalledExactlyOnceWith('task-1'));
    await chat.steer(chat.queuedMessages[0].id);
    expect(streams[0].signal.aborted).toBe(false);
    streams[0].event('task_end');
    expect(streamChat).toHaveBeenCalledTimes(1);
    expect(chat.interrupting).toBe(true);
    streams[0].finish();
    await sending;
    await vi.waitFor(() => expect(streams).toHaveLength(2));
    expect(vi.mocked(streamChat).mock.calls[1][0].request).toBe('new direction');
    expect(chat.queuedMessages.map((message) => message.content)).toEqual(['second']);
    expect(chat.interrupting).toBe(false);
    streams[1].finish();
    await vi.waitFor(() => expect(streams).toHaveLength(3));
    streams[2].finish();
    await steering;
  });

  it('waits for a task id when steering before task_started arrives', async () => {
    const streams = captureStreams(false);
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('new direction');
    const steering = chat.steer(chat.queuedMessages[0].id);
    expect(interruptTask).not.toHaveBeenCalled();
    streams[0].event('task_started');
    await vi.waitFor(() => expect(interruptTask).toHaveBeenCalledTimes(1));
    streams[0].finish();
    await sending;
    await vi.waitFor(() => expect(streams).toHaveLength(2));
    streams[1].finish();
    await steering;
  });

  it.each(['', '已经完成的布局'])('hands off steering without a pause greeting or empty reply (partial=%s)', async (partial) => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('生成五子棋');
    streams[0].onEvent(agentEvent('text_delta', 'session-1', { delta: partial }));
    await chat.selectModel('model-2');
    const file = { id: 'reference-1', sessionId: 'session-1', name: '参考.png' } as ConversationFile;
    vi.mocked(uploadConversationFiles).mockResolvedValue([file]);
    await chat.uploadFiles([new File(['image'], '参考.png')]);
    await chat.send('改成深色主题');
    const steering = chat.steer(chat.queuedMessages[0].id);
    await vi.waitFor(() => expect(interruptTask).toHaveBeenCalledTimes(1));
    streams[0].onEvent(agentEvent('agent_result', 'session-1', {
      text: 'I noticed that you have interrupted me. What can I do for you?', generateReason: 'INTERRUPTED',
    }));
    streams[0].event('agent_end');
    streams[0].finish();
    await sending;
    await vi.waitFor(() => expect(streams).toHaveLength(2));

    expect(chat.messages.map((message) => message.content)).toEqual(partial
      ? ['生成五子棋', partial, '改成深色主题', ''] : ['生成五子棋', '改成深色主题', '']);
    expect(chat.sending).toBe(true);
    expect(streams[0].signal.aborted).toBe(false);
    expect(vi.mocked(streamChat).mock.calls[1][0]).toMatchObject({
      request: '改成深色主题', sessionId: 'session-1', modelId: 'model-2', attachmentIds: ['reference-1'],
    });
    streams[1].onEvent(agentEvent('text_delta', 'session-1', { delta: '已调整主题' }));
    streams[1].finish();
    await steering;
    expect(chat.messages.at(-1)?.content).toBe('已调整主题');
  });

  it('keeps a queue in its original conversation while another conversation is open', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('follow-up');
    await chat.openConversation('session-2');
    expect(chat.queuedMessages).toEqual([]);
    streams[0].finish();
    await vi.waitFor(() => expect(streams).toHaveLength(2));
    expect(vi.mocked(streamChat).mock.calls[1][0].sessionId).toBe('session-1');
    expect(chat.messages).toEqual([]);
    streams[1].finish();
    await sending;
  });

  it('stops without automatically sending queued messages and permits explicit continuation', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('later');
    const stopping = chat.stop();
    await vi.waitFor(() => expect(interruptTask).toHaveBeenCalledTimes(1));
    streams[0].finish();
    await Promise.all([sending, stopping]);
    expect(streamChat).toHaveBeenCalledTimes(1);
    expect(chat.queuedMessages[0].content).toBe('later');
    const continuing = chat.steer(chat.queuedMessages[0].id);
    await vi.waitFor(() => expect(streams).toHaveLength(2));
    streams[1].finish();
    await continuing;
  });

  it.each([false, true])('preserves queued messages if interruption is rejected or fails (network=%s)', async (network) => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('later');
    if (network) {
      vi.mocked(interruptTask).mockRejectedValue(new Error('network error'));
    } else {
      vi.mocked(interruptTask).mockResolvedValue({ accepted: false });
    }
    await expect(chat.steer(chat.queuedMessages[0].id)).rejects.toThrow();
    expect(chat.interrupting).toBe(false);
    expect(chat.queuedMessages[0].content).toBe('later');
    expect(streams[0].signal.aborted).toBe(false);
    streams[0].finish();
    await sending;
    expect(streamChat).toHaveBeenCalledTimes(1);
  });

  it('pauses and retains messages after an incomplete stream, including a subagent task_end', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('later');
    const failure = expect(sending).rejects.toThrow('未收到任务结束确认');
    streams[0].event('task_end', 'subagent');
    streams[0].close();
    await failure;
    expect(streamChat).toHaveBeenCalledTimes(1);
    expect(chat.sending).toBe(false);
    expect(chat.queuedMessages.map((message) => message.content)).toEqual(['later']);
    expect(chat.messages[0].content).toBe('first');
    expect(chat.queuePaused).toBe(true);
  });

  it('preserves a paused queue when switching away and back', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('later');
    const stopping = chat.stop();
    streams[0].finish();
    await Promise.all([sending, stopping]);
    await chat.openConversation('session-2');
    await chat.openConversation('session-1');
    expect(chat.queuedMessages[0].content).toBe('later');
    expect(streamChat).toHaveBeenCalledTimes(1);
  });

  it('holds an edited queue item until saved and preserves its model, files, and position', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    const file = { id: 'file-1', sessionId: 'session-1', name: 'report.txt' } as ConversationFile;
    vi.mocked(uploadConversationFiles).mockResolvedValue([file]);
    await chat.uploadFiles([new File(['report'], 'report.txt')]);
    await chat.send('original');
    await chat.send('last');
    const id = chat.queuedMessages[0].id;
    chat.editQueuedMessage(id);
    chat.updateQueuedDraft(id, 'edited');
    streams[0].finish();
    await sending;
    expect(streams).toHaveLength(1);
    expect(chat.sending).toBe(false);
    await chat.selectModel('model-2');
    const saving = chat.finishQueuedEdit(id, true);
    expect(vi.mocked(streamChat).mock.calls[1][0]).toMatchObject({
      request: 'edited', modelId: 'model-1', attachmentIds: ['file-1'],
    });
    streams[1].finish();
    await vi.waitFor(() => expect(streams).toHaveLength(3));
    expect(vi.mocked(streamChat).mock.calls[2][0].request).toBe('last');
    streams[2].finish();
    await saving;
  });

  it('retains edit drafts across conversations and cancellation respects a stopped queue', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('original');
    const id = chat.queuedMessages[0].id;
    chat.editQueuedMessage(id);
    chat.updateQueuedDraft(id, 'unfinished');
    const stopping = chat.stop();
    streams[0].finish();
    await Promise.all([sending, stopping]);
    await chat.openConversation('session-2');
    await chat.openConversation('session-1');
    expect(chat.queuedMessages[0].draft).toBe('unfinished');
    await chat.finishQueuedEdit(id, false);
    expect(chat.queuedMessages[0].content).toBe('original');
    expect(chat.queuedMessages[0].draft).toBeUndefined();
    expect(streams).toHaveLength(1);
  });

  it('rejects an empty edit without losing the message', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    await chat.send('original');
    const id = chat.queuedMessages[0].id;
    chat.editQueuedMessage(id);
    chat.updateQueuedDraft(id, '  ');
    await expect(chat.finishQueuedEdit(id, true)).rejects.toThrow('消息内容不能为空');
    expect(chat.queuedMessages[0].content).toBe('original');
    streams[0].finish();
    await sending;
  });

  it('never sends a message being deleted while attachment cleanup is pending', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    const file = { id: 'file-1', sessionId: 'session-1', name: 'report.txt' } as ConversationFile;
    vi.mocked(uploadConversationFiles).mockResolvedValue([file]);
    await chat.uploadFiles([new File(['report'], 'report.txt')]);
    await chat.send('remove me');
    await chat.send('keep me');
    let finishDelete!: () => void;
    vi.mocked(deletePendingConversationFile).mockImplementation(() => new Promise<boolean>((resolve) => {
      finishDelete = () => resolve(true);
    }));
    const removing = chat.removeQueuedMessage(chat.queuedMessages[0].id);
    streams[0].finish();
    await sending;
    expect(streams).toHaveLength(1);
    expect(deletePendingConversationFile).toHaveBeenCalledExactlyOnceWith('session-1', 'file-1');
    finishDelete();
    await vi.waitFor(() => expect(streams).toHaveLength(2));
    expect(vi.mocked(streamChat).mock.calls[1][0].request).toBe('keep me');
    streams[1].finish();
    await removing;
    expect(chat.queuedMessages).toEqual([]);
  });

  it('keeps the queue paused and recoverable when attachment deletion fails', async () => {
    const streams = captureStreams();
    const chat = await openChat();
    const sending = chat.send('first');
    const file = { id: 'file-1', sessionId: 'session-1', name: 'report.txt' } as ConversationFile;
    vi.mocked(uploadConversationFiles).mockResolvedValue([file]);
    await chat.uploadFiles([new File(['report'], 'report.txt')]);
    await chat.send('keep on failure');
    vi.mocked(deletePendingConversationFile).mockRejectedValue(new Error('删除失败'));
    await expect(chat.removeQueuedMessage(chat.queuedMessages[0].id)).rejects.toThrow('删除失败');
    expect(chat.queuedMessages[0].removing).toBe(false);
    expect(chat.queuedMessages[0].files).toEqual([file]);
    streams[0].finish();
    await sending;
    expect(streams).toHaveLength(1);
    expect(chat.queuePaused).toBe(true);
  });
});

async function openChat() {
  const chat = useChatStore();
  await chat.initialize();
  await chat.openConversation('session-1');
  return chat;
}

function htmlFile(): ConversationFile {
  return { id: 'html-1', sessionId: 'session-1', messageId: 'user-1', kind: 2,
    name: 'page.html', contentType: 'text/html', sizeBytes: 100, workspacePath: 'output/page.html', createdAt: null };
}

function screenshotEvent(previewFile: ConversationFile): AgentEvent {
  return agentEvent('browser_screenshot_requested', previewFile.sessionId, {
    requestId: 'capture-1', previewFile, reason: '检查页面布局', expiresAt: Date.now() + 60000,
  });
}

function captureStreams(startImmediately = true) {
  const streams: {
    signal: AbortSignal;
    event: (type: string, scope?: 'main' | 'subagent') => void;
    finish: () => void;
    close: () => void;
    onEvent: (event: AgentEvent) => void;
  }[] = [];
  vi.mocked(streamChat).mockImplementation((payload, onEvent, signal) => new Promise<void>((resolve) => {
    const event = (type: string, scope: 'main' | 'subagent' = 'main') => {
      onEvent({ ...agentEvent(type, payload.sessionId), scope });
    };
    streams.push({ signal, event, onEvent, finish: () => { event('task_end'); resolve(); }, close: resolve });
    if (startImmediately) event('task_started');
  }));
  return streams;
}

function emptyConversation(sessionId: string): Conversation {
  return {
    sessionId,
    title: null,
    knowledgeBaseIds: [],
    createdAt: null,
    updatedAt: null,
    messages: [],
    files: [],
  };
}

function agentEvent(
  eventType: string,
  sessionId: string,
  structuredData: Record<string, unknown> = {},
): AgentEvent {
  return {
    eventType,
    taskId: 'task-1',
    userId: 'user-1',
    sessionId,
    structuredData,
  };
}
