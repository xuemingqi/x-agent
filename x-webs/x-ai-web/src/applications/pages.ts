import type { RouteRecordRaw } from 'vue-router';

// 页面由代码注册，应用列表由服务端提供；不加载服务端下发的任意组件或脚本。
export const applicationPages: RouteRecordRaw[] = [
  {
    path: 'http-lab',
    name: 'app-http-lab',
    component: () => import('@/views/apps/HttpLabView.vue'),
  },
  {
    path: 'color-lab',
    name: 'app-color-lab',
    component: () => import('@/views/apps/ColorLabView.vue'),
  },
];

export function hasApplicationPage(id: string): boolean {
  return applicationPages.some((page) => page.path === id);
}
