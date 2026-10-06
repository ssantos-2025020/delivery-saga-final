package com.fastorder.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato de email es inválido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
