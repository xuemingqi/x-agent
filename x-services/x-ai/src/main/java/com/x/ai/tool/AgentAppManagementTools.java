package com.x.ai.tool;

import com.x.ai.service.AgentAppService;
import com.x.ai.service.AgentAppTemplateService;
import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppDo;
import com.x.ai.common.domain.AgentAppVersionDo;
import com.x.ai.common.domain.AgentAppTemplateDo;
import com.x.ai.common.dto.AgentAppSaveDto;
import com.x.ai.common.model.ToolContext;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentAppManagementTools {

    private final AgentAppService agentAppService;

    private final AgentAppTemplateService agentAppTemplateService;


    @Tool(name = "app_template_list", readOnly = true, description = "查询可用于创建个人应用的 TS 模板，返回模板标识和所需权限。")
    public List<AgentAppTemplateDo> templates() {
        return agentAppTemplateService.list();
    }

    @Tool(name = "app_template_install", description = """
            根据用户选择的模板创建个人应用副本；templateId 来自 app_template_list。
            副本源码按普通应用存入 MinIO，支持 app_get、app_update、版本恢复和删除，互不影响。
            需要调整模板时先安装，再读取并修改副本。返回个人应用 ID、名称和当前版本。
            """)
    public AgentAppDo installTemplate(@ToolParam(name = "templateId", description = "应用模板标识") String templateId,
                                     ToolContext context) {
        return summary(agentAppTemplateService.install(requireUserId(context), templateId));
    }


    @Tool(name = "app_list", readOnly = true, description = """
            查询当前用户的个人智能应用，应用均可通过 /apps/{id} 打开、编辑与恢复历史版本。
            应用和版本 ID 都必须原样传递字符串。
            """)
    public List<AgentAppDo> list(ToolContext context) {
        return agentAppService.list(requireUserId(context));
    }

    @Tool(name = "app_get", readOnly = true, description = """
            获取当前用户动态应用的当前版本，包含 html、css、TypeScript source、permissions 和 currentVersion。
            修改已有应用前先读取详情，保留用户未要求替换的功能和源码。
            """)
    public AgentAppDetailDo get(@ToolParam(name = "id", description = "动态应用 ID") String id, ToolContext context) {
        return agentAppService.get(requireUserId(context), id);
    }

    @Tool(name = "app_create", description = """
            根据用户需求创建可以立即在智能应用中心打开的个人应用。
            request 包含 name、description、html（静态页面片段）、css、source（自包含 TypeScript）和 permissions。
            source 使用浏览器 DOM 和全局 window.app SDK，不使用 import/export、require、外部包、CDN 或 fetch。
            html 不放脚本或内联事件，交互全部写在 source；图片使用 data URL。
            权限只声明实际需要的项：model.generate、agent.run、storage、http.request；纯前端工具传空数组。
            window.app.ai.generate({prompt,systemPrompt?,modelId?}) 返回 {text}；
            window.app.agent.run({request,modelId?}) 返回 {text,sessionId}，可调用当前用户启用的 MCP；
            window.app.storage.get(key)、set(key,value)、remove(key) 为异步方法，get 在不存在时返回 null。
            window.app.http.request({method,url,query?,headers?,body?,timeoutMs?}) 发送 HTTP 请求，返回状态、响应头、正文和耗时。
            query/headers 每项为 {name,value,enabled}，不继承平台登录头，SDK 请求必须声明 http.request。
            省略 modelId 使用当前用户默认模型，modelId 必须是字符串。不得在代码里嵌入登录凭证或模型 Key。
            处理异步失败并向使用者显示错误；不要把网页中的用户输入直接拼入 innerHTML。
            平台将完整应用包保存到 MinIO，数据库仅保存引用。成功后返回应用 ID、版本和名称。
            """)
    public AgentAppDo create(@ToolParam(name = "request", description = "应用名称、介绍及完整页面、样式、TypeScript 源码和权限")
                            AgentAppSaveDto request, ToolContext context) {
        return summary(agentAppService.create(requireUserId(context), request));
    }

    @Tool(name = "app_update", description = """
            修改当前用户已有的动态应用并创建新版本。先调用 app_get 读取原应用。
            request 提交完整 name、description、html、css、source、permissions，
            baseVersion 必须原样填写刚读取的 currentVersion，防止覆盖并发编辑。
            运行约定和 SDK 与 app_create 相同。更新保留应用 ID 和已存储的应用数据，历史版本可回滚。
            """)
    public AgentAppDo update(@ToolParam(name = "id", description = "动态应用 ID") String id,
                            @ToolParam(name = "request", description = "完整应用包及 baseVersion") AgentAppSaveDto request,
                            ToolContext context) {
        return summary(agentAppService.update(requireUserId(context), id, request));
    }

    @Tool(name = "app_versions", readOnly = true, description = "查询当前用户动态应用的历史版本和当前生效版本。")
    public List<AgentAppVersionDo> versions(@ToolParam(name = "id", description = "动态应用 ID") String id, ToolContext context) {
        return agentAppService.versions(requireUserId(context), id);
    }

    @Tool(name = "app_activate_version", description = """
            将用户指定的历史版本设为当前应用版本，应用数据不回滚。
            version 来自 app_versions，baseVersion 来自 app_get 的 currentVersion。
            """)
    public AgentAppDo activate(@ToolParam(name = "id", description = "动态应用 ID") String id,
                              @ToolParam(name = "version", description = "要恢复的历史版本") String version,
                              @ToolParam(name = "baseVersion", description = "当前生效版本") String baseVersion,
                              ToolContext context) {
        return summary(agentAppService.activate(requireUserId(context), id, version, baseVersion));
    }

    @Tool(name = "app_delete", description = """
            删除用户明确要求删除的个人动态应用及其历史版本和应用数据。
            """)
    public boolean delete(@ToolParam(name = "id", description = "动态应用 ID") String id, ToolContext context) {
        return agentAppService.delete(requireUserId(context), id);
    }

    /**
     * 保存操作只返回目录信息，避免把完整源码再次注入模型上下文。
     */
    private AgentAppDo summary(AgentAppDetailDo application) {
        return new AgentAppDo().setId(application.getId()).setName(application.getName())
                .setDescription(application.getDescription())
                .setCurrentVersion(application.getCurrentVersion());
    }

    /**
     * 所有者只从平台注入的上下文读取，不允许模型指定其他用户。
     */
    private String requireUserId(ToolContext context) {
        if (context == null || StringUtil.isBlank(context.getUserId())) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return context.getUserId();
    }
}
