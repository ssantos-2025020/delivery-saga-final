package com.fastorder.controller;

import com.fastorder.dto.ComercioRequest;
import com.fastorder.dto.ComercioResponse;
import com.fastorder.dto.ProductoRequest;
import com.fastorder.dto.ProductoResponse;
import com.fastorder.entity.Categoria;
import com.fastorder.service.ComercioService;
import com.fastorder.service.ProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/comercios")
@RequiredArgsConstructor
public class ComercioController {

    private final ComercioService comercioService;
    private final ProductoService productoService;

    @GetMapping
    public ResponseEntity<List<ComercioResponse>> listarComercios(
            @RequestParam(required = false) Categoria categoria) {
        return ResponseEntity.ok(comercioService.listarComercios(categoria));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComercioResponse> crearComercio(
            @Valid @RequestBody ComercioRequest request) {
        ComercioResponse response = comercioService.crearComercio(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/productos")
    public ResponseEntity<List<ProductoResponse>> listarProductos(
            @PathVariable Long id) {
        return ResponseEntity.ok(productoService.listarProductosDisponibles(id));
    }

    @PostMapping("/{id}/productos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductoResponse> crearProducto(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequest request) {
        ProductoResponse response = productoService.crearProducto(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
