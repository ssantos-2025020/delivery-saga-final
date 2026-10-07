package com.delivery.pedidos.dto;

import com.delivery.common.enums.EstadoPedido;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
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
public class CambioEstadoRequest {

    @NotNull(message = "El estado es obligatorio")
    private EstadoPedido estado;
}
