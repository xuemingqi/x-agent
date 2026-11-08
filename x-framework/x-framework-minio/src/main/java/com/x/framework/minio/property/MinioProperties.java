package com.x.framework.minio.property;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    /**
     * MinIO 服务地址。
     */
    private String endpoint = "http://127.0.0.1:9000";

    /**
     * MinIO 访问密钥。
     */
    private String accessKey;

    /**
     * MinIO 私有密钥。
     */
    private String secretKey;

    /**
     * Sandbox 快照所在 Bucket。
     */
    private String bucket = "agent-workspace";

    /**
     * Sandbox 快照对象前缀。
     */
    private String objectPrefix = "sandbox-snapshots";

    /**
     * 会话文件对象前缀。
     */
    private String conversationFileObjectPrefix = "conversation-files";

    /**
     * Bucket 不存在时是否自动创建。
     */
    private boolean createBucketIfAbsent = true;
}
