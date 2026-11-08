package com.x.framework.common.utils;

import org.apache.commons.codec.binary.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** Password hashing compatible with existing user records. */
public final class EncryptionUtil {
    private EncryptionUtil() {}

    /**
     * sha256
     *
     * @param data 加密内容
     * @param key  盐
     */
    public static String hmacSha256(String data, String key) {

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeBase64String(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash password", e);
        }
    }

}
