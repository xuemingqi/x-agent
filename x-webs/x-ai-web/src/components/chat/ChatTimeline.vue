<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

import ChatMessage from './ChatMessage.vue';
import type { DisplayMessage } from '@/stores/chat';
import type { ConversationFile } from '@/types/chat';

const BOTTOM_DISTANCE_THRESHOLD = 48;
const SCROLL_DIRECTION_TOLERANCE = 1;

const props = defineProps<{ messages: DisplayMessage[]; loading: boolean }>();
const emit = defineEmits<{ preview: [file: ConversationFile] }>();

const scroller = ref<HTMLElement>();
const timeline = ref<HTMLElement>();
const autoScrollEnabled = ref(true);
let resizeObserver: ResizeObserver | undefined;
let scrollFrame: number | undefined;
let previousScrollTop = 0;

onMounted(async () => {
  await nextTick();
  scrollToBottom();
  if (timeline.value) {
    resizeObserver = new ResizeObserver(scrollToBottom);
    resizeObserver.observe(timeline.value);
  }
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  if (scrollFrame !== undefined) {
    return;
  }
});

watch(
  () => props.messages.length,
  async (messageCount, previousMessageCount) => {
    if (messageCount <= previousMessageCount) {
      return;
    }
    autoScrollEnabled.value = true;
    await nextTick();
    scrollToBottom();
  },
);

/**
 * 内容高度变化时把视图贴到底部，用户查看历史期间保持当前位置。
 */
function scrollToBottom(): void {
  if (!autoScrollEnabled.value || !scroller.value) {
    return;
  }
  if (scrollFrame !== undefined) {
    cancelAnimationFrame(scrollFrame);
  }
  scrollFrame = requestAnimationFrame(() => {
    if (autoScrollEnabled.value && scroller.value) {
      scroller.value.scrollTop = scroller.value.scrollHeight;
      previousScrollTop = scroller.value.scrollTop;
    }
    scrollFrame = undefined;
  });
}

/**
 * 用户滚动到底部后恢复跟随，离开底部后暂停跟随。
 */
function handleScroll(): void {
  if (!scroller.value) {
    return;
  }
  const currentScrollTop = scroller.value.scrollTop;
  const distanceToBottom = scroller.value.scrollHeight
    - currentScrollTop
    - scroller.value.clientHeight;
  if (currentScrollTop < previousScrollTop - SCROLL_DIRECTION_TOLERANCE) {
    autoScrollEnabled.value = false;
  } else if (distanceToBottom <= BOTTOM_DISTANCE_THRESHOLD) {
    autoScrollEnabled.value = true;
  }
  previousScrollTop = currentScrollTop;
}

/**
 * 用户向上滚动时暂停跟随，向下滚动不做干预——handleScroll 会根据位置自动判断。
 */
function handleWheel(event: WheelEvent): void {
  if (event.deltaY < 0 && scroller.value && scroller.value.scrollTop > 0) {
    autoScrollEnabled.value = false;
  }
}
</script>

<template>
  <div
    ref="scroller"
    class="chat-scroller"
    @scroll.passive="handleScroll"
    @wheel.passive="handleWheel"
  >
    <div ref="timeline" v-loading="loading" class="chat-timeline">
      <ChatMessage v-for="message in messages" :key="message.id" :message="message" @preview="emit('preview', $event)" />
    </div>
  </div>
</template>
