package com.nexacart.security;

import com.nexacart.config.NexaCartProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    @Test
    void blocksRequestsBeyondTheConfiguredWindowLimit() throws Exception {
        NexaCartProperties properties = new NexaCartProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setMaxAttempts(2);
        properties.getRateLimit().setWindowSeconds(60);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties);

        assertThat(execute(filter).getStatus()).isEqualTo(200);
        assertThat(execute(filter).getStatus()).isEqualTo(200);

        MockHttpServletResponse blocked = execute(filter);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void ignoresUnrelatedEndpoints() throws Exception {
        NexaCartProperties properties = new NexaCartProperties();
        properties.getRateLimit().setEnabled(true);
        properties.getRateLimit().setMaxAttempts(1);
        properties.getRateLimit().setWindowSeconds(60);
        AuthRateLimitFilter filter = new AuthRateLimitFilter(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse execute(AuthRateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
