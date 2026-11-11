package com.x.ai.tool;

import com.x.ai.common.dto.McpConnectionTestToolDto;
import com.x.ai.common.dto.McpServerToolCreateDto;
import com.x.ai.common.dto.McpServerToolDto;
import com.x.ai.common.dto.McpServerToolUpdateDto;
import com.x.ai.common.model.ToolContext;
import com.x.ai.service.McpServerToolService;
import com.x.framework.common.utils.StringUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class McpServerTools {

    private final McpServerToolService mcpServerToolService;


    @Tool(name = "mcp_server_create", description = """
            创建当前用户的个人 MCP 服务。
            transportType 只能传整数：1-Streamable HTTP。
            传入 HTTP/HTTPS endpoint，可选传 headers 和 queryParameters。
            requestTimeoutSeconds 取值 1-600，initializationTimeoutSeconds 取值 1-120。
            凭证会被保存，但返回结果只展示凭证 key，不展示 value。
            """)
    public McpServerToolDto create(@ToolParam(name = "request", description = "MCP 服务完整配置") McpServerToolCreateDto request,
                                    ToolContext toolContext) {
        return mcpServerToolService.create(toolContext, request);
    }

    @Tool(name = "mcp_server_list", description = """
            查询当前用户可见的 MCP 服务列表。
            返回的个人 MCP 凭证只包含 key，value 固定为空字符串。
            """, readOnly = true)
    public List<McpServerToolDto> list(ToolContext toolContext) {
        return mcpServerToolService.list(toolContext);
    }

    @Tool(name = "mcp_server_get", description = """
            查询当前用户可见的单个 MCP 服务详情。
            id 必须原样传入列表返回的字符串，禁止转成数字。
            返回的个人 MCP 凭证只包含 key，value 固定为空字符串。
            """, readOnly = true)
    public McpServerToolDto get(@ToolParam(name = "id", description = "MCP 服务配置 ID") String id,
                               ToolContext toolContext) {
        return mcpServerToolService.get(toolContext, StringUtil.parseLongId(id));
    }

    @Tool(name = "mcp_server_test_connection", description = """
            检测当前用户可见的已保存 MCP 配置，并查询本次连接发现的工具名称。
            id 必须原样传入 mcp_server_list 返回的字符串，禁止转成数字。
            返回 connected 和 toolNames；只初始化连接和发现工具，不执行业务工具，不保存检测记录。
            """, readOnly = true)
    public McpConnectionTestToolDto testConnection(@ToolParam(name = "id", description = "MCP 服务配置 ID，必须是字符串") String id,
                                                 ToolContext toolContext) {
        return mcpServerToolService.testConnection(toolContext, StringUtil.parseLongId(id));
    }

    @Tool(name = "mcp_server_update", description = """
            修改当前用户的个人 MCP 服务。
            transportType 只能传整数：1-Streamable HTTP。
            使用 HTTP/HTTPS endpoint、headers、queryParameters。
            提交新的完整配置；凭证字段中已有 key 的空 value 表示保留原值，未提交的 key 表示删除。
            """)
    public McpServerToolDto update(@ToolParam(name = "id", description = "个人 MCP 服务配置 ID") String id,
                                    @ToolParam(name = "request", description = "新的完整配置") McpServerToolUpdateDto request,
                                    ToolContext toolContext) {
        return mcpServerToolService.update(toolContext, StringUtil.parseLongId(id), request);
    }

    @Tool(name = "mcp_server_delete", description = "删除当前用户的个人 MCP 服务")
    public boolean delete(@ToolParam(name = "id", description = "个人 MCP 服务配置 ID") String id,
                          ToolContext toolContext) {
        return mcpServerToolService.delete(toolContext, StringUtil.parseLongId(id));
    }
}
