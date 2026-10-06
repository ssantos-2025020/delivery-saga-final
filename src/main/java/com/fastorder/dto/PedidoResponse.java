package com.fastorder.dto;

import com.fastorder.entity.EstadoPedido;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PedidoResponse {

    private Long id;
    private Long clienteId;
    private String clienteNombre;
    private Long repartidorId;
    private String repartidorNombre;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime fechaPedido;

    private BigDecimal costoEnvio;
    private BigDecimal montoTotal;
    private EstadoPedido estado;

    private List<DetallePedidoResponse> items;
    private List<DetallePedidoResponse> detalles;
}
