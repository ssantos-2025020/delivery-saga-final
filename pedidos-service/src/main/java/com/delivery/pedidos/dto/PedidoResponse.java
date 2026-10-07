package com.delivery.pedidos.dto;

import com.delivery.common.enums.EstadoPedido;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoResponse {

    private Long id;
    private Long clienteId;
    private String clienteNombre;
    private Long repartidorId;
    private String repartidorNombre;
    private EstadoPedido estado;
    private LocalDateTime fechaPedido;
    private BigDecimal costoEnvio;
    private BigDecimal montoTotal;
    private List<DetallePedidoResponse> productos;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetallePedidoResponse {
        private Long productoId;
        private String productoNombre;
        private Integer cantidad;
        private BigDecimal precioUnitario;
        private BigDecimal subtotal;
    }
}
