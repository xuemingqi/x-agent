package com.x.framework.minio.autoconfigure;

import com.x.framework.minio.conf.MinioConfiguration;
import com.x.framework.minio.property.MinioProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
        MinioConfiguration.class,
        MinioProperties.class
})
public class XFrameworkMinioAutoConfiguration {
}
