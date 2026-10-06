package com.fastorder.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos", indexes = {
        @Index(name = "idx_pedido_cliente", columnList = "cliente_id"),
        @Index(name = "idx_pedido_estado", columnList = "estado")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repartidor_id")
    private Usuario repartidor;

    @Column(nullable = false)
    private LocalDateTime fechaPedido;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costoEnvio;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DetallePedido> detalles = new ArrayList<>();
}
