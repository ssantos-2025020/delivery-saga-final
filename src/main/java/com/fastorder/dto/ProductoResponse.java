package com.fastorder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoResponse {

    private Long id;
    private Long comercioId;
    private String nombre;
    private BigDecimal precio;
    private Integer stock;
    private Boolean disponible;
}
