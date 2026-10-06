package com.delivery.catalogo.service;

import com.delivery.catalogo.model.Producto;
import com.delivery.catalogo.model.StockReserva;
import com.delivery.catalogo.repository.ProductoRepository;
import com.delivery.catalogo.repository.StockReservaRepository;
import com.delivery.common.dto.StockReservaRequest;
import com.delivery.common.dto.StockReservaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogoService {
    
    private final ProductoRepository productoRepository;
    private final StockReservaRepository stockReservaRepository;
    
    @Transactional
    public StockReservaResponse reservarStock(StockReservaRequest request) {
        log.info("Iniciando reserva de stock con reservaId: {}", request.getReservaId());
        
        // Validar reservaId único
        if (stockReservaRepository.existsByReservaId(request.getReservaId())) {
            log.warn("ReservaId {} ya existe", request.getReservaId());
            return StockReservaResponse.builder()
                    .exito(false)
                    .mensaje("ReservaId ya existe")
                    .build();
        }
        
        // Obtener IDs de productos sin duplicados
        List<Long> productoIds = request.getItems().stream()
                .map(StockReservaRequest.StockItemRequest::getProductoId)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        
        // Bloqueo pesimista ordenado por ID para evitar deadlocks
        List<Producto> productos = productoRepository.findByIdsWithLock(productoIds);
        
        if (productos.size() != productoIds.size()) {
            log.warn("Uno o más productos no existen");
            return StockReservaResponse.builder()
                    .exito(false)
                    .mensaje("Uno o más productos no existen")
                    .build();
        }
        
        Map<Long, Producto> productoMap = productos.stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));
        
        // Agrupar cantidades por producto si vinieran repetidos
        Map<Long, Integer> cantidadesSolicitadas = request.getItems().stream()
                .collect(Collectors.groupingBy(
                        StockReservaRequest.StockItemRequest::getProductoId,
                        Collectors.summingInt(StockReservaRequest.StockItemRequest::getCantidad)
                ));
        
        // Verificar suficiencia de stock para todos los productos antes de alterar nada
        for (Map.Entry<Long, Integer> entry : cantidadesSolicitadas.entrySet()) {
            Producto producto = productoMap.get(entry.getKey());
            if (producto.getStock() < entry.getValue()) {
                log.warn("Stock insuficiente para producto {}: disponible={}, solicitado={}", 
                        producto.getNombre(), producto.getStock(), entry.getValue());
                return StockReservaResponse.builder()
                        .exito(false)
                        .mensaje(String.format("Stock insuficiente para %s. Disponible: %d, Solicitado: %d",
                                producto.getNombre(), producto.getStock(), entry.getValue()))
                        .build();
            }
        }
        
        List<StockReserva> reservas = new ArrayList<>();
        List<StockReservaResponse.ProductoReservado> productosReservados = new ArrayList<>();
        
        for (Map.Entry<Long, Integer> entry : cantidadesSolicitadas.entrySet()) {
            Producto producto = productoMap.get(entry.getKey());
            int cantidad = entry.getValue();
            
            // Descontar stock
            producto.setStock(producto.getStock() - cantidad);
            
            // Guardar reserva
            StockReserva reserva = StockReserva.builder()
                    .reservaId(request.getReservaId())
                    .producto(producto)
                    .cantidad(cantidad)
                    .estado(StockReserva.EstadoReserva.RESERVADA)
                    .fechaExpiracion(LocalDateTime.now().plusMinutes(5))
                    .build();
            
            reservas.add(reserva);
            
            // Agregar a respuesta
            productosReservados.add(StockReservaResponse.ProductoReservado.builder()
                    .productoId(producto.getId())
                    .nombre(producto.getNombre())
                    .precio(producto.getPrecio())
                    .cantidad(cantidad)
                    .build());
        }
        
        stockReservaRepository.saveAll(reservas);
        
        log.info("Stock reservado exitosamente para reservaId: {}", request.getReservaId());
        
        return StockReservaResponse.builder()
                .exito(true)
                .mensaje("Stock reservado exitosamente")
                .productos(productosReservados)
                .build();
    }
    
    @Transactional
    public void liberarStock(String reservaId) {
        log.info("Liberando stock para reservaId: {}", reservaId);
        
        List<StockReserva> reservas = stockReservaRepository.findByReservaId(reservaId);
        
        if (reservas.isEmpty()) {
            log.warn("No se encontró reserva con reservaId: {}", reservaId);
            return;
        }
        
        for (StockReserva reserva : reservas) {
            // Idempotencia: si ya está liberada, no hacer nada
            if (reserva.getEstado() == StockReserva.EstadoReserva.LIBERADA) {
                log.info("Reserva {} ya está liberada, no-op", reservaId);
                continue;
            }
            
            if (reserva.getEstado() == StockReserva.EstadoReserva.RESERVADA) {
                // Restaurar stock con bloqueo pesimista
                Producto producto = productoRepository.findByIdWithLock(reserva.getProducto().getId())
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
                
                producto.setStock(producto.getStock() + reserva.getCantidad());
                
                // Marcar como liberada
                reserva.setEstado(StockReserva.EstadoReserva.LIBERADA);
                stockReservaRepository.save(reserva);
                
                log.info("Stock restaurado para producto {}: cantidad {}", 
                        producto.getNombre(), reserva.getCantidad());
            }
        }
        
        log.info("Stock liberado exitosamente para reservaId: {}", reservaId);
    }
    
    @Transactional
    public void confirmarReserva(String reservaId) {
        log.info("Confirmando reserva: {}", reservaId);
        
        List<StockReserva> reservas = stockReservaRepository.findByReservaId(reservaId);
        
        for (StockReserva reserva : reservas) {
            if (reserva.getEstado() == StockReserva.EstadoReserva.RESERVADA) {
                reserva.setEstado(StockReserva.EstadoReserva.CONFIRMADA);
                stockReservaRepository.save(reserva);
            }
        }
        
        log.info("Reserva confirmada: {}", reservaId);
    }
    
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void liberarReservasExpiradas() {
        LocalDateTime now = LocalDateTime.now();
        List<StockReserva> expiradas = stockReservaRepository.findReservasExpiradas(now);
        if (!expiradas.isEmpty()) {
            log.info("Liberando {} reservas expiradas", expiradas.size());
            for (StockReserva reserva : expiradas) {
                try {
                    Producto producto = productoRepository.findByIdWithLock(reserva.getProducto().getId())
                            .orElse(null);
                    if (producto != null) {
                        producto.setStock(producto.getStock() + reserva.getCantidad());
                    }
                    reserva.setEstado(StockReserva.EstadoReserva.LIBERADA);
                    stockReservaRepository.save(reserva);
                } catch (Exception e) {
                    log.error("Error al liberar reserva expirada con id {}", reserva.getId(), e);
                }
            }
        }
    }
}
