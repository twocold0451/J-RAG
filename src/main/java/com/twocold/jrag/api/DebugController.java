package com.twocold.jrag.api;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @Value("${langchain4j.open-ai.embedding-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.embedding-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String modelName;

    private static final String JSON_MEDIA_TYPE = "application/json; charset=utf-8";

    @GetMapping("/siliconflow/http2")
    public String testHttp2() {
        return executeRequest(false);
    }

    @GetMapping("/siliconflow/http1")
    public String testHttp1() {
        return executeRequest(true);
    }

    private String executeRequest(boolean forceHttp1) {
        try {
            OkHttpClient.Builder builder = new OkHttpClient.Builder();

            if (forceHttp1) {
                builder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
            }
            // Default OkHttp supports HTTP/2 if not restricted

            OkHttpClient client = builder.build();

            String url = baseUrl;
            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            // If baseUrl is just the host (e.g. https://api.siliconflow.cn/v1), we need to append /embeddings
            if (!url.endsWith("/embeddings")) {
                url = url + "/embeddings";
            }

            // Construct a simple embedding request
            String jsonBody = String.format("{ \"model\": \"%s\", \"input\": \"Test string for debugging\" }", modelName);

            RequestBody body = RequestBody.create(MediaType.parse(JSON_MEDIA_TYPE), jsonBody);

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "null";
                return String.format("Protocol: %s\nCode: %d\nBody: %s",
                        response.protocol(),
                        response.code(),
                        responseBody);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
