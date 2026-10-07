package com.delivery.catalogo.controller;

import com.delivery.catalogo.dto.ComercioRequest;
import com.delivery.catalogo.dto.ComercioResponse;
import com.delivery.catalogo.dto.ProductoRequest;
import com.delivery.catalogo.dto.ProductoResponse;
import com.delivery.catalogo.service.CatalogoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/comercios")
@RequiredArgsConstructor
public class ComercioController {

    private final CatalogoService catalogoService;

    @GetMapping
    public ResponseEntity<List<ComercioResponse>> listarComercios(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String categoria) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("nombre").ascending());
        return ResponseEntity.ok(catalogoService.listarComercios(CatalogoService.CategoriaFiltro.of(categoria), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ComercioResponse> obtenerComercio(@PathVariable Long id) {
        return ResponseEntity.ok(catalogoService.obtenerComercio(id));
    }

    @GetMapping("/{id}/productos")
    public ResponseEntity<List<ProductoResponse>> listarProductos(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean soloDisponibles) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50), Sort.by("nombre").ascending());
        return ResponseEntity.ok(catalogoService.listarProductos(id, soloDisponibles, pageable));
    }

    @PostMapping
    public ResponseEntity<ComercioResponse> crearComercio(@Valid @RequestBody ComercioRequest request) {
        return ResponseEntity.ok(catalogoService.crearComercio(request));
    }

    @PostMapping("/{id}/productos")
    public ResponseEntity<ProductoResponse> crearProducto(@PathVariable Long id,
                                                          @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(catalogoService.crearProducto(id, request));
    }
}
