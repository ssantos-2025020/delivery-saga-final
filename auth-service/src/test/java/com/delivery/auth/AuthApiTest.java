package com.delivery.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiTestBase {

    @Test
    void registrarDevuelve200ConToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Ana Torres",
                                  "email": "ana@correo.com",
                                  "password": "Segura123*",
                                  "direccion": "Av. 102, Zona 12",
                                  "telefono": "5555-1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value("CLIENTE"));
    }

    @Test
    void registrarEmailDuplicadoDevuelve409() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Duplicado",
                                  "email": "admin@fastorder.com",
                                  "password": "OtroPass1*",
                                  "direccion": "Zona 1",
                                  "telefono": "5555-0001"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void registraMismaPasswordIdempotenteDevuelve200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Antonio",
                                  "email": "antonio@correo.com",
                                  "password": "Segura123*"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombre": "Antonio",
                                  "email": "antonio@correo.com",
                                  "password": "Segura123*"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void registrarConCamposObligatoriosVaciosDevuelve400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x\",\"password\":\"\",\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginCredencialesValidasDevuelveToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "admin@fastorder.com", "password": "Admin123*"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void loginCredencialesInvalidasDevuelve401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "admin@fastorder.com", "password": "Incorrecta"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accesoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/comercios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$", hasKey("message")));
    }

    @Test
    void tokenInvalidoDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/comercios")
                        .header("Authorization", "Bearer token.falso.invalido"))
                .andExpect(status().isUnauthorized());
    }
}
