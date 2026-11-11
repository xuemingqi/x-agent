<script setup lang="ts">
import { CopyDocument, Key } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, ref, watch } from 'vue';

import { issueApiKey } from '@/api/auth';
import { getExternalAccessConfig } from '@/api/external-access';

const API_KEY_PLACEHOLDER = '<YOUR_API_KEY>';
const visible = defineModel<boolean>({ required: true });
const apiKey = ref('');
const httpApiUrl = ref('');
const mcpServerUrl = ref('');
const loading = ref(false);
const issuing = ref(false);

const httpExample = computed(() => `curl --request POST '${httpApiUrl.value}' \\
  --header 'Content-Type: application/json' \\
  --header 'X-API-Key: ${API_KEY_PLACEHOLDER}' \\
  --data '{"question":"你好","sessionId":""}'`);

const mcpConfiguration = computed(() => JSON.stringify({
  mcpServers: {
    'x-agent': {
      url: mcpServerUrl.value,
      headers: { 'X-API-Key': API_KEY_PLACEHOLDER },
    },
  },
}, null, 2));

watch(visible, (value) => {
  if (value) {
    apiKey.value = '';
    void loadConfig();
  }
});

async function loadConfig(): Promise<void> {
  loading.value = true;
  try {
    const config = await getExternalAccessConfig();
    httpApiUrl.value = config.httpApiUrl;
    mcpServerUrl.value = config.mcpServerUrl;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '开发者接入配置加载失败');
  } finally {
    loading.value = false;
  }
}

async function generateApiKey(): Promise<void> {
  try {
    await ElMessageBox.confirm('生成新 API Key 后，之前的 Key 会立即失效。确定继续吗？', '生成 API Key', {
      confirmButtonText: '确认生成',
      cancelButtonText: '取消',
      type: 'warning',
    });
    issuing.value = true;
    apiKey.value = (await issueApiKey()).apiKey;
    ElMessage.success('API Key 已生成，请立即保存');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : 'API Key 生成失败');
    }
  } finally {
    issuing.value = false;
  }
}

async function copyText(value: string, message: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(value);
    ElMessage.success(message);
  } catch {
    ElMessage.error('复制失败，请手动复制');
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="开发者接入" width="min(720px, 94vw)" append-to-body destroy-on-close>
    <div v-loading="loading" class="developer-access-content">
      <div class="access-intro">
        <el-icon><Key /></el-icon>
        <div>
          <strong>HTTP Open API 与 MCP 服务共用 API Key</strong>
          <p>平台只保存 Key 的摘要，明文仅在生成后展示一次。</p>
        </div>
        <el-button type="primary" :loading="issuing" @click="generateApiKey">生成或轮换 Key</el-button>
      </div>

      <div v-if="apiKey" class="copyable-value api-key-value">
        <code>{{ apiKey }}</code>
        <button type="button" @click="copyText(apiKey, 'API Key 已复制')">
          <el-icon><CopyDocument /></el-icon>复制
        </button>
      </div>

      <el-tabs>
        <el-tab-pane label="HTTP API">
          <p class="description">阻塞调用当前用户最近更新的模型；复用 sessionId 可连续对话。</p>
          <div class="code-example">
            <pre><code>{{ httpExample }}</code></pre>
            <button type="button" @click="copyText(httpExample, 'HTTP 示例已复制')">
              <el-icon><CopyDocument /></el-icon>复制
            </button>
          </div>
        </el-tab-pane>
        <el-tab-pane label="MCP">
          <p class="description">适用于支持 Streamable HTTP 的 MCP 客户端。</p>
          <div class="code-example">
            <pre><code>{{ mcpConfiguration }}</code></pre>
            <button type="button" @click="copyText(mcpConfiguration, 'MCP 配置已复制')">
              <el-icon><CopyDocument /></el-icon>复制
            </button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-dialog>
</template>

<style scoped>
.developer-access-content { display: grid; gap: 16px; min-height: 300px; }
.access-intro { display: flex; align-items: center; gap: 12px; padding: 14px; border: 1px solid var(--line); border-radius: 12px; }
.access-intro > .el-icon { color: var(--primary-bright); font-size: 20px; }
.access-intro div { flex: 1; }
.access-intro p, .description { margin: 5px 0 0; color: var(--text-muted); font-size: 11px; }
.copyable-value, .code-example { position: relative; padding: 12px; border: 1px solid var(--line); border-radius: 10px; background: var(--code-block-bg); }
.copyable-value { display: flex; align-items: center; gap: 10px; }
.copyable-value code { flex: 1; overflow-x: auto; white-space: nowrap; }
.code-example { margin-top: 10px; }
.code-example pre { max-height: 250px; margin: 0; padding-right: 72px; overflow: auto; white-space: pre-wrap; }
.copyable-value code, .code-example code { color: var(--primary-bright); font: 11px/1.65 "SFMono-Regular", Consolas, monospace; }
.copyable-value button, .code-example button { display: inline-flex; align-items: center; gap: 4px; border: 0; color: var(--primary-bright); background: transparent; cursor: pointer; }
.code-example button { position: absolute; top: 10px; right: 10px; }
</style>
