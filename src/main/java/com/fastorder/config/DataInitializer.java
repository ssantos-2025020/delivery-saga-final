package com.fastorder.config;

import com.fastorder.entity.*;
import com.fastorder.repository.ComercioRepository;
import com.fastorder.repository.ProductoRepository;
import com.fastorder.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final ComercioRepository comercioRepository;
    private final ProductoRepository productoRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.cliente:false}")
    private boolean seedCliente;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Iniciando carga de datos iniciales idempotentes...");

        // 1. Usuario ADMIN
        if (!usuarioRepository.existsByEmail("admin@fastorder.com")) {
            Usuario admin = Usuario.builder()
                    .nombre("Administrador FastOrder")
                    .direccion("Oficinas Centrales")
                    .telefono("11112222")
                    .email("admin@fastorder.com")
                    .password(passwordEncoder.encode("Admin123*"))
                    .rol(Rol.ADMIN)
                    .build();
            usuarioRepository.save(admin);
            log.info("Usuario ADMIN creado: admin@fastorder.com");
        }

        // 2. Usuario REPARTIDOR
        if (!usuarioRepository.existsByEmail("repartidor@fastorder.com")) {
            Usuario repartidor = Usuario.builder()
                    .nombre("Repartidor Express")
                    .direccion("Zona 9, Ciudad")
                    .telefono("33334444")
                    .email("repartidor@fastorder.com")
                    .password(passwordEncoder.encode("Repartidor123*"))
                    .rol(Rol.REPARTIDOR)
                    .build();
            usuarioRepository.save(repartidor);
            log.info("Usuario REPARTIDOR creado: repartidor@fastorder.com");
        }

        // 3. Usuario CLIENTE (por defecto se deja libre para que test-api3.sh paso [1] registre exitosamente con 201)
        if (seedCliente && !usuarioRepository.existsByEmail("cliente@fastorder.com")) {
            Usuario cliente = Usuario.builder()
                    .nombre("Cliente Inicial")
                    .direccion("Zona 10, Ciudad")
                    .telefono("55554321")
                    .email("cliente@fastorder.com")
                    .password(passwordEncoder.encode("Cliente123*"))
                    .rol(Rol.CLIENTE)
                    .build();
            usuarioRepository.save(cliente);
            log.info("Usuario CLIENTE creado: cliente@fastorder.com");
        }

        // 4. Comercio inicial y productos (solo si no existen comercios)
        if (comercioRepository.count() == 0) {
            Comercio comercio = Comercio.builder()
                    .nombre("Burger Express Initial")
                    .categoria(Categoria.RESTAURANTE)
                    .direccion("Avenida Las Americas 10-20")
                    .abierto(true)
                    .build();
            comercio = comercioRepository.save(comercio);

            Producto p1 = Producto.builder()
                    .comercio(comercio)
                    .nombre("Hamburguesa Clasica")
                    .precio(new BigDecimal("35.00"))
                    .stock(50)
                    .disponible(true)
                    .build();

            Producto p2 = Producto.builder()
                    .comercio(comercio)
                    .nombre("Papas Fritas Medianas")
                    .precio(new BigDecimal("18.00"))
                    .stock(100)
                    .disponible(true)
                    .build();

            Producto p3 = Producto.builder()
                    .comercio(comercio)
                    .nombre("Bebida Gaseosa 500ml")
                    .precio(new BigDecimal("12.00"))
                    .stock(80)
                    .disponible(true)
                    .build();

            productoRepository.save(p1);
            productoRepository.save(p2);
            productoRepository.save(p3);
            log.info("Comercio y 3 productos iniciales creados.");
        }

        log.info("Carga de datos iniciales completada.");
    }
}
