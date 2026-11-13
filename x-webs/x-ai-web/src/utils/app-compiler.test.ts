import { describe, expect, it } from 'vitest';

import type { AgentAppContent } from '@/types/agent-app';
import { compileApplicationSource } from './app-compiler';

const content = (source: string): AgentAppContent => ({ sdkVersion: 1, html: '', css: '', source, permissions: [] });

describe('application TypeScript compiler', () => {
  it('removes types and permits awaiting the SDK without external modules', async () => {
    const javascript = await compileApplicationSource(content('const value: string = "ready"; await app.storage.set("value", value);'));
    expect(javascript).toContain('const value = "ready"');
    expect(javascript).toContain('await app.storage.set');
    expect(javascript).not.toContain(': string');
    expect(() => new Function(javascript)).not.toThrow();
  });

  it.each([
    'import { value } from "external";', 'export const value = 1;', 'await import("external");',
    'const value = require("external");', 'const value = import.meta.url;',
    'export default function main() {}', 'import type { Value } from "external";', 'type Value = import("external").Value;',
  ])('rejects module and package entry points: %s', async source => {
    await expect(compileApplicationSource(content(source))).rejects.toThrow('自包含 TypeScript');
  });

  it('reports syntax errors before running the application', async () => {
    await expect(compileApplicationSource(content('const value: = ;'))).rejects.toThrow('第 1 行');
  });

  it('rejects unsupported SDK versions and excessive content', async () => {
    await expect(compileApplicationSource({ ...content(''), sdkVersion: 2 })).rejects.toThrow('SDK 版本');
    await expect(compileApplicationSource(content('a'.repeat(4 * 1024 * 1024 + 1)))).rejects.toThrow('4 MB');
  });
});
