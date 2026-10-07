package com.delivery.pedidos.security;

import com.delivery.common.enums.Rol;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user && user.userId() != null) {
            return Optional.of(user.userId());
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return Optional.of(id);
        }
        try {
            return Optional.of(Long.valueOf(principal.toString()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static Optional<String> currentUserName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.ofNullable(user.nombre());
        }
        return Optional.empty();
    }

    public static Optional<Rol> currentRol() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .map(Rol::valueOf)
                .findFirst();
    }
}
