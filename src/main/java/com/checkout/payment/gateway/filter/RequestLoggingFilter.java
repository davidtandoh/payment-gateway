package com.checkout.payment.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that captures the SRE golden signals for every HTTP request:
 *
 * <ul>
 *   <li><b>Latency</b> — duration in milliseconds</li>
 *   <li><b>Traffic</b> — each request is logged with method + path</li>
 *   <li><b>Errors</b> — HTTP status code logged (4xx/5xx identifiable in aggregators)</li>
 * </ul>
 *
 * <p>A unique correlation ID is set in the SLF4J MDC for the lifetime of each request,
 * enabling end-to-end tracing across all log lines. The MDC is always cleared in a
 * {@code finally} block to prevent leaking context between requests on the same thread.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String correlationId = UUID.randomUUID().toString();
    MDC.put("correlationId", correlationId);
    MDC.put("httpMethod", request.getMethod());
    MDC.put("httpPath", request.getRequestURI());

    long startTime = System.currentTimeMillis();
    try {
      filterChain.doFilter(request, response);
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      MDC.put("httpStatus", String.valueOf(response.getStatus()));
      MDC.put("durationMs", String.valueOf(duration));

      LOG.info("{} {} {} {}ms",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          duration);

      MDC.clear();
    }
  }
}
