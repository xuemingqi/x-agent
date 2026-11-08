package com.x.ai.tool;

import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.constants.PromptConstant;
import com.x.ai.service.BrowserScreenshotService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class BrowserScreenshotTools {

    private final BrowserScreenshotService browserScreenshotService;


    @Tool(name = AgentConstant.BROWSER_SCREENSHOT_TOOL_NAME, description = PromptConstant.BROWSER_SCREENSHOT_TOOL_DESCRIPTION)
    public Mono<ToolResultBlock> request(@ToolParam(name = "fileId", description = "本轮已发布的 HTML 文件 ID，保持字符串") String fileId,
                                         @ToolParam(name = "reason", description = "本次截图要检查的具体效果") String reason,
                                         RuntimeContext context) {
        return browserScreenshotService.request(fileId, reason, context);
    }
}
