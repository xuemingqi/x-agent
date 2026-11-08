<script setup lang="ts">
import { Delete, EditPen, Plus } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { reactive, ref, watch } from 'vue';

import { useChatStore } from '@/stores/chat';
import { useModelSettingStore } from '@/stores/model-setting';
import type { AgentModel, ModelSavePayload } from '@/types/model';
import { MULTIMODAL_CAPABILITY } from '@/types/model';

type ModelForm = Omit<ModelSavePayload, 'contextWindowSize'> & { contextWindowSize: number | undefined };

const MAX_CONTEXT_WINDOW_SIZE = 2_147_483_647;
const modelSetting = useModelSettingStore();
const chat = useChatStore();
const editingId = ref('');
const editing = ref(false);
const saving = ref(false);
const form = reactive<ModelForm>(emptyForm());

function emptyForm(): ModelForm {
  return {
    name: '', modelName: '', modelUrl: '', modelKey: '',
    contextWindowSize: undefined, temperature: null, isDefault: false,
    multimodalCapability: MULTIMODAL_CAPABILITY.NONE,
  };
}

watch(() => modelSetting.dialogVisible, async (visible) => {
  form.modelKey = '';
  if (!visible) return;
  editing.value = false;
  try {
    await modelSetting.initialize();
    if (!modelSetting.models.length) openCreate();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型加载失败');
  }
});

function openCreate(): void {
  editingId.value = '';
  Object.assign(form, emptyForm(), { isDefault: !modelSetting.models.length });
  editing.value = true;
}

function openEdit(model: AgentModel): void {
  editingId.value = model.id;
  Object.assign(form, {
    name: model.name, modelName: model.modelName, modelUrl: model.modelUrl,
    modelKey: '', contextWindowSize: model.contextWindowSize ?? undefined,
    temperature: model.temperature ?? null, isDefault: Boolean(model.isDefault),
    multimodalCapability: model.multimodalCapability ?? MULTIMODAL_CAPABILITY.NONE,
  });
  editing.value = true;
}

async function submit(): Promise<void> {
  const contextWindowSize = form.contextWindowSize;
  if (contextWindowSize == null || !Number.isInteger(contextWindowSize)
      || contextWindowSize <= 0 || contextWindowSize > MAX_CONTEXT_WINDOW_SIZE) {
    ElMessage.warning('请填写模型实际支持的上下文窗口大小（正整数）');
    return;
  }
  const payload: ModelSavePayload = {
    ...form, name: form.name.trim(), modelName: form.modelName.trim(),
    modelUrl: form.modelUrl.trim(), modelKey: form.modelKey?.trim() || undefined,
    contextWindowSize, temperature: form.temperature ?? null,
  };
  if (!payload.name || !payload.modelName || !payload.modelUrl || (!editingId.value && !payload.modelKey)) {
    ElMessage.warning('请填写完整的模型名称、地址及 Key');
    return;
  }
  saving.value = true;
  try {
    const saved = await modelSetting.save(payload, editingId.value || undefined);
    if (saved.isDefault || !chat.selectedModelId) await chat.selectModel(saved.id);
    form.modelKey = '';
    editing.value = false;
    ElMessage.success('模型配置已保存');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型保存失败');
  } finally {
    saving.value = false;
  }
}

async function remove(model: AgentModel): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除模型“${model.name}”吗？`, '删除模型', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    });
    await modelSetting.remove(model.id);
    if (chat.selectedModelId === model.id) {
      await chat.selectModel(modelSetting.defaultModelId || modelSetting.models[0]?.id || '');
    }
    if (!modelSetting.models.length) openCreate();
    ElMessage.success('模型已删除');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '模型删除失败');
    }
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelSetting.dialogVisible"
    title="个人模型设置"
    width="min(720px, 94vw)"
    append-to-body
    :close-on-click-modal="false"
    @close="modelSetting.closeDialog"
  >
    <p class="model-setting-intro">模型与 Key 仅供当前账号使用。默认模型用于 Open API、MCP 等未指定模型的调用。</p>
    <template v-if="!editing">
      <div class="model-setting-toolbar">
        <span>{{ modelSetting.models.length }} 个个人模型</span>
        <el-button type="primary" :icon="Plus" @click="openCreate">添加模型</el-button>
      </div>
      <div class="model-setting-list">
        <article v-for="model in modelSetting.models" :key="model.id" class="model-setting-row">
          <div>
            <strong>{{ model.name }}</strong>
            <el-tag v-if="model.isDefault" size="small" effect="plain">默认</el-tag>
            <span>{{ model.modelName }} · {{ model.contextWindowSize ? model.contextWindowSize.toLocaleString() + ' tokens' : '上下文窗口待配置' }}</span>
          </div>
          <el-button text :icon="EditPen" @click="openEdit(model)">编辑</el-button>
          <el-button text type="danger" :icon="Delete" @click="remove(model)">删除</el-button>
        </article>
      </div>
    </template>
    <el-form v-else label-position="top" class="model-setting-form" @submit.prevent="submit">
      <div class="form-grid">
        <el-form-item label="配置名称" required>
          <el-input v-model="form.name" maxlength="100" placeholder="例如：我的工作模型" />
        </el-form-item>
        <el-form-item label="模型名称" required>
          <el-input v-model="form.modelName" maxlength="100" placeholder="模型服务中的模型标识" />
        </el-form-item>
      </div>
      <el-form-item label="模型服务地址" required>
        <el-input v-model="form.modelUrl" maxlength="2048" placeholder="https://api.example.com/v1" />
      </el-form-item>
      <el-form-item label="模型 Key" :required="!editingId">
        <el-input
          v-model="form.modelKey"
          type="password"
          show-password
          autocomplete="new-password"
          :placeholder="editingId ? '已配置，留空保留当前 Key' : '请输入模型 Key'"
        />
      </el-form-item>
      <div class="form-grid">
        <el-form-item label="上下文窗口（tokens）" required>
          <el-input-number
            v-model="form.contextWindowSize" :min="1" :max="MAX_CONTEXT_WINDOW_SIZE" :precision="0"
            controls-position="right" placeholder="填写模型实际支持的大小"
          />
        </el-form-item>
        <el-form-item label="温度">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" :precision="2" controls-position="right" />
        </el-form-item>
      </div>
      <el-form-item label="设为当前账号的默认模型">
        <el-switch v-model="form.isDefault" />
      </el-form-item>
      <el-form-item label="图片能力">
        <el-select v-model="form.multimodalCapability" aria-label="模型图片能力">
          <el-option label="仅文本" :value="MULTIMODAL_CAPABILITY.NONE" />
          <el-option label="图片理解" :value="MULTIMODAL_CAPABILITY.IMAGE_INPUT" />
          <el-option label="图片生成" :value="MULTIMODAL_CAPABILITY.IMAGE_OUTPUT" />
          <el-option label="图片理解和生成" :value="MULTIMODAL_CAPABILITY.IMAGE_INPUT_OUTPUT" />
        </el-select>
      </el-form-item>
      <p class="field-help">图片能力需与模型服务实际能力一致，不会自动开通服务端能力。</p>
      <p class="field-help">上下文窗口请填写服务实际支持的大小，用于会话压缩与上下文管理。</p>
    </el-form>
    <template #footer>
      <el-button v-if="editing && modelSetting.models.length" :disabled="saving" @click="editing = false">返回列表</el-button>
      <el-button v-else @click="modelSetting.closeDialog">关闭</el-button>
      <el-button v-if="editing" type="primary" :loading="saving" @click="submit">保存模型</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.model-setting-intro, .field-help {
  color: var(--text-soft);
  font-size: 13px;
  line-height: 1.7;
}
.model-setting-toolbar, .model-setting-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.model-setting-toolbar { justify-content: space-between; margin: 20px 0 12px; }
.model-setting-list { display: grid; gap: 10px; }
.model-setting-row { padding: 14px; border: 1px solid var(--line); border-radius: 12px; }
.model-setting-row > div { flex: 1; min-width: 0; }
.model-setting-row strong { margin-right: 8px; overflow-wrap: anywhere; }
.model-setting-row div > span:last-child { display: block; margin-top: 6px; color: var(--text-muted); font-size: 12px; }
.model-setting-form :deep(.el-input-number) { width: 100%; }
</style>
