package com.delivery.auth.config;

import com.delivery.auth.model.Usuario;
import com.delivery.auth.repository.UsuarioRepository;
import com.delivery.common.enums.Rol;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("admin@fastorder.com", "Admin123*", Rol.ADMIN, "Administrador", "Zona 1", "5555-0001");
        seed("repartidor@fastorder.com", "Repartidor123*", Rol.REPARTIDOR, "Repartidor Uno", "Zona 2", "5555-0002");
        seed("cliente@fastorder.com", "Cliente123*", Rol.CLIENTE, "Cliente Uno", "Zona 3", "5555-0003");
    }

    private void seed(String email, String password, Rol rol, String nombre,
                      String direccion, String telefono) {
        if (!usuarioRepository.existsByEmail(email)) {
            usuarioRepository.save(Usuario.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .rol(rol)
                    .nombre(nombre)
                    .direccion(direccion)
                    .telefono(telefono)
                    .activo(true)
                    .fechaRegistro(LocalDateTime.now())
                    .build());
        }
    }
}
