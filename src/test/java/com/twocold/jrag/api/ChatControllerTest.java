package com.twocold.jrag.api;

import com.twocold.jrag.api.dto.ChatRequest;
import com.twocold.jrag.config.JwtUtil;
import com.twocold.jrag.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({com.twocold.jrag.config.JwtInterceptor.class, com.twocold.jrag.config.UserArgumentResolver.class})
public class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateToken(anyString())).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(anyString())).thenReturn(1L);
    }

    @Test
    public void testStreamChatReturnsSseContentType() throws Exception {
        String requestJson = "{\"message\":\"Hello\", \"useDeepThinking\":true}";
        
        // Mock ChatService to return a Flux
        Flux<ServerSentEvent<String>> flux = Flux.just(
            ServerSentEvent.<String>builder().event("thought").data("Thinking...").build(),
            ServerSentEvent.<String>builder().event("message").data("Hello").build()
        );
        when(chatService.streamChat(anyLong(), anyLong(), anyString(), anyBoolean())).thenReturn(flux);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/chat/1/stream")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    public void testStreamChatHandlesErrorsGracefully() throws Exception {
        String requestJson = "{\"message\":\"Error\", \"useDeepThinking\":true}";

        // Mock ChatService to return a Flux that emits an error event
        Flux<ServerSentEvent<String>> errorFlux = Flux.just(
                ServerSentEvent.<String>builder().event("error").data("Something went wrong").build()
        );
        when(chatService.streamChat(anyLong(), anyLong(), anyString(), anyBoolean())).thenReturn(errorFlux);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/chat/1/stream")
                .header("Authorization", "Bearer mock-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk()) // Still 200 OK because it's an SSE stream
                .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data:Something went wrong")));
    }
}