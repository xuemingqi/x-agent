import type { AgentAppContent } from '@/types/agent-app';

const MAX_SOURCE_BYTES = 4 * 1024 * 1024;

/** 应用只使用浏览器和平台 SDK；编译器按需加载，主页面不承担它的体积。 */
export async function compileApplicationSource(content: AgentAppContent): Promise<string> {
  if (content.sdkVersion !== 1) throw new Error('当前客户端不支持此应用的 SDK 版本');
  if (new TextEncoder().encode(content.html + content.css + content.source).length > MAX_SOURCE_BYTES) {
    throw new Error('应用内容超过 4 MB，请缩小源码或拆分数据');
  }
  const ts = await import('typescript');
  const file = ts.createSourceFile('application.ts', content.source, ts.ScriptTarget.ES2022, true, ts.ScriptKind.TS);
  function inspect(node: import('typescript').Node): void {
    if (ts.isImportDeclaration(node) || ts.isImportTypeNode(node) || ts.isImportEqualsDeclaration(node) || ts.isExportDeclaration(node)
      || ts.isExportAssignment(node) || ts.isMetaProperty(node)
      || (ts.isCallExpression(node) && (node.expression.kind === ts.SyntaxKind.ImportKeyword
        || (ts.isIdentifier(node.expression) && node.expression.text === 'require')))
      || (ts.canHaveModifiers(node) && ts.getModifiers(node)?.some(modifier => modifier.kind === ts.SyntaxKind.ExportKeyword))) {
      throw new Error('应用需为自包含 TypeScript，暂不支持 import、export、require 或外部依赖');
    }
    ts.forEachChild(node, inspect);
  }
  inspect(file);
  // 包装后允许直接 await SDK，应用声明也不会污染运行容器的全局变量。
  const result = ts.transpileModule(`(async () => {\n${content.source}\n})();`, {
    fileName: 'application.ts', reportDiagnostics: true,
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext, strict: true },
  });
  const errors = result.diagnostics?.filter(diagnostic => diagnostic.category === ts.DiagnosticCategory.Error) ?? [];
  if (errors.length) {
    throw new Error(errors.map(diagnostic => {
      const position = diagnostic.file && diagnostic.start !== undefined
        ? diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start) : undefined;
      return `${position ? `第 ${Math.max(1, position.line)} 行：` : ''}${ts.flattenDiagnosticMessageText(diagnostic.messageText, '\n')}`;
    }).join('\n'));
  }
  return result.outputText;
}
