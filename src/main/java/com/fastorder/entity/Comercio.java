package com.fastorder.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "comercios", indexes = {
        @Index(name = "idx_comercio_abierto", columnList = "abierto"),
        @Index(name = "idx_comercio_categoria", columnList = "categoria")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comercio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Categoria categoria;

    private String direccion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean abierto = true;
}
