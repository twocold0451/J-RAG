package com.example.qarag.ingestion.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * 简单的 HTML 转 Markdown 工具类
 * 用于将爬取的网页内容转换为结构化的 Markdown，以便利用 MarkdownChunker 进行切分
 */
public class HtmlToMarkdownUtils {

    public static String convert(String html) {
        // 1. 清理 HTML：移除脚本、样式、隐藏元素等
        Document doc = Jsoup.parse(html);
        
        // 移除无用标签
        doc.select("script, style, meta, iframe, noscript, header, footer, nav, aside").remove();
        
        // 尝试移除常见的广告或无关容器 (简单的启发式)
        doc.select(".ads, .advertisement, .sidebar, .menu, .cookie-notice").remove();

        // 2. 遍历节点生成 Markdown
        StringBuilder md = new StringBuilder();
        NodeTraversor.traverse(new MarkdownVisitor(md), doc.body());

        return md.toString().trim();
    }

    private static class MarkdownVisitor implements NodeVisitor {
        private final StringBuilder sb;
        private int listDepth = 0; // 列表嵌套深度

        public MarkdownVisitor(StringBuilder sb) {
            this.sb = sb;
        }

        @Override
        public void head(Node node, int depth) {
            if (node instanceof TextNode) {
                String text = ((TextNode) node).text();
                // 仅当文本非空且不在 pre 标签内时进行修剪
                if (!isInPre(node)) {
                    text = text.replace(' ', ' ').trim(); // 替换 &nbsp;
                    if (!text.isEmpty()) {
                        sb.append(text);
                    }
                } else {
                    sb.append(text);
                }
            } else if (node instanceof Element el) {
                String tagName = el.tagName().toLowerCase();

                switch (tagName) {
                    case "h1" -> sb.append("\n\n# ");
                    case "h2" -> sb.append("\n\n## ");
                    case "h3" -> sb.append("\n\n### ");
                    case "h4" -> sb.append("\n\n#### ");
                    case "h5" -> sb.append("\n\n##### ");
                    case "h6" -> sb.append("\n\n###### ");
                    case "p" -> sb.append("\n\n");
                    case "br" -> sb.append("\n");
                    case "ul", "ol" -> {
                        sb.append("\n");
                        listDepth++;
                    }
                    case "li" -> {
                        sb.append("\n");
                        sb.append("  ".repeat(Math.max(0, listDepth - 1)));
                        if (el.parent() != null && "ol".equals(el.parent().tagName())) {
                            sb.append("1. "); // 简化处理，统一用 1. (Markdown 渲染器会自动修正)
                        } else {
                            sb.append("- ");
                        }
                    }
                    case "blockquote" -> sb.append("\n> ");
                    case "pre" -> sb.append("\n```\n");
                    case "code" -> {
                        if (!isInPre(node)) sb.append("`");
                    }
                    case "a" -> sb.append("[");
                    case "strong", "b" -> sb.append("**");
                    case "em", "i" -> sb.append("*");
                    case "img" -> {
                        String src = el.attr("src");
                        String alt = el.attr("alt");
                        if (!src.isEmpty()) {
                            sb.append("![").append(alt).append("](").append(src).append(")");
                        }
                    }
                    case "tr" -> sb.append("\n| ");
                    case "th", "td" -> sb.append(" ");
                }
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (node instanceof Element el) {
                String tagName = el.tagName().toLowerCase();

                switch (tagName) {
                    case "h1", "h2", "h3", "h4", "h5", "h6" -> sb.append("\n");
                    case "p" -> sb.append("\n");
                    case "ul", "ol" -> {
                        listDepth--;
                        sb.append("\n");
                    }
                    case "pre" -> sb.append("\n```\n");
                    case "code" -> {
                        if (!isInPre(node)) sb.append("`");
                    }
                    case "a" -> {
                        String href = el.attr("href");
                        if (!href.isEmpty()) {
                            sb.append("](").append(href).append(")");
                        } else {
                            sb.append("]");
                        }
                    }
                    case "strong", "b" -> sb.append("**");
                    case "em", "i" -> sb.append("*");
                    case "th", "td" -> sb.append(" |");
                    case "table" -> {
                        // 表格结束后不做特殊处理，但在 head 中处理 tr/td 已经生成了基本结构
                        // 这里可以尝试生成表头分隔线，但比较复杂，先生成基本文本结构
                        sb.append("\n");
                    }
                }
            }
        }

        private boolean isInPre(Node node) {
            if (node == null) return false;
            if (node instanceof Element && "pre".equals(((Element) node).tagName())) return true;
            return isInPre(node.parent());
        }
    }
}
