package com.x.ai.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
public class AgentAppPackageDo {

    /**
     * 应用宿主协议版本，当前只支持 1。
     */
    private int sdkVersion;

    /**
     * 隔离页面的主体 HTML。
     */
    private String html;

    /**
     * 隔离页面的样式。
     */
    private String css;

    /**
     * 浏览器编译执行的 TypeScript 源码。
     */
    private String source;

    /**
     * 此版本声明的 SDK 能力范围。
     */
    private List<String> permissions;
}
