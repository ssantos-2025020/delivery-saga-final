package com.delivery.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class JwtFilter extends AbstractGatewayFilterFactory<JwtFilter.Config> {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    public JwtFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            String method = request.getMethod().name();

            if (esPublico(path, method)) {
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorized(exchange, path, "Token de autorizacion requerido");
            }

            String token = authHeader.substring(7);
            try {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                String rol = claims.get("rol", String.class);
                String nombre = claims.get("nombre", String.class);

                ServerHttpRequest.Builder builder = request.mutate()
                        .header("X-User-Id", userId != null ? userId : "")
                        .header("X-User-Rol", rol != null ? rol : "");
                if (nombre != null && !nombre.isBlank()) {
                    builder.header("X-User-Name", nombre);
                }

                return chain.filter(exchange.mutate().request(builder.build()).build());
            } catch (Exception e) {
                return unauthorized(exchange, path, "Token JWT invalido o expirado");
            }
        };
    }

    private boolean esPublico(String path, String method) {
        if (HttpMethod.OPTIONS.matches(method)) {
            return true;
        }
        if (path.startsWith("/api/v1/auth/") || path.startsWith("/registro")) {
            return true;
        }
        if (HttpMethod.GET.matches(method)
                && (path.equals("/api/v1/comercios") || path.equals("/api/v1/comercios/"))) {
            return true;
        }
        return path.startsWith("/actuator/");
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String path, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String body = String.format(
                "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"path\":\"%s\"}",
                timestamp, message, path);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    public static class Config {}
}
