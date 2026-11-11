package com.x.ai.common.util;

import java.io.IOException;
import java.io.Writer;

/**
 * 提取器共享的有界文本输出，超过上限时立即停止解析。
 */
public class LimitedTextWriter extends Writer {

    private final int limit;

    private final StringBuilder content = new StringBuilder();

    public LimitedTextWriter(int limit) {
        this.limit = limit;
    }

    @Override
    public void write(char[] buffer, int offset, int length) throws IOException {
        if (content.length() + length > limit) {
            throw new IOException("Document text exceeds extraction limit");
        }
        content.append(buffer, offset, length);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return content.toString();
    }
}
