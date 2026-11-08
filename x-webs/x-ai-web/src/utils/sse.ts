export interface SseParseResult {
  events: string[];
  remainder: string;
}

export function parseSseChunk(buffer: string, chunk: string): SseParseResult {
  const blocks = `${buffer}${chunk}`.split(/\r?\n\r?\n/);
  const remainder = blocks.pop() ?? '';
  const events = blocks
    .map((block) => block
      .split(/\r?\n/)
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
      .join('\n'))
    .filter(Boolean);

  return { events, remainder };
}
