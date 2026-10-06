package com.delivery.auth.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
public class RateLimitFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    
    @Value("${app.rate-limit.login.max-attempts:5}")
    private int loginMaxAttempts = 5;
    
    @Value("${app.rate-limit.login.window-seconds:60}")
    private int loginWindowSeconds = 60;
    
    @Value("${app.rate-limit.general.max-requests:100}")
    private int generalMaxRequests = 100;
    
    @Value("${app.rate-limit.general.window-seconds:60}")
    private int generalWindowSeconds = 60;
    
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> generalBuckets = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    
    @PostConstruct
    public void init() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::cleanupExpiredBuckets, 1, 1, TimeUnit.MINUTES);
    }
    
    @PreDestroy
    public void destroy() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }
    
    private void cleanupExpiredBuckets() {
        loginBuckets.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= loginMaxAttempts);
        generalBuckets.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= generalMaxRequests);
    }
    
    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.classic(loginMaxAttempts, 
            Refill.greedy(loginMaxAttempts, Duration.ofSeconds(loginWindowSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }
    
    private Bucket createGeneralBucket() {
        Bandwidth limit = Bandwidth.classic(generalMaxRequests, 
            Refill.greedy(generalMaxRequests, Duration.ofSeconds(generalWindowSeconds)));
        return Bucket4j.builder().addLimit(limit).build();
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();
        String identifier = getClientIdentifier(httpRequest);
        
        Bucket bucket;
        if (path.contains("/login")) {
            bucket = loginBuckets.computeIfAbsent(identifier, k -> createLoginBucket());
        } else {
            bucket = generalBuckets.computeIfAbsent(identifier, k -> createGeneralBucket());
        }
        
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            logger.warn("Rate limit excedido para {} en {}", identifier, path);
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.setHeader("Retry-After", String.valueOf(loginWindowSeconds));
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String body = String.format(
                    "{\"timestamp\":\"%s\",\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Demasiadas solicitudes. Intente más tarde.\",\"path\":\"%s\"}",
                    timestamp, path
            );
            httpResponse.getWriter().write(body);
        }
    }
    
    private String getClientIdentifier(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String email = request.getParameter("email");
        if (email != null && !email.isBlank()) {
            return "email:" + email;
        }
        return request.getRemoteAddr();
    }
}
