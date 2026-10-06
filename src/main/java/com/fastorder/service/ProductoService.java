package com.fastorder.service;

import com.fastorder.dto.ProductoRequest;
import com.fastorder.dto.ProductoResponse;
import com.fastorder.entity.Comercio;
import com.fastorder.entity.Producto;
import com.fastorder.exception.ResourceNotFoundException;
import com.fastorder.repository.ComercioRepository;
import com.fastorder.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ComercioRepository comercioRepository;

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarProductosDisponibles(Long comercioId) {
        if (!comercioRepository.existsById(comercioId)) {
            throw new ResourceNotFoundException("Comercio no encontrado con ID: " + comercioId);
        }

        return productoRepository.findByComercioIdAndDisponibleTrue(comercioId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductoResponse crearProducto(Long comercioId, ProductoRequest request) {
        Comercio comercio = comercioRepository.findById(comercioId)
                .orElseThrow(() -> new ResourceNotFoundException("Comercio no encontrado con ID: " + comercioId));

        Producto producto = Producto.builder()
                .comercio(comercio)
                .nombre(request.getNombre().trim())
                .precio(request.getPrecio())
                .stock(request.getStock())
                .disponible(request.getDisponible())
                .build();

        Producto guardado = productoRepository.save(producto);
        return mapToResponse(guardado);
    }

    public ProductoResponse mapToResponse(Producto producto) {
        return ProductoResponse.builder()
                .id(producto.getId())
                .comercioId(producto.getComercio() != null ? producto.getComercio().getId() : null)
                .nombre(producto.getNombre())
                .precio(producto.getPrecio())
                .stock(producto.getStock())
                .disponible(producto.getDisponible())
                .build();
    }
}
