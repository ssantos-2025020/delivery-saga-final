package com.delivery.pedidos.security;

import java.util.Objects;

public record AuthenticatedUser(Long userId, String nombre) {

    public static AuthenticatedUser fromHeaders(String userIdHeader, String nombre) {
        Long userId = null;
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                userId = Long.valueOf(userIdHeader.trim());
            } catch (NumberFormatException ignored) {
                userId = null;
            }
        }
        return new AuthenticatedUser(userId, nombre == null ? null : nombre.trim());
    }
}
