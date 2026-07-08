package com.twocold.jrag.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twocold.jrag.domain.Chunk;
import com.twocold.jrag.service.QueryDecompositionService;
import com.twocold.jrag.service.RetrievalService;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RagAgentTools 单元测试
 */
class RagAgentToolsTest {

    private RetrievalService retrievalService;
    private QueryDecompositionService decompositionService;
    private FluxSink<ServerSentEvent<String>> sink;
    private List<UUID> documentIds;
    private ObjectMapper objectMapper;
    private Executor executor;
    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        retrievalService = mock(RetrievalService.class);
        decompositionService = mock(QueryDecompositionService.class);
        sink = mock(FluxSink.class);
        documentIds = List.of(UUID.randomUUID());
        objectMapper = new ObjectMapper();
        executor = Runnable::run;  // 使用直接执行，不异步
        chatModel = mock(ChatModel.class);
    }

    @Test
    void testSearchKnowledgeBase_ReturnsSourceList_WhenResultsFound() throws Exception {
        // Arrange
        List<Chunk> chunks = createMockChunks(3);
        when(retrievalService.hybridSearch(anyString(), anyList())).thenReturn(chunks);

        RagAgentTools tools = new RagAgentTools(retrievalService, decompositionService, sink, documentIds, objectMapper, chatModel, executor);

        // Act
        String result = tools.searchKnowledgeBase("test query");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("[来源："));
        verify(retrievalService).hybridSearch("test query", documentIds);
        verify(sink, times(2)).next(any(ServerSentEvent.class)); // thought x 2
    }

    @Test
    void testSearchKnowledgeBase_ReturnsNotFoundMessage_WhenNoResults() throws Exception {
        // Arrange
        when(retrievalService.hybridSearch(anyString(), anyList())).thenReturn(Collections.emptyList());

        RagAgentTools tools = new RagAgentTools(retrievalService, decompositionService, sink, documentIds, objectMapper, chatModel, executor);

        // Act
        String result = tools.searchKnowledgeBase("nonexistent query");

        // Assert
        assertNotNull(result);
        assertTrue(result.contains("未在知识库中找到"));
    }

    @Test
    void testSearchKnowledgeBase_DetectsConsecutiveDuplicateCalls() throws Exception {
        // Arrange - 始终返回空结果
        when(retrievalService.hybridSearch(anyString(), anyList())).thenReturn(Collections.emptyList());

        RagAgentTools tools = new RagAgentTools(retrievalService, decompositionService, sink, documentIds, objectMapper, chatModel, executor);

        // Act - 第一次调用，空结果
        String result1 = tools.searchKnowledgeBase("duplicate query");
        assertFalse(result1.contains("系统警告"), "第一次调用不应触发警告");
        assertTrue(result1.contains("未在知识库中找到"), "第一次调用应返回未找到");

        // 第二次相同调用，应触发警告
        String result2 = tools.searchKnowledgeBase("duplicate query");
        assertTrue(result2.contains("系统警告"), "连续重复调用应触发警告");
        assertTrue(result2.contains("请立即放弃当前工具调用"), "应包含中断指令");
    }

    @Test
    void testSearchKnowledgeBase_AllowsDifferentQueries() throws Exception {
        // Arrange
        when(retrievalService.hybridSearch(anyString(), anyList())).thenReturn(Collections.emptyList());

        RagAgentTools tools = new RagAgentTools(retrievalService, decompositionService, sink, documentIds, objectMapper, chatModel, executor);

        // Act - 不同查询不应触发警告
        String result1 = tools.searchKnowledgeBase("query one");
        String result2 = tools.searchKnowledgeBase("query two");
        String result3 = tools.searchKnowledgeBase("query three");

        // Assert
        assertFalse(result1.contains("系统警告"));
        assertFalse(result2.contains("系统警告"));
        assertFalse(result3.contains("系统警告"));
    }

    @Test
    void testSearchKnowledgeBase_AllowsDuplicateAfterValidResult() throws Exception {
        // Arrange
        List<Chunk> chunks = createMockChunks(2);
        when(retrievalService.hybridSearch(eq("valid query"), anyList())).thenReturn(chunks);
        when(retrievalService.hybridSearch(eq("duplicate after valid"), anyList())).thenReturn(Collections.emptyList());

        RagAgentTools tools = new RagAgentTools(retrievalService, decompositionService, sink, documentIds, objectMapper, chatModel, executor);

        // Act - 第一次调用有有效结果
        String result1 = tools.searchKnowledgeBase("valid query");
        assertFalse(result1.contains("未找到"));

        // 第二次相同查询，但上次有有效结果，不应触发警告
        String result2 = tools.searchKnowledgeBase("valid query");
        // 这次会再次执行搜索（因为上次有有效结果），但由于 mock 返回空，会显示未找到
        assertFalse(result2.contains("系统警告"), "上次有有效结果时，重复查询不应触发警告");
    }

    @Test
    void testDecomposeQuery_DetectsConsecutiveDuplicateCalls() throws Exception {
        // Arrange
        when(decompositionService.decompose(anyString())).thenReturn(List.of("sub1", "sub2"));

        RagAgentTools tools = new RagAgentTools(retrievalService, decompositionService, sink, documentIds, objectMapper, chatModel, executor);

        // Act - 第一次调用
        String result1 = tools.decomposeQuery("complex question");
        assertFalse(result1.contains("系统警告"));

        // 模拟异常场景，让第一次调用失败
        doThrow(new RuntimeException("Decomposition failed"))
                .when(decompositionService).decompose("failing query");

        String result2 = tools.decomposeQuery("failing query");
        // 第二次相同调用应触发警告
        String result3 = tools.decomposeQuery("failing query");
        assertTrue(result3.contains("系统警告"), "连续重复调用应触发警告");
    }

    @Test
    void testSearchKnowledgeBase_RespectsMaxToolCalls() throws Exception {
        // Arrange
        when(retrievalService.hybridSearch(anyString(), anyList())).thenReturn(Collections.emptyList());

        RagAgentTools tools = new RagAgentTools(retrievalService, decompositionService, sink, documentIds, objectMapper, chatModel, executor);

        // Act - 调用 5 次达到上限
        for (int i = 0; i < 5; i++) {
            tools.searchKnowledgeBase("query " + i);
        }

        // 第 6 次调用应被拒绝
        String result = tools.searchKnowledgeBase("query 6");
        assertTrue(result.contains("工具调用次数已达上限"));
    }

    /**
     * 创建模拟 Chunk 列表
     */
    private List<Chunk> createMockChunks(int count) {
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Chunk chunk = new Chunk();
            chunk.setId(UUID.randomUUID());
            chunk.setDocumentId(UUID.randomUUID());
            chunk.setContent("测试内容 " + i);
            chunk.setScore(0.9 - i * 0.1);
            chunk.setSourceMeta("{\"source\": \"test.pdf\", \"page\": " + (i + 1) + "}");
            chunks.add(chunk);
        }
        return chunks;
    }
}
