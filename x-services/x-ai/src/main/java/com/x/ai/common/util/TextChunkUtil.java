package com.x.ai.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 按 Unicode 字符切片并保留重叠，不拆开代理字符对。
 */
public final class TextChunkUtil {

    private TextChunkUtil() {
    }

    public static List<String> split(String content, int chunkSize, int overlap) {
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("Invalid chunk size or overlap");
        }
        int[] characters = content.codePoints().toArray();
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < characters.length; start += chunkSize - overlap) {
            int length = Math.min(chunkSize, characters.length - start);
            String text = new String(characters, start, length);
            if (!text.isBlank()) chunks.add(text);
            if (start + length == characters.length) break;
        }
        return chunks;
    }
}
