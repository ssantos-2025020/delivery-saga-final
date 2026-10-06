package com.delivery.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComercioRequest {
    @NotBlank
    private String nombre;
    private String categoria;
    private Boolean abierto = true;
}
