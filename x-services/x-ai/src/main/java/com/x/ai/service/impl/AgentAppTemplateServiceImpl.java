package com.x.ai.service.impl;

import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppTemplateDo;
import com.x.ai.common.dto.AgentAppSaveDto;
import com.x.ai.service.AgentAppService;
import com.x.ai.service.AgentAppTemplateService;
import com.x.ai.storage.AgentAppPackageStore;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentAppTemplateServiceImpl implements AgentAppTemplateService {

    private static final String TEMPLATE_PATTERN = "classpath*:agent-app-templates/*/manifest.json";

    private final AgentAppService agentAppService;

    private final ResourceLoader resourceLoader;


    @Override
    public List<AgentAppTemplateDo> list() {
        List<AgentAppTemplateDo> templates = Arrays.stream(templateResources()).map(this::readMetadata)
                .sorted(Comparator.comparing(AgentAppTemplateDo::getId)).toList();
        if (new HashSet<>(templates.stream().map(AgentAppTemplateDo::getId).toList()).size() != templates.size()) {
            throw new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR);
        }
        return templates;
    }

    @Override
    public AgentAppDetailDo install(String templateId) {
        return install(UserThreadLocalUtil.getUserId(), templateId);
    }

    @Override
    public AgentAppDetailDo install(String userId, String templateId) {
        Resource manifest = Arrays.stream(templateResources())
                .filter(resource -> readMetadata(resource).getId().equals(templateId)).findFirst()
                .orElseThrow(() -> new XException(ResponseCodeEnum.AGENT_APP_NOT_FOUND));
        AgentAppTemplateDo metadata = readMetadata(manifest);
        try {
            AgentAppSaveDto request = new AgentAppSaveDto().setName(metadata.getName())
                    .setDescription(metadata.getDescription()).setPermissions(metadata.getPermissions())
                    .setHtml(readText(manifest.createRelative("index.html")))
                    .setCss(readText(manifest.createRelative("style.css")))
                    .setSource(readText(manifest.createRelative("app.ts")));
            return agentAppService.create(userId, request);
        } catch (IOException error) {
            throw templateError(error);
        }
    }

    /**
     * 按资源协议发现模板，Java 不枚举应用名称、提示词或业务字段。
     */
    private Resource[] templateResources() {
        try {
            return ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(TEMPLATE_PATTERN);
        } catch (IOException error) {
            throw templateError(error);
        }
    }

    /**
     * 模板标识来自已发现的清单，不把用户输入拼接成资源路径。
     */
    private AgentAppTemplateDo readMetadata(Resource manifest) {
        try {
            AgentAppTemplateDo metadata = JsonUtil.OBJECT_MAPPER.readValue(readText(manifest), AgentAppTemplateDo.class);
            if (metadata == null || metadata.getId() == null || !metadata.getId().matches("[a-z][a-z0-9-]{0,63}")
                    || metadata.getName() == null || metadata.getName().isBlank()
                    || metadata.getDescription() == null || metadata.getPermissions() == null) {
                throw new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR);
            }
            return metadata;
        } catch (RuntimeException error) {
            throw templateError(error);
        }
    }

    /**
     * 模板与个人应用包遵循同一大小上限，读取资源时不产生无界内存占用。
     */
    private String readText(Resource resource) {
        try (var input = resource.getInputStream()) {
            byte[] bytes = input.readNBytes(AgentAppPackageStore.MAX_PACKAGE_BYTES + 1);
            if (bytes.length > AgentAppPackageStore.MAX_PACKAGE_BYTES) {
                throw new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw templateError(error);
        }
    }

    /**
     * 模板资源故障只记录异常类型，不把模板源码回传给错误响应。
     */
    private XException templateError(Exception error) {
        log.warn("智能应用模板读取失败，errorType={}", error.getClass().getSimpleName());
        return new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR);
    }
}
