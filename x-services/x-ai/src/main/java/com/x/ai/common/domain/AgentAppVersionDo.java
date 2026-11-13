package com.x.ai.common.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class AgentAppVersionDo {

    /**
     * 不可变的应用版本标识。
     */
    private String version;

    /**
     * 该版本发布时的应用名称。
     */
    private String name;

    /**
     * 该版本发布时的应用介绍。
     */
    private String description;

    /**
     * MinIO 应用包的 UTF-8 字节数。
     */
    private long sizeBytes;

    /**
     * 应用包的 SHA-256 摘要。
     */
    private String sha256;

    /**
     * 版本写入数据库的时间。
     */
    private LocalDateTime createTime;

    /**
     * 是否为当前打开的发布版本。
     */
    private boolean current;
}
