package com.x.framework.common.utils;

import org.apache.commons.lang3.RandomStringUtils;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author : xuemingqi
 * @since : 2024-11-07 10:56
 */
public class IdUtil extends cn.hutool.core.util.IdUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成密码学安全的 URL-safe 随机字符串。
     *
     * @param byteLength 随机字节数
     * @return 不带 Base64 padding 的随机字符串
     */
    public static String secureRandomUrlSafe(int byteLength) {
        if (byteLength <= 0) {
            throw new IllegalArgumentException("byteLength must be greater than 0");
        }
        byte[] randomBytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 生成随机字母数字组合
     *
     * @param length 长度
     * @return 随机字母数字组合
     */
    public static String randomAlphanumeric(int length) {
        return RandomStringUtils.insecure().nextAlphanumeric(length).toLowerCase();
    }
}
