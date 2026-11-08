import { describe, expect, it } from 'vitest';

import { buildTree, flattenExpandedTree, getTreePath } from './tree';

const rootId = '0';
const parent = { id: '2092518874657460224', parentId: rootId, name: '业务规则' };
const child = { id: '2092518874657460225', parentId: parent.id, name: '准入规则' };
const leaf = { id: '2092518874657460226', parentId: child.id, name: '司机' };

describe('buildTree', () => {
  it('builds nested folders regardless of input order without rounding IDs', () => {
    expect(buildTree([leaf, child, parent], rootId)).toEqual([
      { ...parent, children: [{ ...child, children: [{ ...leaf, children: [] }] }] },
    ]);
  });

  it('does not mutate the source and keeps same-name folders under different parents', () => {
    const sibling = { id: 'other', parentId: rootId, name: parent.name };
    expect(buildTree([parent, sibling], rootId)).toHaveLength(2);
    expect(parent).not.toHaveProperty('children');
  });

  it('excludes orphaned and cyclic branches from the root tree', () => {
    expect(buildTree([
      parent,
      { id: 'a', parentId: 'b' },
      { id: 'b', parentId: 'a' },
      { id: 'self', parentId: 'self' },
      { id: 'orphan', parentId: 'missing' },
    ], rootId)).toEqual([{ ...parent, children: [] }]);
  });
});

describe('getTreePath', () => {
  it('returns breadcrumbs from root to the selected folder', () => {
    expect(getTreePath([leaf, child, parent], leaf.id, rootId)).toEqual([parent, child, leaf]);
    expect(getTreePath([parent], rootId, rootId)).toEqual([]);
  });

  it('rejects missing parents and cycles without looping', () => {
    expect(getTreePath([child], child.id, rootId)).toEqual([]);
    expect(getTreePath([{ id: 'a', parentId: 'b' }, { id: 'b', parentId: 'a' }], 'a', rootId)).toEqual([]);
  });
});

describe('flattenExpandedTree', () => {
  const sibling = { id: 'sibling', parentId: rootId, name: '另一目录' };
  const tree = buildTree([parent, child, leaf, sibling], rootId);

  it('expands children inline and keeps sibling folders visible', () => {
    const rows = flattenExpandedTree(tree, new Set([parent.id, child.id]));
    expect(rows.map(({ node, depth }) => [node.id, depth])).toEqual([
      [parent.id, 0], [child.id, 1], [leaf.id, 2], [sibling.id, 0],
    ]);
  });

  it('hides descendants of collapsed folders even if descendants remain expanded', () => {
    expect(flattenExpandedTree(tree, new Set([child.id])).map(({ node }) => node.id))
      .toEqual([parent.id, sibling.id]);
  });

  it('limits indentation and resets depth when a deep folder becomes the breadcrumb root', () => {
    expect(flattenExpandedTree(tree, new Set([parent.id, child.id]), 1).map(({ node, depth }) => [node.id, depth]))
      .toEqual([[parent.id, 0], [child.id, 1], [sibling.id, 0]]);
    expect(flattenExpandedTree(buildTree([parent, child, leaf], child.id), new Set()).map(({ node, depth }) => [node.id, depth]))
      .toEqual([[leaf.id, 0]]);
  });

  it('handles deeply nested trees without recursive stack overflow', () => {
    const items = Array.from({ length: 12000 }, (_, index) => ({ id: `folder-${index}`, parentId: index ? `folder-${index - 1}` : rootId }));
    const rows = flattenExpandedTree(buildTree(items, rootId), new Set(items.map(({ id }) => id)));
    expect(rows).toHaveLength(items.length);
    expect(rows.at(-1)?.depth).toBe(items.length - 1);
  });
});
