package com.delivery.pedidos.controller;

import com.delivery.common.exception.InvalidStatusException;
import com.delivery.pedidos.dto.CambioEstadoRequest;
import com.delivery.pedidos.dto.PedidoRequest;
import com.delivery.pedidos.dto.PedidoResponse;
import com.delivery.pedidos.security.SecurityUtils;
import com.delivery.pedidos.service.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(@Valid @RequestBody PedidoRequest request) {
        Long clienteId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new InvalidStatusException("Autenticacion requerida"));
        String clienteNombre = SecurityUtils.currentUserName().orElse(null);
        return ResponseEntity.ok(pedidoService.crearPedido(clienteId, clienteNombre, request));
    }

    @GetMapping("/mis-pedidos")
    public ResponseEntity<List<PedidoResponse>> misPedidos() {
        Long clienteId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new InvalidStatusException("Autenticacion requerida"));
        return ResponseEntity.ok(pedidoService.misPedidos(clienteId));
    }

    @GetMapping("/disponibles")
    public ResponseEntity<List<PedidoResponse>> disponibles() {
        return ResponseEntity.ok(pedidoService.disponibles());
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<PedidoResponse> cambiarEstado(@PathVariable Long id,
                                                        @Valid @RequestBody CambioEstadoRequest request) {
        return ResponseEntity.ok(pedidoService.cambiarEstado(id, request));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponse> cancelarPost(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.cancelarPedido(id));
    }

    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponse> cancelar(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.cancelarPedido(id));
    }
}
