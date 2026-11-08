package com.x.ai.sandbox.docker;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.properties.AgentProperties;
import io.agentscope.harness.agent.sandbox.Sandbox;
import io.agentscope.harness.agent.sandbox.SandboxState;
import io.agentscope.harness.agent.sandbox.WorkspaceSpec;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClient;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxClientOptions;
import io.agentscope.harness.agent.sandbox.impl.docker.DockerSandboxState;
import io.agentscope.harness.agent.sandbox.snapshot.SandboxSnapshotSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StreamingDockerSandboxClient extends DockerSandboxClient {

    private final AgentProperties agentProperties;


    @Override
    public Sandbox create(WorkspaceSpec workspaceSpec, SandboxSnapshotSpec snapshotSpec, DockerSandboxClientOptions options) {
        Sandbox sandbox = super.create(workspaceSpec, snapshotSpec, options);
        return createStreamingSandbox((DockerSandboxState) sandbox.getState());
    }

    @Override
    public Sandbox resume(SandboxState state) {
        if (!(state instanceof DockerSandboxState dockerState)) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return createStreamingSandbox(dockerState);
    }

    private StreamingDockerSandbox createStreamingSandbox(DockerSandboxState state) {
        AgentProperties.Sandbox sandbox = agentProperties.getSandbox();
        return new StreamingDockerSandbox(state, sandbox.getFileTransferTimeout(), sandbox.getMaxFileTransferSizeBytes());
    }
}
