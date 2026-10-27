package com.app.carimbai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JsonRequestSizeFilterTest {
    @Test
    void acceptsAtLimitAndPreservesBody() throws Exception {
        var filter = new JsonRequestSizeFilter(10);
        var request = json("0123456789", false);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(req.getInputStream().readAllBytes()).isEqualTo("0123456789".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsOversizedDeclaredBodyBeforeController() throws Exception {
        var chain = mock(FilterChain.class);
        var response = new MockHttpServletResponse();
        new JsonRequestSizeFilter(10).doFilter(json("01234567890", false), response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsOversizedChunkedBodyWithoutContentLength() throws Exception {
        var chain = mock(FilterChain.class);
        var response = new MockHttpServletResponse();
        new JsonRequestSizeFilter(10).doFilter(json("01234567890", true), response, chain);
        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(chain);
    }

    @Test
    void optionsIsNotRead() throws Exception {
        var request = json("01234567890", false);
        request.setMethod("OPTIONS");
        var chain = mock(FilterChain.class);
        new JsonRequestSizeFilter(10).doFilter(request, new MockHttpServletResponse(), chain);
        verify(chain).doFilter(any(), any());
    }

    private MockHttpServletRequest json(String body, boolean unknownLength) {
        var request = new MockHttpServletRequest("POST", "/api/auth/login") {
            @Override public long getContentLengthLong() { return unknownLength ? -1 : super.getContentLengthLong(); }
        };
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
