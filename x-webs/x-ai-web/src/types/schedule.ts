import type { ConversationFile } from './chat';

export type ScheduleMode = 1 | 2 | 3;
export type ScheduleStatus = 1 | 2;
export type ExecutionStatus = 1 | 2 | 3 | 4 | 5;

export interface ScheduleForm {
  taskName: string;
  modelId: string;
  prompt: string;
  scheduleMode: ScheduleMode;
  cronExpression?: string;
  fixedRateMs?: number;
  fixedDelayMs?: number;
  initialDelayMs: number;
}

export type SchedulePayload = ScheduleForm;

export interface ScheduleTask extends ScheduleForm {
  id: string;
  userId: string;
  status: ScheduleStatus;
  lastRunTime: string | null;
  nextRunTime: string | null;
  lastExecutionStatus: ExecutionStatus | null;
  createTime: string;
  updateTime: string;
}

export interface ScheduleExecution {
  id: string;
  scheduleTaskId: string;
  triggerSource: 1 | 2;
  scheduledFireTime: string | null;
  taskId: string | null;
  ownerUserId: string;
  modelId: string;
  status: ExecutionStatus;
  attemptCount: number;
  startTime: string | null;
  finishTime: string | null;
  resultSummary: string | null;
  errorSummary: string | null;
  files: ConversationFile[];
  createTime: string | null;
  updateTime: string | null;
}

export interface ScheduleQuery {
  page: number;
  pageSize: number;
  taskName?: string;
  scheduleMode?: ScheduleMode;
  status?: ScheduleStatus;
}
