package com.x.ai.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class AgentAppTemplateDo {

    /**
     * 资源包声明的模板标识，不是用户应用 ID。
     */
    private String id;

    /**
     * 创建个人副本时使用的默认名称。
     */
    private String name;

    /**
     * 面向使用者的模板能力说明。
     */
    private String description;

    /**
     * 模板运行所需的 SDK 能力；安装后随个人应用版本保存。
     */
    private List<String> permissions;
}
