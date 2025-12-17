package com.example.qarag.ingestion.vision;

import com.example.qarag.config.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 视觉模型服务
 * 用于处理文档中的图片、图表、扫描件
 * 支持 OpenAI GPT-4V 兼容的 API (如 Kimi Vision)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisionService {

    private final RagProperties ragProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 检查视觉服务是否已启用
     */
    public boolean isEnabled() {
        return ragProperties.vision() != null
                && ragProperties.vision().enabled()
                && ragProperties.vision().apiKey() != null
                && !ragProperties.vision().apiKey().isBlank();
    }

    /**
     * 使用视觉模型分析图片
     *
     * @param image  要分析的图片
     * @param prompt 分析提示词
     * @return 模型返回的文本描述
     */
    public String analyzeImage(BufferedImage image, String prompt) {
        if (!isEnabled()) {
            log.warn("Vision service is not enabled or configured");
            return "[视觉模型未启用]";
        }

        try {
            String base64Image = encodeImageToBase64(image);
            return callVisionApi(base64Image, prompt);
        } catch (Exception e) {
            log.error("Failed to analyze image: {}", e.getMessage(), e);
            return "[图片分析失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 将图片编码为 Base64
     */
    private String encodeImageToBase64(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    /**
     * 调用 视觉模型 (支持 OpenAI 兼容格式 和 Google Gemini 原生格式)
     */
    private String callVisionApi(String base64Image, String prompt) {
        RagProperties.Vision config = ragProperties.vision();
        String baseUrl = config.baseUrl();

        // Check if it's Google Gemini API
        if (baseUrl.contains("googleapis.com")) {
            return callGoogleGeminiApi(config, base64Image, prompt);
        }

        // Default to OpenAI compatible API
        return callOpenAiCompatibleApi(config, base64Image, prompt);
    }

    private String callGoogleGeminiApi(RagProperties.Vision config, String base64Image, String prompt) {
        // Construct Google API URL
        // e.g. https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=API_KEY
        String apiVersion = "v1beta"; // Default to v1beta
        String url = String.format("%s/%s/models/%s:generateContent?key=%s",
                config.baseUrl().replaceAll("/$", ""), // Remove trailing slash
                apiVersion,
                config.modelName(),
                config.apiKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Payload for Google Gemini
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt),
                                Map.of("inline_data", Map.of(
                                        "mime_type", "image/png",
                                        "data", base64Image
                                ))
                        ))
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            int payloadSizeKB = base64Image.length() / 1024;
            log.info("Calling Google Gemini API. Image size: {} KB. Model: {}", payloadSizeKB, config.modelName());

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Google Gemini API call completed in {} ms. Status: {}", duration, response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                try {
                    Map<String, Object> bodyMap = objectMapper.readValue(response.getBody(), Map.class);
                    return extractContentFromGoogleResponse(bodyMap);
                } catch (Exception e) {
                    log.error("Failed to parse JSON response from Google Gemini API. Raw response: {}", response.getBody());
                    throw new RuntimeException("Google Gemini API returned invalid JSON", e);
                }
            } else {
                log.error("Google Gemini API returned non-success status: {}. Body: {}", response.getStatusCode(), response.getBody());
                return "[视觉模型 请求失败]";
            }
        } catch (Exception e) {
            log.error("Google Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("Google Gemini API call failed", e);
        }
    }

    private String extractContentFromGoogleResponse(Map<String, Object> response) {
        try {
            // Response structure: { "candidates": [ { "content": { "parts": [ { "text": "..." } ] } } ] }
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                if (content != null) {
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    if (parts != null && !parts.isEmpty()) {
                        return (String) parts.get(0).get("text");
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Google Gemini API response: {}", e.getMessage());
        }
        return "[无法解析 Google Gemini 响应]";
    }

    private String callOpenAiCompatibleApi(RagProperties.Vision config, String base64Image, String prompt) {
        String url = config.baseUrl().endsWith("/")
                ? config.baseUrl() + "chat/completions"
                : config.baseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.apiKey());

        // Log approximate payload size
        int payloadSizeKB = base64Image.length() / 1024;
        log.info("Preparing Vision API request (OpenAI Compatible). Image Base64 size: {} KB. Model: {}", payloadSizeKB, config.modelName());

        // 构建 OpenAI 视觉模型 格式的请求
        Map<String, Object> requestBody = Map.of(
                "model", config.modelName(),
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", prompt),
                                        Map.of("type", "image_url", "image_url",
                                                Map.of("url", "data:image/png;base64," + base64Image))))),
                "max_tokens", 2000);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            log.debug("Calling Vision API: {} with model {}", url, config.modelName());

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    String.class);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Vision API call completed in {} ms. Status: {}", duration, response.getStatusCode());

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                try {
                    Map<String, Object> bodyMap = objectMapper.readValue(response.getBody(), Map.class);
                    return extractContentFromResponse(bodyMap);
                } catch (Exception e) {
                    log.error("Failed to parse JSON response from Vision API. Raw response: {}", response.getBody());
                    throw new RuntimeException("Vision API returned invalid JSON", e);
                }
            } else {
                log.error("Vision API returned non-success status: {}. Body: {}", response.getStatusCode(), response.getBody());
                return "[视觉模型 请求失败]";
            }
        } catch (Exception e) {
            log.error("Vision API call failed: {}", e.getMessage());
            throw new RuntimeException("Vision API call failed", e);
        }
    }

    /**
     * 从 API 响应中提取文本内容
     */
    @SuppressWarnings("unchecked")
    private String extractContentFromResponse(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Vision API response: {}", e.getMessage());
        }
        return "[无法解析 Vision API 响应]";
    }
}
