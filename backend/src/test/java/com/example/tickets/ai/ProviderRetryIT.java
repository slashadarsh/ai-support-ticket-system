package com.example.tickets.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.tickets.support.AbstractIntegrationTest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;

/**
 * Regression test for M-5: the retry policy used for OpenAI calls must be bounded, because re-indexing runs
 * inside the user's request. Spring AI's default (10 attempts, backoff up to 180 s) would block for ~19 minutes.
 */
class ProviderRetryIT extends AbstractIntegrationTest {

    @Autowired RetryTemplate retryTemplate;

    @Test
    void providerCallsGiveUpQuickly() {
        AtomicInteger attempts = new AtomicInteger();
        long started = System.currentTimeMillis();

        assertThatThrownBy(() -> retryTemplate.execute(ctx -> {
            attempts.incrementAndGet();
            throw new TransientAiException("provider down");
        })).isInstanceOf(TransientAiException.class);

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(System.currentTimeMillis() - started).isLessThan(5_000);
    }
}
