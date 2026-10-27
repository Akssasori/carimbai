package com.app.carimbai.security;

import com.app.carimbai.config.PrivacyProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component @RequiredArgsConstructor
public class RestorationQuarantineFilter extends OncePerRequestFilter {
    private final PrivacyProperties settings;
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws IOException,ServletException {
        if (settings.isRestorationQuarantine() && !request.getRequestURI().equals("/actuator/health")) {
            response.setStatus(503); response.setContentType("application/json");
            response.setHeader("Cache-Control","no-store");
            response.getWriter().write("{\"error\":\"RESTORATION_QUARANTINE\"}");
            return;
        }
        chain.doFilter(request,response);
    }
}
