package com.x.ai.tool;

import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.constants.PromptConstant;
import com.x.ai.common.domain.ConversationFileDo;
import com.x.ai.common.model.FileToolContext;
import com.x.ai.service.ConversationFileService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConversationFileTools {

    private final ConversationFileService conversationFileService;

    @Tool(name = AgentConstant.CONVERSATION_FILE_PUBLISH_TOOL_NAME,
            description = PromptConstant.CONVERSATION_FILE_PUBLISH_TOOL_DESCRIPTION)
    public ConversationFileDo publish(@ToolParam(name = "path", description = "生成文件在工作区中的相对路径") String path,
                                      @ToolParam(name = "displayName", required = false, description = "展示和下载使用的文件名") String displayName,
                                      FileToolContext fileContext, RuntimeContext runtimeContext) {
        return conversationFileService.publish(runtimeContext, fileContext.getMessageId(), path, displayName);
    }
}
