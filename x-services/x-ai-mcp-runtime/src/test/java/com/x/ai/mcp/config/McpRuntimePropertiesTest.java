package com.x.ai.mcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class McpRuntimePropertiesTest {

    @Test
    void buildsUserIsolatedRuntimePath() {
        McpRuntimeProperties properties = new McpRuntimeProperties();

        String runtimePath = properties.runtimePath("user@example.com", 42L);

        assertThat(runtimePath).isEqualTo("/internal/mcp/user%40example.com/42");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/mcp", "/internal/mcp/", "/internal/mcp///"})
    void usesOneNormalizedEndpointForServletMappingAndEncodedUserPath(String endpoint) {
        McpRuntimeProperties properties = new McpRuntimeProperties();
        properties.setProxyEndpoint(endpoint);

        assertThat(properties.getProxyEndpoint()).isEqualTo("/internal/mcp");
        assertThat(properties.runtimePath("a+b%20 c@example.com", 42L))
                .isEqualTo("/internal/mcp/a%2Bb%2520%20c%40example.com/42");
    }
}
