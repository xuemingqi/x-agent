-- 公共 Skill 初始化数据。应用不会自动执行本文件，请在 agent_public_skill 建表后手工执行。
INSERT INTO agent_public_skill (name, description, skill_content, source, metadata_json)
SELECT 'skill-creator',
       '当用户希望创建、修改、补充或删除个人 Skill 时使用，负责把需求整理为规范且可执行的 AgentScope Skill。',
       '# Skill Creator

你负责帮助当前用户创建和维护只属于该用户的 Skill。用户 Skill 必须写入当前用户隔离的工作区，不得修改公共 Skill。

## 工作方式

1. 先确认 Skill 的目标、触发场景、输入、输出和必要约束；需求已经清楚时不要重复追问。
2. Skill 名称使用小写字母、数字、点、下划线或短横线，最长 64 个字符，优先使用简短的英文短横线命名。
3. `SKILL.md` 必须包含 YAML frontmatter，其中至少包含 `name` 和 `description`。正文使用中文，写清执行步骤、边界条件和结果要求。
4. 创建 Skill 时调用 `skill_manage`，传入 `action=create`、`name` 和完整的 `content`。`content` 必须包含 frontmatter 和正文。
5. 修改正文时使用 `action=edit` 或 `action=patch`；添加参考资料、模板、脚本或素材时使用 `action=write_file`，文件路径只能位于 `references/`、`templates/`、`scripts/` 或 `assets/`。
6. 只有用户明确要求删除时才使用 `action=delete`。不要删除或覆盖其他名称的 Skill。
7. 工具执行成功后，简要告诉用户 Skill 名称、用途和本次变更，不要输出内部目录或其他用户信息。

## 内容要求

- 指令必须具体、可执行，避免只写宽泛原则。
- 不要写入密码、Token、Cookie、私钥等敏感信息。
- 脚本优先使用沙箱镜像中已经存在的命令；需要额外依赖时，在 Skill 中明确说明。
- Skill 只解决一个清晰职责，避免把无关能力堆在同一个 Skill 中。',
       'platform-seed',
       '{"name":"skill-creator","description":"当用户希望创建、修改、补充或删除个人 Skill 时使用，负责把需求整理为规范且可执行的 AgentScope Skill。"}'
WHERE NOT EXISTS (
    SELECT 1
    FROM agent_public_skill
    WHERE name = 'skill-creator'
);
