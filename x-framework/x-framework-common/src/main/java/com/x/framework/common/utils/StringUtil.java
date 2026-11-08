package com.x.framework.common.utils;

import cn.hutool.core.util.StrUtil;
import org.apache.commons.codec.digest.DigestUtils;

public class StringUtil extends StrUtil {

    /**
     * 计算字符串的 SHA-256 十六进制摘要。
     *
     * @param value 原始字符串
     * @return 小写十六进制摘要
     */
    public static String sha256Hex(String value) {
        return DigestUtils.sha256Hex(value);
    }

    /**
     * 判断字符串是否包含 CR 或 LF。
     */
    public static boolean containsLineBreak(String value) {
        return value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0);
    }

    /**
     * 将工具调用中的字符串 ID 转为正整数，避免模型处理雪花 ID 时丢失精度。
     */
    public static Long parseLongId(String id) {
        try {
            long value = Long.parseLong(id);
            if (value <= 0) {
                throw new IllegalArgumentException("ID 必须是正整数格式的字符串");
            }
            return value;
        } catch (NumberFormatException | NullPointerException exception) {
            throw new IllegalArgumentException("ID 必须是正整数格式的字符串");
        }
    }
}
