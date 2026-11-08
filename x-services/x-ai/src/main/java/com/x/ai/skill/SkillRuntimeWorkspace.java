package com.x.ai.skill;

import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.harness.agent.skill.runtime.MarketplaceStager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 运行时独占的静态工作区；数据库是持久化来源，生成文件随 Agent 生命周期释放。
 */
@Slf4j
public final class SkillRuntimeWorkspace implements AutoCloseable {

    /**
     * 当前 Agent 独占的派生目录，关闭时删除；原模板和持久化 Skill 不受影响。
     */
    @Getter
    private final Path directory;

    private SkillRuntimeWorkspace(Path directory) {
        this.directory = directory;
    }

    public static SkillRuntimeWorkspace create(Path template, List<AgentSkillRepository> repositories) throws IOException {
        Files.createDirectories(template);
        Path parent = Files.createDirectories(template.resolve(".skill-runtimes"));
        SkillRuntimeWorkspace workspace = new SkillRuntimeWorkspace(Files.createTempDirectory(parent, "runtime-"));
        try {
            copyTemplate(template, workspace.directory, "AGENTS.md");
            copyTemplate(template, workspace.directory, "subagents");
            copyTemplate(template, workspace.directory, "tools.json");
            workspace.stageSkills(repositories);
            return workspace;
        } catch (IOException | RuntimeException error) {
            workspace.close();
            throw error;
        }
    }

    /**
     * 按框架的同名覆盖顺序投放 Skill，只暴露最终可用的版本。
     */
    private void stageSkills(List<AgentSkillRepository> repositories) throws IOException {
        Files.createDirectories(directory.resolve(MarketplaceStager.CACHE_DIR));
        var merged = new LinkedHashMap<String, MarketplaceStager.RepoBound>();
        repositories.forEach(repository -> repository.getAllSkills().forEach(skill ->
                merged.put(skill.getName(), new MarketplaceStager.RepoBound(skill, repository))));
        new MarketplaceStager(directory).stage(List.copyOf(merged.values()),
                MarketplaceStager.resolveSourceNamespaces(repositories));
    }

    /**
     * 仅复制 Agent 的静态配置，并拒绝通过符号链接引入工作区之外的内容。
     */
    private static void copyTemplate(Path source, Path target, String name) throws IOException {
        Path root = source.resolve(name);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Workspace templates must not contain symbolic links");
                }
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
    }

    @Override
    public void close() {
        try {
            FileSystemUtils.deleteRecursively(directory);
        } catch (IOException error) {
            log.warn("Failed to remove generated Skill workspace: {}", directory, error);
        }
    }
}
