package com.example.qarag.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 日志脱敏工具类，用于安全地记录包含敏感信息或大文本内容的日志。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class LogMaskingUtils {

    /**
     * 默认最大显示长度
     */
    private static final int DEFAULT_MAX_LENGTH = 100;

    /**
     * 截断文本内容，并在末尾添加长度信息。
     *
     * @param content   原始文本
     * @param maxLength 最大保留长度
     * @return 截断后的文本
     */
    public static String mask(String content, int maxLength) {
        if (content == null) {
            return "null";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "... [Total: " + content.length() + " chars]";
    }

    /**
     * 使用默认长度 (100) 截断文本内容。
     *
     * @param content 原始文本
     * @return 截断后的文本
     */
    public static String mask(String content) {
        return mask(content, DEFAULT_MAX_LENGTH);
    }

    /**
     * 对敏感信息（如 API Key、密码等）进行脱敏。
     * 规则：保留前 4 位和后 4 位，中间使用星号替代。
     *
     * @param secret 敏感字符串
     * @return 脱敏后的字符串
     */
    public static String maskSensitive(String secret) {
        if (secret == null || secret.isBlank()) {
            return "********";
        }
        if (secret.length() <= 8) {
            return "**** (length: " + secret.length() + ")";
        }
        return secret.substring(0, 4) + "********" + secret.substring(secret.length() - 4);
    }

    /**
     * 专门用于用户查询的脱敏输出。
     * 考虑到查询通常较短，可以保留稍多内容。
     *
     * @param query 用户查询
     * @return 脱敏后的查询
     */
    public static String maskQuery(String query) {
        return mask(query, 50);
    }
}
