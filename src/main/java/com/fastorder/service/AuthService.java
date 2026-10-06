package com.fastorder.service;

import com.fastorder.dto.LoginRequest;
import com.fastorder.dto.LoginResponse;
import com.fastorder.dto.RegisterRequest;
import com.fastorder.dto.UsuarioResponse;
import com.fastorder.entity.Rol;
import com.fastorder.entity.Usuario;
import com.fastorder.exception.EmailAlreadyExistsException;
import com.fastorder.repository.UsuarioRepository;
import com.fastorder.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public UsuarioResponse register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        if (usuarioRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("El email ya está registrado: " + email);
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre().trim())
                .direccion(request.getDireccion())
                .telefono(request.getTelefono())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .rol(Rol.CLIENTE) // El rol siempre es CLIENTE y nunca proviene del body
                .build();

        Usuario guardado = usuarioRepository.save(usuario);

        return UsuarioResponse.builder()
                .id(guardado.getId())
                .nombre(guardado.getNombre())
                .direccion(guardado.getDireccion())
                .telefono(guardado.getTelefono())
                .email(guardado.getEmail())
                .rol(guardado.getRol())
                .build();
    }

    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        String token = jwtService.generateToken(usuario.getEmail(), usuario.getRol());

        return LoginResponse.builder()
                .token(token)
                .accessToken(token)
                .tipo("Bearer")
                .rol(usuario.getRol().name())
                .email(usuario.getEmail())
                .build();
    }
}
