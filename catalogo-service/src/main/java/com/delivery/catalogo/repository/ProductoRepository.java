package com.delivery.catalogo.repository;

import com.delivery.catalogo.model.Producto;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
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
public interface ProductoRepository extends JpaRepository<Producto, Long> {

    @Query("SELECT p FROM Producto p WHERE p.comercio.id = :comercioId ORDER BY p.nombre ASC")
    List<Producto> findByComercioId(@Param("comercioId") Long comercioId, Pageable pageable);

    @Query("SELECT p FROM Producto p WHERE p.comercio.id = :comercioId AND p.disponible = true ORDER BY p.nombre ASC")
    List<Producto> findByComercioIdDisponibles(@Param("comercioId") Long comercioId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Producto p WHERE p.id IN :ids ORDER BY p.id ASC")
    List<Producto> findByIdsWithLock(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> findByIdWithLock(@Param("id") Long id);
}
