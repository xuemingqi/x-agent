import axios from 'axios';

import { request, unwrapResult } from './http';
import type { BaseResult } from './types';
import { authorizationHeader } from '@/utils/auth';

export const AUTH_BASE_URL = import.meta.env.VITE_AUTH_BASE_URL || '/auth';

export interface LoginRequest {
  mobile: string;
  password: string;
  codeId: string;
  codeValue: string;
}

export interface VerificationCode {
  id: string;
  imgBase64: string;
}

export interface ApiKeyResult {
  apiKey: string;
}

const authHttp = axios.create({
  baseURL: AUTH_BASE_URL,
  timeout: 30000,
  validateStatus: () => true,
});

export async function getVerificationCode(): Promise<VerificationCode> {
  const response = await authHttp.get<BaseResult<VerificationCode>>('/verification/code');
  return unwrapResult(response.data);
}

export async function login(request: LoginRequest): Promise<string> {
  const response = await authHttp.post<BaseResult<string>>('/login', request);
  return unwrapResult(response.data);
}

export async function logout(): Promise<void> {
  const response = await authHttp.post<BaseResult<null>>('/logout', undefined, {
    headers: authorizationHeader(),
  });
  unwrapResult(response.data);
}

export function issueApiKey(): Promise<ApiKeyResult> {
  return request({ url: '/api-key', method: 'post' });
}
