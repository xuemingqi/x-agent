package com.x.ai.sandbox.docker;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxState;
import io.agentscope.harness.agent.sandbox.layout.WorkspaceEntry;
import io.agentscope.harness.agent.sandbox.layout.WorkspaceProjectionEntry;
import io.agentscope.harness.agent.skill.runtime.MarketplaceStager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledOnOs({OS.LINUX, OS.MAC})
class StreamingDockerSandboxTest {

    private static final String CONTAINER_ID = "test-skill-container";

    private static final String REFRESH_COMMAND = "rm -rf -- \"$1/.skills-cache\" \"$1/user-skills\" \"$1/skills\""
            + " && tar -xf - -C \"$1\"";

    @TempDir
    private Path temporaryDirectory;

    @Test
    void refreshesOnlySandboxSkillDirectoriesAndPreservesHostFilesAndOtherWorkspaceResources() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("host staging"));
        Path workspace = Files.createDirectory(temporaryDirectory.resolve("sandbox's workspace"));
        Path commandLog = temporaryDirectory.resolve("docker-arguments.txt");
        Path docker = createFakeDocker(workspace, commandLog);
        writeFixture(source, ".skills-cache/current/SKILL.md", "current skill");
        writeFixture(source, ".skills-cache/current/scripts/run.sh", "current resource");
        writeFixture(source, "user-skills/legacy/SKILL.md", "host legacy user skill");
        writeFixture(source, "skills/legacy/SKILL.md", "host legacy skill");
        writeFixture(source, "memory/history.md", "host memory must not overwrite sandbox");
        writeFixture(source, "knowledge/manual.md", "host knowledge must not overwrite sandbox");
        writeFixture(workspace, ".skills-cache/stale/SKILL.md", "disabled skill from snapshot");
        writeFixture(workspace, "user-skills/stale/SKILL.md", "legacy user skill from snapshot");
        writeFixture(workspace, "skills/stale/SKILL.md", "legacy skill from snapshot");
        Map<String, String> preserved = Map.of(
                "memory/history.md", "sandbox memory", "conversation/attachment.txt", "conversation attachment",
                "knowledge/manual.md", "sandbox knowledge", "output/result.txt", "task result");
        for (var file : preserved.entrySet()) {
            writeFixture(workspace, file.getKey(), file.getValue());
        }
        var skills = projection(source, List.of(MarketplaceStager.CACHE_DIR, "memory", "user-skills", "skills"));
        var knowledge = projection(source, List.of("knowledge"));
        var sandbox = sandbox(workspace.toString(), Map.of("skills", skills, "knowledge", knowledge), docker);

        sandbox.refreshSkillProjection();

        assertThat(Files.readAllLines(commandLog)).containsExactly("exec", "-i", CONTAINER_ID, "sh", "-c",
                REFRESH_COMMAND, "x-agent-skill-projection", workspace.toString());
        assertThat(Files.readString(workspace.resolve(".skills-cache/current/SKILL.md"))).isEqualTo("current skill");
        assertThat(Files.readString(workspace.resolve(".skills-cache/current/scripts/run.sh"))).isEqualTo("current resource");
        assertThat(workspace.resolve(".skills-cache/stale")).doesNotExist();
        assertThat(workspace.resolve("user-skills")).doesNotExist();
        assertThat(workspace.resolve("skills")).doesNotExist();
        for (var file : preserved.entrySet()) {
            assertThat(Files.readString(workspace.resolve(file.getKey()))).isEqualTo(file.getValue());
        }
        assertThat(Files.readString(source.resolve("user-skills/legacy/SKILL.md"))).isEqualTo("host legacy user skill");
        assertThat(Files.readString(source.resolve("skills/legacy/SKILL.md"))).isEqualTo("host legacy skill");
        assertThat(Files.readString(source.resolve(".skills-cache/current/SKILL.md"))).isEqualTo("current skill");
        assertThat(skills.getIncludeRoots()).containsExactly(MarketplaceStager.CACHE_DIR, "memory", "user-skills", "skills");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "relative/workspace", "/", "/tmp/.."})
    void rejectsUnsafeWorkspaceRootsBeforeInvokingDockerWhenSkillsAreProjected(String workspaceRoot) throws IOException {
        Path source = Files.createDirectory(temporaryDirectory.resolve("source"));
        var skills = projection(source, List.of(MarketplaceStager.CACHE_DIR));
        Path commandLog = temporaryDirectory.resolve("docker-arguments.txt");
        Path docker = createFakeDocker(temporaryDirectory.resolve("sandbox"), commandLog);
        var sandbox = sandbox(temporaryDirectory.resolve("sandbox").toString(), Map.of("skills", skills), docker);
        ((DockerSandboxState) sandbox.getState()).setWorkspaceRoot(workspaceRoot);

        assertThatThrownBy(sandbox::refreshSkillProjection).isInstanceOfSatisfying(XException.class,
                error -> assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_CONFIG_INVALID));

        assertThat(commandLog).doesNotExist();
    }

    @Test
    void skipsDockerWhenNoSkillProjectionIsConfigured() {
        var knowledge = projection(temporaryDirectory, List.of("knowledge"));
        var sandbox = sandbox("/", Map.of("knowledge", knowledge), temporaryDirectory.resolve("nonexistent-docker"));

        assertThatCode(sandbox::refreshSkillProjection).doesNotThrowAnyException();
    }

    private StreamingDockerSandbox sandbox(String workspaceRoot, Map<String, WorkspaceEntry> entries, Path docker) {
        WorkspaceSpec workspace = new WorkspaceSpec();
        workspace.setRoot(workspaceRoot);
        workspace.setEntries(entries);
        DockerSandboxState state = new DockerSandboxState();
        state.setContainerId(CONTAINER_ID);
        state.setWorkspaceRoot(workspaceRoot);
        state.setWorkspaceSpec(workspace);
        return new StreamingDockerSandbox(state, docker.toString(), Duration.ofSeconds(5), 1024 * 1024);
    }

    private WorkspaceProjectionEntry projection(Path source, List<String> roots) {
        WorkspaceProjectionEntry projection = new WorkspaceProjectionEntry();
        projection.setSourceRoot(source.toString());
        projection.setIncludeRoots(roots);
        return projection;
    }

    private void writeFixture(Path root, String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /**
     * 只在参数和清理命令完全匹配时执行临时工作区操作，绝不调用真实 Docker。
     */
    private Path createFakeDocker(Path workspace, Path commandLog) throws IOException {
        Path docker = temporaryDirectory.resolve("fake-docker.sh");
        String script = """
                #!/bin/sh
                set -eu
                printf '%%s\\n' "$@" > %s
                [ "$#" -eq 8 ]
                [ "$1" = exec ] && [ "$2" = -i ] && [ "$3" = %s ]
                [ "$4" = sh ] && [ "$5" = -c ]
                [ "$6" = %s ] && [ "$7" = x-agent-skill-projection ] && [ "$8" = %s ]
                shift 3
                exec "$@"
                """.formatted(shellQuote(commandLog.toString()), shellQuote(CONTAINER_ID),
                shellQuote(REFRESH_COMMAND), shellQuote(workspace.toString()));
        Files.writeString(docker, script);
        Files.setPosixFilePermissions(docker, PosixFilePermissions.fromString("rwx------"));
        return docker;
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
