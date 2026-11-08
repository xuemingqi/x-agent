package com.x.ai.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "agent.multimodal")
public class MultimodalProperties {

    /**
     * 允许下载模型图片的 HTTPS 域名，精确匹配；Base64 图片不需要配置。
     */
    private Set<String> imageHosts = new LinkedHashSet<>();

    /**
     * 单张模型输入或输出图片的最大字节数。
     */
    @Min(1)
    private int maxImageSizeBytes = 10_485_760;
}
