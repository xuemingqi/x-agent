package com.x.ai.app.service;

import com.x.ai.common.domain.AgentAppDo;

import java.util.List;

public interface AgentAppService {

    /**
     * 查询随当前版本注册的智能应用。
     */
    List<AgentAppDo> list();
}
