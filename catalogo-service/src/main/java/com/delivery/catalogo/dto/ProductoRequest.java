package com.delivery.catalogo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoRequest {
    @NotBlank
    private String nombre;
    
    @NotNull
    @DecimalMin("0.01")
    private BigDecimal precio;
    
    @NotNull
    @Min(0)
    private Integer stock;
}
