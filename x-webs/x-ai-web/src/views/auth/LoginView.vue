<script setup lang="ts">
import { Lock, Phone, RefreshRight } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { getVerificationCode, login } from '@/api/auth';
import { saveToken } from '@/utils/auth';

const route = useRoute();
const router = useRouter();
const form = reactive({
  mobile: '',
  password: '',
  codeId: '',
  codeValue: '',
});
const captchaImage = ref('');
const captchaLoading = ref(false);
const submitting = ref(false);

onMounted(refreshCaptcha);

async function refreshCaptcha(): Promise<void> {
  if (captchaLoading.value) {
    return;
  }
  captchaLoading.value = true;
  try {
    const captcha = await getVerificationCode();
    form.codeId = captcha.id;
    form.codeValue = '';
    captchaImage.value = captcha.imgBase64;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '验证码加载失败');
  } finally {
    captchaLoading.value = false;
  }
}

async function submit(): Promise<void> {
  if (!form.mobile.trim() || !form.password || !form.codeValue.trim()) {
    ElMessage.warning('请填写手机号、密码和图形验证码');
    return;
  }
  if (!form.codeId) {
    ElMessage.warning('请刷新图形验证码后重试');
    return;
  }
  submitting.value = true;
  try {
    const token = await login({
      mobile: form.mobile.trim(),
      password: form.password,
      codeId: form.codeId,
      codeValue: form.codeValue.trim(),
    });
    saveToken(token);
    await router.replace(resolveRedirect(route.query.redirect));
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败');
    await refreshCaptcha();
  } finally {
    submitting.value = false;
  }
}

function resolveRedirect(value: unknown): string {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')
    ? value
    : '/chat';
}
</script>

<template>
  <main class="login-page">
    <section class="login-intro">
      <div class="login-brand">
        <span class="agent-mark" aria-hidden="true" />
        <strong>X Agent</strong>
      </div>
      <p>智能体工作台</p>
      <h1>从一个目标，<br />到一份完成的工作</h1>
      <span>配置个人模型，在独立工作区中开展对话、推进任务并查看执行过程</span>
      <div class="login-feature-list">
        <div><i />个人模型配置与会话独立管理</div>
        <div><i />任务过程实时可见、随时中止</div>
        <div><i />会话和执行记录持续保存</div>
      </div>
    </section>

    <section class="login-panel">
      <form class="login-card" @submit.prevent="submit">
        <header>
          <span>智能体工作台</span>
          <h2>登录 X Agent</h2>
          <p>使用系统账号进入智能体工作台</p>
        </header>

        <label class="login-field">
          <span>手机号</span>
          <el-input
            v-model="form.mobile"
            autocomplete="username"
            inputmode="tel"
            placeholder="请输入手机号"
            size="large"
          >
            <template #prefix><el-icon><Phone /></el-icon></template>
          </el-input>
        </label>

        <label class="login-field">
          <span>密码</span>
          <el-input
            v-model="form.password"
            autocomplete="current-password"
            placeholder="请输入密码"
            show-password
            size="large"
            type="password"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </label>

        <label class="login-field">
          <span>图形验证码</span>
          <div class="captcha-row">
            <el-input
              v-model="form.codeValue"
              autocomplete="off"
              maxlength="4"
              placeholder="请输入验证码"
              size="large"
            />
            <button
              class="captcha-image"
              :class="{ loading: captchaLoading }"
              type="button"
              title="刷新验证码"
              @click="refreshCaptcha"
            >
              <img v-if="captchaImage" :src="captchaImage" alt="图形验证码" />
              <el-icon v-else><RefreshRight /></el-icon>
            </button>
          </div>
        </label>

        <button class="login-submit" :disabled="submitting" type="submit">
          {{ submitting ? '正在登录…' : '登录' }}
        </button>
        <p class="login-security">登录凭证只保存在当前浏览器中</p>
      </form>
    </section>
  </main>
</template>
