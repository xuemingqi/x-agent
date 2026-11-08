export interface TreeItem {
  id: string;
  parentId: string;
}

export type TreeNode<T extends TreeItem> = T & { children: TreeNode<T>[] };

export interface TreeRow<T extends TreeItem> {
  node: TreeNode<T>;
  depth: number;
}

/** 按展开状态生成可见列表，保持节点顺序，使用迭代避免深层目录递归溢出。 */
export function flattenExpandedTree<T extends TreeItem>(nodes: readonly TreeNode<T>[],
  expandedIds: ReadonlySet<string>, maxExpandedDepth = Infinity): TreeRow<T>[] {
  const rows: TreeRow<T>[] = [];
  const pending = nodes.map((node) => ({ node, depth: 0 })).reverse();
  const visited = new Set<string>();
  while (pending.length) {
    const row = pending.pop()!;
    if (visited.has(row.node.id)) continue;
    visited.add(row.node.id);
    rows.push(row);
    if (row.depth < maxExpandedDepth && expandedIds.has(row.node.id)) {
      for (let index = row.node.children.length - 1; index >= 0; index--) {
        pending.push({ node: row.node.children[index]!, depth: row.depth + 1 });
      }
    }
  }
  return rows;
}

/** 从平铺父子关系构建树，只返回可从指定根节点到达的节点。 */
export function buildTree<T extends TreeItem>(items: readonly T[], rootId: string): TreeNode<T>[] {
  const nodes = new Map<string, TreeNode<T>>();
  items.forEach((item) => nodes.set(item.id, { ...item, children: [] }));
  const roots: TreeNode<T>[] = [];
  nodes.forEach((node) => {
    if (node.parentId === rootId) {
      roots.push(node);
    } else if (node.parentId !== node.id) {
      nodes.get(node.parentId)?.children.push(node);
    }
  });
  return roots;
}

/** 获取根到目标节点的路径；遇到断链或循环时返回空路径，避免无限遍历。 */
export function getTreePath<T extends TreeItem>(items: readonly T[], id: string, rootId: string): T[] {
  const nodes = new Map(items.map((item) => [item.id, item]));
  const visited = new Set<string>();
  const path: T[] = [];
  let currentId = id;
  while (currentId !== rootId) {
    const item = nodes.get(currentId);
    if (!item || visited.has(currentId)) return [];
    visited.add(currentId);
    path.push(item);
    currentId = item.parentId;
  }
  return path.reverse();
}
