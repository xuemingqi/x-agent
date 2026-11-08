package com.x.ai.skill;

import com.x.ai.common.enums.SkillArchiveFormat;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillArchiveCodecTest {

    @ParameterizedTest
    @EnumSource(SkillArchiveFormat.class)
    void roundTripsInstructionsAndBinaryResources(SkillArchiveFormat format) throws IOException {
        Map<String, byte[]> files = Map.of("example/SKILL.md", "instructions".getBytes(),
                "example/assets/picture.bin", new byte[]{0, 1, -1, 32});

        Map<String, byte[]> restored = SkillArchiveCodec.read(SkillArchiveCodec.write(files, format), format);

        assertEquals(files.keySet(), restored.keySet());
        files.forEach((path, content) -> assertArrayEquals(content, restored.get(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside", "/root", "a/../outside", "a\\outside", "C:/outside", "a//b"})
    void rejectsNonRelativeOrAmbiguousPaths(String path) {
        assertThrows(XException.class, () -> SkillArchiveCodec.validatePath(path));
    }

    @Test
    void binaryResourceCodecDoesNotChangeOriginalBytes() {
        byte[] bytes = {0, -1, -128, 42};
        assertArrayEquals(bytes, SkillResourceCodec.decode(SkillResourceCodec.encode(bytes)));
    }
}
