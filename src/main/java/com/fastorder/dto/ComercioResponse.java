package com.fastorder.dto;

import com.fastorder.entity.Categoria;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComercioResponse {

    private Long id;
    private String nombre;
    private Categoria categoria;
    private String direccion;
    private Boolean abierto;
}
