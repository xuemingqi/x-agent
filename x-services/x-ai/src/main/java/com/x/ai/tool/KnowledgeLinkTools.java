package com.x.ai.tool;

import com.x.ai.common.domain.KnowledgeLinkCatalogDo;
import com.x.ai.common.domain.KnowledgeLinkDetailDo;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.service.KnowledgeLinkToolService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KnowledgeLinkTools {

    private final KnowledgeLinkToolService knowledgeLinkToolService;


    @Tool(name = "knowledge_link_catalog", readOnly = true, description = """
            获取当前用户可见的在线知识库链接目录。
            返回当前用户在线知识库的文档 ID、名称与简短介绍，不返回实际链接，也不读取文档正文。
            仅当用户问题可能需要在线知识库信息时调用本工具。根据文档名称和简短介绍选择相关文档后，
            再调用 knowledge_link_get 获取实际链接。
            本工具不是关键词检索或 RAG，不要把返回的简短介绍当作文档正文。
            如果目录中没有相关文档，应直接说明没有发现可用的在线知识来源。
            """)
    public List<KnowledgeLinkCatalogDo> catalog(ToolContext toolContext) {
        return knowledgeLinkToolService.catalog(toolContext);
    }

    @Tool(name = "knowledge_link_get", readOnly = true, description = """
            根据文档 ID 获取当前用户可见的在线知识库链接。
            通常应先调用 knowledge_link_catalog，根据文档名称和简短介绍选出相关文档 ID，再调用本工具获取实际 URL。
            本工具只返回链接，不读取文档正文。获得链接后，应检查当前可用工具的描述，
            选择明确支持该链接域名或文档平台的工具读取正文。
            如果没有任何可用工具声明支持该链接，应如实说明当前无法读取，不得猜测文档内容。
            """)
    public KnowledgeLinkDetailDo get(@ToolParam(name = "id", description = "在线知识库文档 ID") String id,
                                     ToolContext toolContext) {
        return knowledgeLinkToolService.get(toolContext, StringUtil.parseLongId(id));
    }
}
