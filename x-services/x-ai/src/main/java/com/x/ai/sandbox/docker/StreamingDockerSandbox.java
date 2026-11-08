package com.x.ai.sandbox.docker;

import cn.hutool.core.text.StrPool;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import io.agentscope.harness.agent.sandbox.SandboxFileTransfer;
import io.agentscope.harness.agent.sandbox.WorkspaceProjectionApplier;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandbox;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxState;
import io.agentscope.harness.agent.sandbox.layout.WorkspaceEntry;
import io.agentscope.harness.agent.sandbox.layout.WorkspaceProjectionEntry;
import io.agentscope.harness.agent.skill.runtime.MarketplaceStager;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 通过 docker exec 的标准输入输出传输文件，避免文件内容进入命令参数或受普通执行输出截断影响。
 */
@Slf4j
public class StreamingDockerSandbox extends DockerSandbox implements SandboxFileTransfer {

    private static final int ERROR_OUTPUT_LIMIT_BYTES = 65_536;

    private static final Duration FORCE_STOP_TIMEOUT = Duration.ofSeconds(5);

    private static final Duration LIFECYCLE_COMMAND_TIMEOUT = Duration.ofSeconds(10);

    private static final String GRACEFUL_STOP_SECONDS = "1";

    private final DockerSandboxState state;

    private final String dockerCommand;

    private final Duration transferTimeout;

    private final long maxTransferSizeBytes;

    public StreamingDockerSandbox(DockerSandboxState state, Duration transferTimeout, long maxTransferSizeBytes) {
        this(state, "docker", transferTimeout, maxTransferSizeBytes);
    }

    StreamingDockerSandbox(DockerSandboxState state, String dockerCommand, Duration transferTimeout,
                           long maxTransferSizeBytes) {
        super(requireState(state));
        if (transferTimeout == null || transferTimeout.isZero() || transferTimeout.isNegative()
                || maxTransferSizeBytes <= 0 || StringUtil.isBlank(dockerCommand)) {
            throw new XException(ResponseCodeEnum.AGENT_CONFIG_INVALID);
        }
        this.state = state;
        this.dockerCommand = dockerCommand;
        this.transferTimeout = transferTimeout;
        this.maxTransferSizeBytes = maxTransferSizeBytes;
    }

    @Override
    public void start() throws Exception {
        if (isRunning()) {
            return;
        }
        super.start();
        try {
            refreshSkillProjection();
        } catch (Exception error) {
            shutdown();
            throw error;
        }
    }

    /**
     * 快照恢复后刷新平台管理的 Skill 投影，避免已禁用资源从旧快照重新出现。
     * 仅清理沙箱中的三个 Skill 目录，不触及宿主旧目录、记忆、会话文件和知识库。
     */
    void refreshSkillProjection() throws Exception {
        Map<String, WorkspaceEntry> entries = new LinkedHashMap<>();
        state.getWorkspaceSpec().getEntries().forEach((name, entry) -> {
            if (entry instanceof WorkspaceProjectionEntry projection
                    && projection.getIncludeRoots().contains(MarketplaceStager.CACHE_DIR)) {
                WorkspaceProjectionEntry skills = new WorkspaceProjectionEntry();
                skills.setSourceRoot(projection.getSourceRoot());
                skills.setIncludeRoots(List.of(MarketplaceStager.CACHE_DIR));
                entries.put(name, skills);
            }
        });
        if (entries.isEmpty()) {
            return;
        }
        String workspaceRoot = state.getWorkspaceRoot();
        if (StringUtil.isBlank(workspaceRoot) || !Path.of(workspaceRoot).isAbsolute()
                || Path.of(workspaceRoot).normalize().getParent() == null) {
            throw new XException(ResponseCodeEnum.AGENT_CONFIG_INVALID);
        }
        WorkspaceSpec projection = new WorkspaceSpec();
        projection.setRoot(workspaceRoot);
        projection.setEntries(entries);
        var payload = WorkspaceProjectionApplier.build(projection);
        List<String> command = List.of(dockerCommand, "exec", "-i", requireContainerId(), "sh", "-c",
                "rm -rf -- \"$1/.skills-cache\" \"$1/user-skills\" \"$1/skills\" && tar -xf - -C \"$1\"",
                "x-agent-skill-projection", workspaceRoot);
        execute(command, payload.tarBytes(), ERROR_OUTPUT_LIMIT_BYTES, transferTimeout);
    }

    @Override
    public boolean supportsFileTransfer(String path) {
        return !StringUtil.isBlank(state.getContainerId());
    }

    @Override
    public void shutdown() {
        String containerId = state.getContainerId();
        if (StringUtil.isBlank(containerId) || !state.isContainerOwned()) {
            return;
        }
        runLifecycleCommand(List.of(dockerCommand, "stop", "--timeout=" + GRACEFUL_STOP_SECONDS, containerId));
        runLifecycleCommand(List.of(dockerCommand, "rm", "--force", containerId));
    }

    @Override
    public void uploadFile(String path, byte[] content) {
        if (content == null) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        validateTransferSize(content.length);
        String workspacePath = resolveWorkspacePath(path);
        List<String> command = List.of(dockerCommand, "exec", "-i", requireContainerId(),
                "sh", "-c", "mkdir -p -- \"${1%/*}\" && cat > \"$1\"",
                "x-agent-file-upload", workspacePath);
        try {
            execute(command, content, ERROR_OUTPUT_LIMIT_BYTES, transferTimeout);
        } catch (XException error) {
            throw error;
        } catch (Exception error) {
            throw transferError(error);
        }
    }

    @Override
    public byte[] downloadFile(String path) {
        String workspacePath = resolveWorkspacePath(path);
        List<String> command = List.of(dockerCommand, "exec", "-i", requireContainerId(),
                "sh", "-c", "cat -- \"$1\"", "x-agent-file-download", workspacePath);
        try {
            return execute(command, null, maxTransferSizeBytes, transferTimeout);
        } catch (XException error) {
            throw error;
        } catch (Exception error) {
            throw transferError(error);
        }
    }

    /**
     * 将相对路径解析到工作区内，并拒绝越过工作区根目录的路径。
     */
    String resolveWorkspacePath(String path) {
        if (StringUtil.isBlank(path) || StringUtil.isBlank(state.getWorkspaceRoot())) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        try {
            Path resolved = getResolved(path);
            return resolved.toString().replace(StrPool.C_BACKSLASH, StrPool.C_SLASH);
        } catch (XException error) {
            throw error;
        } catch (RuntimeException error) {
            log.warn("Invalid Docker sandbox file path: {}", path, error);
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
    }

    /**
     * 将路径规范化到沙箱工作区范围内。
     */
    private @NonNull Path getResolved(String path) {
        String unixPath = path.replace(StrPool.C_BACKSLASH, StrPool.C_SLASH);
        Path workspaceRoot = Path.of(state.getWorkspaceRoot()).normalize();
        Path candidate = Path.of(unixPath);
        Path resolved = candidate.isAbsolute() ? candidate.normalize() : workspaceRoot.resolve(candidate).normalize();
        if (!resolved.startsWith(workspaceRoot) || resolved.equals(workspaceRoot)) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return resolved;
    }

    /**
     * 执行文件传输进程并并发消费输出，避免任一管道写满后阻塞。
     */
    private byte[] execute(List<String> command, byte[] input, long stdoutLimit, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(command).start();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<byte[]> stdout = executor.submit(() -> readBytes(process.getInputStream(), stdoutLimit));
            Future<?> stdin = executor.submit(() -> {
                writeInput(process, input);
                return null;
            });

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(FORCE_STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
            }

            await(stdin);
            byte[] output = await(stdout);
            if (process.exitValue() != 0) {
                throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
            }
            return output;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 容器工作区快照完成后，使用短宽限期停止并删除本轮容器。
     */
    private void runLifecycleCommand(List<String> command) {
        try {
            execute(command, null, ERROR_OUTPUT_LIMIT_BYTES, LIFECYCLE_COMMAND_TIMEOUT);
        } catch (Exception error) {
            throw transferError(error);
        }
    }

    /**
     * 写入标准输入并立即关闭，使容器内 cat 能收到 EOF。
     */
    private void writeInput(Process process, byte[] input) throws IOException {
        try (OutputStream output = process.getOutputStream()) {
            if (input != null) {
                output.write(input);
            }
        }
    }

    /**
     * 有界读取进程输出，防止异常文件或诊断信息耗尽服务内存。
     */
    private byte[] readBytes(InputStream input, long limit) throws IOException {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            boolean exceeded = false;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (!exceeded && total <= limit) {
                    output.write(buffer, 0, read);
                } else {
                    exceeded = true;
                }
            }
            if (exceeded) {
                throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
            }
            return output.toByteArray();
        }
    }

    /**
     * 获取已启动沙箱的容器 ID。
     */
    private String requireContainerId() {
        if (StringUtil.isBlank(state.getContainerId())) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return state.getContainerId();
    }

    /**
     * 限制单次传输内容大小。
     */
    private void validateTransferSize(long sizeBytes) {
        if (sizeBytes > maxTransferSizeBytes) {
            throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
        }
    }

    /**
     * 获取异步传输结果并还原实际异常。
     */
    private <T> T await(Future<T> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException error) {
            if (error.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
        }
    }

    /**
     * 将传输异常统一转换为工作区存储异常。
     */
    private XException transferError(Exception error) {
        if (error instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        return new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
    }

    /**
     * 校验 Docker 沙箱状态配置。
     */
    private static DockerSandboxState requireState(DockerSandboxState state) {
        if (state == null) {
            throw new XException(ResponseCodeEnum.AGENT_CONFIG_INVALID);
        }
        return state;
    }
}
