package com.delivery.pedidos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PedidoRequest {

    @NotNull(message = "Los productos son obligatorios")
    @NotEmpty(message = "El pedido debe contener al menos un producto")
    @Size(max = 50, message = "Maximo 50 lineas por pedido")
    @Valid
    private List<ItemPedidoRequest> productos;

    @JsonProperty("items")
    public void setItems(List<ItemPedidoRequest> items) {
        if (this.productos == null) {
            this.productos = items;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemPedidoRequest {

        @NotNull(message = "El id del producto es obligatorio")
        private Long productoId;

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad minima es 1")
        @Max(value = 100, message = "La cantidad maxima por producto es 100")
        private Integer cantidad;
    }
}
