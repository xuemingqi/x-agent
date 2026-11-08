import { describe, expect, it } from 'vitest';

import { parseSseChunk } from './sse';

describe('parseSseChunk', () => {
  it('keeps an incomplete event until the next network chunk arrives', () => {
    const first = parseSseChunk('', 'data: {"code":200,"data":{"eventType":"text_');

    expect(first.events).toEqual([]);

    const second = parseSseChunk(first.remainder, 'delta"}}\n\n');

    expect(second.events).toEqual(['{"code":200,"data":{"eventType":"text_delta"}}']);
    expect(second.remainder).toBe('');
  });

  it('returns every complete event from one chunk', () => {
    const result = parseSseChunk('', 'data: {"code":200,"data":1}\n\ndata: {"code":200,"data":2}\n\n');

    expect(result.events).toEqual([
      '{"code":200,"data":1}',
      '{"code":200,"data":2}',
    ]);
  });
});
