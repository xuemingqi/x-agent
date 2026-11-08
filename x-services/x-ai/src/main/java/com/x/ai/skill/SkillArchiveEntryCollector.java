package com.x.ai.skill;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.x.ai.common.constants.SkillArchiveConstant.MAX_ENTRIES;
import static com.x.ai.common.constants.SkillArchiveConstant.MAX_EXPANDED_BYTES;

/**
 * ZIP、TAR 与文件目录共享条目预算、路径去重和实际读取字节预算。
 */
final class SkillArchiveEntryCollector {

    private final Map<String, byte[]> files = new LinkedHashMap<>();

    private final Set<String> paths = new HashSet<>();

    private int expandedBytes;

    private int entryCount;

    Map<String, byte[]> getFiles() {
        return files;
    }

    byte[] add(String name, boolean directory, long size, InputStream input) throws IOException {
        validateBudget(size);
        if (directory && (name.equals("./") || name.equals("."))) {
            return null;
        }
        String path = SkillArchiveCodec.validatePath(name);
        if (!paths.add(path)) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
        if (directory) {
            return null;
        }
        byte[] content = readContent(input, size);
        files.put(path, content);
        return content;
    }

    private void validateBudget(long size) {
        if (++entryCount > MAX_ENTRIES || size < 0 || size > MAX_EXPANDED_BYTES - expandedBytes) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
    }

    private byte[] readContent(InputStream input, long expectedSize) throws IOException {
        byte[] content = input.readNBytes(MAX_EXPANDED_BYTES - expandedBytes + 1);
        expandedBytes += content.length;
        if (expandedBytes > MAX_EXPANDED_BYTES || content.length != expectedSize) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
        return content;
    }
}
