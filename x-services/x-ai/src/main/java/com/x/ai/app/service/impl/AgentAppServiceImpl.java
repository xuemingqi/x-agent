package com.x.ai.app.service.impl;

import com.x.ai.app.definition.AbstractAgentApp;
import com.x.ai.app.service.AgentAppService;
import com.x.ai.common.domain.AgentAppDo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeMap;

@Slf4j
@Service
public class AgentAppServiceImpl implements AgentAppService {

    private final List<AbstractAgentApp<?>> applications;

    public AgentAppServiceImpl(List<AbstractAgentApp<?>> applications) {
        TreeMap<String, AbstractAgentApp<?>> registered = new TreeMap<>();
        for (AbstractAgentApp<?> application : applications) {
            String id = application.definition().getId();
            if (id == null || !id.matches("[a-z][a-z0-9-]*") || registered.putIfAbsent(id, application) != null) {
                log.error("智能应用注册失败，应用标识无效或重复，appId={}", id);
                throw new XException(ResponseCodeEnum.AGENT_APP_REGISTRATION_ERROR);
            }
        }
        this.applications = List.copyOf(registered.values());
    }

    @Override
    public List<AgentAppDo> list() {
        return applications.stream().map(AbstractAgentApp::definition).toList();
    }
}
