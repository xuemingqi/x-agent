package com.x.ai.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextChunkUtilTest {

    @Test
    void chunksChineseAndSupplementaryCharactersWithOverlapWithoutBreakingSurrogates() {
        List<String> chunks = TextChunkUtil.split("甲😀乙丙丁戊", 4, 1);
        assertEquals(List.of("甲😀乙丙", "丙丁戊"), chunks);
        assertEquals(4, chunks.getFirst().codePointCount(0, chunks.getFirst().length()));
    }

    @Test
    void exactBoundaryDoesNotCreateARedundantTrailingChunk() {
        assertEquals(List.of("1234"), TextChunkUtil.split("1234", 4, 2));
        assertEquals(List.of("1234", "3456"), TextChunkUtil.split("123456", 4, 2));
    }

    @Test
    void invalidOverlapFailsInsteadOfLoopingForever() {
        assertThrows(IllegalArgumentException.class, () -> TextChunkUtil.split("text", 4, 4));
        assertThrows(IllegalArgumentException.class, () -> TextChunkUtil.split("text", 4, -1));
    }
}
