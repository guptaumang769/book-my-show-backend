package com.umang.bookmyshow.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a correlation id into the SLF4J MDC for the lifetime of every HTTP request.
 *
 * <p>WHY CORRELATION IDS MATTER: a single user action fans out into many log lines across many
 * threads and (in a real system) many services. Without a shared id, those lines are impossible to
 * stitch back together in Kibana/Grafana Loki. A correlation id — read from the {@code
 * X-Correlation-Id} header if a gateway/upstream already assigned one, otherwise generated here —
 * is stamped onto every log line via the MDC and echoed back in the response header so the caller
 * (and downstream services) can reuse it. It is the "logs" pillar's answer to what a traceId is for
 * distributed tracing: one key that ties an entire request together.
 *
 * <p>Runs at HIGHEST_PRECEDENCE so the id is present before any other filter or handler logs, and
 * the MDC is cleared in a finally block so the value never leaks onto the next request served by a
 * pooled thread.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
