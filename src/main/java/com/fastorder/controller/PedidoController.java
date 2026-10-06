package com.fastorder.controller;

import com.fastorder.dto.ActualizarEstadoPedidoRequest;
import com.fastorder.dto.CrearPedidoRequest;
import com.fastorder.dto.PedidoResponse;
import com.fastorder.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<PedidoResponse> crearPedido(
            @Valid @RequestBody CrearPedidoRequest request,
            Authentication authentication) {
        PedidoResponse response = pedidoService.crearPedido(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mis-pedidos")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<List<PedidoResponse>> obtenerMisPedidos(Authentication authentication) {
        return ResponseEntity.ok(pedidoService.obtenerMisPedidos(authentication.getName()));
    }

    @GetMapping("/disponibles")
    @PreAuthorize("hasAnyRole('ADMIN', 'REPARTIDOR')")
    public ResponseEntity<List<PedidoResponse>> obtenerPedidosDisponibles(Authentication authentication) {
        return ResponseEntity.ok(pedidoService.obtenerPedidosDisponibles(authentication.getName()));
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN', 'REPARTIDOR')")
    public ResponseEntity<PedidoResponse> actualizarEstadoPedido(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarEstadoPedidoRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(pedidoService.cambiarEstadoPedido(id, request.getEstado(), authentication.getName()));
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN')")
    public ResponseEntity<PedidoResponse> cancelarPedido(
            @PathVariable Long id,
            Authentication authentication) {
        return ResponseEntity.ok(pedidoService.cancelarPedido(id, authentication.getName()));
    }
}
