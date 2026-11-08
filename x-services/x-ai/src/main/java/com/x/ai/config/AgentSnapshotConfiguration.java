package com.x.ai.config;

import com.x.framework.minio.property.MinioProperties;
import io.agentscope.harness.agent.sandbox.snapshot.RemoteSnapshotClient;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AgentSnapshotConfiguration {

    private final MinioProperties minioProperties;

    @Bean
    public RemoteSnapshotClient remoteSnapshotClient(MinioClient minioClient) throws Exception {
        MinioRemoteSnapshotClient snapshotClient = new MinioRemoteSnapshotClient(minioClient, minioProperties.getBucket(),
                minioProperties.getObjectPrefix(),
                minioProperties.isCreateBucketIfAbsent());
        snapshotClient.initialize();
        return snapshotClient;
    }
}
