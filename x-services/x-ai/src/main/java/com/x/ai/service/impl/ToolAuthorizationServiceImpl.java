package com.x.ai.service.impl;

import com.x.ai.common.functional.ToolAuthorizationService;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.ai.service.AdministratorAuthorizationService;
import io.agentscope.core.message.ToolUseBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ToolAuthorizationServiceImpl implements ToolAuthorizationService {

    private static final String PUBLIC_ADMIN_TOOL_PREFIX = "public_admin_";

    private final AdministratorAuthorizationService administratorAuthorizationService;

    @Override
    public void authorize(ToolContext toolContext, ToolUseBlock toolCall) {
        if (toolCall == null || toolCall.getName() == null || toolCall.getName().isBlank()) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        if (toolCall.getName().startsWith(PUBLIC_ADMIN_TOOL_PREFIX)) {
            administratorAuthorizationService.requireAdministrator(toolContext.getUserId());
        }
    }
}
