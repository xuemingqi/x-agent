import type { ScheduleForm, SchedulePayload } from '@/types/schedule';

export function buildSchedulePayload(form: ScheduleForm): SchedulePayload {
  const base = {
    taskName: form.taskName.trim(),
    modelId: form.modelId,
    prompt: form.prompt.trim(),
    scheduleMode: form.scheduleMode,
    initialDelayMs: form.initialDelayMs,
  };
  if (form.scheduleMode === 1) {
    return {
      ...base,
      cronExpression: form.cronExpression?.trim(),
    };
  }
  if (form.scheduleMode === 2) {
    return {
      ...base,
      fixedRateMs: form.fixedRateMs,
    };
  }
  return {
    ...base,
    fixedDelayMs: form.fixedDelayMs,
  };
}
