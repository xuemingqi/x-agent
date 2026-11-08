import axios, { type AxiosRequestConfig } from 'axios';

import type { BaseResult } from './types';
import { authorizationHeader, handleAuthenticationError } from '@/utils/auth';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/ai';

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  validateStatus: () => true,
});

http.interceptors.request.use((config) => {
  Object.assign(config.headers, authorizationHeader());
  return config;
});

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<BaseResult<T>>(config);
  handleAuthenticationError(response.data.code);
  return unwrapResult(response.data);
}

export async function requestBlob(config: AxiosRequestConfig): Promise<Blob> {
  const response = await http.request<Blob>({ ...config, responseType: 'blob' });
  if (response.status >= 200 && response.status < 300) {
    return response.data;
  }
  const text = await response.data.text();
  let message = '下载失败';
  try {
    const result = JSON.parse(text) as BaseResult<never>;
    handleAuthenticationError(result.code);
    message = result.msg || message;
  } catch {
    // 非 JSON 错误响应统一使用下载失败提示。
  }
  throw new Error(message);
}

export function unwrapResult<T>(result: BaseResult<T>): T {
  if (result.code !== 200) {
    throw new Error(result.msg || '请求失败');
  }
  return result.data;
}

export function authHeaders(): Record<string, string> {
  return authorizationHeader();
}
