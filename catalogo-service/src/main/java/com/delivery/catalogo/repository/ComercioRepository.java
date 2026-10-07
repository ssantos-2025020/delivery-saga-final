package com.delivery.catalogo.repository;

import com.delivery.catalogo.model.Comercio;
import com.delivery.common.enums.CategoriaComercio;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComercioRepository extends JpaRepository<Comercio, Long> {

    @Query("SELECT c FROM Comercio c WHERE c.abierto = true ORDER BY c.nombre ASC")
    List<Comercio> findComerciosAbiertos(Pageable pageable);

    @Query("SELECT c FROM Comercio c WHERE c.categoria = :categoria AND c.abierto = true ORDER BY c.nombre ASC")
    List<Comercio> findByCategoriaAndAbierto(@Param("categoria") CategoriaComercio categoria, Pageable pageable);
}
