package com.delivery.catalogo.controller;

import com.delivery.catalogo.service.CatalogoService;
import com.delivery.common.dto.StockReservaRequest;
import com.delivery.common.dto.StockReservaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/stock")
@RequiredArgsConstructor
public class InternalStockController {

    private final CatalogoService catalogoService;

    @Value("${internal.api-key:internal-super-key-2024}")
    private String internalApiKey;

    private boolean isAuthorized(String apiKey) {
        return apiKey != null && apiKey.equals(internalApiKey);
    }

    @PostMapping("/reservar")
    public ResponseEntity<StockReservaResponse> reservarStock(
            @RequestHeader(value = "X-Internal-API-Key", required = false) String apiKey,
            @RequestBody StockReservaRequest request) {
        if (!isAuthorized(apiKey)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(catalogoService.reservarStock(request));
    }

    @PostMapping("/liberar")
    public ResponseEntity<Void> liberarStock(
            @RequestHeader(value = "X-Internal-API-Key", required = false) String apiKey,
            @RequestParam String reservaId) {
        if (!isAuthorized(apiKey)) {
            return ResponseEntity.status(403).build();
        }
        catalogoService.liberarStock(reservaId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/confirmar")
    public ResponseEntity<Void> confirmarReserva(
            @RequestHeader(value = "X-Internal-API-Key", required = false) String apiKey,
            @RequestParam String reservaId) {
        if (!isAuthorized(apiKey)) {
            return ResponseEntity.status(403).build();
        }
        catalogoService.confirmarReserva(reservaId);
        return ResponseEntity.ok().build();
    }
}
