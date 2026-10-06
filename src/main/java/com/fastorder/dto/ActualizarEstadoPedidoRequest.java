package com.fastorder.dto;

import com.fastorder.entity.EstadoPedido;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActualizarEstadoPedidoRequest {

    @NotNull(message = "El nuevo estado es obligatorio")
    private EstadoPedido estado;
}
