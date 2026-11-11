<script setup lang="ts">
import { ArrowDown, Connection, Delete, Document, EditPen, Key, Link, Lock, Setting, User } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref, watch } from 'vue';

import {
  createPublicMcpServer,
  createUserMcpServer,
  deletePublicMcpServer,
  deleteUserMcpServer,
  listMcpServers,
  listPublicMcpServers,
  testMcpConnection,
  setMcpServerEnabled,
  updateMcpUserCredential,
  updatePublicMcpServer,
  updateUserMcpServer,
} from '@/api/mcp';
import { MCP_SCOPE, MCP_TRANSPORT_TYPE } from '@/types/mcp';
import { mergeMcpManagementServers } from './mcpManagement';
import type {
  McpScope,
  McpServer,
  McpServerPayload,
  McpTransportType,
  PublicMcpServerPayload,
} from '@/types/mcp';

interface KeyValueRow {
  key: string;
  value: string;
}

interface McpForm {
  scope: McpScope;
  name: string;
  description: string;
  transportType: McpTransportType;
  endpoint: string;
  command: string;
  argumentsText: string;
  headers: KeyValueRow[];
  queryParameters: KeyValueRow[];
  environment: KeyValueRow[];
  userCredential: string;
  enabled: boolean;
  requestTimeoutSeconds: number;
  initializationTimeoutSeconds: number;
}

type DialogMode = 'create' | 'edit' | 'view' | 'credential';

const props = withDefaults(defineProps<{
  administrator?: boolean;
  showScopeSwitch?: boolean;
}>(), { administrator: false, showScopeSwitch: true });
const activeScope = defineModel<McpScope>('activeScope', { default: MCP_SCOPE.USER });
const servers = ref<McpServer[]>([]);
const loading = ref(false);
const saving = ref(false);
const testingId = ref('');
const togglingServers = reactive(new Set<string>());
const dialogVisible = ref(false);
const selectedId = ref('');
const dialogMode = ref<DialogMode>('create');
const form = reactive<McpForm>(emptyForm());
let loadRequestId = 0;

const isInternalPublic = computed(() => form.scope === MCP_SCOPE.INTERNAL_PUBLIC);
const editingId = computed(() => dialogMode.value === 'edit' ? selectedId.value : '');
const configurationReadOnly = computed(() => dialogMode.value === 'view' || dialogMode.value === 'credential');
const editingCredential = computed(() => dialogMode.value === 'credential');
const visibleServers = computed(() => servers.value.filter((server) => activeScope.value === MCP_SCOPE.PUBLIC
  ? server.scope !== MCP_SCOPE.USER
  : server.scope === MCP_SCOPE.USER));
const dialogTitle = computed(() => {
  if (editingCredential.value) return '配置凭据型公共 MCP';
  if (configurationReadOnly.value) return '公共 MCP';
  return editingId.value ? '修改 MCP' : '创建 MCP';
});
const dialogDescription = computed(() => {
  if (editingCredential.value) return '配置你的访问 Key，即可在 Agent 中使用这项公共能力。';
  if (configurationReadOnly.value) return '查看平台提供的公共 MCP 能力。';
  return editingId.value ? '调整连接参数，保存后将在下一轮对话生效。' : '连接一个 MCP 服务，为 Agent 扩展新的工具能力。';
});

async function loadServers(): Promise<void> {
  const requestId = ++loadRequestId;
  const administrator = props.administrator;
  loading.value = true;
  try {
    const visible = await listMcpServers();
    let loadedServers = visible;
    if (administrator) {
      const publicServers = await listPublicMcpServers();
      loadedServers = mergeMcpManagementServers(visible, publicServers);
    }
    if (requestId === loadRequestId) servers.value = loadedServers;
  } catch (error) {
    if (requestId === loadRequestId) {
      ElMessage.error(error instanceof Error ? error.message : 'MCP 加载失败');
    }
  } finally {
    if (requestId === loadRequestId) loading.value = false;
  }
}

function emptyForm(): McpForm {
  return {
    scope: activeScope.value === MCP_SCOPE.PUBLIC ? MCP_SCOPE.PUBLIC : MCP_SCOPE.USER,
    name: '',
    description: '',
    transportType: MCP_TRANSPORT_TYPE.STREAMABLE_HTTP,
    endpoint: '',
    command: '',
    argumentsText: '',
    headers: [],
    queryParameters: [],
    environment: [],
    userCredential: '',
    enabled: true,
    requestTimeoutSeconds: 60,
    initializationTimeoutSeconds: 30,
  };
}

function openCreate(): void {
  selectedId.value = '';
  dialogMode.value = 'create';
  Object.assign(form, emptyForm());
  dialogVisible.value = true;
}

defineExpose({ openCreate });

function openServer(server: McpServer): void {
  selectedId.value = server.id;
  dialogMode.value = server.editable ? 'edit' : 'view';
  populateForm(server);
  dialogVisible.value = true;
}

function openCredential(server: McpServer): void {
  selectedId.value = server.id;
  dialogMode.value = 'credential';
  populateForm(server);
  dialogVisible.value = true;
}

function populateForm(server: McpServer): void {
  Object.assign(form, {
    name: server.name,
    scope: server.scope,
    description: server.description,
    transportType: server.transportType,
    endpoint: server.endpoint || '',
    command: server.command || '',
    argumentsText: server.arguments.join('\n'),
    headers: toRows(server.headers),
    queryParameters: toRows(server.queryParameters),
    environment: toRows(server.environment),
    userCredential: '',
    enabled: server.enabled,
    requestTimeoutSeconds: server.requestTimeoutSeconds,
    initializationTimeoutSeconds: server.initializationTimeoutSeconds,
  });
}

function statusText(server: McpServer): string {
  if (server.userCredentialRequired && !server.userCredentialConfigured) return '待配置';
  if (!server.enabled) return '服务已停用';
  return server.userEnabled ? '已启用' : '已停用';
}

async function toggleServer(server: McpServer): Promise<void> {
  if (togglingServers.has(server.id)) return;
  togglingServers.add(server.id);
  try {
    server.userEnabled = await setMcpServerEnabled(server, !server.userEnabled);
    ElMessage.success(`MCP 已${server.userEnabled ? '启用' : '停用'}，下一轮对话生效`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '启用状态保存失败');
  } finally {
    togglingServers.delete(server.id);
  }
}

function credentialActionText(server: McpServer): string {
  return server.userCredentialConfigured ? '更新凭证' : '配置凭证';
}

function toRows(values: Record<string, string>): KeyValueRow[] {
  return Object.entries(values).map(([key, value]) => ({ key, value }));
}

function addRow(rows: KeyValueRow[]): void {
  rows.push({ key: '', value: '' });
}

function removeRow(rows: KeyValueRow[], index: number): void {
  rows.splice(index, 1);
}

function toRecord(
  rows: KeyValueRow[],
  label: string,
  preserveEmptyValue: boolean,
): Record<string, string> {
  const values: Record<string, string> = {};
  for (const row of rows) {
    const key = row.key.trim();
    const value = row.value.trim();
    if (!key && !value) continue;
    if (!key) {
      throw new Error(`${label}名称不能为空`);
    }
    if (!value && !preserveEmptyValue) {
      throw new Error(`${label}值不能为空`);
    }
    const duplicatedKey = Object.keys(values).find((existing) => existing.toLowerCase() === key.toLowerCase());
    if (duplicatedKey) {
      throw new Error(`${label}名称“${key}”重复`);
    }
    values[key] = value;
  }
  return values;
}

function buildPayload(): PublicMcpServerPayload {
  if (!form.name.trim() || !form.description.trim()) {
    throw new Error('请填写完整的 MCP 名称和描述');
  }
  const transportType = form.transportType;
  const streamableHttp = transportType === MCP_TRANSPORT_TYPE.STREAMABLE_HTTP;
  const preserveEmptyCredential = Boolean(editingId.value);
  if (streamableHttp && !form.endpoint.trim()) {
    throw new Error('请填写 Streamable HTTP 服务地址');
  }
  if (!streamableHttp && !form.command.trim()) {
    throw new Error('请填写 STDIO 启动命令');
  }
  const payload: PublicMcpServerPayload = {
    name: form.name.trim(),
    description: form.description.trim(),
    transportType,
    endpoint: streamableHttp ? form.endpoint.trim() : undefined,
    command: streamableHttp ? undefined : form.command.trim(),
    arguments: streamableHttp
      ? []
      : form.argumentsText.split('\n').map((argument) => argument.trim()).filter(Boolean),
    headers: streamableHttp ? toRecord(form.headers, 'Header', preserveEmptyCredential) : {},
    queryParameters: streamableHttp
      ? toRecord(form.queryParameters, '查询参数', preserveEmptyCredential)
      : {},
    environment: streamableHttp
      ? {}
      : toRecord(form.environment, '环境变量', preserveEmptyCredential),
    enabled: form.enabled,
    requestTimeoutSeconds: form.requestTimeoutSeconds,
    initializationTimeoutSeconds: form.initializationTimeoutSeconds,
  };
  if (activeScope.value === MCP_SCOPE.PUBLIC) {
    if (!editingId.value) payload.scope = form.scope;
  }
  return payload;
}

async function saveServer(): Promise<void> {
  saving.value = true;
  try {
    const payload = buildPayload();
    if (editingId.value) {
      if (activeScope.value === MCP_SCOPE.PUBLIC) {
        await updatePublicMcpServer(editingId.value, payload);
      } else {
        await updateUserMcpServer(editingId.value, payload as McpServerPayload);
      }
      ElMessage.success('MCP 已更新，下一次对话将加载新配置');
    } else {
      if (activeScope.value === MCP_SCOPE.PUBLIC) {
        await createPublicMcpServer(payload);
      } else {
        await createUserMcpServer(payload as McpServerPayload);
      }
      ElMessage.success('MCP 已创建');
    }
    dialogVisible.value = false;
    await loadServers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

async function saveUserCredential(): Promise<void> {
  const credential = form.userCredential.trim();
  if (!credential) {
    ElMessage.error('请填写用户凭证');
    return;
  }
  saving.value = true;
  try {
    const server = await updateMcpUserCredential(selectedId.value, { credential });
    ElMessage.success(server.enabled ? '凭证已配置，MCP 已启用' : '凭证已配置');
    dialogVisible.value = false;
    await loadServers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

async function removeServer(server: McpServer): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除 MCP“${server.name}”吗？`, '删除 MCP', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    await (server.scope === MCP_SCOPE.USER ? deleteUserMcpServer : deletePublicMcpServer)(server.id);
    ElMessage.success('MCP 已删除');
    await loadServers();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error instanceof Error ? error.message : '删除失败');
    }
  }
}

async function checkConnection(server: McpServer): Promise<void> {
  testingId.value = server.id;
  try {
    const result = await testMcpConnection(server.scope, server.id);
    ElMessage.success(
      result.toolNames.length
        ? `连接成功，发现 ${result.toolNames.length} 个工具`
        : '连接成功，当前服务未返回工具',
    );
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '连接检测失败');
  } finally {
    testingId.value = '';
  }
}

onMounted(loadServers);
watch(() => props.administrator, (administrator, previous) => {
  if (administrator !== previous) void loadServers();
});
</script>

<template>
  <div class="mcp-management">
    <div v-if="showScopeSwitch" class="scope-switch">
      <button :class="{ active: activeScope === MCP_SCOPE.USER }" @click="activeScope = MCP_SCOPE.USER">
        <el-icon><User /></el-icon>
        我的 MCP
      </button>
      <button :class="{ active: activeScope === MCP_SCOPE.PUBLIC }" @click="activeScope = MCP_SCOPE.PUBLIC">
        <el-icon>
          <EditPen v-if="administrator" />
          <Lock v-else />
        </el-icon>
        公共 MCP
      </button>
    </div>

    <p v-if="showScopeSwitch" class="extension-enablement-hint">开关仅对我生效 · 下一轮对话生效</p>
    <div v-loading="loading" class="mcp-grid">
      <article v-for="server in visibleServers" :key="server.id" class="mcp-card"
        :class="{ 'extension-disabled': !server.enabled || !server.userEnabled }">
        <div class="skill-card-head">
          <div class="extension-icon mcp"><el-icon><Connection /></el-icon></div>
          <div>
            <span>{{ server.scope === MCP_SCOPE.USER ? '个人 MCP' : '公共 MCP' }}</span>
            <h2>{{ server.name }}</h2>
          </div>
          <div class="extension-enablement"
            :title="server.enabled ? '仅对我生效，下一轮对话生效' : '服务配置已停用，启用服务后可切换个人开关'">
            <span>{{ statusText(server) }}</span>
            <el-switch :model-value="server.enabled && server.userEnabled" :disabled="!server.enabled"
              :loading="togglingServers.has(server.id)"
              :aria-label="`${server.userEnabled ? '停用' : '启用'} MCP ${server.name}（仅对我生效）`"
              @change="toggleServer(server)" />
          </div>
        </div>
        <p>{{ server.description }}</p>
        <dl>
          <div>
            <dt>传输方式</dt>
            <dd>
              {{ server.transportType === MCP_TRANSPORT_TYPE.STREAMABLE_HTTP ? 'Streamable HTTP' : 'STDIO' }}
            </dd>
          </div>
          <div>
            <dt>运行支持</dt>
            <dd>{{ server.runtimeSupported ? '可用' : '暂不可用' }}</dd>
          </div>
        </dl>
        <div class="skill-actions">
          <el-button
            v-if="server.runtimeSupported && server.enabled && server.userEnabled
              && (!server.userCredentialRequired || server.userCredentialConfigured)"
            text
            :loading="testingId === server.id"
            @click="checkConnection(server)"
          >
            检测连接
          </el-button>
          <el-button
            v-if="server.userCredentialRequired"
            text
            :type="server.userCredentialConfigured ? undefined : 'primary'"
            @click="openCredential(server)"
          >
            <el-icon><Key /></el-icon>
            {{ credentialActionText(server) }}
          </el-button>
          <el-button v-if="server.editable" text @click="openServer(server)">
            <el-icon><EditPen /></el-icon>
            编辑
          </el-button>
          <el-button v-else-if="!server.userCredentialRequired" text @click="openServer(server)">
            查看
          </el-button>
          <el-button v-if="server.editable" text class="danger-action" @click="removeServer(server)">
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
        </div>
      </article>
      <el-empty
        v-if="!loading && !visibleServers.length"
        :image-size="56"
        :description="activeScope === MCP_SCOPE.USER ? '还没有个人 MCP' : '暂无公共 MCP'"
      >
        <template #image>
          <span class="empty-state-icon" aria-hidden="true"><el-icon><Connection /></el-icon></span>
        </template>
      </el-empty>
    </div>

    <el-dialog
      v-model="dialogVisible"
      class="mcp-editor-dialog"
      width="min(760px, 94vw)"
      align-center
      append-to-body
      destroy-on-close
    >
      <template #header>
        <div class="mcp-dialog-heading">
          <div class="mcp-dialog-heading-icon"><el-icon><Connection /></el-icon></div>
          <div>
            <span>{{ activeScope === MCP_SCOPE.PUBLIC ? 'PLATFORM MCP' : 'PERSONAL MCP' }}</span>
            <strong>{{ dialogTitle }}</strong>
            <p>{{ dialogDescription }}</p>
          </div>
          <el-tag v-if="isInternalPublic" effect="plain">用户独立凭据</el-tag>
        </div>
      </template>

      <el-form class="mcp-editor-form" label-position="top">
        <section class="mcp-form-section">
          <header class="mcp-form-section-heading">
            <span><el-icon><Document /></el-icon></span>
            <div>
              <strong>基本信息</strong>
              <p>定义这项能力在 Agent 中的名称和用途。</p>
            </div>
          </header>

          <div class="form-grid">
            <el-form-item label="MCP 名称">
              <el-input
                v-model="form.name"
                :disabled="Boolean(editingId) || configurationReadOnly"
                maxlength="64"
                placeholder="例如：internal-search"
              >
                <template #prefix><el-icon><Connection /></el-icon></template>
              </el-input>
              <small class="field-tip">将用于生成 mcp__名称__工具名。</small>
            </el-form-item>
            <el-form-item label="传输方式">
              <el-select v-model="form.transportType" :disabled="configurationReadOnly">
                <el-option label="Streamable HTTP" :value="MCP_TRANSPORT_TYPE.STREAMABLE_HTTP" />
                <el-option label="STDIO" :value="MCP_TRANSPORT_TYPE.STDIO" />
              </el-select>
            </el-form-item>
          </div>

          <el-form-item label="用途描述">
            <el-input
              v-model="form.description"
              :disabled="configurationReadOnly"
              type="textarea"
              :rows="3"
              maxlength="500"
              resize="none"
              show-word-limit
              placeholder="告诉 Agent 什么时候应该使用这个 MCP"
            />
          </el-form-item>

          <div v-if="!configurationReadOnly" class="mcp-enable-row">
            <div>
              <strong>启用 MCP</strong>
              <span>关闭后保留配置，但不会加载到 Agent。</span>
            </div>
            <el-switch v-model="form.enabled" />
          </div>
        </section>

        <section class="mcp-form-section">
          <header class="mcp-form-section-heading">
            <span><el-icon><Link /></el-icon></span>
            <div>
              <strong>{{ editingCredential ? '访问凭证' : '连接配置' }}</strong>
              <p v-if="editingCredential">凭证仅对当前账号生效，不会共享给其他用户。</p>
              <p v-else>填写服务入口，平台会在保存时检查配置格式。</p>
            </div>
          </header>

          <template v-if="configurationReadOnly">
            <div class="mcp-runtime-note">
              <el-icon><Lock /></el-icon>
              <p v-if="editingCredential">
                凭据型公共 MCP 由平台统一维护，填写你自己的凭证后仅对当前用户启用。
              </p>
              <p v-else>公共 MCP 由平台统一维护，当前用户可直接使用。</p>
            </div>
            <el-form-item v-if="editingCredential" label="你的访问 Key">
              <el-input
                v-model="form.userCredential"
                show-password
                maxlength="2048"
                placeholder="粘贴访问 Key"
              >
                <template #prefix><el-icon><Key /></el-icon></template>
              </el-input>
              <small class="field-tip">鉴权值不会回显，新值会覆盖当前用户的旧值。</small>
            </el-form-item>
          </template>

          <template v-else-if="form.transportType === MCP_TRANSPORT_TYPE.STREAMABLE_HTTP">
            <el-form-item label="服务地址">
              <el-input
                v-model="form.endpoint"
                maxlength="2048"
                placeholder="https://mcp.example.com/mcp"
              >
                <template #prefix><el-icon><Link /></el-icon></template>
              </el-input>
              <small class="field-tip">可以在地址中携带固定查询参数。</small>
            </el-form-item>
          </template>

          <template v-else>
            <div class="form-grid">
              <el-form-item label="启动命令">
                <el-input v-model="form.command" maxlength="512" placeholder="例如：npx" />
              </el-form-item>
              <el-form-item label="启动参数">
                <el-input
                  v-model="form.argumentsText"
                  type="textarea"
                  :rows="2"
                  resize="none"
                  placeholder="每行一个参数"
                />
              </el-form-item>
            </div>
          </template>
        </section>

        <details v-if="!configurationReadOnly" class="mcp-advanced-config">
          <summary>
            <span class="mcp-advanced-icon"><el-icon><Setting /></el-icon></span>
            <span>
              <strong>高级配置</strong>
              <small>凭证参数、环境变量和超时设置</small>
            </span>
            <el-icon class="mcp-advanced-arrow"><ArrowDown /></el-icon>
          </summary>
          <div class="mcp-advanced-content">
            <template v-if="form.transportType === MCP_TRANSPORT_TYPE.STREAMABLE_HTTP">
              <section class="mcp-key-value-section">
                <header>
                  <div>
                    <strong>HTTP Header</strong>
                    <small>编辑时值留空表示保留原值。</small>
                  </div>
                  <el-button text @click="addRow(form.headers)">添加一项</el-button>
                </header>
                <div v-for="(row, index) in form.headers" :key="index" class="mcp-key-value-row">
                  <el-input v-model="row.key" placeholder="Header 名称" />
                  <el-input v-model="row.value" show-password placeholder="Header 值" />
                  <el-button text class="danger-action" @click="removeRow(form.headers, index)">删除</el-button>
                </div>
              </section>

              <section class="mcp-key-value-section">
                <header>
                  <div>
                    <strong>查询参数</strong>
                    <small>编辑时值留空表示保留原值。</small>
                  </div>
                  <el-button text @click="addRow(form.queryParameters)">添加一项</el-button>
                </header>
                <div v-for="(row, index) in form.queryParameters" :key="index" class="mcp-key-value-row">
                  <el-input v-model="row.key" placeholder="参数名称" />
                  <el-input v-model="row.value" show-password placeholder="参数值" />
                  <el-button text class="danger-action" @click="removeRow(form.queryParameters, index)">删除</el-button>
                </div>
              </section>
            </template>

            <section v-else class="mcp-key-value-section">
              <header>
                <div>
                  <strong>环境变量</strong>
                  <small>编辑时值留空表示保留原值。</small>
                </div>
                <el-button text @click="addRow(form.environment)">添加一项</el-button>
              </header>
              <div v-for="(row, index) in form.environment" :key="index" class="mcp-key-value-row">
                <el-input v-model="row.key" placeholder="变量名称" />
                <el-input v-model="row.value" show-password placeholder="变量值" />
                <el-button text class="danger-action" @click="removeRow(form.environment, index)">删除</el-button>
              </div>
            </section>

            <div class="form-grid mcp-timeout-grid">
              <el-form-item label="请求超时">
                <el-input-number v-model="form.requestTimeoutSeconds" :min="1" :max="600" />
                <small class="field-tip">单位：秒</small>
              </el-form-item>
              <el-form-item label="初始化超时">
                <el-input-number v-model="form.initializationTimeoutSeconds" :min="1" :max="120" />
                <small class="field-tip">单位：秒</small>
              </el-form-item>
            </div>
          </div>
        </details>
      </el-form>

      <template #footer>
        <div class="mcp-dialog-footer">
          <span v-if="!configurationReadOnly">保存后将在下一轮对话中加载新配置。</span>
          <div>
            <el-button @click="dialogVisible = false">{{ configurationReadOnly ? '关闭' : '取消' }}</el-button>
            <el-button
              v-if="editingCredential"
              type="primary"
              :loading="saving"
              @click="saveUserCredential"
            >
              保存并启用
            </el-button>
            <el-button v-if="!configurationReadOnly" type="primary" :loading="saving" @click="saveServer">
              {{ editingId ? '保存修改' : '创建 MCP' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mcp-management {
  width: 100%;
  min-width: 0;
}

.mcp-grid {
  grid-template-columns: repeat(auto-fill, minmax(min(360px, 100%), 1fr));
  min-height: 0;
  align-items: stretch;
}

.mcp-card {
  min-height: 0;
  padding: 24px;
  border-radius: 12px;
}

.mcp-card .extension-icon {
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  color: var(--text-soft);
  background: var(--surface-subtle);
  font-size: 18px;
}

.mcp-card h2 {
  font-size: 16px;
  font-weight: 500;
  line-height: 1.5;
}

.mcp-card > p {
  margin: 16px 0 20px;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.mcp-card dl {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  margin-bottom: 20px;
}

.mcp-card dl div {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 0;
  border-radius: 0;
  background: transparent;
}

.mcp-card dd {
  margin: 0;
  font-size: 12px;
}

.mcp-card .skill-actions {
  margin-top: auto;
  flex-wrap: wrap;
  gap: 4px;
}

.mcp-card .skill-actions :deep(.el-button) {
  margin-left: 0;
  padding: 0 8px;
}

@container workspace (max-width: 760px) {
  .mcp-grid { grid-template-columns: minmax(0, 1fr); }
  .mcp-card { padding: 20px; }
}
</style>
