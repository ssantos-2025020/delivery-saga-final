package com.delivery.pedidos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PedidoRequest {
    
    @NotEmpty(message = "El pedido debe tener al menos un item")
    @Size(max = 50, message = "El pedido no puede tener más de 50 items")
    @Valid
    private List<ItemPedidoRequest> items;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemPedidoRequest {
        @jakarta.validation.constraints.NotNull(message = "El productoId es requerido")
        private Long productoId;
        
        @jakarta.validation.constraints.NotNull(message = "La cantidad es requerida")
        @jakarta.validation.constraints.Min(value = 1, message = "La cantidad debe ser al menos 1")
        @jakarta.validation.constraints.Max(value = 100, message = "La cantidad no puede exceder 100")
        private Integer cantidad;
    }
}
