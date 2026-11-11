package com.x.ai.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "knowledge.vector")
public class KnowledgeVectorProperties {

    /**
     * 独立 PostgreSQL 向量数据库连接地址。
     */
    @NotBlank
    private String jdbcUrl;

    /**
     * 向量库账号。
     */
    @NotBlank
    private String username;

    /**
     * 由 Nacos 私有配置或其环境变量占位符提供的向量库密码。
     */
    @NotBlank
    private String password;
}
