package com.x.framework.common.utils;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * 聊天图片的媒体类型识别；仅接受可安全预览的常见栅格格式。
 */
public final class ImageUtil {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "gif", "image/gif", "webp", "image/webp");

    private ImageUtil() {
    }

    /**
     * 根据文件名识别受支持的图片，非图片返回 null。
     */
    public static String contentType(String fileName) {
        int separator = fileName.lastIndexOf('.');
        return EXTENSIONS.get(fileName.substring(separator + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * 依据文件签名识别实际格式，不信任上传请求和模型返回的 MIME 声明。
     */
    public static String contentType(byte[] content) {
        if (content.length < 12) {
            return null;
        }
        String signature = HexFormat.of().formatHex(content, 0, 8);
        if (signature.equals("89504e470d0a1a0a")) {
            return "image/png";
        }
        if (signature.startsWith("ffd8ff")) {
            return "image/jpeg";
        }
        String header = new String(content, 0, 12, StandardCharsets.US_ASCII);
        if (header.startsWith("GIF87a") || header.startsWith("GIF89a")) {
            return "image/gif";
        }
        return header.startsWith("RIFF") && header.endsWith("WEBP") ? "image/webp" : null;
    }

    /**
     * 从受支持的媒体类型取得规范扩展名。
     */
    public static String extension(String contentType) {
        return "image/jpeg".equals(contentType) ? "jpg" : contentType.substring("image/".length());
    }
}
