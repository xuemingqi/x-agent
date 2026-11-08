package com.x.ai.mcp.config;

import com.x.ai.mcp.runtime.McpProxyServlet;
import com.x.ai.mcp.runtime.McpRuntimeRegistry;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServletConfiguration {

    private static final String SERVLET_NAME = "mcpProxyServlet";

    private static final String SERVLET_MAPPING_SUFFIX = "/*";

    @Bean
    public ServletRegistrationBean<McpProxyServlet> mcpProxyServlet(McpRuntimeRegistry runtimeRegistry,
                                                                    McpRuntimeProperties properties) {
        ServletRegistrationBean<McpProxyServlet> registration = new ServletRegistrationBean<>(
                new McpProxyServlet(runtimeRegistry),
                properties.getProxyEndpoint() + SERVLET_MAPPING_SUFFIX);
        registration.setName(SERVLET_NAME);
        registration.setAsyncSupported(true);
        return registration;
    }
}
