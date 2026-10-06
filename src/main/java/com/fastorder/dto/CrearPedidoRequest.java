package com.fastorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrearPedidoRequest {

    @NotEmpty(message = "El pedido debe contener al menos un item")
    @Valid
    private List<CrearPedidoItemRequest> items;
}
