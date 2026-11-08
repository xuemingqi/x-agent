package com.x.framework.common.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Shared service-to-service signing; the secret is supplied by Nacos/environment. */
public final class InternalRequestSigner {
    private static final long MAX_CLOCK_SKEW_MILLIS = 120_000L;
    private final SecretKeySpec key;

    public InternalRequestSigner(String secret) {
        if (secret == null || secret.isBlank() || secret.length() < 32 || secret.contains("${")) {
            throw new IllegalArgumentException("x.security.internal-secret must contain at least 32 characters");
        }
        key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String sign(String path, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal((path + "\n" + timestamp).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot sign internal request", exception);
        }
    }

    public boolean verify(String path, String timestamp, String signature, long now) {
        if (timestamp == null || signature == null || signature.length() != 64) {
            return false;
        }
        try {
            long suppliedTime = Long.parseLong(timestamp);
            if (suppliedTime < now - MAX_CLOCK_SKEW_MILLIS || suppliedTime > now + MAX_CLOCK_SKEW_MILLIS) {
                return false;
            }
            return MessageDigest.isEqual(sign(path, timestamp).getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII));
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
