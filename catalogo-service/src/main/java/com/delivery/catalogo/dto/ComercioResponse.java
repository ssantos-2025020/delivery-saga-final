package com.delivery.catalogo.dto;

import com.delivery.common.enums.CategoriaComercio;
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
public class ComercioResponse {

    private Long id;
    private String nombre;
    private CategoriaComercio categoria;
    private String direccion;
    private Boolean abierto;
}
