package com.delivery.auth;

import com.delivery.auth.model.Usuario;
import com.delivery.auth.repository.UsuarioRepository;
import com.delivery.auth.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtTokenProvider tokenProvider;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    protected String tokenDe(String email) {
        Optional<Usuario> usuario = usuarioRepository.findByEmail(email);
        if (usuario.isEmpty()) {
            throw new IllegalStateException("Usuario no sembrado: " + email);
        }
        Usuario u = usuario.get();
        return tokenProvider.generateToken(u.getId(), u.getEmail(), u.getNombre(), u.getRol());
    }

    protected String adminToken() {
        return tokenDe("admin@fastorder.com");
    }

    protected String clienteToken() {
        return tokenDe("cliente@fastorder.com");
    }

    protected String repartidorToken() {
        return tokenDe("repartidor@fastorder.com");
    }
}
