package com.twocold.jrag.ingestion.utils;

import java.util.regex.Pattern;

/**
 * 文本清洗工具类
 * 负责清理 PDF、Word 等文档提取的文本噪音，优化向量化质量
 */
public class TextCleaner {

    // 多个连续空格
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("[ 　]{2,}");
    // 多个连续换行（保留段落分隔）
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\\n{3,}");
    // 行首行尾空格 + 换行组合
    private static final Pattern LEADING_TRAILING_WHITESPACE = Pattern.compile("[ \\t]+\n|\n[ \\t]+");
    // 无效的控制字符（保留换行和制表符）
    private static final Pattern INVALID_CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\n\r\t]]");
    // PDF 装饰线（如 ___ 或 ---）
    private static final Pattern PDF_DECORATION_LINES = Pattern.compile("^[-_=]{3,}\\s*$", Pattern.MULTILINE);
    // 表格中多余的管道符分隔符（开头和结尾）
    private static final Pattern TABLE_PIPE_TRIM = Pattern.compile("^\\|+|\\|+$", Pattern.MULTILINE);
    // 表格中重复的分割行
    private static final Pattern TABLE_SEPARATOR_REPEAT = Pattern.compile("(\\|[-:]+)+\\n", Pattern.MULTILINE);

    private TextCleaner() {
        // 工具类，私有构造函数
    }

    /**
     * 对文本进行完整的清洗流程
     *
     * @param rawText 原始文本
     * @return 清洗后的文本
     */
    public static String clean(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return rawText;
        }

        String text = rawText;

        // 1. 移除无效控制字符
        text = INVALID_CONTROL_CHARS.matcher(text).replaceAll("");

        // 2. 移除 PDF 装饰线
        text = PDF_DECORATION_LINES.matcher(text).replaceAll("");

        // 3. 规范化空格（全角转半角，多个变单个）
        text = normalizeSpaces(text);

        // 4. 规范化换行
        text = normalizeNewlines(text);

        // 5. 清理表格格式
        text = cleanTableFormat(text);

        return text.trim();
    }

    /**
     * 专门针对 Markdown 表格的清洗
     */
    public static String cleanMarkdownTable(String tableText) {
        if (tableText == null || tableText.isBlank()) {
            return tableText;
        }

        String text = tableText;

        // 移除多余的管道符
        text = TABLE_PIPE_TRIM.matcher(text).replaceAll("");

        // 规范化分割行
        text = TABLE_SEPARATOR_REPEAT.matcher(text).replaceAll("");

        // 清理单元格内的多余换行和空格
        text = text.replaceAll("\\|\\s*\\n\\s*\\|", "|\n|");
        text = text.replaceAll("\\s{2,}", " ");

        return text.trim();
    }

    /**
     * 规范化空格
     */
    private static String normalizeSpaces(String text) {
        // 先规范化全角空格
        text = text.replace('　', ' ');
        // 多个空格变单个
        text = MULTIPLE_SPACES.matcher(text).replaceAll(" ");
        // 移除行首行尾空格
        text = text.replaceAll("^[ \\t]+|[ \\t]+$", "");
        return text;
    }

    /**
     * 规范化换行
     */
    private static String normalizeNewlines(String text) {
        // 多个换行变双换行（保留段落）
        text = MULTIPLE_NEWLINES.matcher(text).replaceAll("\n\n");
        // 移除行首行尾空格+换行组合
        text = LEADING_TRAILING_WHITESPACE.matcher(text).replaceAll("\n");
        return text;
    }

    /**
     * 清理表格格式
     */
    private static String cleanTableFormat(String text) {
        // 如果是表格格式（包含 | 字符）
        if (text.contains("|")) {
            String[] lines = text.split("\\n");
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String trimmed = line.trim();

                // 跳过空的表格行
                if (trimmed.isEmpty() || trimmed.matches("\\|+")) {
                    continue;
                }

                // 规范化管道符
                trimmed = trimmed.replaceAll("\\|{2,}", "|");

                // 如果是分割行（如 |---|---），规范化
                if (trimmed.matches("\\|?[-:]+(\\|[-:]+)*\\|?")) {
                    trimmed = trimmed.replaceAll("^\\||\\|$", "");
                    String[] cells = trimmed.split("\\|");
                    StringBuilder separator = new StringBuilder("|");
                    for (String cell : cells) {
                        cell = cell.trim();
                        if (cell.startsWith(":") && cell.endsWith(":")) {
                            separator.append("---: |");
                        } else if (cell.startsWith(":")) {
                            separator.append(":--- |");
                        } else if (cell.endsWith(":")) {
                            separator.append("---: |");
                        } else {
                            separator.append("---|");
                        }
                    }
                    trimmed = separator.toString();
                }

                // 清理单元格内容
                if (trimmed.startsWith("|")) {
                    trimmed = " " + trimmed.substring(1);
                }
                if (trimmed.endsWith("|")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1) + " ";
                }

                // 单元格内换行变空格
                trimmed = trimmed.replaceAll("\\n", " ");

                sb.append(trimmed);
                if (i < lines.length - 1) {
                    sb.append("\n");
                }
            }

            return sb.toString();
        }

        return text;
    }

    /**
     * 清理 PDF 提取的乱码和噪音
     */
    public static String cleanPdfNoise(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = text;

        // 移除常见的 PDF 噪音模式
        cleaned = cleaned.replaceAll("□{2,}", "");
        cleaned = cleaned.replaceAll("�{1,}", "");
        cleaned = cleaned.replaceAll("\\.{3,}", "...");

        // 移除页码标记（如 "Page 5 of 10"）
        cleaned = cleaned.replaceAll("(?i)page\\s+\\d+\\s+(of|/)\\s+\\d+", "");
        cleaned = cleaned.replaceAll("(?i)^\\s*\\d+\\s*$", "");

        // 移除页眉页脚（单行数字或短文本）
        cleaned = cleaned.replaceAll("(?m)^[\\d\\s]{1,10}$", "");

        return clean(cleaned);
    }

    /**
     * 清理 Word 文档的特殊字符
     */
    public static String cleanWordArtifacts(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = text;

        // Word 分节符
        cleaned = cleaned.replaceAll("[\u0013\u0014\u0015\u0016]", "");
        // Word 分页符
        cleaned = cleaned.replaceAll("\u0014", "\n");
        cleaned = cleaned.replaceAll("\u0015", "\n");

        // 脚注尾注标记
        cleaned = cleaned.replaceAll("\\[\\d+]", "");
        cleaned = cleaned.replaceAll("\\^[0-9]+", "");

        return clean(cleaned);
    }

    /**
     * 清理 Excel 导出文本
     */
    public static String cleanExcelOutput(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String cleaned = text;

        // 处理 tab 分隔符
        cleaned = cleaned.replaceAll("\t", " | ");

        // 移除多余的引号
        cleaned = cleaned.replaceAll("^\"+|\"+$", "");

        return clean(cleaned);
    }
}
