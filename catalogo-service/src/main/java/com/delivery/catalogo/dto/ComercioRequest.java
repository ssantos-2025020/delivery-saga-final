package com.delivery.catalogo.dto;

import com.delivery.common.enums.CategoriaComercio;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ComercioRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombre;

    @NotNull(message = "La categoria es obligatoria")
    private CategoriaComercio categoria;

    @NotBlank(message = "La direccion es obligatoria")
    @Size(max = 100)
    private String direccion;

    private Boolean abierto;
}
