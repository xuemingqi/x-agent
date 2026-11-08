<script setup lang="ts">
import {
  ArrowDown,
  Bell,
  Check,
  Close,
  Connection,
  Delete,
  Document,
  Download,
  EditPen,
  Lock,
  Loading,
  MagicStick,
  Plus,
  Share,
  Upload,
  User,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import {
  acceptSkillShare,
  createPublicSkill,
  createUserSkill,
  deletePublicSkill,
  deleteUserSkill,
  exportSkillArchive,
  importSkillArchive,
  listPendingSkillShares,
  listSkills,
  listPublicSkills,
  rejectSkillShare,
  shareUserSkill,
  setSkillEnabled,
  updatePublicSkill,
  updateUserSkill,
} from '@/api/skill';
import McpManagementPanel from '@/components/extension/McpManagementPanel.vue';
import { useAdministrationStore } from '@/stores/administration';
import { useModelSettingStore } from '@/stores/model-setting';
import { MCP_SCOPE } from '@/types/mcp';
import type { McpScope } from '@/types/mcp';
import { SKILL_SCOPE } from '@/types/skill';
import type { AgentSkill, SkillArchiveFormat, SkillScope, SkillShare, UserSkillPayload } from '@/types/skill';

const activeExtension = ref<'skill' | 'mcp'>('skill');
const administration = useAdministrationStore();
const modelSetting = useModelSettingStore();
const archiveInput = ref<HTMLInputElement | null>(null);
const importing = ref(false);
const exportingSkillKey = ref('');
const togglingSkills = reactive(new Set<string>());
const activeScope = ref<SkillScope>(SKILL_SCOPE.USER);
const activeMcpScope = ref<McpScope>(MCP_SCOPE.USER);
const mcpManagementPanel = ref<{ openCreate: () => void } | null>(null);
const skills = ref<AgentSkill[]>([]);
const pendingShares = ref<SkillShare[]>([]);
const loading = ref(false);
const loadingShares = ref(false);
const saving = ref(false);
const sharing = ref(false);
const respondingShareId = ref('');
const dialogVisible = ref(false);
const shareDialogVisible = ref(false);
const editingName = ref('');
const sharingSkillName = ref('');
const recipientMobile = ref('');
const readOnly = ref(false);
const form = reactive({ name: '', description: '', content: '' });
const archiveFormats: { value: SkillArchiveFormat; label: string; description: string }[] = [
  { value: 'zip', label: 'ZIP', description: '通用压缩包，方便分享' },
  { value: 'tar', label: 'TAR', description: '标准归档，适合命令行使用' },
  { value: 'tar.gz', label: 'TAR.GZ', description: 'Gzip 压缩归档，体积更小' },
];

const visibleSkills = computed(() => skills.value.filter((skill) => skill.scope === activeScope.value));
const pendingShareCount = computed(() => pendingShares.value.length);
const dialogTitle = computed(() => {
  if (readOnly.value) return activeScope.value === SKILL_SCOPE.SHARED ? '共享 Skill' : '公共 Skill';
  return editingName.value ? '修改 Skill' : '创建 Skill';
});
const skillDialogScope = computed(() => {
  if (activeScope.value === SKILL_SCOPE.PUBLIC) return 'PLATFORM SKILL';
  if (activeScope.value === SKILL_SCOPE.SHARED) return 'SHARED SKILL';
  return 'PERSONAL SKILL';
});
const skillDialogDescription = computed(() => {
  if (readOnly.value) return '查看 Skill 的使用场景与完整指令。';
  return editingName.value ? '调整 Skill 描述与执行指令。' : '将一套可复用的工作方法交给 Agent。';
});

async function loadSkills(): Promise<void> {
  loading.value = true;
  try {
    const visible = await listSkills();
    if (administration.isAdministrator) {
      const publicSkills = await listPublicSkills();
      skills.value = [...visible.filter((skill) => skill.scope !== SKILL_SCOPE.PUBLIC), ...publicSkills];
    } else {
      skills.value = visible;
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill 加载失败');
  } finally {
    loading.value = false;
  }
}

async function toggleSkill(skill: AgentSkill): Promise<void> {
  const key = `${skill.scope}-${skill.name}`;
  if (togglingSkills.has(key)) return;
  togglingSkills.add(key);
  try {
    skill.enabled = await setSkillEnabled(skill, !skill.enabled);
    ElMessage.success(`Skill 已${skill.enabled ? '启用' : '停用'}，下一轮对话生效`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '启用状态保存失败');
  } finally {
    togglingSkills.delete(key);
  }
}

async function loadPendingShares(): Promise<void> {
  loadingShares.value = true;
  try {
    pendingShares.value = await listPendingSkillShares();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill 分享邀请加载失败');
  } finally {
    loadingShares.value = false;
  }
}

function openCreate(): void {
  editingName.value = '';
  readOnly.value = false;
  Object.assign(form, { name: '', description: '', content: '' });
  dialogVisible.value = true;
}

async function chooseSkillArchive(): Promise<void> {
  try {
    await modelSetting.initialize();
    if (!modelSetting.requireConfigured()) return;
    if (!modelSetting.defaultModelId) {
      modelSetting.openDialog();
      ElMessage.warning('请先设置当前账号的默认模型，用于整理导入的 Skill');
      return;
    }
    archiveInput.value?.click();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '模型配置加载失败');
  }
}

async function importArchive(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || importing.value) return;
  if (!/\.(zip|tar|tar\.gz|tgz)$/i.test(file.name) || file.size === 0 || file.size > 20 * 1024 * 1024) {
    ElMessage.warning('请选择不超过 20MB 的 ZIP、TAR 或 TAR.GZ 文件');
    return;
  }
  importing.value = true;
  try {
    const skill = await importSkillArchive(file);
    activeScope.value = SKILL_SCOPE.USER;
    ElMessage.success(`已导入 Skill“${skill.name}”`);
    await loadSkills();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill 导入失败');
  } finally {
    importing.value = false;
  }
}

async function exportArchive(skill: AgentSkill, format: SkillArchiveFormat): Promise<void> {
  if (exportingSkillKey.value) return;
  exportingSkillKey.value = `${skill.scope}-${skill.name}`;
  try {
    const blob = await exportSkillArchive(skill, format);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${skill.name}.${format}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill 导出失败');
  } finally {
    exportingSkillKey.value = '';
  }
}

function openMcpCreate(): void {
  mcpManagementPanel.value?.openCreate();
}

function openShare(skill: AgentSkill): void {
  sharingSkillName.value = skill.name;
  recipientMobile.value = '';
  shareDialogVisible.value = true;
}

function openSkill(skill: AgentSkill): void {
  editingName.value = skill.editable ? skill.name : '';
  readOnly.value = !skill.editable;
  Object.assign(form, {
    name: skill.name,
    description: skill.description,
    content: skill.content,
  });
  dialogVisible.value = true;
}

async function saveSkill(): Promise<void> {
  if (!form.name.trim() || !form.description.trim() || !form.content.trim()) {
    ElMessage.warning('请填写完整的 Skill 信息');
    return;
  }
  saving.value = true;
  try {
    const payload: UserSkillPayload = {
      name: form.name.trim(),
      description: form.description.trim(),
      content: form.content.trim(),
    };
    if (editingName.value) {
      await (activeScope.value === SKILL_SCOPE.PUBLIC ? updatePublicSkill : updateUserSkill)(editingName.value, payload);
      ElMessage.success('Skill 已更新');
    } else {
      await (activeScope.value === SKILL_SCOPE.PUBLIC ? createPublicSkill : createUserSkill)(payload);
      ElMessage.success('Skill 已创建');
    }
    dialogVisible.value = false;
    await loadSkills();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

async function removeSkill(skill: AgentSkill): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除 Skill“${skill.name}”及其附属文件吗？`, '删除 Skill', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await (skill.scope === SKILL_SCOPE.PUBLIC ? deletePublicSkill : deleteUserSkill)(skill.name);
    ElMessage.success('Skill 已删除');
    await loadSkills();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
}

async function submitShare(): Promise<void> {
  const mobile = recipientMobile.value.trim();
  if (!/^1[3-9]\d{9}$/.test(mobile)) {
    ElMessage.warning('请输入接收人的有效手机号');
    return;
  }
  sharing.value = true;
  try {
    await shareUserSkill(sharingSkillName.value, mobile);
    shareDialogVisible.value = false;
    ElMessage.success('分享邀请已发送，接收人同意后 Skill 才会生效');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill 分享失败');
  } finally {
    sharing.value = false;
  }
}

async function respondToShare(share: SkillShare, accept: boolean): Promise<void> {
  respondingShareId.value = share.id;
  try {
    if (accept) {
      await acceptSkillShare(share.id);
      ElMessage.success(`已接受 Skill“${share.skillName}”`);
      await Promise.all([loadSkills(), loadPendingShares()]);
    } else {
      await rejectSkillShare(share.id);
      ElMessage.success(`已拒绝 Skill“${share.skillName}”`);
      await loadPendingShares();
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Skill 分享邀请处理失败');
  } finally {
    respondingShareId.value = '';
  }
}

function skillCardLabel(skill: AgentSkill): string {
  if (skill.scope === SKILL_SCOPE.USER) return 'PERSONAL SKILL';
  if (skill.scope === SKILL_SCOPE.SHARED) return 'SHARED SKILL';
  return 'PLATFORM SKILL';
}

onMounted(async () => {
  try {
    await administration.load();
  } catch {
    // 管理员状态不可用时保持普通用户界面。
  }
  await Promise.all([loadSkills(), loadPendingShares()]);
});
</script>

<template>
  <section class="workspace-page extension-page">
    <header class="page-header extension-header">
      <div>
        <p>Capabilities</p>
        <h1>扩展能力</h1>
        <span>管理公共与个人 Skill、MCP 连接，并按用户隔离 Agent 扩展能力。</span>
      </div>
      <div
        v-if="activeExtension === 'skill'
          && (activeScope === SKILL_SCOPE.USER
            || (activeScope === SKILL_SCOPE.PUBLIC && administration.isAdministrator))"
        class="skill-header-actions"
      >
        <input ref="archiveInput" type="file" accept=".zip,.tar,.tar.gz,.tgz" hidden @change="importArchive" />
        <el-tooltip placement="bottom-end" effect="light" popper-class="skill-import-tooltip" :show-after="300">
          <template #content>
            <strong>从压缩包导入</strong>
            <span>ZIP / TAR / TAR.GZ · 最大 20MB</span>
            <p>每包一个 SKILL.md，使用你的默认模型整理。保留附属文件，同名 Skill 不会覆盖。</p>
          </template>
          <el-button class="page-primary-action" type="primary" :loading="importing" @click="chooseSkillArchive">
            <el-icon v-if="!importing"><Upload /></el-icon>
            {{ importing ? '默认模型整理中…' : '导入 Skill' }}
          </el-button>
        </el-tooltip>
        <el-button
          class="page-primary-action"
          type="primary"
          @click="openCreate"
        >
          <el-icon><Plus /></el-icon>
          {{ activeScope === SKILL_SCOPE.PUBLIC ? '创建公共 Skill' : '创建 Skill' }}
        </el-button>
      </div>
      <el-button
        v-else-if="activeExtension === 'mcp'
          && (activeMcpScope === MCP_SCOPE.USER
            || (activeMcpScope === MCP_SCOPE.PUBLIC && administration.isAdministrator))"
        class="page-primary-action"
        type="primary"
        @click="openMcpCreate"
      >
        <el-icon><Plus /></el-icon>
        {{ activeMcpScope === MCP_SCOPE.PUBLIC ? '创建公共 MCP' : '创建 MCP' }}
      </el-button>
    </header>

    <div class="extension-tabs">
      <button :class="{ active: activeExtension === 'skill' }" @click="activeExtension = 'skill'">
        <el-icon><MagicStick /></el-icon>
        Skill
        <i v-if="pendingShareCount" class="notification-dot" aria-label="有待处理的 Skill 分享邀请"></i>
      </button>
      <button :class="{ active: activeExtension === 'mcp' }" @click="activeExtension = 'mcp'">
        <el-icon><Connection /></el-icon>
        MCP
        <span>HTTP 可用</span>
      </button>
    </div>

    <template v-if="activeExtension === 'skill'">
      <div v-if="importing" class="skill-import-progress" role="status">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在使用默认模型整理 Skill<span>完成后将加入“我的 Skill”</span></span>
      </div>
      <div class="scope-switch">
        <button :class="{ active: activeScope === SKILL_SCOPE.USER }" @click="activeScope = SKILL_SCOPE.USER">
          <el-icon><User /></el-icon>
          我的 Skill
        </button>
        <button :class="{ active: activeScope === SKILL_SCOPE.PUBLIC }" @click="activeScope = SKILL_SCOPE.PUBLIC">
          <el-icon>
            <EditPen v-if="administration.isAdministrator" />
            <Lock v-else />
          </el-icon>
          公共 Skill
        </button>
        <button :class="{ active: activeScope === SKILL_SCOPE.SHARED }" @click="activeScope = SKILL_SCOPE.SHARED">
          <el-icon><Bell /></el-icon>
          共享 Skill
          <i v-if="pendingShareCount" class="notification-dot" aria-label="有待处理的 Skill 分享邀请"></i>
        </button>
      </div>

      <section v-if="activeScope === SKILL_SCOPE.SHARED && pendingShareCount" class="skill-share-inbox">
        <div class="skill-share-inbox-title">
          <div>
            <span>待处理邀请</span>
            <p>接受后 Skill 才会加入你的 Agent，拒绝后不会加载。</p>
          </div>
          <el-tag type="danger" effect="dark">{{ pendingShareCount }}</el-tag>
        </div>
        <div v-loading="loadingShares" class="skill-share-list">
          <article v-for="share in pendingShares" :key="share.id" class="skill-share-card">
            <div>
              <span>{{ share.senderDisplayName || share.senderMobile }} 分享</span>
              <h3>{{ share.skillName }}</h3>
              <p>{{ share.description }}</p>
              <small>{{ share.senderMobile }}</small>
            </div>
            <div class="skill-share-actions">
              <el-button
                type="primary"
                :loading="respondingShareId === share.id"
                @click="respondToShare(share, true)"
              >
                <el-icon><Check /></el-icon>
                接受
              </el-button>
              <el-button
                :disabled="Boolean(respondingShareId)"
                @click="respondToShare(share, false)"
              >
                <el-icon><Close /></el-icon>
                拒绝
              </el-button>
            </div>
          </article>
        </div>
      </section>

      <p class="extension-enablement-hint">开关仅对我生效，下一轮对话生效。</p>
      <div v-loading="loading" class="skill-grid">
        <article v-for="skill in visibleSkills" :key="`${skill.scope}-${skill.name}`" class="skill-card"
          :class="{ 'extension-disabled': !skill.enabled }">
          <div class="skill-card-head">
            <div class="extension-icon skill"><el-icon><MagicStick /></el-icon></div>
            <div>
              <span>{{ skillCardLabel(skill) }}</span>
              <h2>{{ skill.name }}</h2>
            </div>
            <div class="extension-enablement" title="仅对我生效，下一轮对话生效">
              <span>{{ skill.enabled ? '已启用' : '已停用' }}</span>
              <el-switch :model-value="skill.enabled" :loading="togglingSkills.has(`${skill.scope}-${skill.name}`)"
                :aria-label="`${skill.enabled ? '停用' : '启用'} Skill ${skill.name}（仅对我生效）`"
                @change="toggleSkill(skill)" />
            </div>
          </div>
          <p>{{ skill.description }}</p>
          <small v-if="skill.sharedByMobile" class="skill-shared-by">由 {{ skill.sharedByMobile }} 分享</small>
          <div v-if="skill.resourcePaths.length" class="skill-resources">
            <el-tag effect="plain">{{ skill.resourcePaths.length }} 个附属文件</el-tag>
          </div>
          <div class="skill-actions">
            <el-button text @click="openSkill(skill)">
              <el-icon v-if="skill.editable"><EditPen /></el-icon>
              {{ skill.editable ? '编辑' : '查看' }}
            </el-button>
            <el-button v-if="skill.scope === SKILL_SCOPE.USER" text @click="openShare(skill)">
              <el-icon><Share /></el-icon>
              分享
            </el-button>
            <el-dropdown
              trigger="click"
              placement="bottom-end"
              :show-arrow="false"
              :offset="8"
              :popper-options="{ modifiers: [{ name: 'preventOverflow', options: { padding: 12 } }] }"
              popper-class="skill-export-popper"
              :disabled="Boolean(exportingSkillKey)"
              @command="exportArchive(skill, $event)"
            >
              <el-button text :loading="exportingSkillKey === `${skill.scope}-${skill.name}`"
                :disabled="Boolean(exportingSkillKey)">
                <el-icon v-if="exportingSkillKey !== `${skill.scope}-${skill.name}`"><Download /></el-icon>
                导出
                <el-icon class="archive-menu-chevron"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <li class="skill-export-heading" role="presentation">
                    <strong>导出 Skill</strong>
                    <span>包含完整指令与附属文件</span>
                  </li>
                  <el-dropdown-item v-for="format in archiveFormats" :key="format.value" :command="format.value">
                    <span class="skill-format-icon" aria-hidden="true"><el-icon><Document /></el-icon></span>
                    <span class="skill-format-copy">
                      <span class="skill-format-title">
                        {{ format.label }}
                        <span v-if="format.value === 'zip'" class="skill-format-recommended">推荐</span>
                      </span>
                      <span class="skill-format-description">{{ format.description }}</span>
                    </span>
                    <el-icon class="skill-format-download" aria-hidden="true"><Download /></el-icon>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button v-if="skill.editable" text class="danger-action" @click="removeSkill(skill)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </article>
        <el-empty
          v-if="!loading && !visibleSkills.length"
          :description="activeScope === SKILL_SCOPE.USER
            ? '还没有个人 Skill，可以在这里创建或直接让 Agent 帮你创建'
            : activeScope === SKILL_SCOPE.SHARED ? '还没有已接受的共享 Skill' : '暂无公共 Skill'"
        />
      </div>
    </template>

    <McpManagementPanel
      v-else
      ref="mcpManagementPanel"
      v-model:active-scope="activeMcpScope"
      :administrator="administration.isAdministrator"
    />

    <el-dialog
      v-model="dialogVisible"
      class="content-editor-dialog skill-editor-dialog"
      width="min(720px, 94vw)"
      align-center
      append-to-body
      destroy-on-close
    >
      <template #header>
        <div class="content-dialog-heading">
          <div class="content-dialog-heading-icon skill"><el-icon><MagicStick /></el-icon></div>
          <div>
            <span>{{ skillDialogScope }}</span>
            <strong>{{ dialogTitle }}</strong>
            <p>{{ skillDialogDescription }}</p>
          </div>
        </div>
      </template>

      <el-form class="content-editor-form" label-position="top">
        <section class="content-form-section">
          <header class="content-form-section-heading">
            <span><el-icon><EditPen /></el-icon></span>
            <div>
              <strong>基本信息</strong>
              <p>帮助 Agent 判断何时应该加载并使用这项能力。</p>
            </div>
          </header>
          <el-form-item label="Skill 名称">
            <el-input
              v-model="form.name"
              :disabled="Boolean(editingName) || readOnly"
              placeholder="例如：weekly-report-writer"
            >
              <template #prefix><el-icon><MagicStick /></el-icon></template>
            </el-input>
            <small class="field-tip">使用小写字母、数字、点、下划线或短横线。</small>
          </el-form-item>
          <el-form-item label="使用场景描述">
            <el-input
              v-model="form.description"
              :disabled="readOnly"
              type="textarea"
              :rows="2"
              maxlength="500"
              resize="none"
              show-word-limit
              placeholder="用一句话说明这个 Skill 解决什么问题"
            />
          </el-form-item>
        </section>

        <section class="content-form-section skill-instruction-section">
          <header class="content-form-section-heading">
            <span><el-icon><Document /></el-icon></span>
            <div>
              <strong>执行指令</strong>
              <p>清楚写明步骤、边界条件和期望的输出结果。</p>
            </div>
            <span class="content-format-badge">MARKDOWN</span>
          </header>
          <el-form-item>
            <div class="skill-content-editor">
              <div class="skill-content-editor-bar">
                <span>SKILL.md</span>
                <small>name 与 description 由系统自动生成</small>
              </div>
              <el-input
                v-model="form.content"
                :disabled="readOnly"
                type="textarea"
                :rows="13"
                resize="none"
                placeholder="使用中文写明执行步骤、边界条件和结果要求。"
              />
            </div>
          </el-form-item>
        </section>
      </el-form>

      <template #footer>
        <div class="content-dialog-footer">
          <span v-if="!readOnly">保存后将在下一轮对话中加载新指令。</span>
          <div>
            <el-button @click="dialogVisible = false">{{ readOnly ? '关闭' : '取消' }}</el-button>
            <el-button v-if="!readOnly" type="primary" :loading="saving" @click="saveSkill">
              {{ editingName ? '保存修改' : '创建 Skill' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="shareDialogVisible" title="分享 Skill" width="min(520px, 92vw)" destroy-on-close>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="Skill">
          <el-input :model-value="sharingSkillName" disabled />
        </el-form-item>
        <el-form-item label="接收人手机号">
          <el-input
            v-model="recipientMobile"
            type="tel"
            maxlength="255"
            placeholder="请输入对方登录平台使用的手机号"
            @keyup.enter="submitShare"
          />
          <small class="field-tip">对方需要在 Skill 页面接受邀请，此 Skill 才会进入其 Agent。</small>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shareDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="sharing" @click="submitShare">发送邀请</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.skill-header-actions {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 10px;
}

.skill-header-actions :deep(.el-button) {
  margin-left: 0;
}

.skill-header-actions :deep(.el-button > span) {
  gap: 7px;
}

.skill-import-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 0 18px;
  padding: 12px 15px;
  border: 1px solid var(--primary-border);
  border-radius: 12px;
  color: var(--primary-action-text);
  background: var(--primary-fill);
  font-size: 13px;
  line-height: 1.6;
}

.skill-import-progress > span > span {
  margin-left: 12px;
  color: var(--text-muted);
  font-size: 12px;
}

.skill-actions {
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.skill-actions :deep(.el-button) {
  height: 32px;
  margin-left: 0;
  padding: 0 10px;
  border-radius: 8px;
  font-size: 12px;
}

.skill-actions :deep(.el-button > span) {
  gap: 5px;
}

.archive-menu-chevron {
  margin-left: 1px;
  font-size: 10px;
  color: var(--text-muted);
}

@media (max-width: 520px) {
  .skill-header-actions {
    width: 100%;
  }

  .skill-header-actions .el-button {
    flex: 1;
    width: auto;
    min-width: 0;
  }

  .skill-import-progress > span > span {
    display: block;
    margin-left: 0;
  }
}
</style>

<style>
.skill-export-popper.el-popper {
  width: min(284px, calc(100vw - 32px));
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--popover-bg);
  box-shadow: 0 16px 48px -12px var(--shadow-strong), 0 3px 10px var(--shadow);
}

.skill-export-popper .el-dropdown-menu {
  padding: 7px;
  background: transparent;
}

.skill-export-heading {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 0 5px 5px;
  padding: 8px 5px 12px;
  border-bottom: 1px solid var(--line-faint);
  line-height: 1.5;
}

.skill-export-heading strong {
  color: var(--text);
  font-size: 13px;
  font-weight: 600;
}

.skill-export-heading > span {
  color: var(--text-muted);
  font-size: 11px;
}

.skill-export-popper .el-dropdown-menu__item {
  gap: 11px;
  min-height: 66px;
  padding: 10px;
  border-radius: 10px;
  color: var(--text);
  line-height: 1.5;
  transition: background 150ms ease, color 150ms ease;
}

.skill-export-popper .el-dropdown-menu__item:not(.is-disabled):hover,
.skill-export-popper .el-dropdown-menu__item:not(.is-disabled):focus {
  color: var(--primary-bright);
  background: var(--primary-fill);
}

.skill-export-popper .el-icon {
  margin-right: 0;
}

.skill-format-icon {
  display: grid;
  flex: 0 0 36px;
  height: 40px;
  place-items: center;
  border: 1px solid var(--primary-border);
  border-radius: 10px;
  color: var(--primary-icon);
  background: var(--primary-fill);
  font-size: 19px;
}

.skill-format-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.skill-format-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.skill-format-recommended {
  padding: 1px 6px;
  border-radius: 5px;
  color: var(--primary-action-text);
  background: var(--primary-soft);
  font-size: 10px;
  font-weight: 500;
}

.skill-format-description {
  color: var(--text-muted);
  font-size: 11px;
  white-space: nowrap;
}

.skill-format-download {
  color: var(--text-faint);
  font-size: 14px;
}

.skill-export-popper .el-dropdown-menu__item:focus-visible {
  outline: 2px solid var(--primary-border-strong);
  outline-offset: -2px;
}

.skill-import-tooltip.el-popper {
  max-width: min(268px, calc(100vw - 32px));
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  color: var(--text-soft);
  background: var(--popover-bg);
  box-shadow: 0 8px 28px var(--shadow);
  font-size: 12px;
  line-height: 1.65;
}

.skill-import-tooltip strong,
.skill-import-tooltip span {
  display: block;
}

.skill-import-tooltip strong {
  margin-bottom: 4px;
  color: var(--text);
  font-size: 13px;
}

.skill-import-tooltip span {
  color: var(--primary-bright);
}

.skill-import-tooltip p {
  margin: 8px 0 0;
}
</style>
