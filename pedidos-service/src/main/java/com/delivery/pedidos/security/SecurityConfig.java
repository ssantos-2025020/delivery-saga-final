package com.delivery.pedidos.security;

import com.delivery.common.enums.Rol;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthFilter headerAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(json(401, "Unauthorized",
                            "Token de autorizacion requerido o invalido", request.getRequestURI()));
                })
                .accessDeniedHandler((request, response, deniedException) -> {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(json(403, "Forbidden",
                            "No tiene permisos para esta operacion", request.getRequestURI()));
                }))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/pedidos").hasRole(Rol.CLIENTE.name())
                .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/mis-pedidos").hasRole(Rol.CLIENTE.name())
                .requestMatchers(HttpMethod.GET, "/api/v1/pedidos/disponibles")
                        .hasAnyRole(Rol.REPARTIDOR.name(), Rol.ADMIN.name())
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/estado")
                        .hasAnyRole(Rol.REPARTIDOR.name(), Rol.ADMIN.name())
                .requestMatchers(HttpMethod.POST, "/api/v1/pedidos/*/cancelar")
                        .hasAnyRole(Rol.CLIENTE.name(), Rol.ADMIN.name())
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/cancelar")
                        .hasAnyRole(Rol.CLIENTE.name(), Rol.ADMIN.name())
                .anyRequest().authenticated())
            .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private String json(int status, String error, String message, String path) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return String.format(
                "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                timestamp, status, error, message, path);
    }
}
