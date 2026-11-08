<script setup lang="ts">
import { Menu } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { onMounted, ref } from 'vue';

import ModelSettingDialog from '@/components/model/ModelSettingDialog.vue';
import AppSidebar from '@/components/navigation/AppSidebar.vue';
import { useModelSettingStore } from '@/stores/model-setting';

const sidebarOpen = ref(false);
const modelSetting = useModelSettingStore();

onMounted(async () => {
  try {
    await modelSetting.initialize();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型配置加载失败');
  }
});
</script>

<template>
  <div class="app-shell">
    <AppSidebar :mobile-open="sidebarOpen" @close="sidebarOpen = false" />
    <button class="mobile-menu" aria-label="打开导航" @click="sidebarOpen = true">
      <el-icon><Menu /></el-icon>
    </button>
    <main class="app-main">
      <RouterView />
    </main>
    <ModelSettingDialog />
  </div>
</template>
