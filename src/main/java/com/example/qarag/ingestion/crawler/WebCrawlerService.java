package com.example.qarag.ingestion.crawler;

import com.example.qarag.ingestion.utils.HtmlToMarkdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class WebCrawlerService {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int TIMEOUT_MS = 30000;

    /**
     * 抓取网页内容并保存为临时 Markdown 文件
     *
     * @param url 目标 URL
     * @return 包含临时文件路径和网页标题的结果对象
     */
    public CrawlResult fetchAndSave(String url) throws IOException {
        log.info("开始抓取网页: {}", url);

        // 1. 获取 HTML
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        String title = doc.title();
        if (title.isBlank()) {
            title = "Web Page";
        }
        
        // 净化标题文件名
        String safeFilename = title.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safeFilename.length() > 100) {
            safeFilename = safeFilename.substring(0, 100);
        }

        // 2. 转换为 Markdown
        String markdown = HtmlToMarkdownUtils.convert(doc.html());
        
        // 添加元数据头部
        String content = String.format("""
                # %s
                
                > **Source**: [%s](%s)
                > **Crawled At**: %s
                
                %s
                """, title, url, url, java.time.LocalDateTime.now(), markdown);

        // 3. 保存为临时文件
        Path tempDir = Files.createTempDirectory("qarag_crawl_");
        Path tempFile = tempDir.resolve(safeFilename + ".md");
        Files.writeString(tempFile, content);

        log.info("网页抓取完成，已保存至: {}", tempFile);
        return new CrawlResult(tempFile, title, url);
    }

    public record CrawlResult(Path tempFile, String title, String sourceUrl) {}
}
