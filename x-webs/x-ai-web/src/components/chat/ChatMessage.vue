<script setup lang="ts">
import { computed } from 'vue';

import BrowserScreenshotCard from '@/components/chat/BrowserScreenshotCard.vue';
import ChatReasoning from '@/components/chat/ChatReasoning.vue';
import ChatTokenUsage from '@/components/chat/ChatTokenUsage.vue';
import FileDownloadCard from '@/components/file/FileDownloadCard.vue';
import type { DisplayMessage } from '@/stores/chat';
import type { ConversationFile } from '@/types/chat';
import { renderMarkdown } from '@/utils/markdown';

const props = defineProps<{ message: DisplayMessage }>();
const emit = defineEmits<{ preview: [file: ConversationFile] }>();

/** 在截图请求处截断处理记录，流式追加的内容始终跟在请求之后。 */
const responseSections = computed(() => {
  const reasoning = props.message.reasoning || [];
  let offset = 0;
  const requests = (props.message.screenshots || []).map((request) => {
    const end = request.reasoningOffset ?? reasoning.length;
    const section = { request, reasoning: reasoning.slice(offset, end) };
    offset = end;
    return section;
  });
  return { requests, reasoning: reasoning.slice(offset) };
});
</script>

<template>
  <article class="chat-message" :class="message.role.toLowerCase()">
    <div class="message-body">
      <div v-if="message.role === 'USER' && message.files?.length" class="file-download-list">
        <FileDownloadCard
          v-for="file in message.files"
          :key="file.id"
          :file="file"
          previewable
          @preview="emit('preview', $event)"
        />
      </div>
      <div v-if="message.role === 'ASSISTANT'" class="message-content assistant-response">
        <template v-for="section in responseSections.requests" :key="section.request.requestId">
          <ChatReasoning
            v-if="section.reasoning.length"
            :items="section.reasoning"
            :started-at="message.processingStartedAt"
            :ended-at="section.request.requestedAt"
          />
          <BrowserScreenshotCard :request="section.request" />
        </template>
        <ChatReasoning
          v-if="responseSections.reasoning.length || message.pendingText"
          :items="responseSections.reasoning"
          :pending-text="message.pendingText"
          :processing="message.processing"
          :started-at="message.processingStartedAt"
          :ended-at="message.processingEndedAt"
        />
        <div
          v-if="message.content"
          class="final-answer markdown-body"
          v-html="renderMarkdown(message.content)"
        />
        <div
          v-else-if="message.processing && !message.reasoning?.length && !message.pendingText
            && !message.files?.length && !message.screenshots?.length"
          class="typing-dots"
        ><i /><i /><i /></div>
      </div>
      <div v-else-if="message.content" class="message-content markdown-body" v-html="renderMarkdown(message.content)" />
      <div v-else-if="message.processing" class="typing-dots"><i /><i /><i /></div>
      <div v-if="message.role !== 'USER' && message.files?.length" class="file-download-list">
        <FileDownloadCard
          v-for="file in message.files"
          :key="file.id"
          :file="file"
          previewable
          @preview="emit('preview', $event)"
        />
      </div>
      <ChatTokenUsage v-if="message.role === 'ASSISTANT' && message.usage" :usage="message.usage" />
    </div>
  </article>
</template>
