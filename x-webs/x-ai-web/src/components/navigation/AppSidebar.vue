<script setup lang="ts">
import {
  ArrowUp,
  Calendar,
  ChatDotRound,
  Close,
  Connection,
  Delete,
  EditPen,
  FolderOpened,
  Grid,
  Moon,
  Plus,
  Setting,
  Sunny,
  SwitchButton,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { logout } from '@/api/auth';
import DeveloperAccessDialog from '@/components/developer/DeveloperAccessDialog.vue';
import { useChatStore } from '@/stores/chat';
import { useAdministrationStore } from '@/stores/administration';
import { useModelSettingStore } from '@/stores/model-setting';
import { clearToken } from '@/utils/auth';
import { currentColorMode, toggleColorMode } from '@/utils/colorMode';

defineProps<{ mobileOpen: boolean }>();
const emit = defineEmits<{ close: [] }>();

const route = useRoute();
const router = useRouter();
const chat = useChatStore();
const administration = useAdministrationStore();
const modelSetting = useModelSettingStore();
const userMenuVisible = ref(false);
const developerAccessDialogVisible = ref(false);
const isChatPage = computed(() => route.name === 'chat');

onMounted(async () => {
  try {
    await administration.load();
  } catch {
    // 管理员状态不可用时仅隐藏管理员入口，不影响当前用户信息。
  }
});

async function signOut(): Promise<void> {
  userMenuVisible.value = false;
  try {
    await logout();
  } catch {
    // 即使服务端 token 已失效，也要清理当前浏览器的登录态。
  } finally {
    clearToken();
    modelSetting.reset();
    administration.reset();
    chat.reset();
    await router.replace('/login');
  }
}

function openDeveloperAccess(): void {
  userMenuVisible.value = false;
  developerAccessDialogVisible.value = true;
}

function openModelSetting(): void {
  userMenuVisible.value = false;
  modelSetting.openDialog();
}

async function newChat(): Promise<void> {
  if (!modelSetting.requireConfigured()) {
    return;
  }
  try {
    const conversation = await chat.createConversation();
    await router.push(`/chat/${conversation.sessionId}`);
    emit('close');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '新建对话失败');
  }
}

async function openConversation(sessionId: string): Promise<void> {
  await router.push(`/chat/${sessionId}`);
  emit('close');
}

async function removeConversation(sessionId: string): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后无法恢复，确定删除这个对话吗？', '删除对话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await chat.removeConversation(sessionId);
    if (String(route.params.sessionId || '') === sessionId) {
      await router.push('/chat');
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
}
</script>

<template>
  <div v-if="mobileOpen" class="sidebar-mask" @click="emit('close')" />
  <aside id="app-navigation" class="app-sidebar" :class="{ 'is-mobile-open': mobileOpen }">
    <div class="brand-row">
      <div class="brand-mark agent-mark" aria-hidden="true" />
      <div class="brand-copy">
        <strong>X Agent</strong>
        <span>智能工作台</span>
      </div>
      <div class="brand-actions">
        <button
          class="theme-toggle"
          :class="{ 'is-dark': currentColorMode === 'dark' }"
          type="button"
          :aria-label="currentColorMode === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          :title="currentColorMode === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
          @click="toggleColorMode"
        >
          <el-icon class="theme-toggle-sun"><Sunny /></el-icon>
          <el-icon class="theme-toggle-moon"><Moon /></el-icon>
        </button>
        <button class="mobile-close" aria-label="关闭导航" @click="emit('close')">
          <el-icon><Close /></el-icon>
        </button>
      </div>
    </div>

    <button class="new-chat-button" @click="newChat">
      <el-icon><Plus /></el-icon>
      <span>新建对话</span>
    </button>

    <nav class="primary-nav" aria-label="主导航">
      <RouterLink to="/chat" :class="{ active: isChatPage }" @click="emit('close')">
        <el-icon><ChatDotRound /></el-icon>
        <span>对话</span>
      </RouterLink>
      <RouterLink to="/schedules" active-class="active" @click="emit('close')">
        <el-icon><Calendar /></el-icon>
        <span>定时任务</span>
      </RouterLink>
      <RouterLink to="/knowledge" active-class="active" @click="emit('close')">
        <el-icon><FolderOpened /></el-icon>
        <span>知识库</span>
      </RouterLink>
      <RouterLink to="/apps" :class="{ active: route.path.startsWith('/apps') }" @click="emit('close')">
        <el-icon><Grid /></el-icon>
        <span>智能应用</span>
      </RouterLink>
      <RouterLink to="/extensions" active-class="active" @click="emit('close')">
        <el-icon><Connection /></el-icon>
        <span>扩展</span>
        <small>Skill · MCP</small>
      </RouterLink>
    </nav>

    <section class="conversation-section">
      <div class="section-heading">
        <span>最近对话</span>
        <button title="新建对话" @click="newChat">
          <el-icon><EditPen /></el-icon>
        </button>
      </div>
      <div v-if="chat.conversations.length" class="conversation-list">
        <button
          v-for="conversation in chat.conversations"
          :key="conversation.sessionId"
          class="conversation-item"
          :class="{ active: String(route.params.sessionId || '') === conversation.sessionId }"
          @click="openConversation(conversation.sessionId)"
        >
          <span>{{ conversation.title || '新对话' }}</span>
          <el-icon class="conversation-delete" @click.stop="removeConversation(conversation.sessionId)">
            <Delete />
          </el-icon>
        </button>
      </div>
      <div v-else class="conversation-empty">暂无对话记录</div>
    </section>

    <el-popover
      v-model:visible="userMenuVisible"
      placement="top-start"
      :width="260"
      :offset="10"
      :show-arrow="false"
      popper-class="user-menu-popper"
      trigger="click"
    >
      <template #reference>
        <button
          class="user-card"
          type="button"
          aria-label="打开用户菜单"
          aria-haspopup="menu"
          :aria-expanded="userMenuVisible"
        >
          <div class="user-avatar">U</div>
          <div class="user-card-copy">
            <strong>当前用户</strong>
            <span>在线{{ administration.isAdministrator ? ' · 管理员' : '' }}</span>
          </div>
          <el-icon class="user-card-arrow" :class="{ expanded: userMenuVisible }"><ArrowUp /></el-icon>
        </button>
      </template>

      <div class="user-menu" role="menu">
        <div class="user-menu-profile">
          <div class="user-avatar">U</div>
          <div>
            <strong>当前用户</strong>
            <span>
              已登录
              {{ administration.isAdministrator ? ' · 管理员' : '' }}
            </span>
          </div>
        </div>
        <div class="user-menu-divider" />
        <button type="button" role="menuitem" @click="openModelSetting">
          <el-icon><Setting /></el-icon>
          <span>模型设置</span>
        </button>
        <button type="button" role="menuitem" @click="openDeveloperAccess">
          <el-icon><Connection /></el-icon>
          <span>开发者接入</span>
        </button>
        <button class="danger" type="button" role="menuitem" @click="signOut">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
        </button>
      </div>
    </el-popover>

    <DeveloperAccessDialog v-model="developerAccessDialogVisible" />
  </aside>
</template>
