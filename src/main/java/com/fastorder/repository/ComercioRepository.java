package com.fastorder.repository;

import com.fastorder.entity.Categoria;
import com.fastorder.entity.Comercio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComercioRepository extends JpaRepository<Comercio, Long> {

    List<Comercio> findByAbiertoTrue();

    List<Comercio> findByAbiertoTrueAndCategoria(Categoria categoria);
}
