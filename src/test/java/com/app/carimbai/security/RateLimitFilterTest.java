package com.app.carimbai.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * FIX-04 / SEC-006 — rate limit por IP/endpoint. O bucket é por (regra, IP);
 * IPs diferentes têm buckets separados; método/path fora das regras passam.
 */
class RateLimitFilterTest {

    @Test
    void exceedingLoginBucket_returns429_andStopsChain() throws Exception {
        var filter = new RateLimitFilter();
        var chain = mock(FilterChain.class);

        // Bucket de /api/auth/login é 10/min — 10 tentativas passam, a 11ª trava.
        for (int i = 0; i < 10; i++) {
            var req = req("POST", "/api/auth/login", "10.0.0.1");
            var res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        var res11 = new MockHttpServletResponse();
        filter.doFilter(req("POST", "/api/auth/login", "10.0.0.1"), res11, chain);
        assertThat(res11.getStatus()).isEqualTo(429);
        assertThat(res11.getHeader("Retry-After")).isNotNull();

        verify(chain, times(10)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void differentIps_haveSeparateBuckets() throws Exception {
        var filter = new RateLimitFilter();
        var chain = mock(FilterChain.class);

        for (int i = 0; i < 10; i++) {
            filter.doFilter(req("POST", "/api/auth/login", "10.0.0.1"),
                    new MockHttpServletResponse(), chain);
        }
        // Outro IP ainda tem cota fresca.
        var res = new MockHttpServletResponse();
        filter.doFilter(req("POST", "/api/auth/login", "10.0.0.2"), res, chain);
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void unmatchedEndpoint_passesThrough() throws Exception {
        var filter = new RateLimitFilter();
        var chain = mock(FilterChain.class);

        for (int i = 0; i < 50; i++) {
            var res = new MockHttpServletResponse();
            filter.doFilter(req("GET", "/api/health", "10.0.0.1"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    private static MockHttpServletRequest req(String method, String path, String ip) {
        var r = new MockHttpServletRequest(method, path);
        r.setRequestURI(path);
        r.setRemoteAddr(ip);
        return r;
    }
}
