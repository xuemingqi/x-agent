package com.x.ai.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class AgentAppSaveDto {

    /**
     * 应用展示名称。
     */
    @NotBlank
    @Size(max = 100)
    private String name;

    /**
     * 面向使用者的能力介绍。
     */
    @NotNull
    @Size(max = 1000)
    private String description;

    /**
     * 页面主体 HTML，不包含宿主页面与登录凭据。
     */
    @NotNull
    @Size(max = 1_048_576)
    private String html;

    /**
     * 应用自己的 CSS 样式。
     */
    @NotNull
    @Size(max = 262_144)
    private String css;

    /**
     * 在浏览器隔离容器中编译执行的 TypeScript 源码。
     */
    @NotBlank
    @Size(max = 1_048_576)
    private String source;

    /**
     * 声明需要的 SDK 能力，支持 model.generate、agent.run、storage 和 http.request。
     */
    @NotNull
    @Size(max = 4)
    private List<@NotBlank String> permissions;

    /**
     * 修改时读取到的当前版本；创建时不填写，用于阻止覆盖其他并发修改。
     */
    @Size(max = 64)
    private String baseVersion;
}
