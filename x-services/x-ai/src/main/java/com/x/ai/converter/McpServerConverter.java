package com.x.ai.converter;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.ai.db.entity.AgentMcpServer;
import com.x.ai.common.model.McpServer;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import java.util.Map;

@Component
public class McpServerConverter {

    /**
     * 将数据库映射对象转换为 MCP 运行时模型，并在边界处完成 JSON 字段解析。
     *
     * @param entity MCP 数据库映射对象
     * @return MCP 运行时模型
     */
    public McpServer convert(AgentMcpServer entity) {
        if (entity == null) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFIG_ERROR);
        }
        return new McpServer()
                .setId(entity.getId())
                .setName(entity.getName())
                .setTransportType(entity.getTransportType())
                .setEndpoint(entity.getEndpoint())
                .setHeaders(parseMap(entity.getHeadersJson()))
                .setQueryParameters(parseMap(entity.getQueryParametersJson()))
                .setRequestTimeoutSeconds(entity.getRequestTimeoutSeconds())
                .setInitializationTimeoutSeconds(entity.getInitializationTimeoutSeconds())
                .setEnabled(Boolean.TRUE.equals(entity.getEnabled()));
    }

    /**
     * 将数据库 JSON 字段解析为字符串映射。
     */
    private Map<String, String> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return Map.copyOf(JsonUtil.strToObjectOrThrow(json, new TypeReference<>() {
            }));
        } catch (RuntimeException error) {
            throw new XException(ResponseCodeEnum.AGENT_MCP_CONFIG_ERROR);
        }
    }
}
