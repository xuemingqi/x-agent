import { createRouter, createWebHistory } from 'vue-router';

import { applicationPages } from '@/applications/pages';
import AppLayout from '@/layouts/AppLayout.vue';
import { getToken } from '@/utils/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: AppLayout,
      children: [
        {
          path: '',
          redirect: '/chat',
        },
        {
          path: 'chat/:sessionId?',
          name: 'chat',
          component: () => import('@/views/chat/ChatView.vue'),
        },
        {
          path: 'apps',
          children: [
            { path: '', name: 'apps', component: () => import('@/views/apps/AppsView.vue') },
            ...applicationPages,
          ],
        },
        {
          path: 'schedules',
          name: 'schedules',
          component: () => import('@/views/schedule/ScheduleView.vue'),
        },
        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/knowledge/KnowledgeView.vue'),
        },
        {
          path: 'extensions',
          name: 'extensions',
          component: () => import('@/views/extension/ExtensionView.vue'),
        },
      ],
    },
  ],
});

router.beforeEach((to) => {
  const token = getToken();
  if (!to.meta.public && !token) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    };
  }
  if (to.name === 'login' && token) {
    return { name: 'chat' };
  }
  return true;
});

export default router;
