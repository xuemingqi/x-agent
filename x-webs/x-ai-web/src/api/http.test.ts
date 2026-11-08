import { describe, expect, it } from 'vitest';

import { unwrapResult } from './http';

describe('unwrapResult', () => {
  it('returns successful response data', () => {
    expect(unwrapResult({ code: 200, msg: 'success!', data: { id: '1' } })).toEqual({ id: '1' });
  });

  it('throws the backend business message', () => {
    expect(() => unwrapResult({ code: 10501, msg: '模型配置不可用！', data: null }))
      .toThrow('模型配置不可用！');
  });
});
