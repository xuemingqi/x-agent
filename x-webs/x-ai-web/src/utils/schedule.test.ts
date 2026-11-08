import { describe, expect, it } from 'vitest';

import { buildSchedulePayload } from './schedule';

const baseForm = {
  taskName: '日报',
  modelId: '2071075222613921792',
  prompt: '生成日报',
  initialDelayMs: 0,
};

describe('buildSchedulePayload', () => {
  it('keeps only the cron field for cron schedules', () => {
    expect(buildSchedulePayload({
      ...baseForm,
      scheduleMode: 1,
      cronExpression: '0 0 9 * * ?',
      fixedRateMs: 60000,
      fixedDelayMs: 60000,
    })).toEqual({
      ...baseForm,
      scheduleMode: 1,
      cronExpression: '0 0 9 * * ?',
    });
  });

  it('keeps only the matching interval for rate and delay schedules', () => {
    expect(buildSchedulePayload({
      ...baseForm,
      scheduleMode: 2,
      cronExpression: '0 0 9 * * ?',
      fixedRateMs: 60000,
      fixedDelayMs: 90000,
    })).toEqual({
      ...baseForm,
      scheduleMode: 2,
      fixedRateMs: 60000,
    });
    expect(buildSchedulePayload({
      ...baseForm,
      scheduleMode: 3,
      cronExpression: '0 0 9 * * ?',
      fixedRateMs: 60000,
      fixedDelayMs: 90000,
    })).toEqual({
      ...baseForm,
      scheduleMode: 3,
      fixedDelayMs: 90000,
    });
  });
});
