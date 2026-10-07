package com.delivery.auth.service;

import com.delivery.auth.dto.AuthResponse;
import com.delivery.auth.dto.LoginRequest;
import com.delivery.auth.dto.RegisterRequest;
import com.delivery.auth.model.Usuario;
import com.delivery.auth.repository.UsuarioRepository;
import com.delivery.auth.security.CustomUserDetails;
import com.delivery.auth.security.JwtTokenProvider;
import com.delivery.common.enums.Rol;
import com.delivery.common.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse registrar(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.getEmail())) {
            Usuario existente = usuarioRepository.findByEmail(request.getEmail()).orElse(null);
            if (existente != null && passwordEncoder.matches(request.getPassword(), existente.getPassword())) {
                return toAuthResponse(existente);
            }
            throw new ConflictException("El email ya esta registrado");
        }

        Usuario usuario = Usuario.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nombre(request.getNombre())
                .direccion(request.getDireccion())
                .telefono(request.getTelefono())
                .rol(Rol.CLIENTE)
                .activo(true)
                .fechaRegistro(LocalDateTime.now())
                .build();

        try {
            usuario = usuarioRepository.save(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("El email ya esta registrado");
        }

        return toAuthResponse(usuario);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        return AuthResponse.builder()
                .token(tokenProvider.generateToken(user.getUserId(), user.getEmail(), user.getNombre(), user.getRol()))
                .tipo("Bearer")
                .userId(user.getUserId())
                .email(user.getEmail())
                .rol(user.getRol().name())
                .build();
    }

    private AuthResponse toAuthResponse(Usuario usuario) {
        return AuthResponse.builder()
                .token(tokenProvider.generateToken(usuario.getId(), usuario.getEmail(),
                        usuario.getNombre(), usuario.getRol()))
                .tipo("Bearer")
                .userId(usuario.getId())
                .email(usuario.getEmail())
                .rol(usuario.getRol().name())
                .build();
    }
}
