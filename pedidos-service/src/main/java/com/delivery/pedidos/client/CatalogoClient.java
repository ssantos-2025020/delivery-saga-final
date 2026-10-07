package com.delivery.pedidos.client;

import com.delivery.common.dto.StockReservaRequest;
import com.delivery.common.dto.StockReservaResponse;
import com.delivery.common.exception.ConflictException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CatalogoClient {

    private static final Logger logger = LoggerFactory.getLogger(CatalogoClient.class);

    private final WebClient webClient;

    @Value("${internal.api-key:internal-super-key-2024}")
    private String internalApiKey;

    @CircuitBreaker(name = "catalogoClient", fallbackMethod = "reservarFallback")
    @Retry(name = "catalogoClient")
    public StockReservaResponse reservar(String reservaId, List<StockReservaRequest.StockItemRequest> items) {
        StockReservaRequest request = StockReservaRequest.builder()
                .reservaId(reservaId)
                .items(items)
                .build();
        try {
            StockReservaResponse response = webClient.post()
                    .uri("/internal/stock/reservar")
                    .header("X-Internal-API-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(StockReservaResponse.class)
                    .block();
            if (response == null) {
                throw new IllegalStateException("Respuesta vacia del catalogo");
            }
            return response;
        } catch (Exception ex) {
            throw new ConflictException("No se pudo reservar stock en el catalogo");
        }
    }

    private StockReservaResponse reservarFallback(String reservaId,
                                                  List<StockReservaRequest.StockItemRequest> items,
                                                  Exception ex) {
        throw new ConflictException("No se pudo reservar stock en el catalogo");
    }

    public void confirmar(String reservaId) {
        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/internal/stock/confirmar")
                            .queryParam("reservaId", reservaId)
                            .build())
                    .header("X-Internal-API-Key", internalApiKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception ex) {
            logger.warn("No se pudo confirmar reserva {}: {}", reservaId, ex.getMessage());
        }
    }

    public void liberar(String reservaId) {
        try {
            webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/internal/stock/liberar")
                            .queryParam("reservaId", reservaId)
                            .build())
                    .header("X-Internal-API-Key", internalApiKey)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception ex) {
            logger.warn("No se pudo liberar reserva {}: {}", reservaId, ex.getMessage());
        }
    }
}
