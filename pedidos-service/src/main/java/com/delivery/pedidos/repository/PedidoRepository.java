package com.delivery.pedidos.repository;

import com.delivery.common.enums.EstadoPedido;
import com.delivery.pedidos.model.Pedido;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Pedido p LEFT JOIN FETCH p.detalles WHERE p.id = :id")
    Optional<Pedido> findByIdConDetallesParaActualizar(@Param("id") Long id);

    @Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.detalles "
            + "WHERE p.clienteId = :clienteId")
    List<Pedido> findByClienteId(@Param("clienteId") Long clienteId);

    @Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.detalles "
            + "WHERE p.estado IN :estados ORDER BY p.fechaPedido ASC")
    List<Pedido> findByEstadoIn(@Param("estados") Collection<EstadoPedido> estados);
}
