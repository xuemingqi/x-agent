import { request } from './http';
import type { PageResult } from './types';
import type {
  ScheduleExecution,
  SchedulePayload,
  ScheduleQuery,
  ScheduleTask,
} from '@/types/schedule';

export function listSchedules(query: ScheduleQuery): Promise<PageResult<ScheduleTask[]>> {
  return request<PageResult<ScheduleTask[]>>({
    url: '/schedule/tasks',
    method: 'GET',
    params: query,
  });
}

export function createSchedule(payload: SchedulePayload): Promise<ScheduleTask> {
  return request<ScheduleTask>({ url: '/schedule/tasks', method: 'POST', data: payload });
}

export function updateSchedule(id: string, payload: SchedulePayload): Promise<ScheduleTask> {
  return request<ScheduleTask>({
    url: `/schedule/tasks/${id}`,
    method: 'PUT',
    data: payload,
  });
}

export function deleteSchedule(id: string): Promise<void> {
  return request<void>({ url: `/schedule/tasks/${id}`, method: 'DELETE' });
}

export function pauseSchedule(id: string): Promise<void> {
  return request<void>({ url: `/schedule/tasks/${id}/pause`, method: 'POST' });
}

export function resumeSchedule(id: string): Promise<void> {
  return request<void>({ url: `/schedule/tasks/${id}/resume`, method: 'POST' });
}

export function runScheduleNow(id: string): Promise<ScheduleExecution> {
  return request<ScheduleExecution>({ url: `/schedule/tasks/${id}/run-now`, method: 'POST' });
}

export function listScheduleExecutions(
  id: string,
  page = 1,
  pageSize = 20,
): Promise<PageResult<ScheduleExecution[]>> {
  return request<PageResult<ScheduleExecution[]>>({
    url: `/schedule/tasks/${id}/executions`,
    method: 'GET',
    params: { page, pageSize },
  });
}
