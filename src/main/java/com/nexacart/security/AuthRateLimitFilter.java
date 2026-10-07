package com.nexacart.security;

import com.nexacart.config.NexaCartProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final NexaCartProperties properties;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(NexaCartProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        int maxAttempts = properties.getRateLimit().getMaxAttempts();
        long windowSeconds = properties.getRateLimit().getWindowSeconds();
        long now = Instant.now().getEpochSecond();
        String key = request.getRemoteAddr() + ":" + request.getRequestURI();

        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.windowStart >= windowSeconds) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= windowSeconds);
        }

        if (window.count.get() > maxAttempts) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            String path = request.getRequestURI().replace("\\", "\\\\").replace("\"", "\\\"");
            response.getWriter().write("""
                    {"timestamp":"%s","status":429,"error":"Too Many Requests","code":"RATE_LIMIT_EXCEEDED","message":"Too many authentication attempts. Try again later.","path":"%s","fieldErrors":null}
                    """.formatted(Instant.now(), path));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static final class Window {
        private final long windowStart;
        private final AtomicInteger count;

        private Window(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
