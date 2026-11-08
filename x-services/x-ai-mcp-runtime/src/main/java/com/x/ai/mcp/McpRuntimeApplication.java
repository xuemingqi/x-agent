package com.x.ai.mcp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@MapperScan("com.x.ai.mcp.db.mapper")
@ConfigurationPropertiesScan
@SpringBootApplication
public class McpRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpRuntimeApplication.class, args);
    }
}
