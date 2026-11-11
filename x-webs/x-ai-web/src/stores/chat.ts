import { defineStore } from 'pinia';
import { computed, reactive, ref } from 'vue';

import { completeBrowserScreenshot, interruptTask, streamChat } from '@/api/chat';
import {
  deletePendingConversationFile,
  uploadConversationFiles,
} from '@/api/conversation-file';
import {
  createConversation as createConversationRequest,
  deleteConversation,
  getConversation,
  listConversations,
} from '@/api/conversation';
import { useModelSettingStore } from '@/stores/model-setting';
import { CONVERSATION_FILE_KIND } from '@/types/chat';
import { createUniqueIdentifier } from '@/utils/identifier';
import { isHtmlFile } from '@/utils/conversation-file';
import { captureBrowserScreenshot, supportsBrowserScreenshot } from '@/utils/browser-screenshot';
import type {
  AgentEvent,
  BrowserScreenshotRequest,
  Conversation,
  ConversationFile,
  ConversationMessage,
  MessageRole,
  TokenUsage,
} from '@/types/chat';

export type DisplayMessageKind = 'message' | 'status';

export interface DisplayReasoningItem {
  id: string;
  type: 'text' | 'tool';
  content: string;
  toolCallId?: string;
  toolName?: string;
  source?: string;
  state?: 'running' | 'success' | 'failed';
  result?: string;
}

export interface DisplayMessage {
  id: string;
  role: MessageRole;
  content: string;
  kind: DisplayMessageKind;
  createdAt?: string | null;
  reasoning?: DisplayReasoningItem[];
  pendingText?: string;
  processing?: boolean;
  processingStartedAt?: number;
  processingEndedAt?: number;
  files?: ConversationFile[];
  screenshots?: BrowserScreenshotRequest[];
  usage?: TokenUsage;
}

interface HistoryToolCall {
  name: string;
  precedingText: string;
}

interface HistoryToolResult {
  name: string;
  result: string;
}

export interface QueuedMessage {
  id: string;
  content: string;
  modelId: string;
  files: ConversationFile[];
  draft?: string;
  removing?: boolean;
}

interface ConversationRuntimeState {
  messages: DisplayMessage[];
  pendingFiles: ConversationFile[];
  queuedMessages: QueuedMessage[];
  queuePaused: boolean;
  interrupting: boolean;
  loading: boolean;
  sending: boolean;
  activeTaskId: string;
  previewFile?: ConversationFile;
  previewOpen: boolean;
}

function createConversationRuntimeState(): ConversationRuntimeState {
  return {
    messages: [],
    pendingFiles: [],
    queuedMessages: [],
    queuePaused: false,
    interrupting: false,
    loading: false,
    sending: false,
    activeTaskId: '',
    previewOpen: false,
  };
}

export const useChatStore = defineStore('chat', () => {
  const modelSetting = useModelSettingStore();
  const models = computed(() => modelSetting.models);
  const selectedModelId = ref('');
  const conversations = ref<Conversation[]>([]);
  const currentSessionId = ref('');
  const runtimeStates = reactive(new Map<string, ConversationRuntimeState>());
  const uploadingFiles = ref(false);
  const abortControllers = new Map<string, AbortController>();
  const runningQueues = new Map<string, Promise<void>>();
  const taskStarts = new Map<string, Promise<string | undefined>>();
  const screenshotControllers = new Map<string, AbortController>();

  const selectedModel = computed(() => models.value.find((model) => model.id === selectedModelId.value));
  const messages = computed(() => getCurrentRuntimeState()?.messages || []);
  const loading = computed(() => getCurrentRuntimeState()?.loading || false);
  const sending = computed(() => getCurrentRuntimeState()?.sending || false);
  const pendingFiles = computed(() => getCurrentRuntimeState()?.pendingFiles || []);
  const queuedMessages = computed(() => getCurrentRuntimeState()?.queuedMessages || []);
  const interrupting = computed(() => getCurrentRuntimeState()?.interrupting || false);
  const queuePaused = computed(() => getCurrentRuntimeState()?.queuePaused || false);
  const previewFile = computed(() => getCurrentRuntimeState()?.previewFile);
  const previewOpen = computed(() => getCurrentRuntimeState()?.previewOpen || false);

  function openHtmlPreview(file: ConversationFile): void {
    if (file.sessionId === currentSessionId.value && isHtmlFile(file)) {
      const state = getRuntimeState(file.sessionId);
      state.previewFile = file;
      state.previewOpen = true;
    }
  }

  function closeHtmlPreview(): void {
    const runtimeState = getCurrentRuntimeState();
    if (runtimeState) runtimeState.previewOpen = false;
  }

  async function respondToScreenshot(request: BrowserScreenshotRequest, accepted: boolean): Promise<void> {
    const state = runtimeStates.get(request.sessionId);
    if (!state?.sending || state.activeTaskId !== request.taskId || request.status !== 'pending') return;
    if (Date.now() >= request.expiresAt) {
      request.status = 'expired';
      return;
    }
    const controller = new AbortController();
    screenshotControllers.set(request.requestId, controller);
    request.error = undefined;
    request.status = accepted ? 'capturing' : 'declining';
    let uploaded: ConversationFile | undefined;
    try {
      if (!accepted) {
        await completeBrowserScreenshot(request.taskId, request.requestId, { accepted: false });
        request.status = 'declined';
        return;
      }
      // 必须在点击事件内立即发起授权，上传和任何其他异步请求都在截图之后。
      const screenshot = await captureBrowserScreenshot(controller.signal);
      controller.signal.throwIfAborted();
      request.status = 'uploading';
      [uploaded] = await uploadConversationFiles(request.sessionId, [screenshot]);
      controller.signal.throwIfAborted();
      if (!uploaded) throw new Error('截图上传失败');
      await completeBrowserScreenshot(request.taskId, request.requestId, { accepted: true, fileId: uploaded.id });
      request.files = [uploaded];
      request.status = 'received';
    } catch (error) {
      // 后端状态事件可能先于回传接口响应到达，以已收到的结果为准。
      if ((request as BrowserScreenshotRequest).status === 'received') return;
      if (uploaded) {
        try {
          await deletePendingConversationFile(request.sessionId, uploaded.id);
        } catch {
          // 已被后端绑定的附件不能删除；保留服务端结果，避免回滚已接受的截图。
        }
      }
      if (controller.signal.aborted) return;
      if (error instanceof DOMException && (error.name === 'NotAllowedError' || error.name === 'AbortError')) {
        try {
          await completeBrowserScreenshot(request.taskId, request.requestId, { accepted: false });
          request.status = 'declined';
          return;
        } catch (cause) {
          error = cause;
        }
      }
      request.status = Date.now() >= request.expiresAt ? 'expired' : 'pending';
      request.error = error instanceof Error ? error.message : '截图回传失败，请重试或拒绝';
      throw error;
    } finally {
      screenshotControllers.delete(request.requestId);
    }
  }

  function cancelScreenshots(message: DisplayMessage): void {
    message.screenshots?.forEach((request) => {
      if (['pending', 'capturing', 'uploading', 'declining'].includes(request.status)) {
        request.status = 'cancelled';
        screenshotControllers.get(request.requestId)?.abort();
      }
    });
  }

  async function initialize(): Promise<void> {
    await modelSetting.initialize();
    if (!models.value.some((model) => model.id === selectedModelId.value)) {
      selectedModelId.value = modelSetting.defaultModelId || models.value[0]?.id || '';
    }
    await loadConversations();
  }

  async function selectModel(modelId: string): Promise<void> {
    if (modelId === selectedModelId.value) {
      return;
    }
    selectedModelId.value = modelId;
  }

  async function loadConversations(): Promise<void> {
    conversations.value = await listConversations();
  }

  async function createConversation(): Promise<Conversation> {
    if (!modelSetting.requireConfigured()) {
      throw new Error('请先配置个人模型');
    }
    if (!selectedModelId.value) {
      throw new Error('请先选择模型');
    }
    const conversation = await createConversationRequest();
    currentSessionId.value = conversation.sessionId;
    runtimeStates.set(conversation.sessionId, createConversationRuntimeState());
    await loadConversations();
    return conversation;
  }

  async function openConversation(sessionId: string): Promise<void> {
    if (!sessionId) {
      return;
    }
    currentSessionId.value = sessionId;
    const runtimeState = getRuntimeState(sessionId);
    if (runtimeState.sending || runtimeState.interrupting) {
      return;
    }
    runtimeState.loading = true;
    try {
      const conversation = await getConversation(sessionId);
      runtimeState.messages = mapHistoryMessages(conversation.messages, conversation.files || []);
    } finally {
      runtimeState.loading = false;
    }
  }

  function closeConversation(): void {
    currentSessionId.value = '';
  }

  async function removeConversation(sessionId: string): Promise<void> {
    await deleteConversation(sessionId);
    const runtimeState = runtimeStates.get(sessionId);
    if (runtimeState) {
      runtimeState.queuePaused = true;
      runtimeState.messages.forEach(cancelScreenshots);
    }
    abortControllers.get(sessionId)?.abort();
    abortControllers.delete(sessionId);
    runtimeStates.delete(sessionId);
    if (currentSessionId.value === sessionId) {
      currentSessionId.value = '';
    }
    await loadConversations();
  }

  async function send(content: string): Promise<void> {
    if (!modelSetting.requireConfigured()) {
      throw new Error('请先配置个人模型');
    }
    const sessionId = currentSessionId.value;
    if (!selectedModelId.value || !sessionId) {
      return;
    }
    const runtimeState = getRuntimeState(sessionId);
    if (runtimeState.loading || uploadingFiles.value || (!content.trim() && !runtimeState.pendingFiles.length)) {
      return;
    }
    runtimeState.queuedMessages.push({
      id: createUniqueIdentifier(),
      content: content.trim() || '请分析所附文件。',
      modelId: selectedModelId.value,
      files: [...runtimeState.pendingFiles],
    });
    runtimeState.pendingFiles = [];
    if (!runtimeState.sending && !runtimeState.interrupting) {
      runtimeState.queuePaused = false;
      await drainQueue(sessionId, runtimeState);
    }
  }

  async function drainQueue(sessionId: string, runtimeState: ConversationRuntimeState): Promise<void> {
    const nextMessage = runtimeState.queuedMessages[0];
    if (runtimeStates.get(sessionId) !== runtimeState || runtimeState.sending || runtimeState.interrupting || runtimeState.queuePaused
      || !nextMessage || nextMessage.draft !== undefined || nextMessage.removing) {
      return;
    }
    runtimeState.sending = true;
    const runningQueue = runQueue(sessionId, runtimeState);
    runningQueues.set(sessionId, runningQueue);
    try {
      await runningQueue;
    } finally {
      if (runningQueues.get(sessionId) === runningQueue) runningQueues.delete(sessionId);
    }
    if (runtimeStates.get(sessionId) === runtimeState) await loadConversations();
  }

  async function runQueue(sessionId: string, runtimeState: ConversationRuntimeState): Promise<void> {
    try {
      while (runtimeStates.get(sessionId) === runtimeState && !runtimeState.queuePaused && runtimeState.queuedMessages.length) {
        // 编辑或删除完成前保留队首位置，不能自动发送正在修改的内容。
        if (runtimeState.queuedMessages[0].draft !== undefined || runtimeState.queuedMessages[0].removing) {
          break;
        }
        const message = runtimeState.queuedMessages.shift()!;
        try {
          await sendMessage(sessionId, runtimeState, message);
        } catch (error) {
          runtimeState.queuePaused = true;
          // 已提交的消息留在对话中；只保留尚未提交的队列，避免重复执行或重新绑定附件。
          throw error;
        }
      }
    } finally {
      runtimeState.sending = false;
    }
  }

  async function sendMessage(sessionId: string, runtimeState: ConversationRuntimeState,
    queuedMessage: QueuedMessage): Promise<void> {
    const { content: requestContent, files: attachments, modelId } = queuedMessage;
    const userMessage: DisplayMessage = {
      id: createUniqueIdentifier(),
      role: 'USER',
      content: requestContent,
      kind: 'message',
      createdAt: new Date().toISOString(),
      files: attachments,
    };
    const assistantMessageId = createUniqueIdentifier();
    const assistantMessage: DisplayMessage = {
      id: assistantMessageId,
      role: 'ASSISTANT',
      content: '',
      kind: 'message',
      createdAt: new Date().toISOString(),
      reasoning: [],
      pendingText: '',
      processing: true,
    };
    runtimeState.messages.push(userMessage, assistantMessage);
    runtimeState.activeTaskId = '';
    let resolveTaskStart!: (taskId: string | undefined) => void;
    taskStarts.set(sessionId, new Promise((resolve) => { resolveTaskStart = resolve; }));
    let taskEnded = false;
    const abortController = new AbortController();
    abortControllers.set(sessionId, abortController);
    try {
      await streamChat({
        request: requestContent,
        sessionId,
        modelId,
        attachmentIds: attachments.map((file) => file.id),
        browserScreenshotEnabled: supportsBrowserScreenshot(),
      }, (event) => {
        if (runtimeStates.get(sessionId) !== runtimeState) return;
        handleEvent(runtimeState, event, assistantMessageId);
        if (event.scope !== 'subagent' && !event.source) {
          if (event.eventType === 'task_started') {
            resolveTaskStart(event.taskId);
          } else if (event.eventType === 'task_end') {
            taskEnded = true;
            runtimeState.activeTaskId = '';
          }
        }
      }, abortController.signal);
      if (!taskEnded) {
        throw new Error('连接已断开，未收到任务结束确认，请检查后重试');
      }
    } catch (error) {
      finishFailedMessage(runtimeState, assistantMessageId);
      throw error;
    } finally {
      const currentMessage = runtimeState.messages.find((message) => message.id === assistantMessageId);
      if (currentMessage) cancelScreenshots(currentMessage);
      if (currentMessage?.processing) {
        currentMessage.processing = false;
        currentMessage.processingEndedAt = Date.now();
      }
      runtimeState.activeTaskId = '';
      resolveTaskStart(undefined);
      if (runtimeStates.get(sessionId) === runtimeState) taskStarts.delete(sessionId);
      if (abortControllers.get(sessionId) === abortController) abortControllers.delete(sessionId);
    }
  }

  function finishFailedMessage(runtimeState: ConversationRuntimeState, assistantMessageId: string): void {
    const message = runtimeState.messages.find((item) => item.id === assistantMessageId);
    if (!message) {
      return;
    }
    message.reasoning?.forEach((item) => {
      if (item.type === 'tool' && item.state === 'running') {
        item.state = 'failed';
        item.content = '调用失败';
      }
    });
    removeEmptyAssistantMessage(runtimeState, message);
  }

  /** 尚未输出内容就结束的任务不留下空白回复占位。 */
  function removeEmptyAssistantMessage(runtimeState: ConversationRuntimeState, message: DisplayMessage): void {
    if (!message.content && !message.pendingText && !message.reasoning?.length && !message.files?.length
      && !message.screenshots?.length && !message.usage) {
      runtimeState.messages = runtimeState.messages.filter((item) => item.id !== message.id);
    }
  }

  async function uploadFiles(files: File[]): Promise<void> {
    if (!modelSetting.requireConfigured()) {
      throw new Error('请先配置个人模型');
    }
    const runtimeState = getCurrentRuntimeState();
    if (!currentSessionId.value || !runtimeState || !files.length || uploadingFiles.value) {
      return;
    }
    if (runtimeState.pendingFiles.length + files.length > 5) {
      throw new Error('每条消息最多上传 5 个文件');
    }
    uploadingFiles.value = true;
    try {
      const uploaded = await uploadConversationFiles(currentSessionId.value, files);
      runtimeState.pendingFiles.push(...uploaded);
    } finally {
      uploadingFiles.value = false;
    }
  }

  async function removePendingFile(fileId: string): Promise<void> {
    const runtimeState = getCurrentRuntimeState();
    const file = runtimeState?.pendingFiles.find((item) => item.id === fileId);
    if (!runtimeState || !file) {
      return;
    }
    await deletePendingConversationFile(file.sessionId, file.id);
    runtimeState.pendingFiles = runtimeState.pendingFiles.filter((item) => item.id !== fileId);
  }

  function handleEvent(
    runtimeState: ConversationRuntimeState,
    event: AgentEvent,
    assistantMessageId: string,
  ): void {
    const assistantMessage = runtimeState.messages.find((message) => message.id === assistantMessageId);
    if (!assistantMessage) {
      return;
    }
    const source = event.source || (event.scope === 'subagent' ? 'subagent' : undefined);
    const isSubagent = event.scope === 'subagent' || Boolean(source);
    if (event.eventType === 'browser_screenshot_requested') {
      const preview = parseEventFiles([event.structuredData.previewFile])[0];
      const requestId = String(event.structuredData.requestId || '');
      if (!preview || preview.sessionId !== event.sessionId || !isHtmlFile(preview) || !requestId) return;
      assistantMessage.screenshots ||= [];
      if (assistantMessage.screenshots.some((request) => request.requestId === requestId)) return;
      commitPendingText(assistantMessage);
      assistantMessage.screenshots.push({
        requestId, taskId: event.taskId, sessionId: event.sessionId, previewFile: preview,
        reason: String(event.structuredData.reason || ''), expiresAt: Number(event.structuredData.expiresAt),
        reasoningOffset: assistantMessage.reasoning?.length || 0, requestedAt: Date.now(),
        status: 'pending', files: [],
      });
      runtimeState.previewFile = preview;
      runtimeState.previewOpen = true;
      return;
    }
    if (event.eventType === 'browser_screenshot_completed') {
      const request = assistantMessage.screenshots?.find((item) => item.requestId === event.structuredData.requestId);
      const status = String(event.structuredData.status);
      if (request && ['received', 'declined', 'expired', 'cancelled'].includes(status)) {
        request.status = status as BrowserScreenshotRequest['status'];
        request.files = parseEventFiles(event.structuredData.files);
        request.error = undefined;
        screenshotControllers.get(request.requestId)?.abort();
      }
      return;
    }
    if (event.eventType === 'token_usage') {
      assistantMessage.usage = event.structuredData.usage as TokenUsage;
      return;
    }
    if (event.eventType === 'task_started') {
      if (isSubagent) {
        return;
      }
      runtimeState.activeTaskId = event.taskId;
      assistantMessage.processingStartedAt = Date.now();
      return;
    }
    if (event.eventType === 'text_delta') {
      if (isSubagent) {
        appendReasoningText(assistantMessage, String(event.structuredData.delta || ''), source);
        return;
      }
      assistantMessage.pendingText = (assistantMessage.pendingText || '')
        + String(event.structuredData.delta || '');
      return;
    }
    if (event.eventType === 'thinking_block_delta') {
      appendReasoningText(
        assistantMessage,
        String(event.structuredData.delta || ''),
        isSubagent ? source : undefined,
      );
      return;
    }
    if (event.eventType === 'agent_result') {
      if (isSubagent) {
        return;
      }
      if (event.structuredData.usage) assistantMessage.usage = event.structuredData.usage as TokenUsage;
      appendEventFiles(assistantMessage, event);
      openPublishedHtml(runtimeState, event);
      const generateReason = event.generateReason || String(event.structuredData.generateReason || '');
      // 中断是任务控制信号，不能覆盖已经输出的内容或展示 SDK 的暂停问候。
      if (generateReason === 'INTERRUPTED') return;
      if (generateReason === 'TOOL_CALLS' || generateReason === 'PERMISSION_ASKING') {
        commitPendingText(assistantMessage);
      } else {
        assistantMessage.content = event.text || String(event.structuredData.text || '')
          || assistantMessage.pendingText || '';
        assistantMessage.pendingText = '';
      }
      return;
    }
    if (event.eventType === 'tool_call_start') {
      if (!isSubagent) {
        commitPendingText(assistantMessage);
      }
      assistantMessage.reasoning?.push({
        id: createUniqueIdentifier(),
        type: 'tool',
        content: '正在调用',
        toolCallId: String(event.structuredData.toolCallId || ''),
        toolName: String(event.structuredData.toolName || 'Tool'),
        source,
        state: 'running',
      });
      return;
    }
    if (event.eventType === 'tool_result_text_delta') {
      const tool = findReasoningTool(assistantMessage, event);
      if (tool) {
        tool.result = (tool.result || '') + String(event.structuredData.delta || '');
      }
      return;
    }
    if (event.eventType === 'tool_result_end') {
      const tool = findReasoningTool(assistantMessage, event);
      if (tool) {
        const state = String(event.structuredData.state || 'SUCCESS');
        tool.content = state === 'SUCCESS' ? '调用完成' : `调用结束 · ${state}`;
        tool.state = state === 'SUCCESS' ? 'success' : 'failed';
      }
      appendEventFiles(assistantMessage, event);
      openPublishedHtml(runtimeState, event);
      return;
    }
    if (event.eventType === 'agent_end') {
      if (isSubagent) {
        return;
      }
      if (!assistantMessage.content && assistantMessage.pendingText) {
        assistantMessage.content = assistantMessage.pendingText;
      }
      assistantMessage.pendingText = '';
      assistantMessage.processing = false;
      assistantMessage.processingEndedAt = Date.now();
      return;
    }
    if (event.eventType === 'task_end') {
      if (isSubagent) {
        return;
      }
      cancelScreenshots(assistantMessage);
      if (!assistantMessage.content && assistantMessage.pendingText) {
        assistantMessage.content = assistantMessage.pendingText;
      }
      assistantMessage.pendingText = '';
      assistantMessage.processing = false;
      assistantMessage.processingEndedAt = Date.now();
      removeEmptyAssistantMessage(runtimeState, assistantMessage);
    }
  }

  function appendReasoningText(message: DisplayMessage, content: string, source?: string): void {
    if (!content) {
      return;
    }
    const lastItem = message.reasoning?.at(-1);
    const followsScreenshot = message.screenshots?.at(-1)?.reasoningOffset === message.reasoning?.length;
    if (lastItem?.type === 'text' && lastItem.source === source && !followsScreenshot) {
      lastItem.content += content;
      return;
    }
    message.reasoning?.push({
      id: createUniqueIdentifier(),
      type: 'text',
      content,
      source,
    });
  }

  function commitPendingText(message: DisplayMessage): void {
    appendReasoningText(message, message.pendingText || '');
    message.pendingText = '';
  }

  function findReasoningTool(message: DisplayMessage, event: AgentEvent): DisplayReasoningItem | undefined {
    const toolCallId = String(event.structuredData.toolCallId || '');
    const source = event.source || undefined;
    return message.reasoning?.findLast((item) => item.type === 'tool'
      && item.toolCallId === toolCallId
      && item.source === source);
  }

  function mapHistoryMessages(
    history: ConversationMessage[],
    files: ConversationFile[] = [],
  ): DisplayMessage[] {
    const result: DisplayMessage[] = [];
    let assistantMessage: DisplayMessage | undefined;
    let outputFiles: ConversationFile[] = [];
    for (const message of history) {
      if (message.role === 'USER') {
        assistantMessage = undefined;
        result.push({
          ...toDisplayMessage(message),
          files: files.filter(
            (file) => file.kind === CONVERSATION_FILE_KIND.INPUT && file.messageId === message.id,
          ),
        });
        outputFiles = files.filter(
          (file) => file.kind === CONVERSATION_FILE_KIND.OUTPUT && file.messageId === message.id,
        );
        continue;
      }
      assistantMessage ||= createHistoryAssistant(message, result, outputFiles);
      if (message.usage) assistantMessage.usage = message.usage;
      if (message.role === 'TOOL') {
        appendHistoryToolResult(assistantMessage, message);
        continue;
      }
      appendHistoryAssistantContent(assistantMessage, message);
    }
    return result;
  }

  function appendEventFiles(message: DisplayMessage, event: AgentEvent): void {
    const files = new Map((message.files || []).map((file) => [file.id, file]));
    parseEventFiles(event.structuredData.files).forEach((file) => files.set(file.id, file));
    if (files.size) message.files = [...files.values()];
  }

  function openPublishedHtml(state: ConversationRuntimeState, event: AgentEvent): void {
    const file = parseEventFiles(event.structuredData.files).filter(isHtmlFile).at(-1);
    if (file && file.sessionId === event.sessionId) {
      if (file.id !== state.previewFile?.id) state.previewOpen = true;
      state.previewFile = file;
    }
  }

  function createHistoryAssistant(
    message: ConversationMessage,
    result: DisplayMessage[],
    files: ConversationFile[],
  ): DisplayMessage {
    const timestamp = toTimestamp(message.createdAt);
    const assistantMessage: DisplayMessage = {
      id: message.id,
      role: 'ASSISTANT',
      content: '',
      kind: 'message',
      createdAt: message.createdAt,
      reasoning: [],
      processing: false,
      processingStartedAt: timestamp,
      processingEndedAt: timestamp,
      files,
    };
    result.push(assistantMessage);
    return assistantMessage;
  }

  function appendHistoryAssistantContent(target: DisplayMessage, source: ConversationMessage): void {
    const toolCall = parseHistoryToolCall(source.content);
    if (!toolCall) {
      const reasoning = parseHistoryReasoning(source.content);
      if (reasoning !== undefined) {
        appendReasoningText(target, reasoning);
        target.processingEndedAt = toTimestamp(source.createdAt) || target.processingEndedAt;
        return;
      }
      target.content = target.content ? `${target.content}\n\n${source.content}` : source.content;
      target.processingEndedAt = toTimestamp(source.createdAt) || target.processingEndedAt;
      return;
    }
    appendReasoningText(target, parseHistoryReasoning(toolCall.precedingText) ?? toolCall.precedingText);
    target.reasoning?.push({
      id: source.id,
      type: 'tool',
      content: '调用完成',
      toolCallId: source.toolCallId || source.id,
      toolName: toolCall.name,
      state: 'success',
    });
  }

  function appendHistoryToolResult(target: DisplayMessage, source: ConversationMessage): void {
    const toolResult = parseHistoryToolResult(source.content);
    const tool = target.reasoning?.findLast((item) => item.type === 'tool'
      && (item.toolCallId === source.toolCallId || item.toolName === toolResult.name));
    if (tool) {
      tool.result = toolResult.result;
      tool.state = 'success';
      tool.content = '调用完成';
    } else {
      target.reasoning?.push({
        id: source.id,
        type: 'tool',
        content: '调用完成',
        toolCallId: source.toolCallId || source.id,
        toolName: toolResult.name,
        state: 'success',
        result: toolResult.result,
      });
    }
    target.processingEndedAt = toTimestamp(source.createdAt) || target.processingEndedAt;
  }

  function parseHistoryToolCall(content: string): HistoryToolCall | undefined {
    const match = content.match(/\[tool_call:\s*([^\s(\]]+)(?:\([\s\S]*\))?\]\s*$/);
    if (!match || match.index === undefined) {
      return undefined;
    }
    return {
      name: match[1],
      precedingText: content.slice(0, match.index).trim(),
    };
  }

  function parseHistoryToolResult(content: string): HistoryToolResult {
    const match = content.match(/^\[tool_result:\s*([^\]]+)]\s*/);
    if (!match) {
      return { name: 'Tool', result: content };
    }
    return {
      name: match[1].trim(),
      result: content.slice(match[0].length).trim(),
    };
  }

  function parseHistoryReasoning(content: string): string | undefined {
    const marker = '[reasoning]';
    if (!content.startsWith(marker)) {
      return undefined;
    }
    return content.slice(marker.length).trim();
  }

  function parseEventFiles(value: unknown): ConversationFile[] {
    if (!Array.isArray(value)) {
      return [];
    }
    return value.filter((file): file is ConversationFile => Boolean(
      file
      && typeof file === 'object'
      && typeof (file as ConversationFile).id === 'string'
      && typeof (file as ConversationFile).sessionId === 'string'
      && typeof (file as ConversationFile).name === 'string',
    ));
  }

  function toDisplayMessage(message: ConversationMessage): DisplayMessage {
    return {
      id: message.id,
      role: message.role,
      content: message.content,
      kind: 'message',
      createdAt: message.createdAt,
    };
  }

  function toTimestamp(value: string | null): number | undefined {
    if (!value) {
      return undefined;
    }
    const timestamp = Date.parse(value);
    return Number.isNaN(timestamp) ? undefined : timestamp;
  }

  async function stop(): Promise<void> {
    await interruptCurrentTurn();
  }

  async function steer(messageId: string): Promise<void> {
    await interruptCurrentTurn(messageId);
  }

  function editQueuedMessage(messageId: string): void {
    const runtimeState = getCurrentRuntimeState();
    const message = runtimeState?.queuedMessages.find((item) => item.id === messageId);
    if (!message || message.removing || runtimeState?.interrupting || runtimeState?.loading) {
      return;
    }
    if (runtimeState?.queuedMessages.some((item) => item.draft !== undefined)) {
      return;
    }
    message.draft ??= message.content;
  }

  function updateQueuedDraft(messageId: string, content: string): void {
    const message = getCurrentRuntimeState()?.queuedMessages.find((item) => item.id === messageId);
    if (message?.draft !== undefined && !message.removing) {
      message.draft = content;
    }
  }

  async function finishQueuedEdit(messageId: string, save: boolean): Promise<void> {
    const sessionId = currentSessionId.value;
    const runtimeState = getCurrentRuntimeState();
    const message = runtimeState?.queuedMessages.find((item) => item.id === messageId);
    if (!runtimeState || !message || message.draft === undefined || message.removing) {
      return;
    }
    if (save) {
      if (!message.draft.trim() && !message.files.length) {
        throw new Error('消息内容不能为空');
      }
      message.content = message.draft.trim() || '请分析所附文件。';
    }
    delete message.draft;
    await drainQueue(sessionId, runtimeState);
  }

  async function removeQueuedMessage(messageId: string): Promise<void> {
    const sessionId = currentSessionId.value;
    const runtimeState = getCurrentRuntimeState();
    const message = runtimeState?.queuedMessages.find((item) => item.id === messageId);
    if (!runtimeState || !message || message.removing || runtimeState.interrupting || runtimeState.loading) {
      return;
    }
    message.removing = true;
    try {
      for (const file of [...message.files]) {
        await deletePendingConversationFile(sessionId, file.id);
        message.files = message.files.filter((item) => item.id !== file.id);
      }
      runtimeState.queuedMessages = runtimeState.queuedMessages.filter((item) => item.id !== messageId);
    } catch (error) {
      runtimeState.queuePaused = true;
      throw error;
    } finally {
      message.removing = false;
    }
    if (runtimeStates.get(sessionId) === runtimeState) {
      await drainQueue(sessionId, runtimeState);
    }
  }

  async function interruptCurrentTurn(messageId?: string): Promise<void> {
    const sessionId = currentSessionId.value;
    const runtimeState = getCurrentRuntimeState();
    if (!sessionId || !runtimeState || runtimeState.interrupting || runtimeState.loading) {
      return;
    }
    const queuedMessage = messageId
      ? runtimeState.queuedMessages.find((message) => message.id === messageId) : undefined;
    if (messageId && (!queuedMessage || queuedMessage.draft !== undefined || queuedMessage.removing)) {
      return;
    }
    runtimeState.queuePaused = true;
    runtimeState.interrupting = true;
    const runningQueue = runningQueues.get(sessionId);
    try {
      const taskId = await taskStarts.get(sessionId);
      if (taskId && runtimeState.activeTaskId === taskId) {
        const result = await interruptTask(taskId);
        if (!result.accepted) {
          throw new Error('任务未接受中断，请稍后重试');
        }
      }
      // 保持 SSE 连接，等待后端归档上下文并结束本轮，不能通过 abort 冒充执行结束。
      await runningQueue;
      if (runtimeStates.get(sessionId) !== runtimeState) {
        return;
      }
      if (queuedMessage) {
        runtimeState.queuedMessages = [queuedMessage,
          ...runtimeState.queuedMessages.filter((message) => message.id !== messageId)];
        runtimeState.queuePaused = false;
      }
    } finally {
      runtimeState.interrupting = false;
    }
    if (queuedMessage && runtimeStates.get(sessionId) === runtimeState) {
      await drainQueue(sessionId, runtimeState);
    }
  }

  function getRuntimeState(sessionId: string): ConversationRuntimeState {
    let runtimeState = runtimeStates.get(sessionId);
    if (!runtimeState) {
      runtimeStates.set(sessionId, createConversationRuntimeState());
      runtimeState = runtimeStates.get(sessionId);
    }
    return runtimeState!;
  }

  function getCurrentRuntimeState(): ConversationRuntimeState | undefined {
    return currentSessionId.value ? runtimeStates.get(currentSessionId.value) : undefined;
  }

  function reset(): void {
    runtimeStates.forEach((runtimeState) => {
      runtimeState.queuePaused = true;
      runtimeState.messages.forEach(cancelScreenshots);
    });
    screenshotControllers.forEach((controller) => controller.abort());
    screenshotControllers.clear();
    runningQueues.clear();
    taskStarts.clear();
    abortControllers.forEach((controller) => controller.abort());
    abortControllers.clear();
    runtimeStates.clear();
    conversations.value = [];
    currentSessionId.value = '';
    selectedModelId.value = '';
    uploadingFiles.value = false;
  }

  return {
    models,
    selectedModelId,
    selectedModel,
    conversations,
    currentSessionId,
    messages,
    loading,
    sending,
    pendingFiles,
    queuedMessages,
    interrupting,
    queuePaused,
    uploadingFiles,
    previewFile,
    previewOpen,
    openHtmlPreview,
    closeHtmlPreview,
    respondToScreenshot,
    initialize,
    selectModel,
    loadConversations,
    createConversation,
    openConversation,
    closeConversation,
    removeConversation,
    uploadFiles,
    removePendingFile,
    send,
    steer,
    editQueuedMessage,
    updateQueuedDraft,
    finishQueuedEdit,
    removeQueuedMessage,
    stop,
    reset,
  };
});
