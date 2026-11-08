package com.x.ai.skill;

import com.x.ai.common.constants.SkillArchiveConstant;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 数据库存原始字节，AgentScope 边界使用其原生 base64 资源协议。
 */
public final class SkillResourceCodec {

    private SkillResourceCodec() {
    }

    public static String encode(byte[] content) {
        return SkillArchiveConstant.BASE64_PREFIX + Base64.getEncoder().encodeToString(content);
    }

    public static byte[] decode(String content) {
        return content.startsWith(SkillArchiveConstant.BASE64_PREFIX)
                ? Base64.getDecoder().decode(content.substring(SkillArchiveConstant.BASE64_PREFIX.length()))
                : content.getBytes(StandardCharsets.UTF_8);
    }
}
