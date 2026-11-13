package com.x.ai.service;

import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppTemplateDo;

import java.util.List;

public interface AgentAppTemplateService {

    /**
     * 查询随平台交付的自包含 TS 应用模板；模板本身没有用户状态。
     */
    List<AgentAppTemplateDo> list();

    /**
     * 为当前登录用户创建模板的独立应用副本，源码按普通应用保存到 MinIO。
     */
    AgentAppDetailDo install(String templateId);

    /**
     * 为可信工具上下文中的指定用户创建模板副本，不依赖线程中的登录身份。
     */
    AgentAppDetailDo install(String userId, String templateId);
}
