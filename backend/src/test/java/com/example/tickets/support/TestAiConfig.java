package com.example.tickets.support;

import org.mockito.Mockito;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestAiConfig {

    @Bean
    @Primary
    public FakeEmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }

    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return Mockito.mock(ChatModel.class);
    }
}
