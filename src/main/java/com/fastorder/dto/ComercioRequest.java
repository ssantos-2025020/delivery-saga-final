package com.fastorder.dto;

import com.fastorder.entity.Categoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComercioRequest {

    @NotBlank(message = "El nombre del comercio es obligatorio")
    private String nombre;

    @NotNull(message = "La categoría es obligatoria")
    private Categoria categoria;

    private String direccion;

    @NotNull(message = "El estado abierto/cerrado es obligatorio")
    private Boolean abierto;
}
