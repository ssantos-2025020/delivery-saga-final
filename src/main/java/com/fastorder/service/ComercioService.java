package com.fastorder.service;

import com.fastorder.dto.ComercioRequest;
import com.fastorder.dto.ComercioResponse;
import com.fastorder.entity.Categoria;
import com.fastorder.entity.Comercio;
import com.fastorder.exception.ResourceNotFoundException;
import com.fastorder.repository.ComercioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComercioService {

    private final ComercioRepository comercioRepository;

    @Transactional(readOnly = true)
    public List<ComercioResponse> listarComercios(Categoria categoria) {
        List<Comercio> comercios;
        if (categoria != null) {
            comercios = comercioRepository.findByAbiertoTrueAndCategoria(categoria);
        } else {
            comercios = comercioRepository.findByAbiertoTrue();
        }

        return comercios.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ComercioResponse crearComercio(ComercioRequest request) {
        Comercio comercio = Comercio.builder()
                .nombre(request.getNombre().trim())
                .categoria(request.getCategoria())
                .direccion(request.getDireccion())
                .abierto(request.getAbierto())
                .build();

        Comercio guardado = comercioRepository.save(comercio);
        return mapToResponse(guardado);
    }

    @Transactional(readOnly = true)
    public Comercio obtenerPorId(Long id) {
        return comercioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio no encontrado con ID: " + id));
    }

    public ComercioResponse mapToResponse(Comercio comercio) {
        return ComercioResponse.builder()
                .id(comercio.getId())
                .nombre(comercio.getNombre())
                .categoria(comercio.getCategoria())
                .direccion(comercio.getDireccion())
                .abierto(comercio.getAbierto())
                .build();
    }
}
