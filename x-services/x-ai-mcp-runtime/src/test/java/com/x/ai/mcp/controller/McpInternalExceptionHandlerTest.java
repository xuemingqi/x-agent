package com.x.ai.mcp.controller;

import com.x.ai.mcp.api.config.McpRuntimeApiFeignConfiguration;
import com.x.ai.mcp.service.McpServerManagementService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.web.exception.handler.CommonExceptionHandler;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpInternalExceptionHandlerTest {

    @Test
    void preservesBusinessErrorAcrossInternalHttpAndFeignBoundary() throws Exception {
        McpServerManagementService managementService = mock(McpServerManagementService.class);
        when(managementService.getPublic(123L)).thenThrow(new XException(ResponseCodeEnum.AGENT_MCP_NOT_FOUND));
        var mvc = MockMvcBuilders.standaloneSetup(new PublicMcpInternalController(managementService))
                .setControllerAdvice(new McpInternalExceptionHandler(), new CommonExceptionHandler()).build();

        var httpResponse = mvc.perform(get("/internal/public-mcp-configurations/123"))
                .andExpect(status().isNotFound()).andReturn().getResponse();
        Response response = Response.builder().status(httpResponse.getStatus()).reason("Not Found")
                .request(Request.create(Request.HttpMethod.GET, "/mcp/internal/public-mcp-configurations/123",
                        Map.of(), null, StandardCharsets.UTF_8, null))
                .headers(Map.of()).body(httpResponse.getContentAsByteArray()).build();

        XException decoded = assertInstanceOf(XException.class,
                new McpRuntimeApiFeignConfiguration().decode("PublicMcpRuntimeApi#get", response));
        assertEquals(ResponseCodeEnum.AGENT_MCP_NOT_FOUND, decoded.getResponseCodeEnum());
    }
}
