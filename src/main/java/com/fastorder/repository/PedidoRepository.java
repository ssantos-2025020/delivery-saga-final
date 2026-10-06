package com.fastorder.repository;

import com.fastorder.entity.EstadoPedido;
import com.fastorder.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByClienteIdOrderByFechaPedidoDesc(Long clienteId);

    List<Pedido> findByEstadoNotInOrderByFechaPedidoDesc(Collection<EstadoPedido> estados);

    List<Pedido> findByEstadoAndRepartidorIsNullOrderByFechaPedidoAsc(EstadoPedido estado);

    @Query("SELECT p FROM Pedido p LEFT JOIN FETCH p.detalles d LEFT JOIN FETCH d.producto WHERE p.id = :id")
    java.util.Optional<Pedido> findByIdWithDetalles(@Param("id") Long id);
}
