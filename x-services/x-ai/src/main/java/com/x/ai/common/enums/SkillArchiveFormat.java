package com.x.ai.common.enums;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum SkillArchiveFormat {

    ZIP("zip", "application/zip"),
    TAR("tar", "application/x-tar"),
    TAR_GZIP("tar.gz", "application/gzip");

    /**
     * 导出文件名使用的扩展名，不含前导点。
     */
    private final String extension;

    /**
     * HTTP 下载响应声明的媒体类型。
     */
    private final String contentType;

    public static SkillArchiveFormat fromExtension(String extension) {
        for (SkillArchiveFormat format : values()) {
            if (format.extension.equalsIgnoreCase(extension)) {
                return format;
            }
        }
        throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
    }

    public static SkillArchiveFormat fromFilename(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return TAR_GZIP;
        }
        if (name.endsWith(".zip")) {
            return ZIP;
        }
        if (name.endsWith(".tar")) {
            return TAR;
        }
        throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
    }
}
