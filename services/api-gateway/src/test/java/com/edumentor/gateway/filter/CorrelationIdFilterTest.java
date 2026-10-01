package com.edumentor.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final GatewayFilterChain chain = mock(GatewayFilterChain.class);

    @BeforeEach
    void setUp() {
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void generatesIdWhenHeaderIsMissing() {
        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/x").build()), chain).block();

        assertThat(forwardedId()).isNotBlank().hasSize(36);
    }

    @Test
    void keepsWellFormedIncomingId() {
        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/x")
                .header("X-Correlation-ID", "req-123_ABC.4").build()), chain).block();

        assertThat(forwardedId()).isEqualTo("req-123_ABC.4");
    }

    @Test
    void replacesMalformedIncomingId() {
        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.get("/api/x")
                .header("X-Correlation-ID", "bad id with spaces!").build()), chain).block();

        assertThat(forwardedId()).isNotEqualTo("bad id with spaces!").hasSize(36);
    }

    private String forwardedId() {
        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(captor.capture());
        return captor.getValue().getRequest().getHeaders().getFirst("X-Correlation-ID");
    }
}